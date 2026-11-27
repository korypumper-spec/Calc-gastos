package com.mibolsillointeligente.app.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class MovementType(val labelEs: String) {
    INCOME("Ingreso"),
    EXPENSE("Gasto"),
    SAVINGS_CONTRIBUTION("Aportación a ahorro"),
    SAVINGS_WITHDRAWAL("Retiro de ahorro"),
    DEBT_PAYMENT("Abono a deuda"),
    BALANCE_ADJUSTMENT("Ajuste de saldo")
}

enum class PeriodType(val labelEs: String) {
    WEEKLY("Semanal"),
    BIWEEKLY("Quincenal"),
    MONTHLY("Mensual"),
    CUSTOM("Personalizado")
}

enum class RecurrenceFrequency(val labelEs: String) {
    NONE("Único"),
    WEEKLY("Semanal"),
    BIWEEKLY("Quincenal"),
    MONTHLY("Mensual")
}

enum class PaymentMethod(val labelEs: String) {
    CASH("Efectivo"),
    DEBIT("Tarjeta de débito"),
    CREDIT("Tarjeta de crédito"),
    TRANSFER("Transferencia SPEI"),
    OTHER("Otro")
}

enum class PaymentStatus(val labelEs: String) {
    PENDING("Pendiente"),
    PAID("Pagado"),
    OVERDUE("Vencido")
}

enum class ThemeMode(val labelEs: String) {
    SYSTEM("Según sistema"),
    LIGHT("Tema claro"),
    DARK("Tema oscuro")
}

object FinanceCategories {
    val incomeCategories = listOf(
        "Sueldo",
        "Ingresos adicionales",
        "Comisiones",
        "Ventas",
        "Apoyos",
        "Otros ingresos"
    )

    val expenseCategories = listOf(
        "Alimentación",
        "Transporte",
        "Vivienda",
        "Servicios",
        "Salud",
        "Educación",
        "Entretenimiento",
        "Compras",
        "Deudas",
        "Otros gastos"
    )

    val scheduledPaymentCategories = listOf(
        "Renta",
        "Servicios",
        "Tarjetas de crédito",
        "Préstamos",
        "Suscripciones",
        "Colegiaturas",
        "Otros compromisos"
    )
}

data class FinancialTransaction(
    val id: Long = 0,
    val type: MovementType,
    val amount: BigDecimal,
    val category: String,
    val date: LocalDate,
    val description: String = "",
    val recurrence: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val isAdjustmentPositive: Boolean = true,
    val idempotencyKey: String,
    val linkedEntityId: Long? = null
)

data class Budget(
    val id: Long = 0,
    val category: String,
    val periodType: PeriodType,
    val limitAmount: BigDecimal,
    val alertThresholds: List<Int> = listOf(50, 75, 90, 100)
)

data class BudgetProgress(
    val budget: Budget,
    val spent: BigDecimal,
    val remaining: BigDecimal,
    val percentageUsed: Int,
    val highestTriggeredThreshold: Int?
)

data class ScheduledPayment(
    val id: Long = 0,
    val description: String,
    val category: String,
    val amount: BigDecimal,
    val dueDate: LocalDate,
    val status: PaymentStatus,
    val recurrence: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val linkedTransactionId: Long? = null
)

data class SavingsGoal(
    val id: Long = 0,
    val name: String,
    val targetAmount: BigDecimal,
    val savedAmount: BigDecimal,
    val targetDate: LocalDate?,
    val description: String = "",
    val iconKey: String = "savings"
)

data class Debt(
    val id: Long = 0,
    val creditorName: String,
    val initialBalance: BigDecimal,
    val remainingBalance: BigDecimal,
    val dueDate: LocalDate?,
    val minimumPayment: BigDecimal,
    val annualInterestRatePercent: BigDecimal
)

data class DebtPaymentRecord(
    val id: Long = 0,
    val debtId: Long,
    val amount: BigDecimal,
    val date: LocalDate,
    val note: String = ""
)

data class DebtPayoffSimulation(
    val isPayoffPossible: Boolean,
    val estimatedMonths: Int,
    val totalInterestEstimated: BigDecimal,
    val totalPaidEstimated: BigDecimal,
    val disclaimer: String = "Estimación orientativa basada en los datos ingresados y pagos constantes; no sustituye un estado de cuenta oficial."
)

data class DashboardSummary(
    val availableBalance: BigDecimal,
    val periodIncome: BigDecimal,
    val periodExpenses: BigDecimal,
    val committedUpcomingPayments: BigDecimal,
    val totalBudgetLimit: BigDecimal,
    val totalBudgetRemaining: BigDecimal,
    val totalSavingsAccumulated: BigDecimal,
    val totalSavingsTarget: BigDecimal,
    val overallSavingsPercentage: Int
)

data class CategoryBreakdown(
    val category: String,
    val total: BigDecimal,
    val percentage: Int
)

data class PeriodComparison(
    val currentIncome: BigDecimal,
    val previousIncome: BigDecimal,
    val currentExpenses: BigDecimal,
    val previousExpenses: BigDecimal
)
