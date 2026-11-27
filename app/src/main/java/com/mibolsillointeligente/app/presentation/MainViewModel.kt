package com.mibolsillointeligente.app.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mibolsillointeligente.app.MiBolsilloApp
import com.mibolsillointeligente.app.core.AppResult
import com.mibolsillointeligente.app.core.DateRange
import com.mibolsillointeligente.app.core.DateUtils
import com.mibolsillointeligente.app.domain.model.BudgetProgress
import com.mibolsillointeligente.app.domain.model.CategoryBreakdown
import com.mibolsillointeligente.app.domain.model.DashboardSummary
import com.mibolsillointeligente.app.domain.model.Debt
import com.mibolsillointeligente.app.domain.model.DebtPaymentRecord
import com.mibolsillointeligente.app.domain.model.FinancialTransaction
import com.mibolsillointeligente.app.domain.model.MovementType
import com.mibolsillointeligente.app.domain.model.PaymentMethod
import com.mibolsillointeligente.app.domain.model.PeriodComparison
import com.mibolsillointeligente.app.domain.model.PeriodType
import com.mibolsillointeligente.app.domain.model.RecurrenceFrequency
import com.mibolsillointeligente.app.domain.model.SavingsGoal
import com.mibolsillointeligente.app.domain.model.ScheduledPayment
import com.mibolsillointeligente.app.domain.model.ThemeMode
import com.mibolsillointeligente.app.domain.usecase.FinanceCalculator
import com.mibolsillointeligente.app.notifications.ReminderScheduler
import com.mibolsillointeligente.app.settings.UserPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class MainUiState(
    val preferences: UserPreferences = UserPreferences(),
    val currentRange: DateRange = DateUtils.getPeriodRange(PeriodType.BIWEEKLY),
    val summary: DashboardSummary = DashboardSummary(
        availableBalance = BigDecimal.ZERO,
        periodIncome = BigDecimal.ZERO,
        periodExpenses = BigDecimal.ZERO,
        committedUpcomingPayments = BigDecimal.ZERO,
        totalBudgetLimit = BigDecimal.ZERO,
        totalBudgetRemaining = BigDecimal.ZERO,
        totalSavingsAccumulated = BigDecimal.ZERO,
        totalSavingsTarget = BigDecimal.ZERO,
        overallSavingsPercentage = 0
    ),
    val transactions: List<FinancialTransaction> = emptyList(),
    val budgetProgressList: List<BudgetProgress> = emptyList(),
    val scheduledPayments: List<ScheduledPayment> = emptyList(),
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val debtPayments: List<DebtPaymentRecord> = emptyList(),
    val expenseBreakdown: List<CategoryBreakdown> = emptyList(),
    val periodComparison: PeriodComparison = PeriodComparison(
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
    ),
    val isUnlocked: Boolean = true
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class ShareCsv(val uri: Uri) : UiEvent
}

