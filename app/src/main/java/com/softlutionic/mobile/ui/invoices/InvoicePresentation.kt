package com.softlutionic.mobile.ui.invoices

import com.google.gson.*
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.ui.contracts.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

data class InvoiceLine(val key: Long,val serviceId: String,val quantity: String="1",val price: String="",val preservedPrice: Boolean=false)
data class InvoiceDraft(val id: String?=null,val values: Map<String,String> = emptyMap(),val lines: List<InvoiceLine> = emptyList(),val clientId: String="",val typeId: String="")
data class InvoiceFilters(val query: String="",val state: String?=null,val clientId: String?=null,val start: String?=null,val end: String?=null)
data class InvoiceRow(val data: JsonObject,val code: String,val client: String,val contract: String,val state: String,val clientId: String) {
    val id get()=InvoicePresentation.text(data,"id")
}

/** UI estimates only. Confirmed totals, state and due dates always come from the API. */
object InvoicePresentation {
    fun text(data: JsonObject?,key: String)=data?.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    fun nested(data: JsonObject?,key: String)=data?.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    fun array(data: JsonObject?,key: String)=data?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { v -> v.isJsonObject }?.asJsonObject }.orEmpty()
    fun byId(catalogs: Map<String,List<JsonObject>>,route: String,id: String)=catalogs[route].orEmpty().find { text(it,"id")==id }
    fun contract(data: JsonObject,catalogs: Map<String,List<JsonObject>>)=nested(data,"contrato_detalle") ?: byId(catalogs,"contratos",text(data,"ContratoId"))
    fun contractRow(data: JsonObject,catalogs: Map<String,List<JsonObject>>): ContractRow = ContractPresentation.row(data,
        ContractPresentation.options("clientes",catalogs["clientes"].orEmpty(),listOf(data)),
        ContractPresentation.options("tipos-contrato",catalogs["tipos-contrato"].orEmpty(),listOf(data)))
    fun contractLabel(data: JsonObject,catalogs: Map<String,List<JsonObject>>): String {
        val row=contractRow(data,catalogs)
        return listOf(row.client,row.type,data.contractText("Descripcion")).filter { it.isNotBlank() }.joinToString(" · ")
    }
    fun row(data: JsonObject,catalogs: Map<String,List<JsonObject>>): InvoiceRow {
        val contract=contract(data,catalogs)
        val context=contract?.let { contractRow(it,catalogs) }
        val client=nested(data,"cliente_detalle") ?: nested(contract,"cliente_detalle") ?: byId(catalogs,"clientes",text(contract,"ClienteId"))
        val name=listOf(text(client,"Nombre"),text(client,"Apellido")).filter { it.isNotBlank() }.joinToString(" ")
        val state=nested(data,"estado_detalle") ?: byId(catalogs,"estados-factura",text(data,"EstadoId"))
        return InvoiceRow(data,text(data,"CodigoFact"),name.ifBlank { context?.client ?: "Cliente no disponible" },
            context?.type ?: "Contrato no disponible",text(state,"estado").ifBlank { "Estado no disponible" },text(contract,"ClienteId").ifBlank { text(client,"id") })
    }
    fun service(line: InvoiceLine,catalogs: Map<String,List<JsonObject>>)=byId(catalogs,"servicios",line.serviceId)
    fun price(line: InvoiceLine,catalogs: Map<String,List<JsonObject>>): BigDecimal? {
        val value=if(line.preservedPrice) line.price.trim() else text(service(line,catalogs),"Precio")
        return value.toIntOrNull()?.takeIf { it>=0 }?.toBigDecimal()
    }
    fun quantity(line: InvoiceLine)=line.quantity.toIntOrNull()?.takeIf { it>0 }
    // Exact existing commercial types; unknown names are never guessed from fragments.
    fun invoiceType(type: JsonObject?): String=when(ContractPresentation.normalize(text(type,"Nombre").trim())) {
        "servicio mensual" -> "CRÉDITO"
        "servicio unico", "soporte tecnico" -> "CONTADO"
        else -> "No determinada"
    }
    fun subtotal(line: InvoiceLine,catalogs: Map<String,List<JsonObject>>): BigDecimal? = quantity(line)?.let { count -> price(line,catalogs)?.multiply(count.toBigDecimal()) }
    fun total(draft: InvoiceDraft,catalogs: Map<String,List<JsonObject>>): BigDecimal? {
        if(draft.lines.isEmpty()) return null
        return draft.lines.fold(BigDecimal.ZERO as BigDecimal?) { sum,line -> sum?.let { value -> subtotal(line,catalogs)?.add(value) } }
    }
    fun editableHeaders(module: Module)=module.fields.filter { !it.read_only && it.name in setOf("ContratoId","CodigoFact","Fecha_Emision","Hora_Emision","Fecha_Vencimiento","Hora_Vencimiento") }
    fun clientName(client: JsonObject?)=listOf(text(client,"Nombre"),text(client,"Apellido")).filter { it.isNotBlank() }.joinToString(" ")
    fun activeClient(client: JsonObject?)=client?.get("Estado")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean==true
    fun matchingContract(contracts: List<JsonObject>,clientId: String,typeId: String): JsonObject? = contracts
        .filter { text(it,"ClienteId")==clientId && text(it,"TipoContratoId")==typeId && it["EstadoContrato"]?.takeIf { flag -> flag.isJsonPrimitive && flag.asJsonPrimitive.isBoolean }?.asBoolean==true && text(it,"id").toLongOrNull()!=null }
        .minByOrNull { text(it,"id").toLong() }
    fun contractPayload(clientId: String,type: JsonObject,emission: InvoiceEmission)=JsonObject().apply {
        addProperty("ClienteId",clientId.toInt()); addProperty("TipoContratoId",text(type,"id").toInt())
        addProperty("Fecha_Inc",emission.date); addProperty("Fecha_Fin",InvoiceTiming.dueDate(emission.captured.toLocalDate(),text(type,"Nombre")).toString())
        addProperty("EstadoContrato",true); addProperty("Descripcion",text(type,"Descripcion").ifBlank { text(type,"Nombre") })
    }
    fun validate(draft: InvoiceDraft,module: Module,catalogs: Map<String,List<JsonObject>>): Map<String,String> {
        val errors=linkedMapOf<String,String>()
        val client=byId(catalogs,"clientes",draft.clientId)
        if(client==null) errors["ClienteId"]="Selecciona un cliente."
        else if(!activeClient(client)) errors["ClienteId"]="El cliente seleccionado está inactivo y no puede facturarse."
        if(byId(catalogs,"tipos-contrato",draft.typeId)==null) errors["TipoContratoId"]="Selecciona un tipo de contrato."
        editableHeaders(module).forEach { field ->
            if(field.name=="ContratoId" || (draft.id==null && field.name in setOf("Fecha_Emision","Hora_Emision"))) return@forEach
            val value=draft.values[field.name].orEmpty()
            val error=when {
                value.isBlank() && field.required -> if(field.name=="ContratoId") "Selecciona un contrato." else "Completa ${label(field.name).lowercase()}."
                value.isBlank() -> null
                field.name=="ContratoId" && byId(catalogs,"contratos",value)==null -> "Actualiza los contratos y selecciona uno disponible."
                field.type=="IntegerField" && value.toIntOrNull()==null -> "Introduce un código entero válido."
                field.type=="DateField" && runCatching { LocalDate.parse(value) }.isFailure -> "Selecciona una fecha válida."
                field.type=="TimeField" && runCatching { LocalTime.parse(value) }.isFailure -> "Selecciona una hora válida."
                else -> null
            }
            if(error!=null) errors[field.name]=error
        }
        if(draft.lines.isEmpty()) errors["lineas"]="Agrega al menos un servicio."
        draft.lines.forEach { line ->
            when {
                service(line,catalogs)==null -> errors["line-${line.key}"]="Actualiza los servicios y selecciona uno disponible."
                line.serviceId.isBlank() -> errors["line-${line.key}"]="Selecciona un servicio."
                quantity(line)==null -> errors["line-${line.key}"]="La cantidad debe ser un entero mayor que cero."
                price(line,catalogs)==null -> errors["line-${line.key}"]="El precio debe ser un entero no negativo."
            }
        }
        return errors
    }
    fun payload(draft: InvoiceDraft,module: Module): JsonObject = JsonObject().apply {
        editableHeaders(module).forEach { field ->
            if(draft.id!=null && field.name in setOf("Fecha_Emision","Hora_Emision")) return@forEach
            val value=draft.values[field.name].orEmpty()
            if(value.isNotBlank()) if(field.relation!=null || field.type=="IntegerField") addProperty(field.name,value.toInt()) else addProperty(field.name,value)
        }
        add("lineas",JsonArray().apply { draft.lines.forEach { line -> add(JsonObject().apply {
            addProperty("ServicioId",line.serviceId.toInt()); addProperty("Cantidad",line.quantity.toInt())
            if(line.preservedPrice && line.price.isNotBlank()) addProperty("PrecioVenta",line.price.trim().toInt())
        }) } })
    }
    fun draft(data: JsonObject,module: Module,catalogs: Map<String,List<JsonObject>> = emptyMap()): InvoiceDraft {
        val context=contract(data,catalogs)
        return InvoiceDraft(text(data,"id"),editableHeaders(module).associate { it.name to text(data,it.name) },
            array(data,"detalles").mapIndexed { index,line -> InvoiceLine(index.toLong()+1,text(line,"ServicioId"),text(line,"Cantidad"),text(line,"PrecioVenta"),true) },text(context,"ClienteId"),text(context,"TipoContratoId"))
    }
    fun label(key: String)=mapOf("ContratoId" to "Contrato","CodigoFact" to "Código de factura","Fecha_Emision" to "Fecha de emisión","Hora_Emision" to "Hora de emisión","Fecha_Vencimiento" to "Fecha de vencimiento","Hora_Vencimiento" to "Hora de vencimiento")[key] ?: key
    fun errors(body: String?): Map<String,String> = runCatching {
        fun readable(value: JsonElement): String = when {
            value.isJsonArray -> value.asJsonArray.joinToString(" ") { readable(it) }
            value.isJsonObject -> value.asJsonObject.entrySet().joinToString(" ") { (key,item) ->
                val name=when(key) { "ServicioId" -> "Servicio"; "PrecioVenta" -> "Precio unitario"; "Cantidad" -> "Cantidad"; else -> if(com.softlutionic.mobile.ui.RecordPresentation.isTechnicalIdField(key)) com.softlutionic.mobile.ui.RecordPresentation.label(key) else label(key) }
                "$name: ${readable(item)}"
            }
            value.isJsonPrimitive -> value.asString.let { if(it.contains("invalid pk",true) || it.contains("does not exist",true)) "La opción seleccionada no está disponible. Actualiza los catálogos." else it.replace("CodigoFact","código de factura").replace("ContratoId","contrato").replace("ServicioId","servicio").replace("PrecioVenta","precio unitario") }
            else -> "Revisa los datos."
        }
        JsonParser.parseString(body).asJsonObject.entrySet().associate { it.key to readable(it.value) }
    }.getOrDefault(emptyMap())
    fun money(value: BigDecimal?)=value?.let { "C$ "+NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits=2; maximumFractionDigits=2 }.format(it) } ?: "No disponible"
    fun decimal(data: JsonObject?,key: String)=runCatching { text(data,key).toBigDecimal() }.getOrNull()
    fun date(value: String)=runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale("es","NI"))) }.getOrDefault(if(value.isBlank()) "No disponible" else value)
    fun time(value: String)=runCatching { LocalTime.parse(value).format(DateTimeFormatter.ofPattern("h:mm a",Locale("es","NI"))) }.getOrDefault(if(value.isBlank()) "" else value)
    fun visible(rows: List<InvoiceRow>,filters: InvoiceFilters)=rows.filter { row ->
        (filters.state==null || row.state==filters.state) && (filters.clientId==null || row.clientId==filters.clientId) &&
        (filters.start==null || text(row.data,"Fecha_Emision")>=filters.start) && (filters.end==null || text(row.data,"Fecha_Emision")<=filters.end) &&
        ContractPresentation.normalize(row.code+" "+row.client+" "+row.contract).contains(ContractPresentation.normalize(filters.query.trim()))
    }
}
