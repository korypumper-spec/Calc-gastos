package com.mibolsillointeligente.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.mibolsillointeligente.app.core.AppResult
import com.mibolsillointeligente.app.core.CurrencyFormatter
import com.mibolsillointeligente.app.core.SafeLogger
import com.mibolsillointeligente.app.data.local.AppDatabase
import com.mibolsillointeligente.app.data.local.entity.BudgetEntity
import com.mibolsillointeligente.app.data.local.entity.DebtEntity
import com.mibolsillointeligente.app.data.local.entity.DebtPaymentEntity
import com.mibolsillointeligente.app.data.local.entity.SavingsGoalEntity
import com.mibolsillointeligente.app.data.local.entity.ScheduledPaymentEntity
import com.mibolsillointeligente.app.data.local.entity.TransactionEntity
import com.mibolsillointeligente.app.domain.model.Budget
import com.mibolsillointeligente.app.domain.model.Debt
import com.mibolsillointeligente.app.domain.model.DebtPaymentRecord
import com.mibolsillointeligente.app.domain.model.FinancialTransaction
import com.mibolsillointeligente.app.domain.model.MovementType
import com.mibolsillointeligente.app.domain.model.PaymentMethod
import com.mibolsillointeligente.app.domain.model.PaymentStatus
import com.mibolsillointeligente.app.domain.model.PeriodType
import com.mibolsillointeligente.app.domain.model.RecurrenceFrequency
import com.mibolsillointeligente.app.domain.model.SavingsGoal
import com.mibolsillointeligente.app.domain.model.ScheduledPayment
import com.mibolsillointeligente.app.domain.usecase.FinanceCalculator
import com.mibolsillointeligente.app.settings.AppPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

class FinanceRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: AppPreferencesDataStore
) {
    private val transactionDao = database.transactionDao()
    private val budgetDao = database.budgetDao()
    private val scheduledPaymentDao = database.scheduledPaymentDao()
    private val savingsGoalDao = database.savingsGoalDao()
    private val debtDao = database.debtDao()

    val transactionsFlow: Flow<List<FinancialTransaction>> =
        transactionDao.observeAll().map { list -> list.map { it.toDomain() } }

    val budgetsFlow: Flow<List<Budget>> =
        budgetDao.observeAll().map { list -> list.map { it.toDomain() } }

    val scheduledPaymentsFlow: Flow<List<ScheduledPayment>> =
        scheduledPaymentDao.observeAll().map { list -> list.map { it.toDomain() } }

    val savingsGoalsFlow: Flow<List<SavingsGoal>> =
        savingsGoalDao.observeAll().map { list -> list.map { it.toDomain() } }

    val debtsFlow: Flow<List<Debt>> =
        debtDao.observeDebts().map { list -> list.map { it.toDomain() } }

    val debtPaymentsFlow: Flow<List<DebtPaymentRecord>> =
        debtDao.observePayments().map { list -> list.map { it.toDomain() } }

    suspend fun addTransaction(
        type: MovementType,
        amount: BigDecimal,
        category: String,
        date: LocalDate,
        description: String,
        recurrence: RecurrenceFrequency,
        paymentMethod: PaymentMethod,
        isAdjustmentPositive: Boolean = true,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (amount <= BigDecimal.ZERO) {
            return@withContext AppResult.Error("El importe debe ser mayor a cero.")
        }
        if (category.isBlank()) {
            return@withContext AppResult.Error("Selecciona una categoría válida.")
        }
        runCatching {
            val centavos = CurrencyFormatter.bigDecimalToCentavos(amount)
            val inserted = transactionDao.insertIgnoreDuplicate(
                TransactionEntity(
                    type = type.name,
                    amountCentavos = centavos,
                    category = category.trim(),
                    dateEpochDay = date.toEpochDay(),
                    description = description.trim(),
                    recurrence = recurrence.name,
                    paymentMethod = paymentMethod.name,
                    isAdjustmentPositive = isAdjustmentPositive,
                    idempotencyKey = idempotencyKey
                )
            )
            if (inserted <= 0L) {
                return@withContext AppResult.Error("Este movimiento ya fue registrado.")
            }
            AppResult.Success(Unit)
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "ADD_TX_FAIL")
            AppResult.Error("No fue posible guardar el movimiento.")
        }
    }

    suspend fun updateTransaction(tx: FinancialTransaction): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (tx.amount <= BigDecimal.ZERO) {
            return@withContext AppResult.Error("El importe debe ser mayor a cero.")
        }
        runCatching {
            transactionDao.update(
                TransactionEntity(
                    id = tx.id,
                    type = tx.type.name,
                    amountCentavos = CurrencyFormatter.bigDecimalToCentavos(tx.amount),
                    category = tx.category.trim(),
                    dateEpochDay = tx.date.toEpochDay(),
                    description = tx.description.trim(),
                    recurrence = tx.recurrence.name,
                    paymentMethod = tx.paymentMethod.name,
                    isAdjustmentPositive = tx.isAdjustmentPositive,
                    idempotencyKey = tx.idempotencyKey,
                    linkedEntityId = tx.linkedEntityId
                )
            )
            AppResult.Success(Unit)
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "UPD_TX_FAIL")
            AppResult.Error("No fue posible actualizar el movimiento.")
        }
    }

    suspend fun deleteTransaction(id: Long): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transactionDao.deleteById(id)
            AppResult.Success(Unit)
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "DEL_TX_FAIL")
            AppResult.Error("No se pudo eliminar el movimiento.")
        }
    }

    suspend fun saveBalanceAdjustment(
        currentBalance: BigDecimal,
        newTargetBalance: BigDecimal,
        reason: String
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        val diff = newTargetBalance.subtract(currentBalance).setScale(2, RoundingMode.HALF_EVEN)
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return@withContext AppResult.Error("El nuevo saldo coincide con el saldo actual.")
        }
        addTransaction(
            type = MovementType.BALANCE_ADJUSTMENT,
            amount = diff.abs(),
            category = "Ajuste de saldo",
            date = LocalDate.now(),
            description = reason.ifBlank { "Ajuste manual auditado de saldo" },
            recurrence = RecurrenceFrequency.NONE,
            paymentMethod = PaymentMethod.OTHER,
            isAdjustmentPositive = diff > BigDecimal.ZERO
        )
    }

    suspend fun saveBudget(
        category: String,
        periodType: PeriodType,
        limitAmount: BigDecimal,
        thresholds: List<Int>
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (limitAmount <= BigDecimal.ZERO) {
            return@withContext AppResult.Error("El límite del presupuesto debe ser mayor a cero.")
        }
        runCatching {
            val cleanThresholds = thresholds.filter { it in 1..100 }.distinct().sorted()
                .ifEmpty { listOf(50, 75, 90, 100) }
            budgetDao.upsert(
                BudgetEntity(
                    category = category.trim(),
                    periodType = periodType.name,
                    limitCentavos = CurrencyFormatter.bigDecimalToCentavos(limitAmount),
                    thresholdsCsv = cleanThresholds.joinToString(",")
                )
            )
            AppResult.Success(Unit)
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "SAVE_BUDGET_FAIL")
            AppResult.Error("No se pudo guardar el presupuesto.")
        }
    }

    suspend fun deleteBudget(id: Long) = withContext(Dispatchers.IO) {
        budgetDao.deleteById(id)
    }

    suspend fun addScheduledPayment(
        description: String,
        category: String,
        amount: BigDecimal,
        dueDate: LocalDate,
        recurrence: RecurrenceFrequency
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (amount <= BigDecimal.ZERO || description.isBlank()) {
            return@withContext AppResult.Error("Ingresa una descripción e importe válido.")
        }
        runCatching {
            scheduledPaymentDao.insert(
                ScheduledPaymentEntity(
                    description = description.trim(),
                    category = category.trim(),
                    amountCentavos = CurrencyFormatter.bigDecimalToCentavos(amount),
                    dueDateEpochDay = dueDate.toEpochDay(),
                    status = PaymentStatus.PENDING.name,
                    recurrence = recurrence.name
                )
            )
            AppResult.Success(Unit)
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "ADD_SCHED_FAIL")
            AppResult.Error("No se pudo registrar el compromiso de pago.")
        }
    }

    suspend fun markScheduledPaymentPaid(paymentId: Long): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val ok = database.markScheduledPaymentPaidAtomic(paymentId, LocalDate.now().toEpochDay())
            if (ok) AppResult.Success(Unit)
            else AppResult.Error("El pago ya estaba marcado o no existe.")
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "MARK_PAID_FAIL")
            AppResult.Error("Error al registrar el pago.")
        }
    }

    suspend fun deleteScheduledPayment(id: Long) = withContext(Dispatchers.IO) {
        scheduledPaymentDao.deleteById(id)
    }

    suspend fun addSavingsGoal(
        name: String,
        targetAmount: BigDecimal,
        targetDate: LocalDate?,
        description: String,
        iconKey: String
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (name.isBlank() || targetAmount <= BigDecimal.ZERO) {
            return@withContext AppResult.Error("Ingresa un nombre y monto objetivo válido.")
        }
        runCatching {
            savingsGoalDao.insert(
                SavingsGoalEntity(
                    name = name.trim(),
                    targetCentavos = CurrencyFormatter.bigDecimalToCentavos(targetAmount),
                    savedCentavos = 0L,
                    targetDateEpochDay = targetDate?.toEpochDay(),
                    description = description.trim(),
                    iconKey = iconKey
                )
            )
            AppResult.Success(Unit)
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "ADD_GOAL_FAIL")
            AppResult.Error("No se pudo crear la meta de ahorro.")
        }
    }

    suspend fun recordSavingsMovement(
        goalId: Long,
        amount: BigDecimal,
        isContribution: Boolean,
        note: String
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (amount <= BigDecimal.ZERO) {
            return@withContext AppResult.Error("El monto debe ser mayor a cero.")
        }
        runCatching {
            val ok = database.recordSavingsMovementAtomic(
                goalId = goalId,
                amountCentavos = CurrencyFormatter.bigDecimalToCentavos(amount),
                isContribution = isContribution,
                dateEpochDay = LocalDate.now().toEpochDay(),
                note = note.trim(),
                idempotencyKey = UUID.randomUUID().toString()
            )
            if (ok) AppResult.Success(Unit)
            else AppResult.Error(
                if (isContribution) "No se pudo registrar la aportación."
                else "El retiro supera el ahorro disponible en esta meta."
            )
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "SAVINGS_MOV_FAIL")
            AppResult.Error("No se pudo completar la operación de ahorro.")
        }
    }

    suspend fun deleteSavingsGoal(id: Long) = withContext(Dispatchers.IO) {
        savingsGoalDao.deleteById(id)
    }

    suspend fun addDebt(
        creditorName: String,
        initialBalance: BigDecimal,
        dueDate: LocalDate?,
        minimumPayment: BigDecimal,
        annualInterestRatePercent: BigDecimal
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (creditorName.isBlank() || initialBalance <= BigDecimal.ZERO) {
            return@withContext AppResult.Error("Ingresa acreedor y saldo válido.")
        }
        runCatching {
            val initialCentavos = CurrencyFormatter.bigDecimalToCentavos(initialBalance)
            val minCentavos = CurrencyFormatter.bigDecimalToCentavos(minimumPayment.max(BigDecimal.ZERO))
            val rateBps = annualInterestRatePercent
                .max(BigDecimal.ZERO)
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_EVEN)
                .toInt()
            debtDao.insertDebt(
                DebtEntity(
                    creditorName = creditorName.trim(),
                    initialBalanceCentavos = initialCentavos,
                    remainingBalanceCentavos = initialCentavos,
                    dueDateEpochDay = dueDate?.toEpochDay(),
                    minimumPaymentCentavos = minCentavos,
                    annualInterestRateBps = rateBps
                )
            )
            AppResult.Success(Unit)
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "ADD_DEBT_FAIL")
            AppResult.Error("No se pudo registrar la deuda.")
        }
    }

    suspend fun recordDebtPayment(
        debtId: Long,
        amount: BigDecimal,
        note: String
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (amount <= BigDecimal.ZERO) {
            return@withContext AppResult.Error("El abono debe ser mayor a cero.")
        }
        runCatching {
            val ok = database.recordDebtPaymentAtomic(
                debtId = debtId,
                amountCentavos = CurrencyFormatter.bigDecimalToCentavos(amount),
                dateEpochDay = LocalDate.now().toEpochDay(),
                note = note.trim(),
                idempotencyKey = UUID.randomUUID().toString()
            )
            if (ok) AppResult.Success(Unit)
            else AppResult.Error("No fue posible aplicar el abono.")
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "DEBT_PAY_FAIL")
            AppResult.Error("Error al registrar el abono.")
        }
    }

    suspend fun deleteDebt(id: Long) = withContext(Dispatchers.IO) {
        debtDao.deleteDebtById(id)
    }

    suspend fun exportCsvToShareableUri(): AppResult<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = transactionDao.getAllSnapshot().map { it.toDomain() }
            val csv = FinanceCalculator.exportTransactionsToCsv(snapshot)
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportsDir, "mi_bolsillo_movimientos_${LocalDate.now()}.csv")
            file.writeText(csv, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            AppResult.Success(uri)
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "EXPORT_CSV_FAIL")
            AppResult.Error("No se pudo generar el archivo CSV.")
        }
    }

    /**
     * Carga datos de demostración únicamente si el usuario los activa de forma voluntaria.
     */
    suspend fun loadDemoData(): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.clearAllFinancialTables()
            val today = LocalDate.now()
            preferences.setInitialBalanceCentavos(3500_00L)

            transactionDao.insertIgnoreDuplicate(
                TransactionEntity(
                    type = MovementType.INCOME.name,
                    amountCentavos = 9500_00L,
                    category = "Sueldo",
                    dateEpochDay = today.minusDays(5).toEpochDay(),
                    description = "Quincena (Demo)",
                    recurrence = RecurrenceFrequency.BIWEEKLY.name,
                    paymentMethod = PaymentMethod.TRANSFER.name,
                    isAdjustmentPositive = true,
                    idempotencyKey = "demo_income_1"
                )
            )
            transactionDao.insertIgnoreDuplicate(
                TransactionEntity(
                    type = MovementType.EXPENSE.name,
                    amountCentavos = 1420_50L,
                    category = "Alimentación",
                    dateEpochDay = today.minusDays(3).toEpochDay(),
                    description = "Despensa semanal (Demo)",
                    recurrence = RecurrenceFrequency.NONE.name,
                    paymentMethod = PaymentMethod.DEBIT.name,
                    isAdjustmentPositive = false,
                    idempotencyKey = "demo_expense_1"
                )
            )
            transactionDao.insertIgnoreDuplicate(
                TransactionEntity(
                    type = MovementType.EXPENSE.name,
                    amountCentavos = 480_00L,
                    category = "Transporte",
                    dateEpochDay = today.minusDays(1).toEpochDay(),
                    description = "Recarga y traslados (Demo)",
                    recurrence = RecurrenceFrequency.NONE.name,
                    paymentMethod = PaymentMethod.CASH.name,
                    isAdjustmentPositive = false,
                    idempotencyKey = "demo_expense_2"
                )
            )

            budgetDao.upsert(
                BudgetEntity(
                    category = "Alimentación",
                    periodType = PeriodType.BIWEEKLY.name,
                    limitCentavos = 2500_00L,
                    thresholdsCsv = "50,75,90,100"
                )
            )
            scheduledPaymentDao.insert(
                ScheduledPaymentEntity(
                    description = "Internet y telefonía (Demo)",
                    category = "Servicios",
                    amountCentavos = 599_00L,
                    dueDateEpochDay = today.plusDays(4).toEpochDay(),
                    status = PaymentStatus.PENDING.name,
                    recurrence = RecurrenceFrequency.MONTHLY.name
                )
            )
            savingsGoalDao.insert(
                SavingsGoalEntity(
                    name = "Fondo de emergencia (Demo)",
                    targetCentavos = 15000_00L,
                    savedCentavos = 4200_00L,
                    targetDateEpochDay = today.plusMonths(6).toEpochDay(),
                    description = "Respaldo para imprevistos",
                    iconKey = "shield"
                )
            )
            debtDao.insertDebt(
                DebtEntity(
                    creditorName = "Tarjeta bancaria (Demo)",
                    initialBalanceCentavos = 6000_00L,
                    remainingBalanceCentavos = 4200_00L,
                    dueDateEpochDay = today.plusDays(10).toEpochDay(),
                    minimumPaymentCentavos = 700_00L,
                    annualInterestRateBps = 3600
                )
            )
            preferences.setDemoDataActive(true)
            AppResult.Success(Unit)
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "DEMO_LOAD_FAIL")
            AppResult.Error("No se pudieron cargar los datos de demostración.")
        }
    }

    suspend fun deleteAllLocalData(): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.clearAllFinancialTables()
            preferences.clearAllPreferences()
            File(context.cacheDir, "exports").deleteRecursively()
            AppResult.Success(Unit)
        }.getOrElse {
            SafeLogger.logTechnicalError("FinanceRepo", "WIPE_FAIL")
            AppResult.Error("No fue posible borrar los datos locales.")
        }
    }
}

