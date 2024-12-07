package com.github.seriousjul.sprinter.frameworks.testng

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.rt.testng.RemoteTestNGStarter
import com.intellij.util.PathUtil
import com.theoryinpractice.testng.configuration.TestNGConfiguration
import com.github.seriousjul.sprinter.SharedJvmConfiguration
import com.github.seriousjul.sprinter.frameworks.AbstractSharedJvmRunnableState
import com.github.seriousjul.sprinter.frameworks.TestFrameworkForRunningInSharedJVM
import com.github.seriousjul.sprinter.frameworks.testng.rt.IDEARemoteSameJvmTestNGStarterKotlin
import com.github.seriousjul.sprinter.settings.getSharedSprinterSettings


class SharedJvmTestNGRunnableState(
    environment: ExecutionEnvironment,
    sharedJvmConfiguration: SharedJvmConfiguration,
    initialTestConfiguration: TestNGConfiguration,
    testFramework: TestFrameworkForRunningInSharedJVM
) : AbstractSharedJvmRunnableState<TestNGConfiguration, TestFrameworkForRunningInSharedJVM>(
    environment,
    sharedJvmConfiguration,
    initialTestConfiguration,
    testFramework) {
    override val mainClassName: String
        get() = IDEARemoteSameJvmTestNGStarterKotlin::class.qualifiedName!!

    override fun createJavaParameters(): JavaParameters {
        val parameters = super.createJavaParameters()

        val settings = getSharedSprinterSettings(environment.project)
        if (settings.passCMDArgsFromOriginalConfig) {
            initialTestConfiguration.programParameters
                ?.let(ParametersList::parse).orEmpty()
                .asSequence()
                .filter { parameters.programParametersList.hasParameter(it) }
                .forEach(parameters.programParametersList::add)
        }
        return parameters
    }

    override fun configureRTClasspath(parameters: JavaParameters, module: Module) {
        super.configureRTClasspath(parameters, module)
        parameters.classPath.add(PathUtil.getJarPathForClass(IDEARemoteSameJvmTestNGStarterKotlin::class.java))
        parameters.classPath.addFirst(PathUtil.getJarPathForClass(RemoteTestNGStarter::class.java))
    }
}

