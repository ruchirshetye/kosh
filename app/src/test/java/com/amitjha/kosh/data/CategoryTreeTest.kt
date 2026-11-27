package com.amitjha.kosh.data

import com.amitjha.kosh.model.TxnType
import com.amitjha.kosh.sms.CategoryGuesser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTreeTest {
    private fun cat(key: String, parent: String? = null) =
        CategoryEntity(key = key, name = key.replaceFirstChar { it.uppercase() }, type = TxnType.EXPENSE, icon = "category", colorArgb = 0, parentKey = parent)

    private val tree = CategoryTree(
        listOf(cat("bills"), cat("food"), cat("bills_water", "bills"), cat("bills_mobile", "bills"), cat("orphan", "gone"), cat("deep", "bills_water")),
    )

    @Test
    fun subcategoriesRollUpIntoTheirParent() {
        assertEquals("bills", tree.rootOf("bills_mobile"))
        assertEquals("food", tree.rootOf("food"))
        assertEquals(mapOf("bills" to 350L, "food" to 50L), tree.rollUp(mapOf("bills" to 100L, "bills_mobile" to 200L, "food" to 50L, "bills_water" to 50L)))
    }

    @Test
    fun keysUnderAParentIncludeItsChildren() {
        assertEquals(setOf("bills", "bills_mobile", "bills_water"), tree.keysUnder("bills"))
        assertEquals(setOf("bills_mobile"), tree.keysUnder("bills_mobile"))
    }

    @Test
    fun orphansAndNestedCategoriesStayTopLevel() {
        assertFalse(tree.isSub("orphan"))
        assertEquals("orphan", tree.rootOf("orphan"))
        // One level only: a child of a subcategory is not nested further.
        assertFalse(tree.isSub("deep"))
        assertEquals(listOf("bills", "food", "orphan", "deep"), tree.roots(tree.let { listOf(cat("bills"), cat("food"), cat("bills_water", "bills"), cat("orphan", "gone"), cat("deep", "bills_water")) }).map { it.key })
    }

    @Test
    fun labelNamesTheParent() {
        assertEquals("Bills › Bills_mobile", tree.label("bills_mobile"))
        assertEquals("Food", tree.label("food"))
        assertEquals("unknown", tree.label("unknown"))
    }

    @Test
    fun builtInSubcategoriesSitUnderBillsAndMatchTheGuesser() {
        val defaults = CategoryTree(DefaultCategories.all)
        val subs = defaults.childrenOf("bills").map { it.key }.toSet()
        assertEquals(
            setOf("bills_electricity", "bills_mobile", "bills_internet", "bills_gas", "bills_water", "bills_dth", "bills_maintenance", "bills_property_tax"),
            subs,
        )
        // Every key the SMS guesser can return exists as a category.
        val keys = DefaultCategories.all.map { it.key }.toSet()
        assertTrue(CategoryGuesser.CATEGORY_KEYS.filterNot { it in keys }.toString(), CategoryGuesser.CATEGORY_KEYS.all { it in keys })
    }
}
