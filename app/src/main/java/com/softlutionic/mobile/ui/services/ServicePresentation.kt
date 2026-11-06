package com.softlutionic.mobile.ui.services

import com.google.gson.*
import com.softlutionic.mobile.data.remote.Field
import com.softlutionic.mobile.domain.model.Record
import java.math.BigDecimal
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

enum class ServiceSort { NAME_ASC, NAME_DESC, PRICE_ASC, PRICE_DESC }
data class ServiceListState(val query: String = "", val sort: ServiceSort = ServiceSort.NAME_ASC)
internal fun JsonObject.serviceText(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

/** Presentation only. The original Record and schema drive the shared service CRUD. */
object ServicePresentation {
    val editableNames = listOf("Nombre_Servicio", "Descripcion", "Precio", "Duracion")
    fun editable(fields: List<Field>) = editableNames.mapNotNull { key -> fields.find { it.name == key && !it.read_only } }
    private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT).trim()
    fun amount(record: Record): BigDecimal? = runCatching { record.payload["Precio"]?.takeUnless { it.isJsonNull }?.asBigDecimal }.getOrNull()
    fun price(record: Record): String? = amount(record)?.let { amount ->
        "C$" + NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = amount.stripTrailingZeros().scale().coerceAtLeast(0)
        }.format(amount)
    }
    fun decimal(field: Field) = field.type in listOf("DecimalField", "FloatField")
    fun priceValue(raw: String, field: Field): JsonPrimitive? {
        val value = raw.trim()
        if(decimal(field)) {
            if(!value.matches("[0-9]+(\\.[0-9]+)?".toRegex())) return null
            return value.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO }?.let(::JsonPrimitive)
        }
        // The inspected model uses SQL/Django IntegerField, not a monetary DecimalField.
        if(!value.matches("[0-9]+".toRegex())) return null
        return value.toIntOrNull()?.takeIf { it >= 0 }?.let(::JsonPrimitive)
    }
    fun payload(fields: List<Field>, values: Map<String, String>) = JsonObject().apply {
        editable(fields).forEach { field -> values[field.name]?.let { raw ->
            if(field.name == "Precio") priceValue(raw, field)?.let { add(field.name, it) }
            else addProperty(field.name, raw.trim())
        } }
    }
    fun visible(records: List<Record>, state: ServiceListState): List<Record> {
        val query = normalize(state.query)
        val filtered = records.filter { record ->
            val text = normalize(record.payload.serviceText("Nombre_Servicio") + " " + record.payload.serviceText("Descripcion"))
            query.split("\\s+".toRegex()).all(text::contains)
        }
        val names = compareBy<Record> { normalize(it.payload.serviceText("Nombre_Servicio")) }.thenBy { it.id }
        return filtered.sortedWith(when(state.sort) {
            ServiceSort.NAME_ASC -> names
            ServiceSort.NAME_DESC -> names.reversed()
            else -> Comparator { a, b ->
                val av = amount(a); val bv = amount(b)
                when { av == null && bv == null -> names.compare(a,b); av == null -> 1; bv == null -> -1
                    else -> (if(state.sort == ServiceSort.PRICE_ASC) av.compareTo(bv) else bv.compareTo(av)).takeIf { it != 0 } ?: names.compare(a,b) }
            }
        })
    }
    fun errors(body: String?): Map<String, String> = runCatching {
        JsonParser.parseString(body ?: "{}").asJsonObject.entrySet().filter { it.key in editableNames + listOf("detail", "non_field_errors") }
            .mapNotNull { (key, value) ->
                val text = if(value.isJsonArray) value.asJsonArray.filter { it.isJsonPrimitive }.joinToString(" ") { it.asString }
                    else if(value.isJsonPrimitive) value.asString else ""
                if(text.isBlank()) null else key to text.take(400)
            }.toMap()
    }.getOrDefault(emptyMap())
}
