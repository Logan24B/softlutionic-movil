package com.softlutionic.mobile.ui.consultation

import com.google.gson.JsonObject
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.data.mapper.toRecord
import com.softlutionic.mobile.ui.contracts.ContractPresentation
import com.softlutionic.mobile.ui.invoices.InvoicePresentation as I
import com.softlutionic.mobile.ui.payments.PaymentPresentation
import java.math.BigDecimal
import com.softlutionic.mobile.domain.auth.AuthorizationPolicy
import com.softlutionic.mobile.domain.auth.UserIdentity

data class ConsultationFilter(val query: String="",val state: String?=null,val user: String?=null,val action: String?=null,val entity: String?=null,val start: String?=null,val end: String?=null)
data class MoraCard(val data: JsonObject,val invoice: String,val client: String,val invoiceState: String,val due: String,val state: String)
data class NoticeCard(val data: JsonObject,val client: String,val message: String,val read: Boolean?,val title: String="",val date: String="")
data class AuditCard(val data: JsonObject,val user: String,val action: String,val entity: String,val target: String,val description: String,val technicalDescription: Boolean)

/** Presentation restrictions narrow schema capabilities; they do not grant role permissions. */
object ConsultationPresentation {
    val routes=setOf("moras","notificaciones","auditoria")
    fun readOnly(module: Module)=module.name in setOf("moras","auditoria")
    fun canRead(actor: UserIdentity?,module: Module)=AuthorizationPolicy.effective(actor,module).permissions.read && (module.name!="auditoria" || AuthorizationPolicy.canReadUsers(actor))
    // /leer/ is a per-user action, not generic record editing; Django enforces visibility.
    fun canMarkRead(module: Module)=module.name=="notificaciones" && module.permissions.read
    fun bool(data: JsonObject,key: String): Boolean?=data[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
    fun mora(data: JsonObject,catalogs: Map<String,List<JsonObject>>): MoraCard {
        val invoice=I.byId(catalogs,"facturas",I.text(data,"FacturaId")) ?: I.nested(data,"factura_detalle")
        val row=invoice?.let { I.row(it,catalogs) }
        val state=I.text(data,"estado_descripcion").ifBlank { when(bool(data,"EstadoMora")) { true -> "Pagada"; false -> "Pendiente"; null -> "Estado no disponible" } }
        return MoraCard(data,row?.code?.ifBlank { "Factura no disponible" } ?: "Factura no disponible",row?.client ?: "Cliente no disponible",row?.state ?: "Estado no disponible",
            invoice?.let { I.date(I.text(it,"Fecha_Vencimiento"))+" · "+I.time(I.text(it,"Hora_Vencimiento")) } ?: "No disponible",state)
    }
    fun notice(data: JsonObject,catalogs: Map<String,List<JsonObject>>): NoticeCard {
        return NoticeCard(data,"",I.text(data,"mensaje"),bool(data,"leida"),I.text(data,"titulo"),I.text(data,"fecha_creacion"))
    }
    private val auditRoutes=mapOf("clientes" to "clientes","facturas" to "facturas","pagos" to "pagos","usuarios" to "usuarios","contratos" to "contratos","servicios" to "servicios","mora" to "moras","notificaciones" to "notificaciones")
    private val technical=Regex("(?i)(\\b(?:id|IdLog|\\w+Id|ClavePrincipal|pk)\\b\\s*[=:]|\\\"(?:id|IdLog|\\w+Id|ClavePrincipal)\\\"\\s*:)")
    fun audit(data: JsonObject,catalogs: Map<String,List<JsonObject>>): AuditCard {
        val table=I.text(data,"Tabla")
        val route=auditRoutes[table.substringAfterLast('.').trim('[',']').lowercase()]
        val target=route?.let { I.byId(catalogs,it,I.text(data,"ClavePrincipal")) }
        val targetLabel=when(route) {
            "facturas" -> target?.let { I.text(it,"CodigoFact") }
            "pagos" -> target?.let { PaymentPresentation.receipt(it) }
            "usuarios" -> target?.let { I.text(it,"Usuario").ifBlank { listOf(I.text(it,"PrimerNombre"),I.text(it,"SegundoNombre")).filter(String::isNotBlank).joinToString(" ") } }
            else -> target?.toRecord(catalogs)?.title?.takeUnless { it=="Registro" }
        }.orEmpty()
        val detail=I.text(data,"Detalle")
        val key=I.text(data,"ClavePrincipal")
        val containsKey=key.isNotBlank() && Regex("(?<![\\p{L}\\p{N}])"+Regex.escape(key)+"(?![\\p{L}\\p{N}])").containsMatchIn(detail)
        return AuditCard(data,I.text(data,"Usuario").ifBlank { "Usuario no registrado" },I.text(data,"Accion").ifBlank { "Acción no registrada" },table.ifBlank { "Entidad no registrada" },targetLabel,detail,technical.containsMatchIn(detail) || containsKey)
    }
    private fun search(value: String,query: String)=ContractPresentation.normalize(value).contains(ContractPresentation.normalize(query.trim()))
    private fun dateMatches(value: String,filter: ConsultationFilter): Boolean {
        val date=runCatching { java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate().toString() }.getOrDefault(value.take(10))
        if(filter.start==null && filter.end==null) return true
        if(runCatching { java.time.LocalDate.parse(date) }.isFailure) return false
        return (filter.start==null || date>=filter.start) && (filter.end==null || date<=filter.end)
    }
    fun visibleMoras(rows: List<MoraCard>,filter: ConsultationFilter)=rows.filter {
        search(it.invoice+" "+it.client,filter.query) && (filter.state==null || it.state==filter.state) &&
            dateMatches(I.text(I.nested(it.data,"factura_detalle"),"Fecha_Vencimiento"),filter)
    }
    fun visibleNotices(rows: List<NoticeCard>,filter: ConsultationFilter)=rows.filter {
        search(it.title+" "+it.message+" "+it.client,filter.query) && (filter.state==null || filter.state==when(it.read) { true -> "Leída"; false -> "No leída"; null -> "Estado no disponible" }) && dateMatches(it.date,filter)
    }
    fun visibleAudit(rows: List<AuditCard>,filter: ConsultationFilter)=rows.filter {
        search(listOf(it.user,it.action,it.entity,it.target,if(it.technicalDescription) "" else it.description).joinToString(" "),filter.query) &&
            (filter.user==null || filter.user==it.user) && (filter.action==null || filter.action==it.action) && (filter.entity==null || filter.entity==it.entity) && dateMatches(I.text(it.data,"Fecha"),filter)
    }
    fun pendingSummary(rows: List<MoraCard>): Pair<Int,BigDecimal?> {
        val pending=rows.filter { bool(it.data,"EstadoMora")==false }
        val count=pending.map { I.text(it.data,"FacturaId") }.filter(String::isNotBlank).distinct().size
        val amounts=pending.map { I.decimal(it.data,"Monto_Mora") }
        return count to if(amounts.any { it==null }) null else amounts.filterNotNull().fold(BigDecimal.ZERO,BigDecimal::add)
    }
}
