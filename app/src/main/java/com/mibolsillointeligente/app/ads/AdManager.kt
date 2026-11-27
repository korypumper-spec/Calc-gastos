package com.mibolsillointeligente.app.ads

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.mibolsillointeligente.app.BuildConfig
import com.mibolsillointeligente.app.R
import com.mibolsillointeligente.app.core.SafeLogger

object AdManager {
    @Volatile
    private var isMobileAdsInitialized = false

    @Volatile
    var canRequestAds: Boolean = false
        private set

    /**
     * Solicita el consentimiento publicitario mediante Google UMP cuando corresponde
     * e inicializa Google Mobile Ads únicamente si la configuración de anuncios está activa.
     */
    fun initializeConsentAndAds(activity: Activity, adsRemoved: Boolean) {
        if (adsRemoved || !BuildConfig.ADS_CONFIGURED) {
            canRequestAds = false
            return
        }

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    if (consentInfo.canRequestAds()) {
                        canRequestAds = true
                        initSdkOnce(activity.applicationContext)
                    }
                }
            },
            {
                // Si no hay conexión o falla UMP, respetar la última autorización válida o no solicitar anuncios
                if (consentInfo.canRequestAds()) {
                    canRequestAds = true
                    initSdkOnce(activity.applicationContext)
                } else {
                    canRequestAds = false
                }
                SafeLogger.logTechnicalError("AdManager", "UMP_UPDATE_SKIPPED")
            }
        )
    }

    private fun initSdkOnce(context: Context) {
        if (isMobileAdsInitialized) return
        synchronized(this) {
            if (!isMobileAdsInitialized) {
                MobileAds.initialize(context)
                isMobileAdsInitialized = true
            }
        }
    }
}

/**
 * Banner adaptativo anclado discreto, claramente identificado como "Anuncio".
 * Nunca se muestra en formularios, bloqueo biométrico, confirmaciones de pago ni sobre gráficos.
 */
@Composable
fun DiscreetAdaptiveBannerAd(
    adsRemoved: Boolean,
    modifier: Modifier = Modifier
) {
    if (adsRemoved || !BuildConfig.ADS_CONFIGURED || BuildConfig.ADMOB_BANNER_UNIT_ID.isBlank()) {
        return
    }
    if (!AdManager.canRequestAds) {
        return
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val adWidthDp = (configuration.screenWidthDp - 32).coerceAtLeast(320)

    var adLoaded by remember { mutableStateOf(false) }
    var adFailed by remember { mutableStateOf(false) }

    val adView = remember(adWidthDp) {
        AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
            setAdSize(
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
            )
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    adLoaded = true
                    adFailed = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    adLoaded = false
                    adFailed = true
                    SafeLogger.logTechnicalError("AdBanner", "LOAD_FAIL_${error.code}")
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }

    if (!adFailed) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = stringResource(R.string.ad_disclosure_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                if (adLoaded || !adFailed) {
                    AndroidView(
                        factory = { adView },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Nota técnica sobre compra sin publicidad:
 * La opción para eliminar anuncios mediante Google Play Billing se mantiene deshabilitada
 * en esta versión hasta registrar el producto administrado (SKU) en Google Play Console
 * y validar las compras mediante backend o Play Developer API.
 */
object PlayBillingStatus {
    const val IS_BILLING_ENABLED = false
}
