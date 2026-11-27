package com.mibolsillointeligente.app.presentation.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mibolsillointeligente.app.ads.PlayBillingStatus
import com.mibolsillointeligente.app.core.CurrencyFormatter
import com.mibolsillointeligente.app.domain.model.ThemeMode
import com.mibolsillointeligente.app.notifications.ReminderNotificationHelper
import com.mibolsillointeligente.app.presentation.MainUiState
import com.mibolsillointeligente.app.presentation.components.ConfirmActionDialog
import com.mibolsillointeligente.app.presentation.components.FinanceCard
import com.mibolsillointeligente.app.security.BiometricLockManager
import java.math.BigDecimal

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSettingsScreen(
    uiState: MainUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onAdjustBalance: (BigDecimal, String) -> Unit,
    onSetBiometricLock: (Boolean) -> Unit,
    onSetRemindersEnabled: (Boolean) -> Unit,
    onUpdateThresholds: (List<Int>) -> Unit,
    onLoadDemoData: () -> Unit,
    onDeleteAllData: () -> Unit
) {
    val context = LocalContext.current
    val biometricSupported = remember(context) { BiometricLockManager.canAuthenticate(context) }

    var showBalanceAdjustDialog by remember { mutableStateOf(false) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var showDemoConfirmDialog by remember { mutableStateOf(false) }
    var thresholdsText by remember(uiState.preferences.budgetAlertThresholds) {
        mutableStateOf(uiState.preferences.budgetAlertThresholds.joinToString(","))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onSetRemindersEnabled(granted)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Perfil y configuración", style = MaterialTheme.typography.titleLarge)
        }

        // Apariencia
        item {
            FinanceCard {
                Text("Apariencia", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.preferences.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(mode.labelEs) }
                        )
                    }
                }
            }
        }

        // Saldo y umbrales de presupuesto
        item {
            FinanceCard {
                Text("Control de saldo y alertas", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Saldo disponible actual: ${CurrencyFormatter.formatMxn(uiState.summary.availableBalance)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showBalanceAdjustDialog = true }) {
                    Text("Ajustar saldo con registro")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = thresholdsText,
                    onValueChange = { thresholdsText = it },
                    label = { Text("Alertas globales de presupuesto (%)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val parsed = thresholdsText.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .filter { it in 1..100 }
                    if (parsed.isNotEmpty()) onUpdateThresholds(parsed)
                }) {
                    Text("Guardar porcentajes")
                }
            }
        }

        // Seguridad y recordatorios
        item {
            FinanceCard {
                Text("Seguridad y recordatorios", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bloqueo biométrico o PIN del dispositivo", fontWeight = FontWeight.Medium)
                        Text(
                            text = if (biometricSupported) {
                                "Solicita huella, rostro o credencial del dispositivo al abrir."
                            } else {
                                "Configura un PIN o biometría en tu teléfono para activar esta opción."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.preferences.biometricLockEnabled,
                        enabled = biometricSupported,
                        onCheckedChange = onSetBiometricLock
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Recordatorios locales", fontWeight = FontWeight.Medium)
                        Text(
                            text = "Avisos locales sin servidor sobre vencimientos próximos. La app funciona normal aunque rechaces permisos.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.preferences.remindersEnabled,
                        onCheckedChange = { enable ->
                            if (!enable) {
                                onSetRemindersEnabled(false)
                            } else if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !ReminderNotificationHelper.hasNotificationPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSetRemindersEnabled(true)
                            }
                        }
                    )
                }
            }
        }

        // Privacidad, datos de demostración y borrado
        item {
            FinanceCard {
                Text("Datos locales y privacidad", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tus movimientos se almacenan localmente en tu dispositivo (Room SQLite en almacenamiento privado) con allowBackup desactivado y llaves auxiliares en Android Keystore. No enviamos datos financieros a servidores externos ni a redes publicitarias.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (PlayBillingStatus.IS_BILLING_ENABLED) {
                        "Versión sin anuncios disponible."
                    } else {
                        "Opción sin publicidad mediante Google Play Billing: deshabilitada en esta compilación hasta registrar el SKU en Play Console."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDemoConfirmDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cargar demo")
                    }
                    Button(
                        onClick = { showWipeConfirmDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Borrar todo")
                    }
                }
            }
        }
    }

    if (showBalanceAdjustDialog) {
        var newBalanceText by remember { mutableStateOf(uiState.summary.availableBalance.toPlainString()) }
        var reason by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showBalanceAdjustDialog = false },
            title = { Text("Ajustar saldo disponible") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Todo ajuste crea un registro auditable en tus movimientos para conservar la integridad financiera.",
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedTextField(
                        value = newBalanceText,
                        onValueChange = { newBalanceText = it; err = null },
                        label = { Text("Nuevo saldo (MXN)") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Motivo del ajuste") },
                        singleLine = true
                    )
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = CurrencyFormatter.parseAmountInput(newBalanceText, allowZero = true)
                    if (parsed == null) {
                        err = "Ingresa un monto válido"
                    } else {
                        onAdjustBalance(parsed, reason)
                        showBalanceAdjustDialog = false
                    }
                }) { Text("Registrar ajuste") }
            },
            dismissButton = {
                TextButton(onClick = { showBalanceAdjustDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDemoConfirmDialog) {
        ConfirmActionDialog(
            title = "Cargar datos de demostración",
            message = "Esto reemplazará tus registros actuales por movimientos de ejemplo marcados como (Demo). Puedes borrarlos en cualquier momento.",
            confirmLabel = "Cargar demo",
            onConfirm = {
                onLoadDemoData()
                showDemoConfirmDialog = false
            },
            onDismiss = { showDemoConfirmDialog = false }
        )
    }

    if (showWipeConfirmDialog) {
        ConfirmActionDialog(
            title = "Eliminar todos los datos locales",
            message = "Esta acción borrará permanentemente todos tus ingresos, gastos, presupuestos, pagos, metas, deudas y preferencias del dispositivo. No se puede deshacer.",
            confirmLabel = "Borrar todo",
            isDestructive = true,
            onConfirm = {
                onDeleteAllData()
                showWipeConfirmDialog = false
            },
            onDismiss = { showWipeConfirmDialog = false }
        )
    }
}
