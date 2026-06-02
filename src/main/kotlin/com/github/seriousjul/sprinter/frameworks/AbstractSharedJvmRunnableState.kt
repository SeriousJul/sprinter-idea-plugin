package com.github.seriousjul.sprinter.frameworks

import com.github.seriousjul.sprinter.SharedJvmConfiguration
import com.github.seriousjul.sprinter.SharedJvmExecutorService
import com.github.seriousjul.sprinter.getHotswapAgentJavaArgumentsProvider
import com.github.seriousjul.sprinter.settings.SharedSprinterSettingsState
import com.github.seriousjul.sprinter.settings.getSharedSprinterSettings
import com.intellij.execution.*
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.filters.ArgumentFileFilter
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.testframework.TestSearchScope
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PathUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

abstract class AbstractSharedJvmRunnableState<C: JavaTestConfigurationBase, F: TestFrameworkForRunningInSharedJVM>(
    environment: ExecutionEnvironment,
    protected val sharedJvmConfiguration: SharedJvmConfiguration,
    protected val initialTestConfiguration: C,
    protected val testFramework: F
): JavaTestFrameworkRunnableState<SharedJvmConfiguration>(environment) {
    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val process = startProcess(executor)
        val console = createConsole(executor)
        console?.attachToProcess(process)
        return DefaultExecutionResult(console, process, *createActions(console, process, executor))
    }

    private fun startProcess(executor: Executor): OSProcessHandler {
        val targetedCommandLineBuilder = targetedCommandLine
        val commandLine = targetedCommandLineBuilder.build()

        // Try to prepare a remote TargetEnvironment and create a process via reflection
        var processHandler: OSProcessHandler? = null
        var serverSocketLocal: java.net.ServerSocket? = null

        try {
            val getPreparedMethod = try {
                environment.javaClass.getMethod(
                    "getPreparedTargetEnvironment",
                    com.intellij.execution.configurations.RunProfileState::class.java,
                    TargetProgressIndicator::class.java
                )
            } catch (nnse: NoSuchMethodException) {
                null
            }

            if (getPreparedMethod != null) {
                val remoteEnvironment = getPreparedMethod.invoke(environment, this, TargetProgressIndicator.EMPTY)

                // resolveServerSocketPort requires a TargetEnvironment; call it via reflection if present
                try {
                    val resolveMethod = this::class.java.getDeclaredMethod("resolveServerSocketPort", remoteEnvironment!!::class.java)
                    resolveMethod.isAccessible = true
                    resolveMethod.invoke(this, remoteEnvironment)
                } catch (_: Throwable) {
                    // If reflection fails, do nothing; the remote environment may not require explicit port resolution.
                }

                serverSocketLocal = serverSocket

                // createProcess(remoteEnv, commandLine, EmptyProgressIndicator)
                val createMethod = try {
                    remoteEnvironment?.javaClass?.getMethod("createProcess", commandLine.javaClass, com.intellij.openapi.progress.ProgressIndicator::class.java)
                } catch (e: NoSuchMethodException) {
                    null
                }

                val proc: Process? = if (createMethod != null) {
                    createMethod.invoke(remoteEnvironment, commandLine, EmptyProgressIndicator()) as? Process
                } else {
                    null
                }

                if (proc != null) {
                    val getPresentationMethod = try {
                        commandLine.javaClass.getMethod("getCommandPresentation", remoteEnvironment.javaClass)
                    } catch (e: NoSuchMethodException) {
                        null
                    }

                    val presentation = try {
                        if (getPresentationMethod != null) getPresentationMethod.invoke(commandLine, remoteEnvironment) as? String
                        else null
                    } catch (t: Throwable) { null }

                    val presentationStr = presentation ?: commandLine.toString()

                    processHandler = KillableColoredProcessHandler.Silent(
                        proc,
                        presentationStr,
                        try { commandLine.javaClass.getMethod("getCharset").invoke(commandLine) as java.nio.charset.Charset } catch (_: Throwable) { commandLine.charset },
                        try {
                            commandLine.javaClass.getMethod("getFilesToDeleteOnTermination").invoke(commandLine) as? Collection<java.io.File>
                        } catch (_: Throwable) {
                            null
                        }?.toMutableSet() ?: targetedCommandLineBuilder.filesToDeleteOnTermination
                    )
                }
            }
        } catch (t: Throwable) {
            processHandler = null
        }

        // Fallback: try to create local process from the commandLine via reflection or available API
        if (processHandler == null) {
            val proc: Process? = try {
                try { commandLine.javaClass.getMethod("createProcess").invoke(commandLine) as? Process }
                catch (_: Throwable) { null }
            } catch (t: Throwable) { null }

            val presentationStr = try { commandLine.javaClass.getMethod("getCommandLineString").invoke(commandLine) as? String } catch (_: Throwable) { commandLine.toString() }

            val charset = try { commandLine.javaClass.getMethod("getCharset").invoke(commandLine) as java.nio.charset.Charset } catch (_: Throwable) { commandLine.charset }

            val filesToDelete = try {
                commandLine.javaClass.getMethod("getFilesToDeleteOnTermination").invoke(commandLine) as? Collection<java.io.File>
            } catch (_: Throwable) {
                null
            }?.toMutableSet() ?: targetedCommandLineBuilder.filesToDeleteOnTermination

            val procNonNull = proc ?: throw ExecutionException("Failed to create process")
            processHandler = KillableColoredProcessHandler.Silent(procNonNull, presentationStr, charset, filesToDelete)
            serverSocketLocal = serverSocket
        }

        val process = processHandler!!

        environment.project.getService(SharedJvmExecutorService::class.java)
            .saveSharedJvmProcess(process, serverSocketLocal!!, executor, testFramework)

        val content = targetedCommandLineBuilder.getUserData(JdkUtil.COMMAND_LINE_CONTENT)
        content?.forEach { (key: String, value: String) ->
            addConsoleFilters(ArgumentFileFilter(key, value))
        }
        ProcessTerminatedListener.attach(process)
        JavaRunConfigurationExtensionManager.instance.attachExtensionsToProcess(configuration, process, runnerSettings)
        return process
    }

    override fun createJavaParameters(): JavaParameters {
        return ReadAction.nonBlocking<JavaParameters> {
            val parameters = super.createJavaParameters()
            val settings = getSharedSprinterSettings(environment.project)
            addVmParametersFromInitialConfigIfNeeded(parameters, settings)
            addEnvsFromInitialConfigIfNeeded(parameters, settings)

            parameters.mainClass = mainClassName

            createServerSocket(parameters)

            createTemporaryFolderWithHotswapAgentProperties()?.let(parameters.classPath::addFirst)
            getHotswapAgentJavaArgumentsProvider(environment.project).addArguments(parameters)
            JavaRunConfigurationExtensionManager.instance.updateJavaParameters(configuration, parameters, runnerSettings, environment.executor)
            parameters
        }.executeSynchronously()
    }

    override fun isReadActionRequired(): Boolean {
        return false
    }

    protected abstract val mainClassName: String

    protected fun addVmParametersFromInitialConfigIfNeeded(
        parameters: JavaParameters,
        settings: SharedSprinterSettingsState
    ) {
        if (settings.passSystemPropsFromOriginalConfig) {
            ParametersList.parse(initialTestConfiguration.vmParameters)
                .asSequence()
                .filter { !parameters.vmParametersList.hasParameter(it) }
                .forEach(parameters.vmParametersList::add)
        }
    }

    protected fun addEnvsFromInitialConfigIfNeeded(
        parameters: JavaParameters,
        settings: SharedSprinterSettingsState
    ) {
        if (settings.passEnvironmentVariablesFromOriginalConfig) {
            val resultingEnv = mutableMapOf<String, String>()
            resultingEnv.putAll(initialTestConfiguration.envs)
            resultingEnv.putAll(parameters.env)
            parameters.env = resultingEnv
        }
    }

    override fun configureRTClasspath(parameters: JavaParameters, module: Module) {
        parameters.classPath.addFirst(PathUtil.getJarPathForClass(ULong::class.java))
    }

    private fun createTemporaryFolderWithHotswapAgentProperties(): File? {
        val hotswapProperties = getSharedSprinterSettings(environment.project).hotswapProperties
        if (hotswapProperties.isBlank()) return null
        val tempDir = FileUtil.createTempDirectory("hotswap", null, true)
        FileUtilRt.doIOOperation<Path, Throwable> {
            Files.writeString(
                tempDir.toPath().resolve("hotswap-agent.properties"),
                hotswapProperties,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE
            )
        }
        return tempDir
    }

    override fun getConfiguration(): SharedJvmConfiguration {
        return sharedJvmConfiguration
    }

    override fun getFrameworkName(): String = "SharedJvmRunner"
    override fun getFrameworkId(): String = "SharedJvmRunner"
    override fun getForkMode(): String = "none"

    override fun passTempFile(parameters: ParametersList, tempFilePath: String?) {
        throw IllegalStateException("Not expected to be called")
    }

    override fun getScope(): TestSearchScope? = null
    override fun passForkMode(forkMode: String, tempFile: File, parameters: JavaParameters) {
        throw IllegalStateException("Not expected to be called")
    }
}
