package com.softlutionic.mobile.ui.payments

import com.google.gson.*
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.ui.invoices.InvoicePresentation as I
import com.softlutionic.mobile.ui.contracts.ContractPresentation
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Catalog visibility is presentation only; routes and Room entries remain intact. */
object MobileNavigation {
    val hiddenCatalogs=setOf("departamentos","estados-factura","estados_factura")
    fun visible(name: String)=name !in hiddenCatalogs
}
data class PaymentFilters(val query: String="",val stateId: String?=null)
data class PaymentInvoice(val data: JsonObject,val code: String,val client: String,val state: String) {
    val id get()=I.text(data,"id")
    val stateId get()=I.text(data,"EstadoId")
    val payable get()=ContractPresentation.normalize(state.trim()) in setOf("pendiente","vencida")
}
data class PaymentRow(val data: JsonObject,val invoice: PaymentInvoice?)
object PaymentPresentation {
    val writable=setOf("Fecha_Pago","Monto_Pagado","EstadoPago","MetodoPago","FacturaId","NumeroRecibo")
    fun invoice(data: JsonObject,catalogs: Map<String,List<JsonObject>>): PaymentInvoice {
        val row=I.row(data,catalogs)
        return PaymentInvoice(data,row.code,row.client,row.state)
    }
    fun payment(data: JsonObject,catalogs: Map<String,List<JsonObject>>): PaymentRow {
        // Fresh invoice collection outranks the invoice snapshot embedded in a payment.
        val invoice=I.byId(catalogs,"facturas",I.text(data,"FacturaId")) ?: I.nested(data,"factura_detalle")
        return PaymentRow(data,invoice?.let { invoice(it,catalogs) })
    }
    fun matches(invoice: PaymentInvoice?,filter: PaymentFilters): Boolean {
        if(invoice==null) return filter.stateId==null && filter.query.isBlank()
        return (filter.stateId==null || invoice.stateId==filter.stateId) &&
            ContractPresentation.normalize(invoice.code+" "+invoice.client).contains(ContractPresentation.normalize(filter.query.trim()))
    }
    fun receipt(data: JsonObject)=I.text(data,"NumeroRecibo").let { if(it.isBlank()) "Pago registrado" else "REC-"+it.padStart(6,'0') }
    fun confirmed(data: JsonObject)=data["EstadoPago"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean==true
    fun method(data: JsonObject)=I.text(data,"metodo_descripcion").ifBlank {
        data["MetodoPago"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.let { if(it.asBoolean) "Transferencia" else "Efectivo" } ?: "No disponible"
    }
    fun dateTime(value: String)=runCatching {
        OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a",Locale("es","NI")))
    }.getOrDefault("Fecha no disponible")
    fun payload(module: Module,invoice: PaymentInvoice,receipt: String,transfer: Boolean,instant: ZonedDateTime): JsonObject {
        require(receipt.toIntOrNull()?.let { it>0 }==true) { "Introduce un número de recibo entero mayor que cero." }
        require(invoice.payable) { "La factura ya no está pendiente de pago." }
        require(module.fields.filter { it.required && !it.read_only }.all { it.name in writable }) { "El contrato de pago cambió. Actualiza la aplicación." }
        val values=JsonObject().apply {
            addProperty("FacturaId",invoice.id.toInt()); addProperty("NumeroRecibo",receipt.toInt())
            addProperty("Monto_Pagado",I.decimal(invoice.data,"Monto_Total") ?: error("Monto no disponible."))
            addProperty("EstadoPago",true); addProperty("MetodoPago",transfer)
            // Python/DRF stores microseconds; use whole seconds so an uncertain POST can reconcile exactly.
            addProperty("Fecha_Pago",instant.withNano(0).toOffsetDateTime().toString())
        }
        return JsonObject().apply { module.fields.filter { !it.read_only && it.name in writable }.forEach { add(it.name,values[it.name]) } }
    }
}
