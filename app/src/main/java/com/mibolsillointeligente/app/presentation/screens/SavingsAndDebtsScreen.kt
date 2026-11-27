package com.mibolsillointeligente.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.mibolsillointeligente.app.ads.DiscreetAdaptiveBannerAd
import com.mibolsillointeligente.app.core.CurrencyFormatter
import com.mibolsillointeligente.app.core.DateUtils
import com.mibolsillointeligente.app.domain.model.Debt
import com.mibolsillointeligente.app.domain.model.SavingsGoal
import com.mibolsillointeligente.app.domain.usecase.FinanceCalculator
import com.mibolsillointeligente.app.presentation.MainUiState
import com.mibolsillointeligente.app.presentation.components.FinanceCard
import com.mibolsillointeligente.app.presentation.components.LabeledProgressBar
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun SavingsAndDebtsScreen(
    uiState: MainUiState,
    onAddSavingsGoal: (String, BigDecimal, LocalDate?, String, String) -> Unit,
    onRecordSavingsMovement: (Long, BigDecimal, Boolean, String) -> Unit,
    onDeleteSavingsGoal: (Long) -> Unit,
    onAddDebt: (String, BigDecimal, LocalDate?, BigDecimal, BigDecimal) -> Unit,
    onRecordDebtPayment: (Long, BigDecimal, String) -> Unit,
    onDeleteDebt: (Long) -> Unit,
    onExportCsv: () -> Unit
) {
    var showNewGoalDialog by remember { mutableStateOf(false) }
    var savingsMovementTarget by remember { mutableStateOf<Pair<SavingsGoal, Boolean>?>(null) }
    var showNewDebtDialog by remember { mutableStateOf(false) }
    var debtPaymentTarget by remember { mutableStateOf<Debt?>(null) }
    var simulatorDebt by remember { mutableStateOf<Debt?>(null) }

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
                Text("Metas de ahorro", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { showNewGoalDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Nueva meta")
                }
            }
        }

        if (uiState.savingsGoals.isEmpty()) {
            item {
                FinanceCard {
                    Text(
                        text = "Crea metas de ahorro (fondo de emergencia, viaje, estudios). Las aportaciones se apartan de tu saldo disponible sin duplicar gastos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(uiState.savingsGoals, key = { "g_${it.id}" }) { goal ->
                val pct = FinanceCalculator.calculateSavingsPercentage(goal)
                val remaining = FinanceCalculator.calculateSavingsRemaining(goal)
                FinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(goal.name, style = MaterialTheme.typography.titleMedium)
                            if (goal.description.isNotBlank()) {
                                Text(
                                    text = goal.description,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            goal.targetDate?.let { date ->
                                Text(
                                    text = "Objetivo: ${DateUtils.formatShort(date.toEpochDay())}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { onDeleteSavingsGoal(goal.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar meta")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LabeledProgressBar(
                        label = "$pct% completado",
                        rightValue = "${CurrencyFormatter.formatMxn(goal.savedAmount)} / ${CurrencyFormatter.formatMxn(goal.targetAmount)}",
                        progressFraction = pct / 100f,
                        progressColor = MaterialTheme.colorScheme.tertiary,
                        subtitle = "Faltante: ${CurrencyFormatter.formatMxn(remaining)}"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { savingsMovementTarget = goal to true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Aportar")
                        }
                        OutlinedButton(
                            onClick = { savingsMovementTarget = goal to false },
                            enabled = goal.savedAmount > BigDecimal.ZERO,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retirar")
                        }
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
                Text("Deudas y préstamos", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { showNewDebtDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Nueva deuda")
                }
            }
        }

        if (uiState.debts.isEmpty()) {
            item {
                FinanceCard {
                    Text(
                        text = "Registra tus tarjetas, créditos o préstamos para dar seguimiento al saldo pendiente y simular en cuántos meses puedes liquidarlos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(uiState.debts, key = { "d_${it.id}" }) { debt ->
                val history = uiState.debtPayments.filter { it.debtId == debt.id }
                FinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(debt.creditorName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Pendiente: ${CurrencyFormatter.formatMxn(debt.remainingBalance)} · Inicial: ${CurrencyFormatter.formatMxn(debt.initialBalance)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Pago mínimo: ${CurrencyFormatter.formatMxn(debt.minimumPayment)} · Tasa anual: ${debt.annualInterestRatePercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteDebt(debt.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar deuda")
                        }
                    }
                    if (history.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Últimos abonos: ${history.take(2).joinToString(" | ") { "${DateUtils.formatCompact(it.date.toEpochDay())}: ${CurrencyFormatter.formatMxn(it.amount)}" }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { debtPaymentTarget = debt },
                            enabled = debt.remainingBalance > BigDecimal.ZERO,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Registrar abono")
                        }
                        OutlinedButton(
                            onClick = { simulatorDebt = debt },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simulador")
                        }
                    }
                }
            }
        }

        // Reportes y estadísticas con comparación entre periodos
        item {
            Spacer(modifier = Modifier.height(8.dp))
            FinanceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reportes y comparación", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onExportCsv) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                        Text("CSV")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val cmp = uiState.periodComparison
                Text(
                    text = "Periodo actual (${uiState.preferences.selectedPeriod.labelEs}) vs periodo anterior:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ingresos actuales:")
                    Text(CurrencyFormatter.formatMxn(cmp.currentIncome), fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ingresos anteriores:")
                    Text(CurrencyFormatter.formatMxn(cmp.previousIncome))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Gastos actuales:")
                    Text(CurrencyFormatter.formatMxn(cmp.currentExpenses), fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Gastos anteriores:")
                    Text(CurrencyFormatter.formatMxn(cmp.previousExpenses))
                }
            }
        }

        item {
            DiscreetAdaptiveBannerAd(adsRemoved = uiState.preferences.adsRemoved)
        }
    }

    if (showNewGoalDialog) {
        var name by remember { mutableStateOf("") }
        var targetText by remember { mutableStateOf("") }
        var dateText by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showNewGoalDialog = false },
            title = { Text("Nueva meta de ahorro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; err = null },
                        label = { Text("Nombre de la meta") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it; err = null },
                        label = { Text("Cantidad objetivo (MXN)") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it; err = null },
                        label = { Text("Fecha objetivo opcional (AAAA-MM-DD)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descripción opcional") },
                        singleLine = true
                    )
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = CurrencyFormatter.parseAmountInput(targetText)
                    val date = if (dateText.isBlank()) null else runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                    when {
                        name.isBlank() -> err = "Ingresa un nombre"
                        target == null -> err = "Ingresa un monto objetivo válido"
                        dateText.isNotBlank() && date == null -> err = "Fecha inválida (AAAA-MM-DD)"
                        else -> {
                            onAddSavingsGoal(name, target, date, description, "savings")
                            showNewGoalDialog = false
                        }
                    }
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showNewGoalDialog = false }) { Text("Cancelar") }
            }
        )
    }

    savingsMovementTarget?.let { (goal, isContribution) ->
        var amountText by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { savingsMovementTarget = null },
            title = { Text(if (isContribution) "Aportar a ${goal.name}" else "Retirar de ${goal.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; err = null },
                        label = { Text("Importe en MXN") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota opcional") },
                        singleLine = true
                    )
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = CurrencyFormatter.parseAmountInput(amountText)
                    if (parsed == null) {
                        err = "Importe inválido"
                    } else {
                        onRecordSavingsMovement(goal.id, parsed, isContribution, note)
                        savingsMovementTarget = null
                    }
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { savingsMovementTarget = null }) { Text("Cancelar") }
            }
        )
    }

    if (showNewDebtDialog) {
        var creditor by remember { mutableStateOf("") }
        var balanceText by remember { mutableStateOf("") }
        var minPayText by remember { mutableStateOf("") }
        var rateText by remember { mutableStateOf("0") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showNewDebtDialog = false },
            title = { Text("Registrar deuda o préstamo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = creditor,
                        onValueChange = { creditor = it; err = null },
                        label = { Text("Nombre del acreedor") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { balanceText = it; err = null },
                        label = { Text("Saldo inicial (MXN)") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minPayText,
                        onValueChange = { minPayText = it; err = null },
                        label = { Text("Pago mínimo mensual (MXN)") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it; err = null },
                        label = { Text("Tasa de interés anual % (opcional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val bal = CurrencyFormatter.parseAmountInput(balanceText)
                    val minPay = CurrencyFormatter.parseAmountInput(minPayText, allowZero = true) ?: BigDecimal.ZERO
                    val rate = CurrencyFormatter.parseAmountInput(rateText, allowZero = true) ?: BigDecimal.ZERO
                    when {
                        creditor.isBlank() -> err = "Ingresa el nombre del acreedor"
                        bal == null -> err = "Saldo inicial inválido"
                        else -> {
                            onAddDebt(creditor, bal, null, minPay, rate)
                            showNewDebtDialog = false
                        }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDebtDialog = false }) { Text("Cancelar") }
            }
        )
    }

    debtPaymentTarget?.let { debt ->
        var amountText by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { debtPaymentTarget = null },
            title = { Text("Abonar a ${debt.creditorName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saldo pendiente: ${CurrencyFormatter.formatMxn(debt.remainingBalance)}")
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; err = null },
                        label = { Text("Importe del abono (MXN)") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota opcional") },
                        singleLine = true
                    )
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = CurrencyFormatter.parseAmountInput(amountText)
                    if (parsed == null) {
                        err = "Importe inválido"
                    } else {
                        onRecordDebtPayment(debt.id, parsed, note)
                        debtPaymentTarget = null
                    }
                }) { Text("Aplicar abono") }
            },
            dismissButton = {
                TextButton(onClick = { debtPaymentTarget = null }) { Text("Cancelar") }
            }
        )
    }

    simulatorDebt?.let { debt ->
        var simulatedPaymentText by remember {
            mutableStateOf(
                if (debt.minimumPayment > BigDecimal.ZERO) debt.minimumPayment.toPlainString()
                else "1000.00"
            )
        }
        val parsedPayment = CurrencyFormatter.parseAmountInput(simulatedPaymentText) ?: BigDecimal.ZERO
        val simulation = FinanceCalculator.simulateDebtPayoff(
            remainingBalance = debt.remainingBalance,
            monthlyPayment = parsedPayment,
            annualInterestRatePercent = debt.annualInterestRatePercent
        )

        AlertDialog(
            onDismissRequest = { simulatorDebt = null },
            title = { Text("Simulador: ${debt.creditorName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saldo pendiente: ${CurrencyFormatter.formatMxn(debt.remainingBalance)} · Tasa anual: ${debt.annualInterestRatePercent}%")
                    OutlinedTextField(
                        value = simulatedPaymentText,
                        onValueChange = { simulatedPaymentText = it },
                        label = { Text("Pago mensual estimado (MXN)") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    if (simulation.isPayoffPossible) {
                        Text(
                            text = "Tiempo estimado: ${simulation.estimatedMonths} mes(es)",
                            fontWeight = FontWeight.Bold
                        )
                        Text("Intereses estimados: ${CurrencyFormatter.formatMxn(simulation.totalInterestEstimated)}")
                        Text("Total estimado a pagar: ${CurrencyFormatter.formatMxn(simulation.totalPaidEstimated)}")
                    } else {
                        Text(
                            text = "Con ese pago mensual no se alcanza a cubrir el interés mensual o el pago es cero. Aumenta el monto para estimar la liquidación.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = simulation.disclaimer,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { simulatorDebt = null }) { Text("Cerrar") }
            }
        )
    }
}
