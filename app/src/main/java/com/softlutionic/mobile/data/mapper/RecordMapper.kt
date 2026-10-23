package com.softlutionic.mobile.data.mapper
import com.google.gson.*
import com.softlutionic.mobile.domain.model.Record
fun JsonObject.toRecord(): Record {
 val id = get("id")?.asString ?: get("IdLog")?.asString ?: ""
 val title = listOf("Nombre", "Apellido", "Nombre_Servicio", "CodigoFact", "NumeroRecibo", "PrimerNombre", "SegundoNombre", "Mensaje", "Tabla", "estado", "Descripcion").mapNotNull { get(it)?.takeIf { v -> v.isJsonPrimitive }?.asString }.joinToString(" ").ifBlank { "Registro $id" }
 val primitiveDetail = entrySet().filter { it.value.isJsonPrimitive && it.key !in listOf("Contrasena","password") }.joinToString("\n") { "${it.key}: ${it.value.asString}" }
 val related = entrySet().filter { it.key.endsWith("_detalle") && it.value.isJsonObject }.joinToString("\n") { "${it.key.removeSuffix("_detalle")}: ${it.value.asJsonObject.toRecord().title}" }
 val lines = get("detalles")?.takeIf { it.isJsonArray }?.asJsonArray?.joinToString("\n") { value ->
  val line=value.asJsonObject
  val service=line.get("servicio_detalle")?.takeIf { it.isJsonObject }?.asJsonObject?.toRecord()?.title ?: "Servicio ${line.get("ServicioId")?.asString.orEmpty()}"
  "$service · Cantidad: ${line.get("Cantidad")?.asString.orEmpty()} · Precio: ${line.get("PrecioVenta")?.asString.orEmpty()} · Subtotal: ${line.get("Subtotal")?.asString.orEmpty()}"
 }.orEmpty()
 val detail = listOf(primitiveDetail,related,lines).filter { it.isNotBlank() }.joinToString("\n\n")
 return Record(id,title,detail,this)
}
