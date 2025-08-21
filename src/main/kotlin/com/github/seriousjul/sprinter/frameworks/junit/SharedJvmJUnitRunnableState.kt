package com.github.seriousjul.sprinter.frameworks.junit

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.util.PathUtil
import com.github.seriousjul.sprinter.SharedJvmConfiguration
import com.github.seriousjul.sprinter.frameworks.AbstractSharedJvmRunnableState
import com.github.seriousjul.sprinter.frameworks.TestFrameworkForRunningInSharedJVM
import com.github.seriousjul.sprinter.frameworks.junit.rt.SameJvmJUnitStarter

class SharedJvmJUnitRunnableState(
    environment: ExecutionEnvironment,
    sharedJvmConfiguration: SharedJvmConfiguration,
    initialTestConfiguration : JUnitConfiguration,
    testFramework: TestFrameworkForRunningInSharedJVM
) : AbstractSharedJvmRunnableState<JUnitConfiguration, TestFrameworkForRunningInSharedJVM>(
    environment,
    sharedJvmConfiguration,
    initialTestConfiguration,
    testFramework) {
    override val mainClassName: String
        get() = SameJvmJUnitStarter::class.qualifiedName!!

    override fun createJavaParameters(): JavaParameters {
        val parameters = super.createJavaParameters()

        val testObject = initialTestConfiguration.testObject
        val parametersForTestObject = testObject.javaParameters
        testObject.downloadAdditionalDependencies(parametersForTestObject)

        parameters.classPath.addAll(parametersForTestObject.classPath.pathList)
        //parameters.programParametersList.addAll(parametersForTestObject.programParametersList.parameters)
        return parameters
    }

    override fun configureRTClasspath(parameters: JavaParameters, module: Module) {
        super.configureRTClasspath(parameters, module)
        parameters.classPath.add(PathUtil.getJarPathForClass(SameJvmJUnitStarter::class.java))
    }
}