private fun TransactionEntity.toDomain() = FinancialTransaction(
    id = id,
    type = runCatching { MovementType.valueOf(type) }.getOrDefault(MovementType.EXPENSE),
    amount = CurrencyFormatter.centavosToBigDecimal(amountCentavos),
    category = category,
    date = LocalDate.ofEpochDay(dateEpochDay),
    description = description,
    recurrence = runCatching { RecurrenceFrequency.valueOf(recurrence) }.getOrDefault(RecurrenceFrequency.NONE),
    paymentMethod = runCatching { PaymentMethod.valueOf(paymentMethod) }.getOrDefault(PaymentMethod.CASH),
    isAdjustmentPositive = isAdjustmentPositive,
    idempotencyKey = idempotencyKey,
    linkedEntityId = linkedEntityId
)

private fun BudgetEntity.toDomain() = Budget(
    id = id,
    category = category,
    periodType = runCatching { PeriodType.valueOf(periodType) }.getOrDefault(PeriodType.BIWEEKLY),
    limitAmount = CurrencyFormatter.centavosToBigDecimal(limitCentavos),
    alertThresholds = thresholdsCsv.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .ifEmpty { listOf(50, 75, 90, 100) }
)

private fun ScheduledPaymentEntity.toDomain() = ScheduledPayment(
    id = id,
    description = description,
    category = category,
    amount = CurrencyFormatter.centavosToBigDecimal(amountCentavos),
    dueDate = LocalDate.ofEpochDay(dueDateEpochDay),
    status = runCatching { PaymentStatus.valueOf(status) }.getOrDefault(PaymentStatus.PENDING),
    recurrence = runCatching { RecurrenceFrequency.valueOf(recurrence) }.getOrDefault(RecurrenceFrequency.NONE),
    linkedTransactionId = linkedTransactionId
)

