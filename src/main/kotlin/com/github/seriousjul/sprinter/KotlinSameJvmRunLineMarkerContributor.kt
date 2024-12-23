package com.github.seriousjul.sprinter

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.psi.PsiElement
import com.github.seriousjul.sprinter.frameworks.testFrameworkForRunningInSharedJVMExtensionPoint
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor
import org.jetbrains.kotlin.idea.junit.JunitKotlinTestFrameworkProvider
import org.jetbrains.kotlin.psi.KtFile

class KotlinSameJvmRunLineMarkerContributor : RunLineMarkerContributor() {
    private val contributorDelegate = KotlinTestRunLineMarkerContributor()

    override fun getInfo(element: PsiElement): Info? {
        contributorDelegate.getInfo(element) ?: return null
        return calculateInfoIfTestFrameworkIsFound(element)
    }

    override fun getSlowInfo(element: PsiElement): Info? {
        contributorDelegate.getSlowInfo(element) ?: return null
        return calculateInfoIfTestFrameworkIsFound(element)
    }

    private fun calculateInfoIfTestFrameworkIsFound(element: PsiElement): Info? {
        if (!RootKindFilter.projectAndLibrarySources.matches(element) || element.containingFile !is KtFile) {
            return null
        }
        val testEntity =
            JunitKotlinTestFrameworkProvider.getInstance().getJavaTestEntity(element, checkMethod = true) ?: return null
        val testMethod = testEntity.testMethod
        val canRunTestsForElement = testFrameworkForRunningInSharedJVMExtensionPoint.extensionList.any {
            if (testMethod != null) {
                it.canRunTestsFor(testMethod)
            } else {
                it.canRunTestsFor(testEntity.testClass)
            }
        }
        return if (canRunTestsForElement) {
            Info(ActionManager.getInstance().getAction(RunTestsInExistingJvmAction.ACTION_NAME))
        } else null
    }
}