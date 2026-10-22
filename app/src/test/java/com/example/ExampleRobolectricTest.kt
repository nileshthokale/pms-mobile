package com.example

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Pharmacy Prescription Bridge", appName)
    }

    @Test
    fun `simplified header and four primary mobile navigation tabs work with More screen`() {
        composeRule.waitForIdle()

        // Verify simplified header title and role badge
        composeRule.onNodeWithText("Pharmacy Bridge").assertIsDisplayed()
        composeRule.onNodeWithTag("header_role_badge").assertIsDisplayed()

        // Verify 4 primary Admin mobile bottom navigation items: Home, Queue, Review, More
        composeRule.onNodeWithTag("nav_dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_incoming").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_review").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_more").assertIsDisplayed()

        // Scroll to and verify "How the app works" card on Home
        composeRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasTestTag("how_the_app_works_card"))
        composeRule.onNodeWithTag("how_the_app_works_card").assertIsDisplayed()

        // Open Ready for Counter from Home KPI card and verify back button says "Back to Home"
        composeRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasText("Ready for Counter"))
        composeRule.onNodeWithText("Ready for Counter").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back to Home").assertIsDisplayed()
        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()

        // Open More and verify all 4 secondary destinations and awaiting vs imported/completed counts
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("more_option_approved").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("awaiting Counter Sale", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("imported/completed", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("more_option_catalogue").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("more_option_history").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("more_option_settings").performScrollTo().assertIsDisplayed()

        // Open Ready for Counter Sale from More and verify back button says "Back to More"
        composeRule.onNodeWithTag("more_option_approved").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back to More").assertIsDisplayed()

        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_option_catalogue").performScrollTo().assertIsDisplayed()
    }
}
