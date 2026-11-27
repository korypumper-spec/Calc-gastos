package com.mibolsillointeligente.app.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mibolsillointeligente.app.MiBolsilloApp
import com.mibolsillointeligente.app.ads.AdManager
import com.mibolsillointeligente.app.navigation.MiBolsilloRootScaffold
import com.mibolsillointeligente.app.presentation.theme.MiBolsilloTheme
import com.mibolsillointeligente.app.security.BiometricLockManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MiBolsilloApp
        lifecycleScope.launch {
            val prefs = app.preferences.preferencesFlow.first()
            AdManager.initializeConsentAndAds(this@MainActivity, prefs.adsRemoved)
            if (prefs.biometricLockEnabled && BiometricLockManager.canAuthenticate(this@MainActivity)) {
                viewModel.setLockedInitially(true)
                triggerBiometricPrompt()
            }
        }

        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
            MiBolsilloTheme(themeMode = uiState.preferences.themeMode) {
                MiBolsilloRootScaffold(
                    viewModel = viewModel,
                    onRequestBiometricUnlock = ::triggerBiometricPrompt
                )
            }
        }
    }

    private fun triggerBiometricPrompt() {
        BiometricLockManager.authenticate(
            activity = this,
            onSuccess = { viewModel.markUnlocked() },
            onError = { /* El usuario puede reintentar desde el botón Desbloquear */ }
        )
    }
}
