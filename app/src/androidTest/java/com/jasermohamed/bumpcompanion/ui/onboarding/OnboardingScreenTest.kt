package com.jasermohamed.bumpcompanion.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jasermohamed.bumpcompanion.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun advancesAndCanComplete() {
        var completed = false
        composeRule.setContent {
            MaterialTheme { OnboardingScreen { completed = true } }
        }

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_title_1)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.next)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_title_2)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.skip)).performClick()

        composeRule.runOnIdle { assertTrue(completed) }
    }
}
