package com.softlutionic.mobile.data.remote

import com.google.gson.JsonObject

/** Canonical JSON returned by the mobile contact serializer; no Mongo details. */
data class ContactRequestDto(
    val id: String, val nombre: String?, val correo: String?, val telefono: String?,
    val asunto: String?, val mensaje: String?, val estado: String?, val prioridad: String?,
    val origen: String?, val tipo: String?, val fechaCreacion: String?
) {
    companion object {
        const val MODULE="solicitudes-contacto"
        fun fromJson(data: JsonObject): ContactRequestDto {
            fun text(key: String)=data[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            val id=text("id")?.takeIf(String::isNotBlank) ?: error("La solicitud no tiene un identificador válido.")
            return ContactRequestDto(id,text("nombre"),text("correo"),text("telefono"),text("asunto"),text("mensaje"),
                text("estado"),text("prioridad"),text("origen"),text("tipo"),text("fecha_creacion"))
        }
    }
}
