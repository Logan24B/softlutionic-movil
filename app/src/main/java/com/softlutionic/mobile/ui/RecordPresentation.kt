package com.softlutionic.mobile.ui

/** Visible labels only. Never removes a key from an API/Room record or payload. */
object RecordPresentation {
    private val businessFields=setOf("CodigoFact","NumeroRecibo","NumeroFactura","CodigoFactura","NumeroContrato","CodigoContrato","Referencia","ReferenciaPago")
    fun isTechnicalIdField(name: String): Boolean {
        if(businessFields.any { it.equals(name,true) }) return false
        return name.equals("id",true) || name.equals("pk",true) || name.endsWith("_id",true) ||
            name.endsWith("Id") || name.endsWith("ID") || name.matches(Regex("^(Id|ID)[A-Z_].*"))
    }
    fun label(name: String): String = when(name) {
        "TipoContratoId" -> "Tipo de contrato"
        "EstadoFacturaId","EstadoId" -> "Estado de factura"
        else -> name.removeSuffix("_detalle").replace(Regex("^(Id|ID)(?=[A-Z_])"),"").replace(Regex("(?i)_?id$"),"")
            .replace(Regex("([a-z])([A-Z])"),"$1 $2").replace('_',' ').replaceFirstChar { it.uppercase() }
    }
    fun embeddedKey(name: String): String = name.replace(Regex("(?i)_?id$"),"")
        .replace(Regex("([a-z])([A-Z])"),"$1_$2").lowercase()+"_detalle"
}
