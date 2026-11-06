package com.softlutionic.mobile.ui.clients

import com.google.gson.*
import com.softlutionic.mobile.data.remote.Field
import com.softlutionic.mobile.domain.model.Record
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun JsonObject.clientText(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

enum class ClientStatusFilter { ALL, ACTIVE, INACTIVE }
data class ClientFilters(val query: String = "", val status: ClientStatusFilter = ClientStatusFilter.ALL, val departmentId: String? = null)
data class DepartmentOption(val id: String, val name: String)
data class ClientRow(val record: Record, val name: String, val initials: String, val department: String, val active: Boolean?)

/** Client-only presentation rules; the schema remains the authority for editable fields. */
object ClientPresentation {
    val editableNames = listOf("Nombre", "Apellido", "Cedula", "Telefono", "Correo", "Direccion", "DepartamentoId", "Estado")
    fun editable(fields: List<Field>) = editableNames.mapNotNull { name -> fields.find { it.name == name && !it.read_only } }
    fun departments(options: List<JsonObject>, records: List<Record>): List<DepartmentOption> {
        val catalog = options.mapNotNull { option ->
            val id = option.clientText("id"); val name = option.clientText("Nombre")
            if (id.isBlank() || name.isBlank()) null else DepartmentOption(id, name)
        }
        val embedded = records.mapNotNull { record ->
            val detail = record.payload["departamento_detalle"]?.takeIf { it.isJsonObject }?.asJsonObject
            val id = record.payload.clientText("DepartamentoId")
            val name = detail?.clientText("Nombre").orEmpty()
            if (id.isBlank() || name.isBlank()) null else DepartmentOption(id, name)
        }
        return (catalog + embedded).distinctBy { it.id }.sortedBy { normalize(it.name) }
    }
    fun row(record: Record, departments: List<DepartmentOption>): ClientRow {
        val data = record.payload
        val words = listOf(data.clientText("Nombre"), data.clientText("Apellido")).filter { it.isNotBlank() }
        val name = words.joinToString(" ")
        val initials = words.mapNotNull { it.trim().firstOrNull()?.toString() }.take(2).joinToString("").uppercase(Locale.ROOT)
        val embedded = data["departamento_detalle"]?.takeIf { it.isJsonObject }?.asJsonObject?.clientText("Nombre").orEmpty()
        val department = departments.find { it.id == data.clientText("DepartamentoId") }?.name ?: embedded
        val active = when (data.clientText("Estado").lowercase(Locale.ROOT)) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
        return ClientRow(record, name, initials.ifBlank { "?" }, department, active)
    }
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT).trim()
    fun matches(row: ClientRow, filters: ClientFilters): Boolean {
        if (filters.status == ClientStatusFilter.ACTIVE && row.active != true) return false
        if (filters.status == ClientStatusFilter.INACTIVE && row.active != false) return false
        if (filters.departmentId != null && row.record.payload.clientText("DepartamentoId") != filters.departmentId) return false
        val query = normalize(filters.query)
        if (query.isEmpty()) return true
        val data = row.record.payload
        val haystack = normalize(listOf(row.name, data.clientText("Cedula"), data.clientText("Telefono"), data.clientText("Correo")).joinToString(" "))
        if (query.split("\\s+".toRegex()).all { haystack.contains(it) }) return true
        // Phone and identity-number searches also accept unformatted digits.
        val digits = query.filter(Char::isDigit)
        return digits.isNotEmpty() && query.all { it.isDigit() || it in "+- ()" } &&
            listOf("Telefono", "Cedula").any { data.clientText(it).filter(Char::isDigit).contains(digits) }
    }
    fun validPhone(value: String): Boolean {
        val digits = value.filter(Char::isDigit)
        return value.all { it.isDigit() || it in "+- ()." } && (digits.length == 8 || (digits.length == 11 && digits.startsWith("505")))
    }
    fun normalizedPhone(value: String): String {
        if(!validPhone(value)) return value
        val digits = value.filter(Char::isDigit).takeLast(8)
        return "+505 ${digits.take(4)} ${digits.takeLast(4)}"
    }
    fun date(raw: String) = runCatching {
        LocalDate.parse(raw.take(10)).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("es", "NI")))
    }.getOrDefault(raw)
    fun time(raw: String) = runCatching {
        LocalTime.parse(raw).format(DateTimeFormatter.ofPattern("h:mm a", Locale("es", "NI")))
    }.getOrDefault(raw)
    fun payload(fields: List<Field>, values: Map<String, JsonElement>): JsonObject = JsonObject().apply {
        editable(fields).forEach { field -> values[field.name]?.let {
            add(field.name, if(field.name == "Telefono" && it.isJsonPrimitive) JsonPrimitive(normalizedPhone(it.asString)) else it)
        } }
    }
    /** Parse only JSON validation details. HTML/proxy errors never become form labels. */
    fun fieldErrors(body: String?): Map<String, String> = runCatching {
        val json = JsonParser.parseString(body ?: "{}").asJsonObject
        json.entrySet().filter { it.key in editableNames }.mapNotNull { (key, value) ->
            val message = if (value.isJsonArray) value.asJsonArray.filter { it.isJsonPrimitive }.joinToString(" ") { it.asString }
                else if (value.isJsonPrimitive) value.asString else ""
            if (message.isBlank()) null else key to message.take(400)
        }.toMap()
    }.getOrDefault(emptyMap())
    fun generalError(body: String?): String? = runCatching {
        val json = JsonParser.parseString(body ?: "{}").asJsonObject
        listOf("non_field_errors", "detail").mapNotNull { key -> json[key]?.let { value ->
            if(value.isJsonArray) value.asJsonArray.filter { it.isJsonPrimitive }.joinToString(" ") { it.asString }
            else if(value.isJsonPrimitive) value.asString else null
        } }.joinToString(" ").take(400).takeIf { it.isNotBlank() }
    }.getOrNull()
}
