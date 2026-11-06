package com.softlutionic.mobile

import com.google.gson.*
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.domain.auth.*
import com.softlutionic.mobile.ui.consultation.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ConsultationPresentationTest {
    private fun json(s: String)=JsonParser.parseString(s).asJsonObject
    private val invoice=json("""{"id":991991,"CodigoFact":1002,"EstadoId":42,"Fecha_Vencimiento":"2026-10-28","Hora_Vencimiento":"18:00:00","cliente_detalle":{"id":882882,"Nombre":"Alejandra","Apellido":"Rodríguez"}}""")
    private val catalogs=mapOf("facturas" to listOf(invoice),"estados-factura" to listOf(json("""{"id":42,"estado":"Vencida"}""")))
    private fun mora(id: Int=1,paid: Boolean=false,amount: String="20.25")=ConsultationPresentation.mora(json("""{"id":$id,"FacturaId":991991,"EstadoMora":$paid,"Monto_Mora":"$amount"}"""),catalogs)
    private val all=Capability(true,true,true,true)
    @Test fun financeAndAuditAreReadOnlyEvenIfSchemaClaimsWrites() { assertTrue(ConsultationPresentation.readOnly(Module("moras",all,emptyList()))); assertTrue(ConsultationPresentation.readOnly(Module("auditoria",all,emptyList()))) }
    @Test fun moraResolvesBusinessRelations() { val row=mora(); assertEquals("1002",row.invoice); assertEquals("Alejandra Rodríguez",row.client); assertEquals("Vencida",row.invoiceState); assertEquals("Pendiente",row.state) }
    @Test fun moraDoesNotInventRelations() { val row=ConsultationPresentation.mora(json("""{"FacturaId":5555}"""),emptyMap()); assertEquals("Factura no disponible",row.invoice); assertFalse(row.invoice.contains("5555")); assertEquals("Estado no disponible",row.state) }
    @Test fun searchInvoiceAndClientWithoutIds() { assertEquals(1,ConsultationPresentation.visibleMoras(listOf(mora()),ConsultationFilter(query="1002")).size); assertEquals(1,ConsultationPresentation.visibleMoras(listOf(mora()),ConsultationFilter(query="rodriguez")).size); assertTrue(ConsultationPresentation.visibleMoras(listOf(mora()),ConsultationFilter(query="991991")).isEmpty()) }
    @Test fun stateAndQueryCombine() { assertEquals(1,ConsultationPresentation.visibleMoras(listOf(mora(),mora(2,true)),ConsultationFilter(query="Alejandra",state="Pagada")).size) }
    @Test fun summaryIncludesOnlyPendingReportedAmountsAndDistinctInvoices() { val result=ConsultationPresentation.pendingSummary(listOf(mora(),mora(2,true,"900"))); assertEquals(1,result.first); assertEquals(0,BigDecimal("20.25").compareTo(result.second)) }
    @Test fun missingAmountDoesNotBecomeZero() { val row=ConsultationPresentation.mora(json("""{"FacturaId":991991,"EstadoMora":false}"""),catalogs); assertNull(ConsultationPresentation.pendingSummary(listOf(row)).second) }
    @Test fun emptySummaryIsZeroWithoutFakeRecords() { val result=ConsultationPresentation.pendingSummary(emptyList()); assertEquals(0,result.first); assertEquals(BigDecimal.ZERO,result.second) }
    @Test fun notificationsUseMongoApiFieldsAndStringId() {
        val row=ConsultationPresentation.notice(json("""{"id":"507f1f77bcf86cd799439011","titulo":"Pago registrado","mensaje":"Mensaje real","leida":false,"fecha_creacion":"2026-11-06T16:00:00Z"}"""),emptyMap())
        assertEquals("Mensaje real",row.message); assertEquals("",row.client); assertEquals(false,row.read)
        assertEquals("Pago registrado",row.title); assertEquals("2026-11-06T16:00:00Z",row.date)
        assertEquals("507f1f77bcf86cd799439011",row.data["id"].asString)
        assertEquals(1,ConsultationPresentation.visibleNotices(listOf(row),ConsultationFilter(query="Pago")).size)
    }
    @Test fun missingReadStateNotInvented() { assertNull(ConsultationPresentation.notice(json("""{"mensaje":"Hola"}"""),emptyMap()).read) }
    @Test fun perUserReadActionDoesNotRequireGenericEditing() {
        val module=Module("notificaciones",Capability(true,false,false,false),emptyList())
        assertTrue(ConsultationPresentation.canMarkRead(module))
        assertFalse(ConsultationPresentation.canMarkRead(module.copy(permissions=module.permissions.copy(read=false))))
        assertFalse(ConsultationPresentation.canMarkRead(module.copy(name="moras")))
    }
    @Test fun optionalNoticeFieldsRemainUnavailable() {
        val row=ConsultationPresentation.notice(json("""{"mensaje":null,"titulo":null,"fecha_creacion":null,"leida":null}"""),emptyMap())
        assertEquals("",row.message); assertEquals("",row.title); assertEquals("",row.date); assertNull(row.read)
    }
    @Test fun noticeDatesCombineWithReadStateAndSearchInDeviceTimezone() {
        val instant="2026-11-06T00:30:00Z"
        val day=java.time.OffsetDateTime.parse(instant).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate()
        val row=ConsultationPresentation.notice(json("""{"titulo":"Factura","mensaje":"Nueva factura","leida":false,"fecha_creacion":"$instant"}"""),emptyMap())
        val filter=ConsultationFilter(query="Factura",state="No leída",start=day.toString(),end=day.toString())
        assertEquals(1,ConsultationPresentation.visibleNotices(listOf(row),filter).size)
        assertTrue(ConsultationPresentation.visibleNotices(listOf(row),filter.copy(state="Leída")).isEmpty())
        assertTrue(ConsultationPresentation.visibleNotices(listOf(row),filter.copy(start=day.plusDays(1).toString())).isEmpty())
        assertTrue(ConsultationPresentation.visibleNotices(listOf(row.copy(date="")),filter).isEmpty())
        assertEquals(1,ConsultationPresentation.visibleNotices(listOf(row.copy(date="")),ConsultationFilter()).size)
    }
    @Test fun readFilterUsesRecordedState() { val rows=listOf(ConsultationPresentation.notice(json("""{"mensaje":"Hola","leida":true}"""),emptyMap())); assertEquals(1,ConsultationPresentation.visibleNotices(rows,ConsultationFilter(state="Leída")).size); assertTrue(ConsultationPresentation.visibleNotices(rows,ConsultationFilter(state="No leída")).isEmpty()) }
    @Test fun auditRequiresExistingAdminPolicyAndSchemaRead() {
        val module=Module("auditoria",all,emptyList())
        assertFalse(ConsultationPresentation.canRead(UserIdentity("1",UserLevel.EMPLOYEE),module)); assertFalse(ConsultationPresentation.canRead(null,module))
        assertTrue(ConsultationPresentation.canRead(UserIdentity("1",UserLevel.ADMIN),module)); assertTrue(ConsultationPresentation.canRead(UserIdentity("1",UserLevel.SUPERUSER),module))
        assertFalse(ConsultationPresentation.canRead(UserIdentity("1",UserLevel.ADMIN),module.copy(permissions=all.copy(read=false))))
    }
    @Test fun auditResolvesTargetButPreservesRecordedPrincipal() {
        val row=ConsultationPresentation.audit(json("""{"IdLog":771771,"Tabla":"Facturas","ClavePrincipal":"991991","Accion":"UPDATE","Usuario":"sql_service","Detalle":"Actualización registrada"}"""),catalogs)
        assertEquals("1002",row.target); assertEquals("sql_service",row.user); assertEquals("UPDATE",row.action); assertFalse(row.technicalDescription)
    }
    @Test fun technicalDescriptionKeptOutOfCards() { val row=ConsultationPresentation.audit(json("""{"Tabla":"Clientes","ClavePrincipal":"77","Detalle":"ClienteId=77"}"""),emptyMap()); assertTrue(row.technicalDescription); assertEquals("",row.target); assertEquals("ClienteId=77",row.description) }
    @Test fun pkInFreeTextIsAlsoTechnical() { assertTrue(ConsultationPresentation.audit(json("""{"ClavePrincipal":"77","Detalle":"Registro 77 actualizado"}"""),emptyMap()).technicalDescription) }
    @Test fun auditFiltersUseActualRecordedCategories() {
        val row=ConsultationPresentation.audit(json("""{"Usuario":"operador","Accion":"UPDATE","Tabla":"Clientes","Fecha":"2026-10-28T12:00:00Z","Detalle":"Cambio de correo"}"""),emptyMap())
        assertEquals(1,ConsultationPresentation.visibleAudit(listOf(row),ConsultationFilter(query="correo",user="operador",action="UPDATE",entity="Clientes",start="2026-10-27",end="2026-10-29")).size)
        assertTrue(ConsultationPresentation.visibleAudit(listOf(row),ConsultationFilter(action="INSERT")).isEmpty())
    }
    @Test fun missingAuditActorNotReplacedWithLoggedInUser() { assertEquals("Usuario no registrado",ConsultationPresentation.audit(JsonObject(),emptyMap()).user) }
}
