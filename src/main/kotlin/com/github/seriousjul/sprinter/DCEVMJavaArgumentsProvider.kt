package com.github.seriousjul.sprinter

import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.github.seriousjul.sprinter.settings.UsedJvmType
import com.github.seriousjul.sprinter.settings.getLocalSprinterSettings
import com.github.seriousjul.sprinter.settings.getModulesWithHotSwapAgentPluginsService
import com.github.seriousjul.sprinter.settings.getSharedSprinterSettings

interface DCEVMJavaArgumentsProvider {
    fun addArguments(params: JavaParameters)
}

class DCEVMJavaArgumentsProviderImpl(project: Project) : DCEVMJavaArgumentsProvider {
    private val localSettings = getLocalSprinterSettings(project)
    private val sharedSettings = getSharedSprinterSettings(project)
    private val modulesWithHotSwapAgentPluginsService = getModulesWithHotSwapAgentPluginsService(project)

    override fun addArguments(params: JavaParameters) {
        if (localSettings.usedJvmType != UsedJvmType.DCEVM) {
            return
        }

        params.vmParametersList.addParametersString(sharedSettings.additionalJvmParameters)

        val hotswapAgentSpecified = localSettings.hotswapAgentLocation.isNotBlank()
        if (hotswapAgentSpecified) {
            val hotswapAgentWithParameters = "-javaagent:${localSettings.hotswapAgentLocation}".let {
                if (sharedSettings.hotswapAgentCMDArguments.isNotBlank()) {
                    "$it=${sharedSettings.hotswapAgentCMDArguments}"
                } else it
            }
            params.vmParametersList.addAll(
                "-XX:HotswapAgent=external",
                hotswapAgentWithParameters
            )
            modulesWithHotSwapAgentPluginsService.getModules().forEach {
                OrderEnumerator.orderEntries(it).recursively().withoutSdk().classes()
                    .collectPaths(params.classPath)
            }
        }
    }
}

fun getHotswapAgentJavaArgumentsProvider(project: Project): DCEVMJavaArgumentsProvider {
    return project.getService(DCEVMJavaArgumentsProvider::class.java)
}