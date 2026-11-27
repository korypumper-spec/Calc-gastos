package com.mibolsillointeligente.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["dateEpochDay"]),
        Index(value = ["type"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amountCentavos: Long,
    val category: String,
    val dateEpochDay: Long,
    val description: String,
    val recurrence: String,
    val paymentMethod: String,
    val isAdjustmentPositive: Boolean,
    val idempotencyKey: String,
    val linkedEntityId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["category", "periodType"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val periodType: String,
    val limitCentavos: Long,
    val thresholdsCsv: String
)

@Entity(
    tableName = "scheduled_payments",
    indices = [Index(value = ["dueDateEpochDay"]), Index(value = ["status"])]
)
data class ScheduledPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val category: String,
    val amountCentavos: Long,
    val dueDateEpochDay: Long,
    val status: String,
    val recurrence: String,
    val linkedTransactionId: Long? = null
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetCentavos: Long,
    val savedCentavos: Long,
    val targetDateEpochDay: Long?,
    val description: String,
    val iconKey: String
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creditorName: String,
    val initialBalanceCentavos: Long,
    val remainingBalanceCentavos: Long,
    val dueDateEpochDay: Long?,
    val minimumPaymentCentavos: Long,
    val annualInterestRateBps: Int
)

@Entity(
    tableName = "debt_payments",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["debtId"])]
)
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val amountCentavos: Long,
    val dateEpochDay: Long,
    val note: String
)
