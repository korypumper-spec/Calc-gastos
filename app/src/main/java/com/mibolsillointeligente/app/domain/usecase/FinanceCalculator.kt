package com.mibolsillointeligente.app.domain.usecase

import com.mibolsillointeligente.app.core.DateRange
import com.mibolsillointeligente.app.domain.model.Budget
import com.mibolsillointeligente.app.domain.model.BudgetProgress
import com.mibolsillointeligente.app.domain.model.CategoryBreakdown
import com.mibolsillointeligente.app.domain.model.DashboardSummary
import com.mibolsillointeligente.app.domain.model.DebtPayoffSimulation
import com.mibolsillointeligente.app.domain.model.FinancialTransaction
import com.mibolsillointeligente.app.domain.model.MovementType
import com.mibolsillointeligente.app.domain.model.PaymentStatus
import com.mibolsillointeligente.app.domain.model.PeriodComparison
import com.mibolsillointeligente.app.domain.model.SavingsGoal
import com.mibolsillointeligente.app.domain.model.ScheduledPayment
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

object FinanceCalculator {
    private val hundred = BigDecimal(100)
    private val twelve = BigDecimal(12)
    private val mathContext = MathContext(10, RoundingMode.HALF_EVEN)

    /**
     * Calcula el saldo disponible de forma consistente:
     * Saldo disponible = saldo inicial + ingresos registrados - gastos registrados
     *                    - aportaciones netas reservadas en metas de ahorro.
     *
     * Los pagos próximos pendientes NO se descuentan del saldo disponible para evitar
     * doble contabilización; se muestran en un indicador separado.
     */
    fun calculateAvailableBalance(
        initialBalance: BigDecimal,
        allTransactions: List<FinancialTransaction>
    ): BigDecimal {
        var balance = initialBalance.setScale(2, RoundingMode.HALF_EVEN)
        for (tx in allTransactions) {
            val amount = tx.amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN)
            balance = when (tx.type) {
                MovementType.INCOME -> balance.add(amount)
                MovementType.EXPENSE, MovementType.DEBT_PAYMENT -> balance.subtract(amount)
                MovementType.SAVINGS_CONTRIBUTION -> balance.subtract(amount)
                MovementType.SAVINGS_WITHDRAWAL -> balance.add(amount)
                MovementType.BALANCE_ADJUSTMENT -> {
                    if (tx.isAdjustmentPositive) balance.add(amount) else balance.subtract(amount)
                }
            }
        }
        return balance.setScale(2, RoundingMode.HALF_EVEN)
    }

    fun calculateCommittedUpcoming(
        scheduledPayments: List<ScheduledPayment>,
        today: LocalDate = LocalDate.now()
    ): BigDecimal {
        return scheduledPayments
            .filter { resolveEffectiveStatus(it, today) != PaymentStatus.PAID }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }
            .setScale(2, RoundingMode.HALF_EVEN)
    }

    fun resolveEffectiveStatus(
        payment: ScheduledPayment,
        today: LocalDate = LocalDate.now()
    ): PaymentStatus {
        return when {
            payment.status == PaymentStatus.PAID -> PaymentStatus.PAID
            payment.dueDate.isBefore(today) -> PaymentStatus.OVERDUE
            else -> PaymentStatus.PENDING
        }
    }

    fun calculatePeriodIncome(
        transactions: List<FinancialTransaction>,
        range: DateRange
    ): BigDecimal {
        return transactions
            .filter { it.type == MovementType.INCOME && range.contains(it.date) }
            .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
            .setScale(2, RoundingMode.HALF_EVEN)
    }

    fun calculatePeriodExpenses(
        transactions: List<FinancialTransaction>,
        range: DateRange
    ): BigDecimal {
        return transactions
            .filter {
                (it.type == MovementType.EXPENSE || it.type == MovementType.DEBT_PAYMENT) &&
                    range.contains(it.date)
            }
            .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
            .setScale(2, RoundingMode.HALF_EVEN)
    }

    fun calculateBudgetProgress(
        budget: Budget,
        transactions: List<FinancialTransaction>,
        range: DateRange
    ): BudgetProgress {
        val spent = transactions
            .filter {
                (it.type == MovementType.EXPENSE || it.type == MovementType.DEBT_PAYMENT) &&
                    range.contains(it.date) &&
                    (budget.category == "General" || it.category.equals(budget.category, ignoreCase = true))
            }
            .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
            .setScale(2, RoundingMode.HALF_EVEN)

        val remaining = budget.limitAmount.subtract(spent).setScale(2, RoundingMode.HALF_EVEN)
        val pct = if (budget.limitAmount > BigDecimal.ZERO) {
            spent.multiply(hundred)
                .divide(budget.limitAmount, 0, RoundingMode.HALF_UP)
                .toInt()
                .coerceAtLeast(0)
        } else {
            0
        }

        val triggered = budget.alertThresholds
            .sorted()
            .lastOrNull { pct >= it }

        return BudgetProgress(
            budget = budget,
            spent = spent,
            remaining = remaining,
            percentageUsed = pct,
            highestTriggeredThreshold = triggered
        )
    }

    fun calculateDashboardSummary(
        initialBalance: BigDecimal,
        allTransactions: List<FinancialTransaction>,
        currentRange: DateRange,
        budgets: List<BudgetProgress>,
        scheduledPayments: List<ScheduledPayment>,
        savingsGoals: List<SavingsGoal>,
        today: LocalDate = LocalDate.now()
    ): DashboardSummary {
        val availableBalance = calculateAvailableBalance(initialBalance, allTransactions)
        val periodIncome = calculatePeriodIncome(allTransactions, currentRange)
        val periodExpenses = calculatePeriodExpenses(allTransactions, currentRange)
        val committed = calculateCommittedUpcoming(scheduledPayments, today)

        val totalLimit = budgets.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.budget.limitAmount) }
        val totalRemaining = budgets.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.remaining) }

        val totalSaved = savingsGoals.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.savedAmount) }
            .setScale(2, RoundingMode.HALF_EVEN)
        val totalTarget = savingsGoals.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.targetAmount) }
            .setScale(2, RoundingMode.HALF_EVEN)

        val savingsPct = if (totalTarget > BigDecimal.ZERO) {
            totalSaved.multiply(hundred)
                .divide(totalTarget, 0, RoundingMode.HALF_UP)
                .toInt()
                .coerceIn(0, 100)
        } else {
            0
        }

        return DashboardSummary(
            availableBalance = availableBalance,
            periodIncome = periodIncome,
            periodExpenses = periodExpenses,
            committedUpcomingPayments = committed,
            totalBudgetLimit = totalLimit.setScale(2, RoundingMode.HALF_EVEN),
            totalBudgetRemaining = totalRemaining.setScale(2, RoundingMode.HALF_EVEN),
            totalSavingsAccumulated = totalSaved,
            totalSavingsTarget = totalTarget,
            overallSavingsPercentage = savingsPct
        )
    }

    fun calculateSavingsPercentage(goal: SavingsGoal): Int {
        if (goal.targetAmount <= BigDecimal.ZERO) return 0
        return goal.savedAmount.multiply(hundred)
            .divide(goal.targetAmount, 0, RoundingMode.HALF_UP)
            .toInt()
            .coerceIn(0, 100)
    }

    fun calculateSavingsRemaining(goal: SavingsGoal): BigDecimal {
        return goal.targetAmount.subtract(goal.savedAmount)
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_EVEN)
    }

    fun calculateExpenseBreakdownByCategory(
        transactions: List<FinancialTransaction>,
        range: DateRange
    ): List<CategoryBreakdown> {
        val expenses = transactions.filter {
            (it.type == MovementType.EXPENSE || it.type == MovementType.DEBT_PAYMENT) &&
                range.contains(it.date)
        }
        val total = expenses.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
        if (total <= BigDecimal.ZERO) return emptyList()

        return expenses.groupBy { it.category }
            .map { (cat, list) ->
                val catTotal = list.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
                    .setScale(2, RoundingMode.HALF_EVEN)
                val pct = catTotal.multiply(hundred)
                    .divide(total, 0, RoundingMode.HALF_UP)
                    .toInt()
                    .coerceIn(0, 100)
                CategoryBreakdown(category = cat, total = catTotal, percentage = pct)
            }
            .sortedByDescending { it.total }
    }

    fun calculatePeriodComparison(
        transactions: List<FinancialTransaction>,
        currentRange: DateRange,
        previousRange: DateRange
    ): PeriodComparison {
        return PeriodComparison(
            currentIncome = calculatePeriodIncome(transactions, currentRange),
            previousIncome = calculatePeriodIncome(transactions, previousRange),
            currentExpenses = calculatePeriodExpenses(transactions, currentRange),
            previousExpenses = calculatePeriodExpenses(transactions, previousRange)
        )
    }

    /**
     * Simulador de liquidación de deuda usando cálculo mensual exacto con BigDecimal.
     */
    fun simulateDebtPayoff(
        remainingBalance: BigDecimal,
        monthlyPayment: BigDecimal,
        annualInterestRatePercent: BigDecimal
    ): DebtPayoffSimulation {
        val cleanBalance = remainingBalance.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN)
        if (cleanBalance.compareTo(BigDecimal.ZERO) == 0) {
            return DebtPayoffSimulation(
                isPayoffPossible = true,
                estimatedMonths = 0,
                totalInterestEstimated = BigDecimal.ZERO.setScale(2),
                totalPaidEstimated = BigDecimal.ZERO.setScale(2)
            )
        }
        if (monthlyPayment <= BigDecimal.ZERO) {
            return DebtPayoffSimulation(
                isPayoffPossible = false,
                estimatedMonths = 0,
                totalInterestEstimated = BigDecimal.ZERO.setScale(2),
                totalPaidEstimated = BigDecimal.ZERO.setScale(2)
            )
        }

        val monthlyRate = annualInterestRatePercent
            .max(BigDecimal.ZERO)
            .divide(hundred, mathContext)
            .divide(twelve, mathContext)

        var balance = cleanBalance
        var months = 0
        var totalInterest = BigDecimal.ZERO
        var totalPaid = BigDecimal.ZERO
        val maxMonths = 600

        while (balance > BigDecimal.ZERO && months < maxMonths) {
            val interest = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_EVEN)
            if (monthlyPayment <= interest && monthlyRate > BigDecimal.ZERO) {
                return DebtPayoffSimulation(
                    isPayoffPossible = false,
                    estimatedMonths = 0,
                    totalInterestEstimated = BigDecimal.ZERO.setScale(2),
                    totalPaidEstimated = BigDecimal.ZERO.setScale(2)
                )
            }
            val totalDueThisMonth = balance.add(interest)
            val payment = if (monthlyPayment >= totalDueThisMonth) totalDueThisMonth else monthlyPayment
            balance = totalDueThisMonth.subtract(payment).setScale(2, RoundingMode.HALF_EVEN)
            totalInterest = totalInterest.add(interest)
            totalPaid = totalPaid.add(payment)
            months++
        }

        val completed = balance.compareTo(BigDecimal.ZERO) == 0
        return DebtPayoffSimulation(
            isPayoffPossible = completed,
            estimatedMonths = if (completed) months else 0,
            totalInterestEstimated = totalInterest.setScale(2, RoundingMode.HALF_EVEN),
            totalPaidEstimated = totalPaid.setScale(2, RoundingMode.HALF_EVEN)
        )
    }

    /**
     * Exporta movimientos a formato CSV compatible con Excel y Google Sheets (RFC 4180 + BOM UTF-8).
     */
    fun exportTransactionsToCsv(transactions: List<FinancialTransaction>): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("Fecha,Tipo,Categoría,Importe (MXN),Método de pago,Frecuencia,Descripción\n")
        for (tx in transactions.sortedByDescending { it.date }) {
            val signedAmount = when (tx.type) {
                MovementType.INCOME, MovementType.SAVINGS_WITHDRAWAL -> tx.amount
                MovementType.EXPENSE, MovementType.DEBT_PAYMENT, MovementType.SAVINGS_CONTRIBUTION -> tx.amount.negate()
                MovementType.BALANCE_ADJUSTMENT -> if (tx.isAdjustmentPositive) tx.amount else tx.amount.negate()
            }
            sb.append(escapeCsv(tx.date.toString())).append(',')
            sb.append(escapeCsv(tx.type.labelEs)).append(',')
            sb.append(escapeCsv(tx.category)).append(',')
            sb.append(signedAmount.setScale(2, RoundingMode.HALF_EVEN).toPlainString()).append(',')
            sb.append(escapeCsv(tx.paymentMethod.labelEs)).append(',')
            sb.append(escapeCsv(tx.recurrence.labelEs)).append(',')
            sb.append(escapeCsv(tx.description)).append('\n')
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        // Neutralizar inyección de fórmulas en hojas de cálculo (=, +, -, @)
        val safe = if (value.isNotEmpty() && value[0] in charArrayOf('=', '+', '-', '@')) {
            "'$value"
        } else {
            value
        }
        return if (safe.contains(',') || safe.contains('"') || safe.contains('\n')) {
            "\"${safe.replace("\"", "\"\"")}\""
        } else {
            safe
        }
    }
}
