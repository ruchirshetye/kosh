package com.amitjha.kosh.repo

import com.amitjha.kosh.data.CategoryEntity
import com.amitjha.kosh.data.TransactionEntity
import com.amitjha.kosh.model.TxnType
import com.amitjha.kosh.util.Period
import com.amitjha.kosh.util.Time
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SummaryTest {

    private fun txn(day: LocalDate, type: TxnType, amount: Long, cat: String, merchant: String? = null, exclude: Boolean = false) =
        TransactionEntity(
            accountId = 1, type = type, amount = amount, categoryKey = cat, merchant = merchant,
            timestamp = Time.startOfDay(day) + 3_600_000, excludeFromStats = exclude,
        )

    @Test
    fun summarisesAndExcludesTransfers() {
        val period = Period.of(java.time.YearMonth.of(2025, 3))
        val cats = mapOf("food" to CategoryEntity(key = "food", name = "Food", type = TxnType.EXPENSE, icon = "", colorArgb = 0))
        val txns = listOf(
            txn(LocalDate.of(2025, 3, 1), TxnType.INCOME, 100_000_00, "salary"),
            txn(LocalDate.of(2025, 3, 2), TxnType.EXPENSE, 400_00, "food", "Swiggy"),
            txn(LocalDate.of(2025, 3, 2), TxnType.EXPENSE, 600_00, "food", "Swiggy"),
            txn(LocalDate.of(2025, 3, 3), TxnType.EXPENSE, 1_000_00, "shopping", "Amazon"),
            txn(LocalDate.of(2025, 3, 4), TxnType.EXPENSE, 20_000_00, "transfer"),
            txn(LocalDate.of(2025, 3, 5), TxnType.INCOME, 5_000_00, "refund", exclude = true),
            txn(LocalDate.of(2025, 4, 1), TxnType.EXPENSE, 999_00, "food"),
        )
        val s = TransactionRepository.summarise(period, txns, cats)
        assertEquals(100_000_00L, s.income)
        assertEquals(2_000_00L, s.expense)
        assertEquals(98_000_00L, s.savings)
        assertEquals("food", s.byCategory.first().categoryKey)
        assertEquals(0.5, s.byCategory.first().share, 1e-9)
        assertEquals("Swiggy", s.topMerchants.first().merchant)
        assertEquals(2, s.topMerchants.first().count)
        assertEquals(31, s.daily.size)
        assertEquals(1_000_00L, s.daily[1].expense)
    }

    @Test
    fun subcategoriesRollUpUnderTheirParentWithASplit() {
        val period = Period.of(java.time.YearMonth.of(2025, 3))
        fun cat(key: String, parent: String? = null) = CategoryEntity(key = key, name = key, type = TxnType.EXPENSE, icon = "", colorArgb = 0, parentKey = parent)
        val cats = listOf(cat("bills"), cat("bills_electricity", "bills"), cat("bills_mobile", "bills"), cat("food")).associateBy { it.key }
        val txns = listOf(
            txn(LocalDate.of(2025, 3, 2), TxnType.EXPENSE, 1_500_00, "bills_electricity", "MSEDCL"),
            txn(LocalDate.of(2025, 3, 3), TxnType.EXPENSE, 500_00, "bills_mobile", "Jio"),
            txn(LocalDate.of(2025, 3, 4), TxnType.EXPENSE, 200_00, "bills", "BillDesk"),
            txn(LocalDate.of(2025, 3, 5), TxnType.EXPENSE, 1_000_00, "food", "Swiggy"),
        )
        val s = TransactionRepository.summarise(period, txns, cats)
        assertEquals(listOf("bills", "food"), s.byCategory.map { it.categoryKey })
        val bills = s.byCategory.first()
        assertEquals(2_200_00L, bills.total)
        assertEquals(3, bills.count)
        assertEquals(listOf("bills_electricity", "bills_mobile", "bills"), bills.subs.map { it.categoryKey })
        assertEquals(emptyList<CategorySpend>(), s.byCategory[1].subs)
    }

    @Test
    fun parentBudgetCoversItsSubcategoryBudgetsInTheEnvelope() {
        fun cat(key: String, parent: String? = null) = CategoryEntity(key = key, name = key, type = TxnType.EXPENSE, icon = "", colorArgb = 0, parentKey = parent)
        val tree = com.amitjha.kosh.data.CategoryTree(listOf(cat("bills"), cat("bills_electricity", "bills"), cat("bills_mobile", "bills"), cat("food")))
        fun budget(key: String?, limit: Long) = com.amitjha.kosh.data.BudgetEntity(categoryKey = key, monthlyLimit = limit)
        // Bills' own limit already includes Electricity, so Electricity isn't assigned twice.
        assertEquals(
            listOf(5_000L, 3_000L),
            BudgetRepository.assignedLimits(listOf(budget(null, 50_000), budget("bills", 5_000), budget("bills_electricity", 2_000), budget("food", 3_000)), tree),
        )
        // Without a Bills budget, a subcategory budget counts on its own.
        assertEquals(listOf(2_000L), BudgetRepository.assignedLimits(listOf(budget("bills_electricity", 2_000)), tree))
    }

    @Test
    fun customMonthStartPeriods() {
        val p = Period.containing(LocalDate.of(2025, 3, 10), monthStartDay = 25)
        assertEquals(LocalDate.of(2025, 2, 25), p.start)
        assertEquals(LocalDate.of(2025, 3, 25), p.endExclusive)
        assertEquals(java.time.YearMonth.of(2025, 3), p.label)
        val q = Period.containing(LocalDate.of(2025, 3, 26), monthStartDay = 25)
        assertEquals(LocalDate.of(2025, 3, 25), q.start)
        assertEquals(Period.of(java.time.YearMonth.of(2025, 3), 25), p)
    }

    @Test
    fun budgetProjectionForCustomPeriod() {
        val p = Period.containing(LocalDate.of(2025, 3, 6), monthStartDay = 25)
        val proj = BudgetRepository.project(28_000_00, 10_000_00, p, LocalDate.of(2025, 3, 6))
        assertEquals(10, proj.daysElapsed)
        assertEquals(28, proj.daysInMonth)
        assertEquals(28_000_00L, proj.projected)
    }
}