private data class CustomRangeState(
    val start: LocalDate?,
    val end: LocalDate?
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MiBolsilloApp
    private val repository = app.repository
    private val preferences = app.preferences

    private val customRange = MutableStateFlow(CustomRangeState(null, null))
    private val unlockedState = MutableStateFlow(true)

    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<MainUiState> = combine(
        preferences.preferencesFlow,
        repository.transactionsFlow,
        repository.budgetsFlow,
        repository.scheduledPaymentsFlow,
        repository.savingsGoalsFlow,
        repository.debtsFlow,
        repository.debtPaymentsFlow,
        customRange,
        unlockedState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val prefs = values[0] as UserPreferences
        @Suppress("UNCHECKED_CAST")
        val txs = values[1] as List<FinancialTransaction>
        @Suppress("UNCHECKED_CAST")
        val budgets = values[2] as List<com.mibolsillointeligente.app.domain.model.Budget>
        @Suppress("UNCHECKED_CAST")
        val payments = values[3] as List<ScheduledPayment>
        @Suppress("UNCHECKED_CAST")
        val goals = values[4] as List<SavingsGoal>
        @Suppress("UNCHECKED_CAST")
        val debts = values[5] as List<Debt>
        @Suppress("UNCHECKED_CAST")
        val debtPayments = values[6] as List<DebtPaymentRecord>
        val custom = values[7] as CustomRangeState
        val unlocked = values[8] as Boolean

        val today = LocalDate.now()
        val range = DateUtils.getPeriodRange(
            periodType = prefs.selectedPeriod,
            referenceDate = today,
            customStart = custom.start,
            customEnd = custom.end
        )
        val previousRange = DateUtils.getPreviousPeriodRange(prefs.selectedPeriod, range)

        val budgetProgress = budgets.map { budget ->
            val budgetRange = DateUtils.getPeriodRange(
                periodType = budget.periodType,
                referenceDate = today,
                customStart = custom.start,
                customEnd = custom.end
            )
            FinanceCalculator.calculateBudgetProgress(budget, txs, budgetRange)
        }

        val summary = FinanceCalculator.calculateDashboardSummary(
            initialBalance = prefs.initialBalance,
            allTransactions = txs,
            currentRange = range,
            budgets = budgetProgress,
            scheduledPayments = payments,
            savingsGoals = goals,
            today = today
        )

        val breakdown = FinanceCalculator.calculateExpenseBreakdownByCategory(txs, range)
        val comparison = FinanceCalculator.calculatePeriodComparison(txs, range, previousRange)

        MainUiState(
            preferences = prefs,
            currentRange = range,
            summary = summary,
            transactions = txs,
            budgetProgressList = budgetProgress,
            scheduledPayments = payments,
            savingsGoals = goals,
            debts = debts,
            debtPayments = debtPayments,
            expenseBreakdown = breakdown,
            periodComparison = comparison,
            isUnlocked = !prefs.biometricLockEnabled || unlocked
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState()
    )

    fun setLockedInitially(locked: Boolean) {
        unlockedState.value = !locked
    }

    fun markUnlocked() {
        unlockedState.value = true
    }

    fun setPeriodType(periodType: PeriodType) {
        viewModelScope.launch { preferences.setSelectedPeriod(periodType) }
    }

    fun setCustomDateRange(start: LocalDate, end: LocalDate) {
        customRange.value = CustomRangeState(start, end)
        setPeriodType(PeriodType.CUSTOM)
    }

    fun addMovement(
        type: MovementType,
        amount: BigDecimal,
        category: String,
        date: LocalDate,
        description: String,
        recurrence: RecurrenceFrequency,
        paymentMethod: PaymentMethod
    ) {
        viewModelScope.launch {
            when (val res = repository.addTransaction(type, amount, category, date, description, recurrence, paymentMethod)) {
                is AppResult.Success -> _events.emit(UiEvent.Message("${type.labelEs} guardado correctamente."))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun updateMovement(tx: FinancialTransaction) {
        viewModelScope.launch {
            when (val res = repository.updateTransaction(tx)) {
                is AppResult.Success -> _events.emit(UiEvent.Message("Movimiento actualizado."))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun deleteMovement(id: Long) {
        viewModelScope.launch {
            when (val res = repository.deleteTransaction(id)) {
                is AppResult.Success -> _events.emit(UiEvent.Message("Movimiento eliminado."))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun adjustBalanceWithAudit(targetBalance: BigDecimal, reason: String) {
        viewModelScope.launch {
            val current = uiState.value.summary.availableBalance
            when (val res = repository.saveBalanceAdjustment(current, targetBalance, reason)) {
                is AppResult.Success -> _events.emit(UiEvent.Message("Ajuste registrado en el historial."))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun saveBudget(category: String, periodType: PeriodType, limit: BigDecimal, thresholds: List<Int>) {
        viewModelScope.launch {
            when (val res = repository.saveBudget(category, periodType, limit, thresholds)) {
                is AppResult.Success -> _events.emit(UiEvent.Message("Presupuesto guardado."))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch {
            repository.deleteBudget(id)
            _events.emit(UiEvent.Message("Presupuesto eliminado."))
        }
    }

    fun addScheduledPayment(
        description: String,
        category: String,
        amount: BigDecimal,
        dueDate: LocalDate,
        recurrence: RecurrenceFrequency
    ) {
        viewModelScope.launch {
            when (val res = repository.addScheduledPayment(description, category, amount, dueDate, recurrence)) {
                is AppResult.Success -> _events.emit(UiEvent.Message("Compromiso de pago guardado."))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun markScheduledPaymentPaid(paymentId: Long) {
        viewModelScope.launch {
            when (val res = repository.markScheduledPaymentPaid(paymentId)) {
                is AppResult.Success -> _events.emit(UiEvent.Message("Pago marcado como realizado y registrado en gastos."))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun deleteScheduledPayment(id: Long) {
        viewModelScope.launch {
            repository.deleteScheduledPayment(id)
            _events.emit(UiEvent.Message("Pago programado eliminado."))
        }
    }

    fun addSavingsGoal(name: String, target: BigDecimal, targetDate: LocalDate?, description: String, iconKey: String) {
        viewModelScope.launch {
            when (val res = repository.addSavingsGoal(name, target, targetDate, description, iconKey)) {
                is AppResult.Success -> _events.emit(UiEvent.Message("Meta de ahorro creada."))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun recordSavingsMovement(goalId: Long, amount: BigDecimal, isContribution: Boolean, note: String) {
        viewModelScope.launch {
            when (val res = repository.recordSavingsMovement(goalId, amount, isContribution, note)) {
                is AppResult.Success -> _events.emit(
                    UiEvent.Message(if (isContribution) "Aportación registrada." else "Retiro registrado.")
                )
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun deleteSavingsGoal(id: Long) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(id)
            _events.emit(UiEvent.Message("Meta de ahorro eliminada."))
        }
    }

    fun addDebt(
        creditor: String,
        initialBalance: BigDecimal,
        dueDate: LocalDate?,
        minimumPayment: BigDecimal,
        annualInterestRate: BigDecimal
    ) {
        viewModelScope.launch {
            when (val res = repository.addDebt(creditor, initialBalance, dueDate, minimumPayment, annualInterestRate)) {
                is AppResult.Success -> _events.emit(UiEvent.Message("Deuda registrada."))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun recordDebtPayment(debtId: Long, amount: BigDecimal, note: String) {
        viewModelScope.launch {
            when (val res = repository.recordDebtPayment(debtId, amount, note)) {
                is AppResult.Success -> _events.emit(UiEvent.Message("Abono aplicado correctamente."))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun deleteDebt(id: Long) {
        viewModelScope.launch {
            repository.deleteDebt(id)
            _events.emit(UiEvent.Message("Deuda eliminada."))
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            when (val res = repository.exportCsvToShareableUri()) {
                is AppResult.Success -> _events.emit(UiEvent.ShareCsv(res.data))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { preferences.setBiometricLockEnabled(enabled) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setRemindersEnabled(enabled)
            if (enabled) ReminderScheduler.schedule(app) else ReminderScheduler.cancel(app)
        }
    }

    fun updateBudgetThresholds(thresholds: List<Int>) {
        viewModelScope.launch {
            preferences.setBudgetThresholds(thresholds)
            _events.emit(UiEvent.Message("Umbrales de alerta actualizados."))
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            when (val res = repository.loadDemoData()) {
                is AppResult.Success -> _events.emit(UiEvent.Message("Datos de demostración cargados."))
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            when (val res = repository.deleteAllLocalData()) {
                is AppResult.Success -> {
                    ReminderScheduler.cancel(app)
                    _events.emit(UiEvent.Message("Todos los datos locales fueron eliminados."))
                }
                is AppResult.Error -> _events.emit(UiEvent.Message(res.userMessage))
            }
        }
    }
}
