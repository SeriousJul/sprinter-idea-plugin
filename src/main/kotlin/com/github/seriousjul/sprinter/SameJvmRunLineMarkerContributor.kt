package com.github.seriousjul.sprinter

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.psi.*
import com.github.seriousjul.sprinter.frameworks.testFrameworkForRunningInSharedJVMExtensionPoint

class SameJvmRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        val canRunTestForElement = testFrameworkForRunningInSharedJVMExtensionPoint.extensionList.any {
            it.canRunTestsFor(element)
        }
        return if (canRunTestForElement) {
            Info(ActionManager.getInstance().getAction(RunTestsInExistingJvmAction.ACTION_NAME))
        } else null
    }
}