private fun SavingsGoalEntity.toDomain() = SavingsGoal(
    id = id,
    name = name,
    targetAmount = CurrencyFormatter.centavosToBigDecimal(targetCentavos),
    savedAmount = CurrencyFormatter.centavosToBigDecimal(savedCentavos),
    targetDate = targetDateEpochDay?.let { LocalDate.ofEpochDay(it) },
    description = description,
    iconKey = iconKey
)

private fun DebtEntity.toDomain() = Debt(
    id = id,
    creditorName = creditorName,
    initialBalance = CurrencyFormatter.centavosToBigDecimal(initialBalanceCentavos),
    remainingBalance = CurrencyFormatter.centavosToBigDecimal(remainingBalanceCentavos),
    dueDate = dueDateEpochDay?.let { LocalDate.ofEpochDay(it) },
    minimumPayment = CurrencyFormatter.centavosToBigDecimal(minimumPaymentCentavos),
    annualInterestRatePercent = BigDecimal.valueOf(annualInterestRateBps.toLong())
        .divide(BigDecimal(100), 2, RoundingMode.HALF_EVEN)
)

private fun DebtPaymentEntity.toDomain() = DebtPaymentRecord(
    id = id,
    debtId = debtId,
    amount = CurrencyFormatter.centavosToBigDecimal(amountCentavos),
    date = LocalDate.ofEpochDay(dateEpochDay),
    note = note
)
