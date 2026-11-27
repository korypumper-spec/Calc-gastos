package com.mibolsillointeligente.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mibolsillointeligente.app.core.CurrencyFormatter
import com.mibolsillointeligente.app.domain.model.PeriodType
import com.mibolsillointeligente.app.domain.model.ThemeMode
import com.mibolsillointeligente.app.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mi_bolsillo_prefs")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val selectedPeriod: PeriodType = PeriodType.BIWEEKLY,
    val initialBalance: BigDecimal = BigDecimal.ZERO.setScale(2),
    val biometricLockEnabled: Boolean = false,
    val remindersEnabled: Boolean = false,
    val budgetAlertThresholds: List<Int> = listOf(50, 75, 90, 100),
    val demoDataActive: Boolean = false,
    val adsRemoved: Boolean = false
)

class AppPreferencesDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_PERIOD = stringPreferencesKey("selected_period")
        val INITIAL_BALANCE_CENTAVOS = longPreferencesKey("initial_balance_centavos")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val BUDGET_THRESHOLDS = stringPreferencesKey("budget_thresholds")
        val DEMO_DATA_ACTIVE = booleanPreferencesKey("demo_data_active")
        val ADS_REMOVED_TOKEN = stringPreferencesKey("ads_removed_token")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

        val period = prefs[Keys.SELECTED_PERIOD]
            ?.let { runCatching { PeriodType.valueOf(it) }.getOrNull() }
            ?: PeriodType.BIWEEKLY

        val initialBalanceCentavos = prefs[Keys.INITIAL_BALANCE_CENTAVOS] ?: 0L
        val thresholds = prefs[Keys.BUDGET_THRESHOLDS]
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 1..100 }
            ?.distinct()
            ?.sorted()
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(50, 75, 90, 100)

        val adsRemoved = prefs[Keys.ADS_REMOVED_TOKEN]
            ?.let { KeystoreCryptoManager.decryptText(it) == "ADS_REMOVED_VERIFIED" }
            ?: false

        UserPreferences(
            themeMode = theme,
            selectedPeriod = period,
            initialBalance = CurrencyFormatter.centavosToBigDecimal(initialBalanceCentavos),
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: false,
            budgetAlertThresholds = thresholds,
            demoDataActive = prefs[Keys.DEMO_DATA_ACTIVE] ?: false,
            adsRemoved = adsRemoved
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setSelectedPeriod(period: PeriodType) {
        context.dataStore.edit { it[Keys.SELECTED_PERIOD] = period.name }
    }

    suspend fun setInitialBalanceCentavos(centavos: Long) {
        context.dataStore.edit { it[Keys.INITIAL_BALANCE_CENTAVOS] = centavos }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMINDERS_ENABLED] = enabled }
    }

    suspend fun setBudgetThresholds(thresholds: List<Int>) {
        val normalized = thresholds.filter { it in 1..100 }.distinct().sorted()
        if (normalized.isNotEmpty()) {
            context.dataStore.edit { it[Keys.BUDGET_THRESHOLDS] = normalized.joinToString(",") }
        }
    }

    suspend fun setDemoDataActive(active: Boolean) {
        context.dataStore.edit { it[Keys.DEMO_DATA_ACTIVE] = active }
    }

    suspend fun clearAllPreferences() {
        context.dataStore.edit { it.clear() }
    }
}
