package com.mibolsillointeligente.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mibolsillointeligente.app.core.CurrencyFormatter
import com.mibolsillointeligente.app.domain.model.FinanceCategories
import com.mibolsillointeligente.app.domain.model.FinancialTransaction
import com.mibolsillointeligente.app.domain.model.MovementType
import com.mibolsillointeligente.app.domain.model.PaymentMethod
import com.mibolsillointeligente.app.domain.model.RecurrenceFrequency
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun FinanceCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirmar",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovementFormSheet(
    initialType: MovementType = MovementType.EXPENSE,
    editingTransaction: FinancialTransaction? = null,
    onSave: (
        type: MovementType,
        amount: BigDecimal,
        category: String,
        date: LocalDate,
        description: String,
        recurrence: RecurrenceFrequency,
        paymentMethod: PaymentMethod
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember {
        mutableStateOf(
            editingTransaction?.type?.takeIf {
                it == MovementType.INCOME || it == MovementType.EXPENSE
            } ?: initialType
        )
    }
    var amountText by remember {
        mutableStateOf(editingTransaction?.amount?.toPlainString() ?: "")
    }
    val categories = if (selectedType == MovementType.INCOME) {
        FinanceCategories.incomeCategories
    } else {
        FinanceCategories.expenseCategories
    }
    var selectedCategory by remember(selectedType) {
        mutableStateOf(
            editingTransaction?.category?.takeIf { it in categories } ?: categories.first()
        )
    }
    var description by remember { mutableStateOf(editingTransaction?.description ?: "") }
    var dateText by remember {
        mutableStateOf((editingTransaction?.date ?: LocalDate.now()).toString())
    }
    var recurrence by remember {
        mutableStateOf(editingTransaction?.recurrence ?: RecurrenceFrequency.NONE)
    }
    var paymentMethod by remember {
        mutableStateOf(editingTransaction?.paymentMethod ?: PaymentMethod.CASH)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (editingTransaction == null) "Registrar movimiento rápido" else "Editar movimiento",
                style = MaterialTheme.typography.titleLarge
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(MovementType.EXPENSE, MovementType.INCOME)
                options.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(type.labelEs)
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it; errorMessage = null },
                label = { Text("Importe en MXN") },
                placeholder = { Text("0.00") },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Categoría", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it; errorMessage = null },
                label = { Text("Fecha (AAAA-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Frecuencia", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceFrequency.entries.forEach { freq ->
                    FilterChip(
                        selected = recurrence == freq,
                        onClick = { recurrence = freq },
                        label = { Text(freq.labelEs) }
                    )
                }
            }

            Text("Medio de pago (opcional)", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.entries.forEach { method ->
                    FilterChip(
                        selected = paymentMethod == method,
                        onClick = { paymentMethod = method },
                        label = { Text(method.labelEs) }
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        val parsedAmount = CurrencyFormatter.parseAmountInput(amountText)
                        val parsedDate = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                        when {
                            parsedAmount == null -> errorMessage = "Ingresa un importe válido mayor a $0.00"
                            parsedDate == null -> errorMessage = "Usa el formato de fecha AAAA-MM-DD"
                            else -> onSave(
                                selectedType,
                                parsedAmount,
                                selectedCategory,
                                parsedDate,
                                description,
                                recurrence,
                                paymentMethod
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Guardar")
                }
            }
        }
    }
}

@Composable
fun LabeledProgressBar(
    label: String,
    rightValue: String,
    progressFraction: Float,
    progressColor: Color = MaterialTheme.colorScheme.tertiary,
    subtitle: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(rightValue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
