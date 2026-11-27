package com.mibolsillointeligente.app

import com.mibolsillointeligente.app.core.CurrencyFormatter
import com.mibolsillointeligente.app.core.DateUtils
import com.mibolsillointeligente.app.domain.model.Budget
import com.mibolsillointeligente.app.domain.model.FinancialTransaction
import com.mibolsillointeligente.app.domain.model.MovementType
import com.mibolsillointeligente.app.domain.model.PaymentStatus
import com.mibolsillointeligente.app.domain.model.PeriodType
import com.mibolsillointeligente.app.domain.model.SavingsGoal
import com.mibolsillointeligente.app.domain.model.ScheduledPayment
import com.mibolsillointeligente.app.domain.usecase.FinanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FinanceCalculatorTest {

    private fun tx(
        id: Long,
        type: MovementType,
        amount: String,
        category: String = "General",
        date: LocalDate = LocalDate.of(2026, 5, 10),
        isAdjustmentPositive: Boolean = true,
        key: String = "k_$id"
    ) = FinancialTransaction(
        id = id,
        type = type,
        amount = BigDecimal(amount),
        category = category,
        date = date,
        idempotencyKey = key,
        isAdjustmentPositive = isAdjustmentPositive
    )

    @Test
    fun `available balance matches initial plus incomes minus expenses`() {
        val initial = BigDecimal("1000.00")
        val movements = listOf(
            tx(1, MovementType.INCOME, "5500.50", "Sueldo"),
            tx(2, MovementType.EXPENSE, "1250.25", "Alimentación"),
            tx(3, MovementType.EXPENSE, "300.00", "Transporte")
        )
        val result = FinanceCalculator.calculateAvailableBalance(initial, movements)
        assertEquals(BigDecimal("4950.25"), result)
    }

    @Test
    fun `committed upcoming payments do not double deduct available balance`() {
        val initial = BigDecimal("2000.00")
        val movements = listOf(tx(1, MovementType.INCOME, "3000.00", "Sueldo"))
        val scheduled = listOf(
            ScheduledPayment(
                id = 1,
                description = "Renta",
                category = "Renta",
                amount = BigDecimal("4000.00"),
                dueDate = LocalDate.now().plusDays(3),
                status = PaymentStatus.PENDING
            )
        )
        val range = DateUtils.getPeriodRange(PeriodType.MONTHLY, LocalDate.of(2026, 5, 10))
        val summary = FinanceCalculator.calculateDashboardSummary(
            initialBalance = initial,
            allTransactions = movements,
            currentRange = range,
            budgets = emptyList(),
            scheduledPayments = scheduled,
            savingsGoals = emptyList()
        )
        assertEquals(BigDecimal("5000.00"), summary.availableBalance)
        assertEquals(BigDecimal("4000.00"), summary.committedUpcomingPayments)
    }

    @Test
    fun `savings contributions and withdrawals update balance consistently without double counting`() {
        val initial = BigDecimal("3000.00")
        val movements = listOf(
            tx(1, MovementType.SAVINGS_CONTRIBUTION, "1000.00", "Ahorro"),
            tx(2, MovementType.SAVINGS_WITHDRAWAL, "250.00", "Ahorro")
        )
        val available = FinanceCalculator.calculateAvailableBalance(initial, movements)
        assertEquals(BigDecimal("2250.00"), available)

        val goal = SavingsGoal(
            id = 1,
            name = "Emergencia",
            targetAmount = BigDecimal("2000.00"),
            savedAmount = BigDecimal("750.00"),
            targetDate = null
        )
        assertEquals(38, FinanceCalculator.calculateSavingsPercentage(goal))
        assertEquals(BigDecimal("1250.00"), FinanceCalculator.calculateSavingsRemaining(goal))
    }

    @Test
    fun `budget progress calculates remaining and triggers configurable thresholds`() {
        val range = DateUtils.getPeriodRange(PeriodType.BIWEEKLY, LocalDate.of(2026, 5, 10))
        val budget = Budget(
            id = 1,
            category = "Alimentación",
            periodType = PeriodType.BIWEEKLY,
            limitAmount = BigDecimal("2000.00"),
            alertThresholds = listOf(50, 75, 90, 100)
        )
        val txs = listOf(
            tx(1, MovementType.EXPENSE, "1550.00", "Alimentación", LocalDate.of(2026, 5, 12)),
            tx(2, MovementType.EXPENSE, "500.00", "Transporte", LocalDate.of(2026, 5, 12)),
            tx(3, MovementType.EXPENSE, "900.00", "Alimentación", LocalDate.of(2026, 5, 20)) // fuera de 1ra quincena
        )
        val progress = FinanceCalculator.calculateBudgetProgress(budget, txs, range)
        assertEquals(BigDecimal("1550.00"), progress.spent)
        assertEquals(BigDecimal("450.00"), progress.remaining)
        assertEquals(78, progress.percentageUsed)
        assertEquals(75, progress.highestTriggeredThreshold)
    }

    @Test
    fun `debt payment reduces balance and payoff simulator estimates months`() {
        val simOk = FinanceCalculator.simulateDebtPayoff(
            remainingBalance = BigDecimal("5000.00"),
            monthlyPayment = BigDecimal("1000.00"),
            annualInterestRatePercent = BigDecimal("24.00")
        )
        assertTrue(simOk.isPayoffPossible)
        assertTrue(simOk.estimatedMonths in 5..7)
        assertTrue(simOk.totalInterestEstimated > BigDecimal.ZERO)

        val simImpossible = FinanceCalculator.simulateDebtPayoff(
            remainingBalance = BigDecimal("10000.00"),
            monthlyPayment = BigDecimal("100.00"),
            annualInterestRatePercent = BigDecimal("36.00")
        )
        assertFalse(simImpossible.isPayoffPossible)
    }

    @Test
    fun `biweekly period splits month accurately into 1-15 and 16-end`() {
        val firstHalf = DateUtils.getPeriodRange(PeriodType.BIWEEKLY, LocalDate.of(2026, 2, 10))
        assertEquals(LocalDate.of(2026, 2, 1), firstHalf.start)
        assertEquals(LocalDate.of(2026, 2, 15), firstHalf.end)

        val secondHalf = DateUtils.getPeriodRange(PeriodType.BIWEEKLY, LocalDate.of(2026, 2, 20))
        assertEquals(LocalDate.of(2026, 2, 16), secondHalf.start)
        assertEquals(LocalDate.of(2026, 2, 28), secondHalf.end)
    }

    @Test
    fun `amount parser rejects zero negative and invalid values while handling large extremes`() {
        assertNull(CurrencyFormatter.parseAmountInput("0"))
        assertNull(CurrencyFormatter.parseAmountInput("-50.00"))
        assertNull(CurrencyFormatter.parseAmountInput("abc"))
        assertEquals(BigDecimal("0.00"), CurrencyFormatter.parseAmountInput("0", allowZero = true))
        assertEquals(
            BigDecimal("999999999.99"),
            CurrencyFormatter.parseAmountInput("999999999.99")
        )
        assertNull(CurrencyFormatter.parseAmountInput("1000000000.00"))
    }

    @Test
    fun `editing and deleting transactions recalculates balance cleanly`() {
        val initial = BigDecimal("500.00")
        val list = mutableListOf(
            tx(1, MovementType.INCOME, "1000.00"),
            tx(2, MovementType.EXPENSE, "200.00")
        )
        assertEquals(BigDecimal("1300.00"), FinanceCalculator.calculateAvailableBalance(initial, list))

        // Editar gasto a 350.00
        list[1] = list[1].copy(amount = BigDecimal("350.00"))
        assertEquals(BigDecimal("1150.00"), FinanceCalculator.calculateAvailableBalance(initial, list))

        // Eliminar gasto
        list.removeAt(1)
        assertEquals(BigDecimal("1500.00"), FinanceCalculator.calculateAvailableBalance(initial, list))
    }

    @Test
    fun `csv export neutralizes spreadsheet formula injection and includes utf8 bom`() {
        val maliciousTx = tx(1, MovementType.EXPENSE, "100.00", "Otros").copy(
            description = "=CMD|' /C calc'!A0"
        )
        val csv = FinanceCalculator.exportTransactionsToCsv(listOf(maliciousTx))
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("'=CMD"))
        assertTrue(csv.contains("-100.00"))
    }
}
