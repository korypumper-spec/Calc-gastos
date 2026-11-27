package com.mibolsillointeligente.app.core

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val userMessage: String) : AppResult<Nothing>
}

object SafeLogger {
    /**
     * Regla de privacidad financiera: nunca registrar importes, saldos ni descripciones.
     * Solo se registran eventos técnicos sin datos sensibles en compilaciones de depuración.
     */
    fun logTechnicalError(tag: String, errorCode: String) {
        if (com.mibolsillointeligente.app.BuildConfig.DEBUG) {
            android.util.Log.w(tag, "Error técnico controlado: $errorCode")
        }
    }
}
