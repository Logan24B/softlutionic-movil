package com.softlutionic.mobile.data.mapper

import com.google.gson.*
import com.softlutionic.mobile.domain.model.Record
import com.softlutionic.mobile.ui.RecordPresentation

fun JsonObject.toRecord(catalogs: Map<String,List<JsonObject>> = emptyMap(), relations: Map<String,String> = emptyMap()): Record =
    mappedRecord(catalogs,relations,0)

private fun JsonObject.mappedRecord(catalogs: Map<String,List<JsonObject>>,relations: Map<String,String>,depth: Int): Record {
    fun text(key: String)=get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    val id=text("id").ifBlank { text("IdLog") }
    val defaultRoutes=mapOf("ClienteId" to "clientes","ServicioId" to "servicios","UsuarioId" to "usuarios",
        "ContratoId" to "contratos","TipoContratoId" to "tipos-contrato","DepartamentoId" to "departamentos",
        "FacturaId" to "facturas","PagoId" to "pagos","MoraId" to "moras","EstadoId" to "estados-factura","EstadoFacturaId" to "estados-factura")
    fun related(key: String): String {
        if(depth>=3) return "No disponible"
        val embedded=get(RecordPresentation.embeddedKey(key))?.takeIf { it.isJsonObject }?.asJsonObject
        val route=relations[key] ?: defaultRoutes[key]
        val option=embedded ?: catalogs[route].orEmpty().find { it["id"]?.takeIf { v -> v.isJsonPrimitive }?.asString==text(key) }
        return option?.mappedRecord(catalogs,emptyMap(),depth+1)?.title?.takeUnless { it=="Registro" } ?: "No disponible"
    }
    val person=listOf(text("Nombre"),text("Apellido")).filter { it.isNotBlank() }.joinToString(" ")
        .ifBlank { listOf(text("PrimerNombre"),text("SegundoNombre")).filter { it.isNotBlank() }.joinToString(" ") }
    val business=listOf("CodigoFact","NumeroRecibo","NumeroFactura","CodigoFactura","NumeroContrato","CodigoContrato","Referencia","ReferenciaPago").firstNotNullOfOrNull { key -> text(key).takeIf { it.isNotBlank() } }
    val contractLabel=if(has("ClienteId") && has("TipoContratoId")) listOf(related("ClienteId"),related("TipoContratoId")).filter { it!="No disponible" }.joinToString(" ? ") else ""
    val title=person.ifBlank { text("Nombre_Servicio") }.ifBlank { business.orEmpty() }.ifBlank { text("Usuario") }
        .ifBlank { contractLabel }.ifBlank { listOf("Mensaje","Tabla","estado","Descripcion").firstNotNullOfOrNull { text(it).takeIf { v -> v.isNotBlank() } }.orEmpty() }.ifBlank { "Registro" }
    val parts=mutableListOf<String>()
    entrySet().filter { it.value.isJsonPrimitive && !RecordPresentation.isTechnicalIdField(it.key) && it.key !in relations && it.key !in setOf("Contrasena","password") }
        .forEach { parts.add("${it.key}: ${it.value.asString}") }
    val displayed=mutableSetOf<String>()
    entrySet().filter { it.key in relations || (RecordPresentation.isTechnicalIdField(it.key) && it.key in defaultRoutes) }.forEach {
        parts.add("${RecordPresentation.label(it.key)}: ${related(it.key)}"); displayed.add(RecordPresentation.embeddedKey(it.key))
    }
    entrySet().filter { it.key.endsWith("_detalle") && it.value.isJsonObject && it.key !in displayed }.forEach {
        val label=RecordPresentation.label(it.key)
        parts.add("$label: ${if(depth<3) it.value.asJsonObject.mappedRecord(catalogs,emptyMap(),depth+1).title else "No disponible"}")
    }
    get("detalles")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { value ->
        if(value.isJsonObject) {
            val line=value.asJsonObject
            fun amount(key: String)=line[key]?.takeIf { it.isJsonPrimitive }?.asString ?: "No disponible"
            val service=line["servicio_detalle"]?.takeIf { it.isJsonObject }?.asJsonObject
                ?: catalogs["servicios"].orEmpty().find { it["id"]==line["ServicioId"] }
            val name=service?.mappedRecord(catalogs,emptyMap(),depth+1)?.title ?: "Servicio no disponible"
            parts.add("$name ? Cantidad: ${amount("Cantidad")} ? Precio: ${amount("PrecioVenta")} ? Subtotal: ${amount("Subtotal")}")
        }
    }
    return Record(id,title,parts.joinToString("\n"),this)
}
