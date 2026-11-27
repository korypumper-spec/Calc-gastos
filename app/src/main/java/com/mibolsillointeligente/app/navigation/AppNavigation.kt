package com.mibolsillointeligente.app.navigation

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mibolsillointeligente.app.R
import com.mibolsillointeligente.app.domain.model.FinancialTransaction
import com.mibolsillointeligente.app.domain.model.MovementType
import com.mibolsillointeligente.app.presentation.MainViewModel
import com.mibolsillointeligente.app.presentation.UiEvent
import com.mibolsillointeligente.app.presentation.components.MovementFormSheet
import com.mibolsillointeligente.app.presentation.screens.BudgetAndPaymentsScreen
import com.mibolsillointeligente.app.presentation.screens.HomeScreen
import com.mibolsillointeligente.app.presentation.screens.MovementsScreen
import com.mibolsillointeligente.app.presentation.screens.ProfileSettingsScreen
import com.mibolsillointeligente.app.presentation.screens.SavingsAndDebtsScreen

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "Inicio", Icons.Default.Home),
    MOVEMENTS("movements", "Movimientos", Icons.Default.ListAlt),
    BUDGET("budget", "Presupuesto", Icons.Default.AccountBalanceWallet),
    SAVINGS("savings", "Ahorro", Icons.Default.Savings),
    PROFILE("profile", "Perfil", Icons.Default.Person)
}

@Composable
fun MiBolsilloRootScaffold(
    viewModel: MainViewModel,
    onRequestBiometricUnlock: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var movementSheetType by remember { mutableStateOf<MovementType?>(null) }
    var editingTransaction by remember { mutableStateOf<FinancialTransaction?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is UiEvent.ShareCsv -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Movimientos - Calculadora de Gastos")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Exportar movimientos CSV"))
                }
            }
        }
    }

    if (!uiState.isUnlocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_artwork),
                    contentDescription = "Calculadora de Gastos",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                        .padding(6.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Calculadora de Gastos",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tu información financiera está protegida. Desbloquea con tu biometría o PIN del dispositivo.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onRequestBiometricUnlock) {
                    Text("Desbloquear")
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calculadora de Gastos",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_artwork),
                    contentDescription = "Icono Calculadora de Gastos",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(2.dp)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTransaction = null
                    movementSheetType = MovementType.EXPENSE
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Registrar movimiento")
            }
        },
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            NavigationBar {
                AppDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.HOME.route) {
                HomeScreen(
                    uiState = uiState,
                    onSelectPeriod = viewModel::setPeriodType,
                    onSelectCustomRange = viewModel::setCustomDateRange,
                    onQuickAddMovement = { type ->
                        editingTransaction = null
                        movementSheetType = type
                    },
                    onNavigateToBudgets = { navController.navigate(AppDestination.BUDGET.route) },
                    onNavigateToSavings = { navController.navigate(AppDestination.SAVINGS.route) }
                )
            }
            composable(AppDestination.MOVEMENTS.route) {
                MovementsScreen(
                    uiState = uiState,
                    onEditMovement = { tx ->
                        editingTransaction = tx
                        movementSheetType = tx.type
                    },
                    onDeleteMovement = viewModel::deleteMovement,
                    onExportCsv = viewModel::exportCsv
                )
            }
            composable(AppDestination.BUDGET.route) {
                BudgetAndPaymentsScreen(
                    uiState = uiState,
                    onSaveBudget = viewModel::saveBudget,
                    onDeleteBudget = viewModel::deleteBudget,
                    onAddScheduledPayment = viewModel::addScheduledPayment,
                    onMarkScheduledPaymentPaid = viewModel::markScheduledPaymentPaid,
                    onDeleteScheduledPayment = viewModel::deleteScheduledPayment
                )
            }
            composable(AppDestination.SAVINGS.route) {
                SavingsAndDebtsScreen(
                    uiState = uiState,
                    onAddSavingsGoal = viewModel::addSavingsGoal,
                    onRecordSavingsMovement = viewModel::recordSavingsMovement,
                    onDeleteSavingsGoal = viewModel::deleteSavingsGoal,
                    onAddDebt = viewModel::addDebt,
                    onRecordDebtPayment = viewModel::recordDebtPayment,
                    onDeleteDebt = viewModel::deleteDebt,
                    onExportCsv = viewModel::exportCsv
                )
            }
            composable(AppDestination.PROFILE.route) {
                ProfileSettingsScreen(
                    uiState = uiState,
                    onSetThemeMode = viewModel::setThemeMode,
                    onAdjustBalance = viewModel::adjustBalanceWithAudit,
                    onSetBiometricLock = viewModel::setBiometricLock,
                    onSetRemindersEnabled = viewModel::setRemindersEnabled,
                    onUpdateThresholds = viewModel::updateBudgetThresholds,
                    onLoadDemoData = viewModel::loadDemoData,
                    onDeleteAllData = viewModel::deleteAllData
                )
            }
        }
    }

    if (movementSheetType != null) {
        MovementFormSheet(
            initialType = movementSheetType!!,
            editingTransaction = editingTransaction,
            onSave = { type, amount, category, date, description, recurrence, method ->
                val currentEditing = editingTransaction
                if (currentEditing == null) {
                    viewModel.addMovement(type, amount, category, date, description, recurrence, method)
                } else {
                    viewModel.updateMovement(
                        currentEditing.copy(
                            type = type,
                            amount = amount,
                            category = category,
                            date = date,
                            description = description,
                            recurrence = recurrence,
                            paymentMethod = method
                        )
                    )
                }
                movementSheetType = null
                editingTransaction = null
            },
            onDismiss = {
                movementSheetType = null
                editingTransaction = null
            }
        )
    }
}
