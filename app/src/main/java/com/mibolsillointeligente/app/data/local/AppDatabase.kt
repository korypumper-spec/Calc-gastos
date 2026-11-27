package com.mibolsillointeligente.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mibolsillointeligente.app.data.local.dao.BudgetDao
import com.mibolsillointeligente.app.data.local.dao.DebtDao
import com.mibolsillointeligente.app.data.local.dao.SavingsGoalDao
import com.mibolsillointeligente.app.data.local.dao.ScheduledPaymentDao
import com.mibolsillointeligente.app.data.local.dao.TransactionDao
import com.mibolsillointeligente.app.data.local.entity.BudgetEntity
import com.mibolsillointeligente.app.data.local.entity.DebtEntity
import com.mibolsillointeligente.app.data.local.entity.DebtPaymentEntity
import com.mibolsillointeligente.app.data.local.entity.SavingsGoalEntity
import com.mibolsillointeligente.app.data.local.entity.ScheduledPaymentEntity
import com.mibolsillointeligente.app.data.local.entity.TransactionEntity
import com.mibolsillointeligente.app.domain.model.MovementType
import com.mibolsillointeligente.app.domain.model.PaymentMethod
import com.mibolsillointeligente.app.domain.model.PaymentStatus
import com.mibolsillointeligente.app.domain.model.RecurrenceFrequency

@Database(
    entities = [
        TransactionEntity::class,
        BudgetEntity::class,
        ScheduledPaymentEntity::class,
        SavingsGoalEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun scheduledPaymentDao(): ScheduledPaymentDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun debtDao(): DebtDao

    /**
     * Marca un pago programado como pagado y registra el gasto correspondiente
     * dentro de una sola transacción atómica para evitar duplicados o descuadres.
     */
    @Transaction
    open suspend fun markScheduledPaymentPaidAtomic(
        paymentId: Long,
        paidDateEpochDay: Long
    ): Boolean {
        val payment = scheduledPaymentDao().getById(paymentId) ?: return false
        if (payment.status == PaymentStatus.PAID.name) return false

        val idempotencyKey = "scheduled_payment_${payment.id}"
        val expenseCategory = when (payment.category) {
            "Renta" -> "Vivienda"
            "Tarjetas de crédito", "Préstamos" -> "Deudas"
            "Colegiaturas" -> "Educación"
            "Servicios" -> "Servicios"
            else -> "Otros gastos"
        }

        val txId = transactionDao().insertIgnoreDuplicate(
            TransactionEntity(
                type = MovementType.EXPENSE.name,
                amountCentavos = payment.amountCentavos,
                category = expenseCategory,
                dateEpochDay = paidDateEpochDay,
                description = "Pago programado: ${payment.description}",
                recurrence = payment.recurrence,
                paymentMethod = PaymentMethod.TRANSFER.name,
                isAdjustmentPositive = false,
                idempotencyKey = idempotencyKey,
                linkedEntityId = payment.id
            )
        )
        if (txId <= 0L) return false

        scheduledPaymentDao().update(
            payment.copy(
                status = PaymentStatus.PAID.name,
                linkedTransactionId = txId
            )
        )
        return true
    }

    /**
     * Registra una aportación o retiro de una meta de ahorro de forma atómica.
     */
    @Transaction
    open suspend fun recordSavingsMovementAtomic(
        goalId: Long,
        amountCentavos: Long,
        isContribution: Boolean,
        dateEpochDay: Long,
        note: String,
        idempotencyKey: String
    ): Boolean {
        if (amountCentavos <= 0L) return false
        val goal = savingsGoalDao().getById(goalId) ?: return false
        if (!isContribution && goal.savedCentavos < amountCentavos) return false

        val movementType = if (isContribution) {
            MovementType.SAVINGS_CONTRIBUTION
        } else {
            MovementType.SAVINGS_WITHDRAWAL
        }

        val txId = transactionDao().insertIgnoreDuplicate(
            TransactionEntity(
                type = movementType.name,
                amountCentavos = amountCentavos,
                category = "Ahorro",
                dateEpochDay = dateEpochDay,
                description = if (note.isNotBlank()) "${goal.name}: $note" else goal.name,
                recurrence = RecurrenceFrequency.NONE.name,
                paymentMethod = PaymentMethod.TRANSFER.name,
                isAdjustmentPositive = !isContribution,
                idempotencyKey = idempotencyKey,
                linkedEntityId = goal.id
            )
        )
        if (txId <= 0L) return false

        val updatedSaved = if (isContribution) {
            goal.savedCentavos + amountCentavos
        } else {
            (goal.savedCentavos - amountCentavos).coerceAtLeast(0L)
        }
        savingsGoalDao().update(goal.copy(savedCentavos = updatedSaved))
        return true
    }

    /**
     * Registra un abono a deuda actualizando saldo pendiente, historial de abonos y movimientos.
     */
    @Transaction
    open suspend fun recordDebtPaymentAtomic(
        debtId: Long,
        amountCentavos: Long,
        dateEpochDay: Long,
        note: String,
        idempotencyKey: String
    ): Boolean {
        if (amountCentavos <= 0L) return false
        val debt = debtDao().getById(debtId) ?: return false
        val appliedAmount = amountCentavos.coerceAtMost(debt.remainingBalanceCentavos)
        if (appliedAmount <= 0L) return false

        val txId = transactionDao().insertIgnoreDuplicate(
            TransactionEntity(
                type = MovementType.DEBT_PAYMENT.name,
                amountCentavos = appliedAmount,
                category = "Deudas",
                dateEpochDay = dateEpochDay,
                description = if (note.isNotBlank()) "Abono ${debt.creditorName}: $note" else "Abono a ${debt.creditorName}",
                recurrence = RecurrenceFrequency.NONE.name,
                paymentMethod = PaymentMethod.TRANSFER.name,
                isAdjustmentPositive = false,
                idempotencyKey = idempotencyKey,
                linkedEntityId = debt.id
            )
        )
        if (txId <= 0L) return false

        debtDao().insertDebtPayment(
            DebtPaymentEntity(
                debtId = debt.id,
                amountCentavos = appliedAmount,
                dateEpochDay = dateEpochDay,
                note = note
            )
        )
        debtDao().updateDebt(
            debt.copy(remainingBalanceCentavos = (debt.remainingBalanceCentavos - appliedAmount).coerceAtLeast(0L))
        )
        return true
    }

    @Transaction
    open suspend fun clearAllFinancialTables() {
        debtDao().deleteAllPayments()
        debtDao().deleteAllDebts()
        scheduledPaymentDao().deleteAll()
        savingsGoalDao().deleteAll()
        budgetDao().deleteAll()
        transactionDao().deleteAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Ejemplo de migración explícita para futuras actualizaciones de esquema sin pérdida de datos.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reservada para futuras versiones del esquema
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mi_bolsillo_inteligente.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
