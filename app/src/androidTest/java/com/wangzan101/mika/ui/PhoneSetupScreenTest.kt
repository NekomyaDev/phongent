package com.wangzan101.mika.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wangzan101.mika.core.shell.BuiltinShellFailure
import com.wangzan101.mika.core.shell.BuiltinShellFix
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.core.shell.PairingDiscovery
import com.wangzan101.mika.core.shell.PhoneSetupFlow
import com.wangzan101.mika.core.shell.SetupInputs
import com.wangzan101.mika.core.shell.SetupStep
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 引导页和「高级」里的手机操作几行(只用假数据,不碰系统设置)。 */
@RunWith(AndroidJUnit4::class)
class PhoneSetupScreenTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun show(inputs: SetupInputs, headsUpLowered: Boolean = false) = compose.setContent {
        PhoneSetupContent(
            view = PhoneSetupFlow.view(inputs),
            onStepAction = { calls += "step $it" },
            onFailureFix = { calls += "fix $it" },
            onAllowNotifications = { calls += "notify" },
            onWirelessAlreadyOn = { calls += "assume" },
            onSubmit = { c, p -> calls += "submit $c $p" },
            onClose = { calls += "close" },
            headsUpLowered = headsUpLowered,
            onOpenHeadsUp = { calls += "headsUp" },
            onAppInfo = { calls += "appInfo" },
            onSkipAccessibility = { calls += "skipA11y" },
        )
    }

    @Test
    fun firstStepOpensAboutPhone() {
        show(SetupInputs(false, false, BuiltinShellStatus.NotPaired))
        compose.onNodeWithText(PhoneSetupCopy.step(SetupStep.DeveloperOptions).title).assertIsDisplayed()
        compose.onNodeWithTag("setup-action").performClick()
        compose.onNodeWithTag("setup-later").performClick()
        assertEquals(listOf("step DeveloperOptions", "close"), calls)
    }

    @Test
    fun accessibilityStepOpensSettingsOffersAppInfoAndCanBeSkipped() {
        show(SetupInputs(true, true, BuiltinShellStatus.NotPaired, accessibility = false, restrictedSettings = true))
        compose.onNodeWithText(PhoneSetupCopy.step(SetupStep.Accessibility).title).assertIsDisplayed()
        compose.onNodeWithTag("setup-action").performClick()
        compose.onNodeWithTag("setup-app-info").performClick()
        compose.onNodeWithTag("setup-skip-a11y").performClick()
        assertEquals(listOf("step Accessibility", "appInfo", "skipA11y"), calls)
    }

    @Test
    fun pairingHintDependsOnAutoRead() {
        show(SetupInputs(true, true, BuiltinShellStatus.NotPaired, accessibility = false, accessibilitySkipped = true))
        compose.onNodeWithText(PhoneSetupCopy.PULL_DOWN_HINT).assertIsDisplayed()
    }

    @Test
    fun pairingStepWarnsWhenHeadsUpIsLowered() {
        show(SetupInputs(true, true, BuiltinShellStatus.NotPaired, PairingDiscovery.Searching, notificationsAllowed = true), headsUpLowered = true)
        compose.onNodeWithTag("setup-heads-up").assertIsDisplayed()
        compose.onNodeWithText(PhoneSetupCopy.HEADS_UP_LOWERED).assertIsDisplayed()
        compose.onNodeWithTag("setup-heads-up-open").performClick()
        assertEquals(listOf("headsUp"), calls)
    }

    @Test
    fun pairingStepTakesAManualCode() {
        show(SetupInputs(true, true, BuiltinShellStatus.NotPaired, PairingDiscovery.Searching))
        compose.onNodeWithTag("setup-manual").performClick()
        compose.onNodeWithTag("setup-code").performTextInput("123 456")
        compose.onNodeWithTag("setup-port").performTextInput("37001")
        // 真机上键盘弹出来会把按钮挤到可见区下面,先滚过去再点
        compose.onNodeWithTag("setup-submit").performScrollTo().performClick()
        assertEquals(listOf("submit 123456 37001"), calls)
    }

    @Test
    fun failureShowsReasonAndOneTapFix() {
        show(SetupInputs(true, true, BuiltinShellStatus.Failed(BuiltinShellFailure.HelperStartFailed)))
        compose.onNodeWithText(PhoneSetupCopy.failure(BuiltinShellFailure.HelperStartFailed).title).assertIsDisplayed()
        compose.onNodeWithTag("setup-fix").performClick()
        assertEquals(listOf("fix ${BuiltinShellFix.Retry}"), calls)
    }

    @Test
    fun successKeepsChecklistAndWaitsForTheDoneButton() {
        show(SetupInputs(true, true, BuiltinShellStatus.Running))
        compose.onNodeWithText(PhoneSetupCopy.SUCCESS_LINE).assertIsDisplayed()
        compose.onNodeWithText(PhoneSetupCopy.step(SetupStep.DeveloperOptions).title).assertIsDisplayed()
        compose.onNodeWithText(PhoneSetupCopy.SUCCESS_NOTE).assertIsDisplayed()
        compose.onNodeWithText(PhoneSetupCopy.SUCCESS_DONE).assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithTag("setup-done").performClick()
        assertEquals(listOf("close"), calls)
    }

    @Test
    fun unpairNeedsConfirmation() {
        compose.setContent { PhoneHelperSection(BuiltinShellStatus.Running, onRepair = { calls += "repair" }, onUnpair = { calls += "unpair" }) }
        compose.onNodeWithText(PhoneSetupCopy.REPAIR).performClick()
        compose.onNodeWithText(PhoneSetupCopy.UNPAIR).performClick()
        compose.onNodeWithText(PhoneSetupCopy.UNPAIR_TITLE).assertIsDisplayed()
        compose.onNodeWithText(PhoneSetupCopy.UNPAIR_CONFIRM).performClick()
        assertEquals(listOf("repair", "unpair"), calls)
    }
}
