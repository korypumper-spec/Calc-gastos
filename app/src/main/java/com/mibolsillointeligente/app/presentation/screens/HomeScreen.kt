package com.mibolsillointeligente.app.presentation.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mibolsillointeligente.app.ads.DiscreetAdaptiveBannerAd
import com.mibolsillointeligente.app.core.CurrencyFormatter
import com.mibolsillointeligente.app.core.DateUtils
import com.mibolsillointeligente.app.domain.model.MovementType
import com.mibolsillointeligente.app.domain.model.PaymentStatus
import com.mibolsillointeligente.app.domain.model.PeriodType
import com.mibolsillointeligente.app.domain.usecase.FinanceCalculator
import com.mibolsillointeligente.app.presentation.MainUiState
import com.mibolsillointeligente.app.presentation.components.FinanceCard
import com.mibolsillointeligente.app.presentation.components.LabeledProgressBar
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    onSelectPeriod: (PeriodType) -> Unit,
    onSelectCustomRange: (LocalDate, LocalDate) -> Unit,
    onQuickAddMovement: (MovementType) -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToSavings: () -> Unit
) {
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val upcomingDue = remember(uiState.scheduledPayments) {
        val today = LocalDate.now()
        uiState.scheduledPayments
            .filter { FinanceCalculator.resolveEffectiveStatus(it, today) != PaymentStatus.PAID }
            .sortedBy { it.dueDate }
            .take(3)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = DateUtils.formatRangeLabel(uiState.currentRange),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeriodType.entries.forEach { period ->
                        FilterChip(
                            selected = uiState.preferences.selectedPeriod == period,
                            onClick = {
                                if (period == PeriodType.CUSTOM) {
                                    showCustomDateDialog = true
                                } else {
                                    onSelectPeriod(period)
                                }
                            },
                            label = { Text(period.labelEs) }
                        )
                    }
                }
            }
        }

        // Tarjeta principal de saldo disponible y accesos rápidos
        item {
            FinanceCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = "Saldo disponible",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = CurrencyFormatter.formatMxn(uiState.summary.availableBalance),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (uiState.summary.availableBalance.signum() < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ingresos del periodo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = CurrencyFormatter.formatMxn(uiState.summary.periodIncome),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gastos del periodo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = CurrencyFormatter.formatMxn(uiState.summary.periodExpenses),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onQuickAddMovement(MovementType.EXPENSE) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                        Text("Gasto rápido")
                    }
                    OutlinedButton(
                        onClick = { onQuickAddMovement(MovementType.INCOME) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                        Text("Ingreso")
                    }
                }
            }
        }

        // Indicadores separados: Comprometido próximo y Presupuesto restante
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FinanceCard(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pagos próximos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyFormatter.formatMxn(uiState.summary.committedUpcomingPayments),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Apartado informativo (no descontado aún)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FinanceCard(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Presupuesto restante",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val remainingBudget = uiState.summary.totalBudgetRemaining
                    Text(
                        text = if (uiState.budgetProgressList.isEmpty()) "Sin definir"
                        else CurrencyFormatter.formatMxn(remainingBudget),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (remainingBudget.signum() < 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onNavigateToBudgets,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Ver presupuestos")
                    }
                }
            }
        }

        // Progreso de ahorro
        item {
            FinanceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Progreso de ahorro", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onNavigateToSavings) {
                        Text("Gestionar metas")
                    }
                }
                if (uiState.savingsGoals.isEmpty()) {
                    Text(
                        text = "Aún no tienes metas de ahorro registradas. Crea tu primera meta para apartar dinero sin perder claridad en tu saldo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LabeledProgressBar(
                        label = "${uiState.summary.overallSavingsPercentage}% acumulado",
                        rightValue = "${CurrencyFormatter.formatMxn(uiState.summary.totalSavingsAccumulated)} / ${CurrencyFormatter.formatMxn(uiState.summary.totalSavingsTarget)}",
                        progressFraction = uiState.summary.overallSavingsPercentage / 100f,
                        progressColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // Próximos vencimientos
        item {
            FinanceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Próximos vencimientos", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onNavigateToBudgets) {
                        Text("Calendario")
                    }
                }
                if (upcomingDue.isEmpty()) {
                    Text(
                        text = "No tienes pagos pendientes registrados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val today = LocalDate.now()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        upcomingDue.forEach { payment ->
                            val status = FinanceCalculator.resolveEffectiveStatus(payment, today)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(payment.description, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "${payment.category} · Vence ${DateUtils.formatShort(payment.dueDate.toEpochDay())} · ${status.labelEs}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (status == PaymentStatus.OVERDUE) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                Text(
                                    text = CurrencyFormatter.formatMxn(payment.amount),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (status == PaymentStatus.OVERDUE) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Anuncio banner adaptativo discreto únicamente al pie del resumen informativo
        item {
            DiscreetAdaptiveBannerAd(
                adsRemoved = uiState.preferences.adsRemoved,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (showCustomDateDialog) {
        var startText by remember { mutableStateOf(uiState.currentRange.start.toString()) }
        var endText by remember { mutableStateOf(uiState.currentRange.end.toString()) }
        var dateError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCustomDateDialog = false },
            title = { Text("Periodo personalizado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it; dateError = null },
                        label = { Text("Fecha inicial (AAAA-MM-DD)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it; dateError = null },
                        label = { Text("Fecha final (AAAA-MM-DD)") },
                        singleLine = true
                    )
                    if (dateError != null) {
                        Text(dateError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val start = runCatching { LocalDate.parse(startText.trim()) }.getOrNull()
                    val end = runCatching { LocalDate.parse(endText.trim()) }.getOrNull()
                    if (start == null || end == null) {
                        dateError = "Formato válido: AAAA-MM-DD"
                    } else {
                        onSelectCustomRange(start, end)
                        showCustomDateDialog = false
                    }
                }) {
                    Text("Aplicar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDateDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
