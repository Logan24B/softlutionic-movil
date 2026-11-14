package com.softlutionic.mobile.data.remote

import com.google.gson.JsonObject
import java.math.BigDecimal
import java.time.LocalDate

data class AnalyticsFilters(val start: String? = null, val end: String? = null) {
    init {
        listOfNotNull(start, end).forEach {
            require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(it))
            LocalDate.parse(it)
        }
        require(start == null || end == null || start <= end)
    }
}
data class AnalyticsService(val key: String, val name: String?, val amount: BigDecimal)
data class AnalyticsData(val amounts: Map<String, BigDecimal>, val services: List<AnalyticsService>)

/** Contract verified against apps/analytics/api.py and services.py. No zero fallback. */
object AnalyticsDto {
    val keys = listOf("facturacion_total", "monto_facturas_pagadas", "facturacion_pendiente", "facturacion_vencida")
    private fun text(row: JsonObject, key: String): String {
        val value = row[key]
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }
    private fun rows(root: JsonObject, filters: AnalyticsFilters): List<JsonObject> {
        val actual = root.getAsJsonObject("filters") ?: error("Missing filters")
        for ((key, expected) in listOf("start" to filters.start, "end" to filters.end)) {
            require(if (expected == null) !actual.has(key) else text(actual, key) == expected)
        }
        val data = root.getAsJsonArray("data") ?: error("Missing data")
        return data.map { it.asJsonObject }
    }
    fun parse(summary: JsonObject, ranking: JsonObject, filters: AnalyticsFilters): AnalyticsData {
        val entries = rows(summary, filters).map { text(it, "indicador") to BigDecimal(text(it, "importe")) }
        require(entries.map { it.first }.distinct().size == entries.size)
        val amounts = entries.toMap()
        require(keys.all { it in amounts })
        val services = rows(ranking, filters).map { row ->
            val key = row["servicio_key"]
            require(key != null && key.isJsonPrimitive && key.asJsonPrimitive.isNumber)
            val name = row["servicio"]?.let { if (it.isJsonNull) null else text(row, "servicio") }
            AnalyticsService(key.asString, name, BigDecimal(text(row, "importe")))
        }
        require(services.map { it.key }.distinct().size == services.size)
        return AnalyticsData(amounts, services.sortedByDescending { it.amount })
    }
}
