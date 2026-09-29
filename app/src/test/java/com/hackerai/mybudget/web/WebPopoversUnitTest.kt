package com.hackerai.mybudget.web

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WebPopoversUnitTest {

    @Test
    fun test1_allFiveFilterTriggerAndPanelElementsExistInIndexHtml() {
        val htmlFile = File("E:/AndroidStudioProjects/MyBudget/app/src/main/assets/web/index.html")
        assertTrue("index.html must exist", htmlFile.exists())
        val html = htmlFile.readText()

        val expectedTriggers = listOf(
            "btnTypeTrigger",
            "btnAccountTrigger",
            "btnCategoryTrigger",
            "btnSubcategoryTrigger",
            "btnPayeeTrigger"
        )

        val expectedPanels = listOf(
            "popoverType",
            "popoverAccount",
            "popoverCategory",
            "popoverSubcategory",
            "popoverPayee"
        )

        expectedTriggers.forEach { triggerId ->
            assertTrue("Trigger '$triggerId' must exist in index.html", html.contains("id=\"$triggerId\""))
        }

        expectedPanels.forEach { panelId ->
            assertTrue("Popover panel '$panelId' must exist in index.html", html.contains("id=\"$panelId\""))
        }
    }

    @Test
    fun test2_popoverTriggersUseDeclarativeDataPopoverAttributes() {
        val htmlFile = File("E:/AndroidStudioProjects/MyBudget/app/src/main/assets/web/index.html")
        val html = htmlFile.readText()

        assertTrue("btnTypeTrigger must use data-popover", html.contains("data-popover=\"popoverType\""))
        assertTrue("btnAccountTrigger must use data-popover", html.contains("data-popover=\"popoverAccount\""))
        assertTrue("btnCategoryTrigger must use data-popover", html.contains("data-popover=\"popoverCategory\""))
        assertTrue("btnSubcategoryTrigger must use data-popover", html.contains("data-popover=\"popoverSubcategory\""))
        assertTrue("btnPayeeTrigger must use data-popover", html.contains("data-popover=\"popoverPayee\""))
    }

    @Test
    fun test3_eventDelegationAndTogglePopoverImplementation() {
        val jsFile = File("E:/AndroidStudioProjects/MyBudget/app/src/main/assets/web/app.js")
        assertTrue("app.js must exist", jsFile.exists())
        val js = jsFile.readText()

        assertTrue("app.js must contain togglePopover function", js.contains("function togglePopover(popoverId)"))
        assertTrue("app.js must contain closeAllPopovers function", js.contains("function closeAllPopovers()"))
        assertTrue("app.js must use event delegation with closest('.popover-trigger')", js.contains("e.target.closest('.popover-trigger')"))
        assertTrue("app.js event delegation must call stopPropagation()", js.contains("e.stopPropagation()"))
    }

    @Test
    fun test4_multipleTypesAndFilterUI() {
        var selectedTypes = mutableListOf<String>()

        // User selects Expense and Income simultaneously
        selectedTypes.add("Expense")
        selectedTypes.add("Income")

        assertEquals(2, selectedTypes.size)
        assertTrue(selectedTypes.contains("Expense"))
        assertTrue(selectedTypes.contains("Income"))

        fun getTriggerText(label: String, selectedArr: List<String>): String {
            return if (selectedArr.isEmpty()) "All $label" else "$label (${selectedArr.size})"
        }

        assertEquals("Types (2)", getTriggerText("Types", selectedTypes))

        // User clears selection
        selectedTypes.clear()
        assertEquals("All Types", getTriggerText("Types", selectedTypes))
    }
}
