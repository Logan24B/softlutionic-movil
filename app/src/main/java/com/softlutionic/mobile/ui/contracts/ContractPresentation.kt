package com.softlutionic.mobile.ui.contracts

import com.google.gson.*
import com.softlutionic.mobile.data.remote.Field
import java.text.Normalizer
import java.time.LocalDate

fun JsonObject.contractText(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
data class ContractFilters(val query: String = "", val clientId: String? = null, val typeId: String? = null)
data class ContractOption(val id: String, val label: String)
data class ContractRow(val data: JsonObject, val client: String, val type: String) {
    val id get() = data.contractText("id")
    val description get() = data.contractText("Descripcion")
}

/** Contract-only presentation; raw schema and API records remain unchanged. */
object ContractPresentation {
    val labels = mapOf("ClienteId" to "Cliente", "TipoContratoId" to "Tipo de contrato",
        "Fecha_Inc" to "Fecha de inicio", "Fecha_Fin" to "Fecha de finalización",
        "EstadoContrato" to "Contrato activo", "Descripcion" to "Descripción")
    fun label(field: Field) = labels[field.name] ?: if(com.softlutionic.mobile.ui.RecordPresentation.isTechnicalIdField(field.name)) com.softlutionic.mobile.ui.RecordPresentation.label(field.name) else field.label
    fun editable(fields: List<Field>) = fields.filter { !it.read_only && (!com.softlutionic.mobile.ui.RecordPresentation.isTechnicalIdField(it.name) || it.relation!=null) && !it.name.endsWith("_detalle") }
    private fun human(data: JsonObject, route: String) = if(route == "clientes")
        listOf(data.contractText("Nombre"), data.contractText("Apellido")).filter { it.isNotBlank() }.joinToString(" ")
        else data.contractText("Nombre").ifBlank { data.contractText("Descripcion") }
    fun options(route: String, catalog: List<JsonObject>, records: List<JsonObject>): List<ContractOption> {
        val key = if(route == "clientes") "cliente_detalle" else "tipo_contrato_detalle"
        val relation = if(route == "clientes") "ClienteId" else "TipoContratoId"
        val embedded = records.mapNotNull { record -> record[key]?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy()?.apply {
            if(!has("id")) addProperty("id", record.contractText(relation))
        } }
        return (catalog + embedded).mapNotNull { item ->
            val id = item.contractText("id"); val name = human(item, route)
            if(id.isBlank() || name.isBlank()) null else ContractOption(id, name)
        }.distinctBy { it.id }.sortedBy { normalize(it.label) }
    }
    fun row(data: JsonObject, clients: List<ContractOption>, types: List<ContractOption>): ContractRow {
        fun resolve(key: String, options: List<ContractOption>, missing: String) =
            options.find { it.id == data.contractText(key) }?.label ?: missing
        return ContractRow(data, resolve("ClienteId", clients, "Cliente no disponible"),
            resolve("TipoContratoId", types, if(data.contractText("TipoContratoId").isBlank()) "Sin tipo de contrato" else "Tipo no disponible"))
    }
    fun visible(rows: List<ContractRow>, filter: ContractFilters) = rows.filter {
        (filter.clientId == null || it.data.contractText("ClienteId") == filter.clientId) &&
        (filter.typeId == null || it.data.contractText("TipoContratoId") == filter.typeId) &&
        normalize(listOf(it.client, it.type, it.description).joinToString(" ")).contains(normalize(filter.query.trim()))
    }
    fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)
    fun validate(field: Field, value: String): String? = when {
        field.required && value.isBlank() -> when(field.name) {
            "ClienteId" -> "Selecciona un cliente."
            "TipoContratoId" -> "Selecciona un tipo de contrato."
            else -> "Completa ${label(field).lowercase()}."
        }
        value.isNotBlank() && field.max_length != null && value.length > field.max_length -> "Máximo ${field.max_length} caracteres."
        value.isNotBlank() && field.type == "DateField" && runCatching { LocalDate.parse(value) }.isFailure -> "Usa una fecha válida (AAAA-MM-DD)."
        else -> null
    }
    fun payload(fields: List<Field>, values: Map<String, String>) = JsonObject().apply {
        editable(fields).forEach { field ->
            val value = values[field.name] ?: return@forEach
            when {
                value.isBlank() && !field.required && field.type == "BooleanField" -> Unit // Preserve the server default.
                value.isBlank() && field.relation != null && !field.required -> add(field.name, JsonNull.INSTANCE)
                field.type == "BooleanField" -> addProperty(field.name, value.toBooleanStrict())
                field.relation != null || field.type == "IntegerField" -> add(field.name, JsonPrimitive(value.toLong()))
                else -> addProperty(field.name, value)
            }
        }
    }
    fun errors(body: String?): Map<String, String> = runCatching {
        JsonParser.parseString(body).asJsonObject.entrySet().associate { (key, value) ->
            key to if(value.isJsonArray) value.asJsonArray.joinToString(" ") { it.asString } else value.asString
        }
    }.getOrDefault(emptyMap())
}
