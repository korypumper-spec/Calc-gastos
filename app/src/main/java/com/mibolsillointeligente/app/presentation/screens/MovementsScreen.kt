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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mibolsillointeligente.app.core.CurrencyFormatter
import com.mibolsillointeligente.app.core.DateUtils
import com.mibolsillointeligente.app.domain.model.FinancialTransaction
import com.mibolsillointeligente.app.domain.model.MovementType
import com.mibolsillointeligente.app.presentation.MainUiState
import com.mibolsillointeligente.app.presentation.components.ConfirmActionDialog
import com.mibolsillointeligente.app.presentation.components.FinanceCard
import com.mibolsillointeligente.app.presentation.components.LabeledProgressBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovementsScreen(
    uiState: MainUiState,
    onEditMovement: (FinancialTransaction) -> Unit,
    onDeleteMovement: (Long) -> Unit,
    onExportCsv: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<MovementType?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var deletingTarget by remember { mutableStateOf<FinancialTransaction?>(null) }

    val availableCategories = remember(uiState.transactions) {
        uiState.transactions.map { it.category }.distinct().sorted()
    }

    val filteredTransactions = remember(
        uiState.transactions,
        searchQuery,
        selectedTypeFilter,
        selectedCategoryFilter
    ) {
        uiState.transactions.filter { tx ->
            val matchesQuery = searchQuery.isBlank() ||
                tx.description.contains(searchQuery, ignoreCase = true) ||
                tx.category.contains(searchQuery, ignoreCase = true)
            val matchesType = selectedTypeFilter == null || tx.type == selectedTypeFilter
            val matchesCategory = selectedCategoryFilter == null || tx.category == selectedCategoryFilter
            matchesQuery && matchesType && matchesCategory
        }
    }

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
                Text("Movimientos", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onExportCsv) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                    Text("Exportar CSV")
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar por descripción o categoría") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTypeFilter == null,
                    onClick = { selectedTypeFilter = null },
                    label = { Text("Todos") }
                )
                FilterChip(
                    selected = selectedTypeFilter == MovementType.INCOME,
                    onClick = { selectedTypeFilter = MovementType.INCOME },
                    label = { Text("Ingresos") }
                )
                FilterChip(
                    selected = selectedTypeFilter == MovementType.EXPENSE,
                    onClick = { selectedTypeFilter = MovementType.EXPENSE },
                    label = { Text("Gastos") }
                )
            }
        }

        if (availableCategories.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { selectedCategoryFilter = null },
                        label = { Text("Todas las categorías") }
                    )
                    availableCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = {
                                selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                            },
                            label = { Text(cat) }
                        )
                    }
                }
            }
        }

        if (uiState.expenseBreakdown.isNotEmpty()) {
            item {
                FinanceCard {
                    Text(
                        text = "Resumen de gastos por categoría (${uiState.preferences.selectedPeriod.labelEs})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.expenseBreakdown.take(5).forEach { item ->
                            LabeledProgressBar(
                                label = "${item.category} (${item.percentage}%)",
                                rightValue = CurrencyFormatter.formatMxn(item.total),
                                progressFraction = item.percentage / 100f,
                                progressColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (filteredTransactions.isEmpty()) {
            item {
                FinanceCard {
                    Text(
                        text = "No hay movimientos registrados que coincidan con tu filtro. Usa el botón + para registrar un ingreso o gasto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredTransactions, key = { it.id }) { tx ->
                val isPositive = tx.type == MovementType.INCOME ||
                    tx.type == MovementType.SAVINGS_WITHDRAWAL ||
                    (tx.type == MovementType.BALANCE_ADJUSTMENT && tx.isAdjustmentPositive)

                val editable = tx.type == MovementType.INCOME || tx.type == MovementType.EXPENSE

                FinanceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.description.ifBlank { tx.category },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${tx.type.labelEs} · ${tx.category} · ${DateUtils.formatShort(tx.date.toEpochDay())}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tx.recurrence != com.mibolsillointeligente.app.domain.model.RecurrenceFrequency.NONE) {
                                Text(
                                    text = "Recurrente: ${tx.recurrence.labelEs} · ${tx.paymentMethod.labelEs}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = (if (isPositive) "+" else "-") + CurrencyFormatter.formatMxn(tx.amount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isPositive) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Row {
                                if (editable) {
                                    IconButton(onClick = { onEditMovement(tx) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar movimiento")
                                    }
                                }
                                IconButton(onClick = { deletingTarget = tx }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar movimiento")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deletingTarget?.let { target ->
        ConfirmActionDialog(
            title = "Eliminar movimiento",
            message = "¿Confirmas eliminar este movimiento de ${CurrencyFormatter.formatMxn(target.amount)}? El saldo se recalculará automáticamente.",
            confirmLabel = "Eliminar",
            isDestructive = true,
            onConfirm = {
                onDeleteMovement(target.id)
                deletingTarget = null
            },
            onDismiss = { deletingTarget = null }
        )
    }
}
