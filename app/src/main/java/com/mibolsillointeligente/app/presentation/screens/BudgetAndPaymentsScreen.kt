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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mibolsillointeligente.app.core.CurrencyFormatter
import com.mibolsillointeligente.app.core.DateUtils
import com.mibolsillointeligente.app.domain.model.FinanceCategories
import com.mibolsillointeligente.app.domain.model.PaymentStatus
import com.mibolsillointeligente.app.domain.model.PeriodType
import com.mibolsillointeligente.app.domain.model.RecurrenceFrequency
import com.mibolsillointeligente.app.domain.model.ScheduledPayment
import com.mibolsillointeligente.app.domain.usecase.FinanceCalculator
import com.mibolsillointeligente.app.presentation.MainUiState
import com.mibolsillointeligente.app.presentation.components.ConfirmActionDialog
import com.mibolsillointeligente.app.presentation.components.FinanceCard
import com.mibolsillointeligente.app.presentation.components.LabeledProgressBar
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetAndPaymentsScreen(
    uiState: MainUiState,
    onSaveBudget: (String, PeriodType, BigDecimal, List<Int>) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onAddScheduledPayment: (String, String, BigDecimal, LocalDate, RecurrenceFrequency) -> Unit,
    onMarkScheduledPaymentPaid: (Long) -> Unit,
    onDeleteScheduledPayment: (Long) -> Unit
) {
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var payingTarget by remember { mutableStateOf<ScheduledPayment?>(null) }

    val today = LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Presupuestos", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { showBudgetDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Nuevo límite")
                }
            }
        }

        if (uiState.budgetProgressList.isEmpty()) {
            item {
                FinanceCard {
                    Text(
                        text = "Configura presupuestos semanales, quincenales o mensuales por categoría con alertas al 50%, 75%, 90% y 100%.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(uiState.budgetProgressList, key = { "b_${it.budget.id}" }) { progress ->
                val isOverOrNear = progress.percentageUsed >= 90
                val barColor = if (isOverOrNear) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
                FinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${progress.budget.category} (${progress.budget.periodType.labelEs})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Alertas configuradas: ${progress.budget.alertThresholds.joinToString("%, ") { "$it" }}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteBudget(progress.budget.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar presupuesto")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LabeledProgressBar(
                        label = "${progress.percentageUsed}% consumido",
                        rightValue = "${CurrencyFormatter.formatMxn(progress.spent)} / ${CurrencyFormatter.formatMxn(progress.budget.limitAmount)}",
                        progressFraction = progress.percentageUsed / 100f,
                        progressColor = barColor,
                        subtitle = if (progress.remaining.signum() >= 0) {
                            "Restante: ${CurrencyFormatter.formatMxn(progress.remaining)}"
                        } else {
                            "Excedido por ${CurrencyFormatter.formatMxn(progress.remaining.abs())}"
                        }
                    )
                    progress.highestTriggeredThreshold?.let { threshold ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Alerta de presupuesto: alcanzaste el $threshold% del límite establecido.",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (threshold >= 90) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Calendario de pagos", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { showPaymentDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Agendar pago")
                }
            }
        }

        if (uiState.scheduledPayments.isEmpty()) {
            item {
                FinanceCard {
                    Text(
                        text = "Registra renta, servicios, tarjetas o colegiaturas. Ningún pago se registra como gastado automáticamente hasta que lo marques como pagado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(uiState.scheduledPayments, key = { "p_${it.id}" }) { payment ->
                val effectiveStatus = FinanceCalculator.resolveEffectiveStatus(payment, today)
                FinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(payment.description, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${payment.category} · Vence ${DateUtils.formatShort(payment.dueDate.toEpochDay())} · ${effectiveStatus.labelEs}",
                                style = MaterialTheme.typography.labelMedium,
                                color = when (effectiveStatus) {
                                    PaymentStatus.OVERDUE -> MaterialTheme.colorScheme.error
                                    PaymentStatus.PAID -> MaterialTheme.colorScheme.secondary
                                    PaymentStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Text(
                            text = CurrencyFormatter.formatMxn(payment.amount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (effectiveStatus != PaymentStatus.PAID) {
                            Button(onClick = { payingTarget = payment }) {
                                Icon(Icons.Default.CheckCircleOutline, contentDescription = null)
                                Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                                Text("Marcar como pagado")
                            }
                        }
                        IconButton(onClick = { onDeleteScheduledPayment(payment.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar compromiso")
                        }
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        val categories = listOf("General") + FinanceCategories.expenseCategories
        var selectedCat by remember { mutableStateOf(categories.first()) }
        var selectedPeriod by remember { mutableStateOf(uiState.preferences.selectedPeriod) }
        var limitText by remember { mutableStateOf("") }
        var thresholdsText by remember {
            mutableStateOf(uiState.preferences.budgetAlertThresholds.joinToString(","))
        }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Nuevo presupuesto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Categoría", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.take(6).forEach { cat ->
                            FilterChip(
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    Text("Periodo", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(PeriodType.WEEKLY, PeriodType.BIWEEKLY, PeriodType.MONTHLY).forEach { p ->
                            FilterChip(
                                selected = selectedPeriod == p,
                                onClick = { selectedPeriod = p },
                                label = { Text(p.labelEs) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it; err = null },
                        label = { Text("Límite en MXN") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = thresholdsText,
                        onValueChange = { thresholdsText = it },
                        label = { Text("Alertas % (ej. 50,75,90,100)") },
                        singleLine = true
                    )
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val limit = CurrencyFormatter.parseAmountInput(limitText)
                    val thresholds = thresholdsText.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .filter { it in 1..100 }
                        .distinct()
                        .sorted()
                        .ifEmpty { listOf(50, 75, 90, 100) }
                    if (limit == null) {
                        err = "Ingresa un límite válido mayor a $0.00"
                    } else {
                        onSaveBudget(selectedCat, selectedPeriod, limit, thresholds)
                        showBudgetDialog = false
                    }
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showPaymentDialog) {
        var desc by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(FinanceCategories.scheduledPaymentCategories.first()) }
        var amountText by remember { mutableStateOf("") }
        var dueText by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
        var recurrence by remember { mutableStateOf(RecurrenceFrequency.MONTHLY) }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("Agendar compromiso de pago") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it; err = null },
                        label = { Text("Descripción (ej. Renta, CFE, Telmex)") },
                        singleLine = true
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FinanceCategories.scheduledPaymentCategories.take(5).forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; err = null },
                        label = { Text("Importe en MXN") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dueText,
                        onValueChange = { dueText = it; err = null },
                        label = { Text("Vencimiento (AAAA-MM-DD)") },
                        singleLine = true
                    )
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsedAmount = CurrencyFormatter.parseAmountInput(amountText)
                    val parsedDate = runCatching { LocalDate.parse(dueText.trim()) }.getOrNull()
                    when {
                        desc.isBlank() -> err = "Ingresa una descripción"
                        parsedAmount == null -> err = "Importe inválido"
                        parsedDate == null -> err = "Fecha inválida (AAAA-MM-DD)"
                        else -> {
                            onAddScheduledPayment(desc, category, parsedAmount, parsedDate, recurrence)
                            showPaymentDialog = false
                        }
                    }
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) { Text("Cancelar") }
            }
        )
    }

    payingTarget?.let { target ->
        ConfirmActionDialog(
            title = "Confirmar pago realizado",
            message = "¿Marcar \"${target.description}\" (${CurrencyFormatter.formatMxn(target.amount)}) como pagado? Se registrará un gasto único en tus movimientos.",
            confirmLabel = "Marcar como pagado",
            onConfirm = {
                onMarkScheduledPaymentPaid(target.id)
                payingTarget = null
            },
            onDismiss = { payingTarget = null }
        )
    }
}
