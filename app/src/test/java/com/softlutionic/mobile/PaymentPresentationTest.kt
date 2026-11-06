package com.softlutionic.mobile

import com.google.gson.*
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.ui.payments.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class PaymentPresentationTest {
    private fun json(value: String)=JsonParser.parseString(value).asJsonObject
    private val states=listOf(json("""{"id":17,"estado":"Pendiente"}"""),json("""{"id":39,"estado":"Vencida"}"""),json("""{"id":81,"estado":"Pagada"}"""),json("""{"id":92,"estado":"Anulada"}"""))
    private val catalogs=mapOf("estados-factura" to states)
    private fun invoice(state: Int=17)=PaymentPresentation.invoice(json("""{"id":987,"CodigoFact":1002,"ClienteId":777,"EstadoId":$state,"Monto_Total":"4402.00","cliente_detalle":{"id":777,"Nombre":"Alejandra","Apellido":"Rodríguez"}}"""),catalogs)
    private val module=Module("pagos",Capability(true,true,true,false),PaymentPresentation.writable.map { Field(it,it,true,"",null,null) })
    @Test fun hidesOnlyPresentationCatalogs() {
        assertFalse(MobileNavigation.visible("estados-factura")); assertFalse(MobileNavigation.visible("departamentos")); assertTrue(MobileNavigation.visible("pagos"))
        assertEquals("estados-factura",module.copy(name="estados-factura").name)
    }
    @Test fun resolvesOfficialStateWithoutFixedIds() { assertEquals("Vencida",invoice(39).state) }
    @Test fun paidAndCancelledNeverPayable() { assertFalse(invoice(81).payable); assertFalse(invoice(92).payable); assertTrue(invoice(17).payable); assertTrue(invoice(39).payable) }
    @Test fun searchCommercialCode() { assertTrue(PaymentPresentation.matches(invoice(),PaymentFilters("1002"))) }
    @Test fun searchClientAccentInsensitive() { assertTrue(PaymentPresentation.matches(invoice(),PaymentFilters("alejandra rodriguez"))) }
    @Test fun searchExcludesTechnicalIds() { assertFalse(PaymentPresentation.matches(invoice(),PaymentFilters("987"))); assertFalse(PaymentPresentation.matches(invoice(),PaymentFilters("777"))) }
    @Test fun searchAndStateCombine() { assertTrue(PaymentPresentation.matches(invoice(),PaymentFilters("Alejandra","17"))); assertFalse(PaymentPresentation.matches(invoice(),PaymentFilters("Alejandra","39"))) }
    @Test fun everyStateAndAll() { listOf(17,39,81,92).forEach { assertTrue(PaymentPresentation.matches(invoice(it),PaymentFilters())); assertTrue(PaymentPresentation.matches(invoice(it),PaymentFilters(stateId=it.toString()))) } }
    @Test fun payloadUsesInternalInvoiceAndSameInstant() {
        val now=ZonedDateTime.parse("2026-10-28T23:59:59.123456789-06:00[America/Guatemala]")
        val payload=PaymentPresentation.payload(module,invoice(),"33",true,now)
        assertEquals(987,payload["FacturaId"].asInt); assertEquals("4402.00",payload["Monto_Pagado"].asString)
        assertEquals("2026-10-28T23:59:59-06:00",payload["Fecha_Pago"].asString); assertTrue(payload["EstadoPago"].asBoolean)
        assertFalse(payload.has("EstadoId")); assertFalse(payload.has("ClienteId"))
    }
    @Test fun readOnlyFieldsOmitted() {
        val changed=module.copy(fields=module.fields.map { if(it.name=="MetodoPago") it.copy(read_only=true) else it })
        assertFalse(PaymentPresentation.payload(changed,invoice(),"33",true,ZonedDateTime.now()).has("MetodoPago"))
    }
    @Test fun freshInvoiceOutranksEmbeddedSnapshot() {
        val data=json("""{"FacturaId":987,"factura_detalle":{"id":987,"EstadoId":17}}""")
        assertEquals("Pagada",PaymentPresentation.payment(data,catalogs+("facturas" to listOf(invoice(81).data))).invoice?.state)
    }
    @Test fun receiptUsesCommercialFieldOnly() { assertEquals("REC-000033",PaymentPresentation.receipt(json("""{"id":22,"NumeroRecibo":33}"""))); assertEquals("Pago registrado",PaymentPresentation.receipt(json("""{"id":22}"""))) }
    @Test fun methodUsesActualBooleanContract() { assertEquals("Transferencia",PaymentPresentation.method(json("""{"MetodoPago":true}"""))); assertEquals("Efectivo",PaymentPresentation.method(json("""{"MetodoPago":false}"""))) }
}
