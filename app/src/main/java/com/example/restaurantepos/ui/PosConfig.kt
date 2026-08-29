package com.example.restaurantepos.ui

import android.content.Context
import org.json.JSONObject

/**
 * Configuración multipaís común entre ambas apps (PC y teléfono).
 *
 * El servidor (PC) es la única fuente de verdad: guarda el país, impuesto y
 * moneda en pos_database.json y lo expone vía /config. La elección del país
 * se hace únicamente desde la PC; el teléfono descarga la configuración
 * durante la sincronización (/sync-fast) y la guarda en SharedPreferences
 * para mostrar la moneda y el impuesto correctos.
 */
data class PosCountryConfig(
    val code: String = "MX",
    val name: String = "México",
    val currency: String = "$",
    val currencyCode: String = "MXN",
    val taxName: String = "IVA",
    val taxRate: Double = 16.0,
    val fiscalScheme: String = "CFDI",
    val fiscalRequired: Boolean = true
) {
    fun taxLabel(): String = "$taxName ($taxRate%)"
    fun formatMoney(amount: Double): String =
        String.format(java.util.Locale.US, "$currency%,.2f", amount)
}

object PosConfigStore {
    private const val PREFS = "pos_config"
    private const val KEY_CODE = "country_code"
    private const val KEY_NAME = "country_name"
    private const val KEY_CURRENCY = "currency"
    private const val KEY_CURRENCY_CODE = "currency_code"
    private const val KEY_TAX_NAME = "tax_name"
    private const val KEY_TAX_RATE = "tax_rate"
    private const val KEY_FISCAL = "fiscal_scheme"
    private const val KEY_FISCAL_REQ = "fiscal_required"

    fun save(context: Context, config: PosCountryConfig) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CODE, config.code)
            .putString(KEY_NAME, config.name)
            .putString(KEY_CURRENCY, config.currency)
            .putString(KEY_CURRENCY_CODE, config.currencyCode)
            .putString(KEY_TAX_NAME, config.taxName)
            .putFloat(KEY_TAX_RATE, config.taxRate.toFloat())
            .putString(KEY_FISCAL, config.fiscalScheme)
            .putBoolean(KEY_FISCAL_REQ, config.fiscalRequired)
            .apply()
    }

    fun load(context: Context): PosCountryConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PosCountryConfig(
            code = prefs.getString(KEY_CODE, "MX") ?: "MX",
            name = prefs.getString(KEY_NAME, "México") ?: "México",
            currency = prefs.getString(KEY_CURRENCY, "$") ?: "$",
            currencyCode = prefs.getString(KEY_CURRENCY_CODE, "MXN") ?: "MXN",
            taxName = prefs.getString(KEY_TAX_NAME, "IVA") ?: "IVA",
            taxRate = prefs.getFloat(KEY_TAX_RATE, 16.0f).toDouble(),
            fiscalScheme = prefs.getString(KEY_FISCAL, "CFDI") ?: "CFDI",
            fiscalRequired = prefs.getBoolean(KEY_FISCAL_REQ, true)
        )
    }

    /** Actualiza la configuración desde un JSON del servidor (/sync-fast o /config). */
    fun applyFromJson(context: Context, json: JSONObject) {
        val code = json.optString("code", "MX").ifBlank { "MX" }
        val name = json.optString("name", code)
        val currency = json.optString("currency", "$").ifBlank { "$" }
        val currencyCode = json.optString("currency_code", currency)
        val taxName = json.optString("tax_name", "IVA").ifBlank { "IVA" }
        val taxRate = json.optDouble("tax_rate", 16.0)
        val fiscalScheme = json.optString("fiscal_scheme", "Ninguno").ifBlank { "Ninguno" }
        val fiscalRequired = json.optBoolean("fiscal_required", false)
        save(
            context,
            PosCountryConfig(
                code = code,
                name = name,
                currency = currency,
                currencyCode = currencyCode,
                taxName = taxName,
                taxRate = taxRate,
                fiscalScheme = fiscalScheme,
                fiscalRequired = fiscalRequired
            )
        )
    }
}
