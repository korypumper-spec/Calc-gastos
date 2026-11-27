package com.mibolsillointeligente.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val mexicoLocale: Locale = Locale.forLanguageTag("es-MX")
    private val hundred = BigDecimal(100)

    fun formatMxn(amount: BigDecimal): String {
        val formatter = NumberFormat.getCurrencyInstance(mexicoLocale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return formatter.format(amount.setScale(2, RoundingMode.HALF_EVEN))
    }

    fun formatCentavosMxn(centavos: Long): String {
        return formatMxn(centavosToBigDecimal(centavos))
    }

    fun centavosToBigDecimal(centavos: Long): BigDecimal {
        return BigDecimal.valueOf(centavos).divide(hundred, 2, RoundingMode.HALF_EVEN)
    }

    fun bigDecimalToCentavos(amount: BigDecimal): Long {
        val scaled = amount.setScale(2, RoundingMode.HALF_EVEN)
        return scaled.multiply(hundred).longValueExact()
    }

    /**
     * Valida y convierte una cadena ingresada por el usuario a BigDecimal positivo o cero.
     * Devuelve null si el formato es inválido o excede límites razonables.
     */
    fun parseAmountInput(raw: String, allowZero: Boolean = false): BigDecimal? {
        val cleaned = raw.trim()
            .replace("$", "")
            .replace(",", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return null
        val parsed = cleaned.toBigDecimalOrNull() ?: return null
        val scaled = parsed.setScale(2, RoundingMode.HALF_EVEN)
        val minAllowed = if (allowZero) BigDecimal.ZERO else BigDecimal("0.01")
        val maxAllowed = BigDecimal("999999999.99")
        if (scaled < minAllowed || scaled > maxAllowed) return null
        return scaled
    }
}
