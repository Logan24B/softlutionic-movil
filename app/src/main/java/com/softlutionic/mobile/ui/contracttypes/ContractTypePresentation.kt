package com.softlutionic.mobile.ui.contracttypes

import com.google.gson.JsonObject
import com.softlutionic.mobile.data.remote.Field
import com.softlutionic.mobile.ui.contracts.ContractPresentation

data class ContractTypeRow(val name: String, val description: String)

/** Technical keys stay in the original record, outside the visible presentation. */
object ContractTypePresentation {
    fun text(data: JsonObject,key: String) = data[key]?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    fun row(data: JsonObject) = ContractTypeRow(text(data,"Nombre").ifBlank { "Nombre no disponible" },text(data,"Descripcion"))
    fun editable(fields: List<Field>) = fields.filter { !it.read_only && it.name in setOf("Nombre","Descripcion") }
    fun label(field: Field) = when(field.name) { "Nombre" -> "Nombre"; "Descripcion" -> "Descripción"; else -> field.label }
    fun visible(records: List<JsonObject>,query: String) = records.filter {
        val row=row(it)
        ContractPresentation.normalize(row.name+" "+row.description).contains(ContractPresentation.normalize(query.trim()))
    }
    fun validate(field: Field,value: String): String? = when {
        field.required && value.isBlank() -> "Completa ${label(field).lowercase()}."
        field.max_length!=null && value.length>field.max_length -> "Máximo ${field.max_length} caracteres."
        else -> null
    }
    fun payload(fields: List<Field>,values: Map<String,String>) = JsonObject().apply {
        editable(fields).forEach { field -> values[field.name]?.let { addProperty(field.name,it.trim()) } }
    }
}
