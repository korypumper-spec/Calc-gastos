package com.mibolsillointeligente.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mibolsillointeligente.app.presentation.MainActivity
import org.junit.Rule
import org.junit.Test

class AppNavigationComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationTabsNavigateBetweenScreens() {
        composeRule.onNodeWithText("Saldo disponible").assertIsDisplayed()

        composeRule.onNodeWithText("Movimientos").performClick()
        composeRule.onNodeWithText("Exportar CSV").assertIsDisplayed()

        composeRule.onNodeWithText("Presupuesto").performClick()
        composeRule.onNodeWithText("Calendario de pagos").assertIsDisplayed()

        composeRule.onNodeWithText("Ahorro").performClick()
        composeRule.onNodeWithText("Metas de ahorro").assertIsDisplayed()

        composeRule.onNodeWithText("Perfil").performClick()
        composeRule.onNodeWithText("Perfil y configuración").assertIsDisplayed()
    }
}
