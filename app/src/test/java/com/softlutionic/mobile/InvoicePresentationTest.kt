package com.softlutionic.mobile

import com.google.gson.*
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.domain.auth.*
import com.softlutionic.mobile.ui.invoices.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class InvoicePresentationTest {
    private fun json(value: String)=JsonParser.parseString(value).asJsonObject
    private val module=Module("facturas",Capability(true,true,true,false),listOf(
        Field("ContratoId","Contrato",true,"PrimaryKeyRelatedField","contratos",null),
        Field("Fecha_Emision","Fecha",true,"DateField",null,null),Field("Hora_Emision","Hora",true,"TimeField",null,null),
        Field("CodigoFact","Código",true,"IntegerField",null,null)))
    private val contract=json("""{"id":901,"ClienteId":902,"TipoContratoId":903,"cliente_detalle":{"id":902,"Estado":true,"Cedula":"DOC-PRUEBA","Correo":"marina@example.test","Nombre":"Ana","Apellido":"Pérez"},"tipo_contrato_detalle":{"id":903,"Nombre":"Servicio mensual"},"Descripcion":"Soporte"}""")
    private val catalogs=mapOf("clientes" to listOf(contract.getAsJsonObject("cliente_detalle")),"tipos-contrato" to listOf(contract.getAsJsonObject("tipo_contrato_detalle")),"contratos" to listOf(contract),"servicios" to listOf(
        json("""{"id":904,"Nombre_Servicio":"Mantenimiento","Precio":850}"""),json("""{"id":905,"Nombre_Servicio":"Soporte","Precio":1200}""")),
        "estados-factura" to listOf(json("""{"id":906,"estado":"Pendiente"}""")))
    private fun draft()=InvoiceDraft(values=mapOf("ContratoId" to "901","Fecha_Emision" to "2026-10-28","Hora_Emision" to "09:30:00","CodigoFact" to "1002"),
        lines=listOf(InvoiceLine(1,"904","2"),InvoiceLine(2,"905","1")),clientId="902",typeId="903")
    @Test fun visibleCodeIsCommercialAndTechnicalIdsStayOutOfPresentation() {
        val row=InvoicePresentation.row(json("""{"id":9999,"CodigoFact":1002,"ContratoId":901,"EstadoId":906}"""),catalogs)
        assertEquals("1002",row.code); assertEquals("Ana Pérez",row.client); assertEquals("Servicio mensual",row.contract); assertEquals("Pendiente",row.state)
        assertFalse((row.code+row.client+row.contract+row.state).contains("9999")); assertEquals("9999",row.id)
    }
    @Test fun embeddedRelationsWorkWithoutLiveCatalogs() {
        val data=json("""{"id":7,"CodigoFact":2,"estado_detalle":{"id":8,"estado":"Pagada"}}""").apply { add("contrato_detalle",contract) }
        val row=InvoicePresentation.row(data,emptyMap())
        assertEquals("2",row.code); assertEquals("Ana Pérez",row.client); assertEquals("Pagada",row.state)
    }
    @Test fun quantitiesMustBePositiveIntegersWithinApiRange() {
        listOf("0","-1","texto","1.5","2147483648").forEach { assertNull(InvoicePresentation.quantity(InvoiceLine(1,"904",it))) }
        assertEquals(2,InvoicePresentation.quantity(InvoiceLine(1,"904","2")))
    }
    @Test fun blankPriceUsesCatalogTariffButRemainsOmittedFromPayload() {
        assertEquals(BigDecimal("850"),InvoicePresentation.price(draft().lines[0],catalogs))
        assertFalse(InvoicePresentation.payload(draft(),module)["lineas"].asJsonArray[0].asJsonObject.has("PrecioVenta"))
    }
    @Test fun newLineCannotOverrideCatalogPrice() {
        listOf("900","-1","8.5","texto").forEach {
            val line=InvoiceLine(1,"904","1",it)
            assertEquals(BigDecimal("850"),InvoicePresentation.price(line,catalogs))
            assertFalse(InvoicePresentation.payload(draft().copy(lines=listOf(line)),module)["lineas"].asJsonArray[0].asJsonObject.has("PrecioVenta"))
        }
    }
    @Test fun invoiceTypeIsDerivedOnlyForKnownCommercialTypes() {
        assertEquals("CR\u00c9DITO",InvoicePresentation.invoiceType(json("""{"Nombre":"Servicio mensual"}""")))
        assertEquals("CONTADO",InvoicePresentation.invoiceType(json("""{"Nombre":"Servicio unico"}""")))
        assertEquals("CONTADO",InvoicePresentation.invoiceType(json("""{"Nombre":"Soporte tecnico"}""")))
        assertEquals("No determinada",InvoicePresentation.invoiceType(json("""{"Nombre":"Plan mensual especial"}""")))
    }
    @Test fun multipleLinesSubtotalAndTotalUseDecimalArithmetic() {
        assertEquals(BigDecimal("1700"),InvoicePresentation.subtotal(draft().lines[0],catalogs))
        assertEquals(BigDecimal("2900"),InvoicePresentation.total(draft(),catalogs))
        assertTrue(InvoicePresentation.money(InvoicePresentation.total(draft(),catalogs)).contains("2,900.00"))
    }
    @Test fun removingLineAndChangingQuantityUpdatesEstimate() {
        val changed=draft().copy(lines=listOf(draft().lines[0].copy(quantity="3")))
        assertEquals(BigDecimal("2550"),InvoicePresentation.total(changed,catalogs))
    }
    @Test fun invalidLinesNeverProduceAPartialPlausibleTotal() {
        assertNull(InvoicePresentation.total(draft().copy(lines=draft().lines+InvoiceLine(3,"905","0")),catalogs))
        assertNull(InvoicePresentation.total(InvoiceDraft(),catalogs))
    }
    @Test fun exactAtomicPayloadContainsOnlyWritableHeadersAndLineas() {
        val data=InvoicePresentation.payload(draft(),module)
        assertEquals(setOf("ContratoId","CodigoFact","Fecha_Emision","Hora_Emision","lineas"),data.keySet())
        assertEquals(901,data["ContratoId"].asInt); assertEquals(1002,data["CodigoFact"].asInt)
        assertEquals(2,data["lineas"].asJsonArray.size())
        val line=data["lineas"].asJsonArray[0].asJsonObject
        assertEquals(setOf("ServicioId","Cantidad"),line.keySet()); assertEquals(904,line["ServicioId"].asInt)
    }
    @Test fun generatedFieldsAndTechnicalIdsAreNeverPosted() {
        val data=InvoicePresentation.payload(draft().copy(values=draft().values+mapOf("id" to "9","UsuarioId" to "3","EstadoId" to "1","Monto_Total" to "0","TipoFactura" to "CONTADO","Observaciones" to "No enviar","Fecha_Vencimiento" to "2026-11-01")),module)
        listOf("id","UsuarioId","EstadoId","Monto_Total","Fecha_Vencimiento","Hora_Vencimiento","detalles","FacturaId","TipoFactura","Observaciones").forEach { assertFalse(data.has(it)) }
    }
    @Test fun draftRestoresExistingSalePriceRatherThanRepricingOldInvoice() {
        val data=json("""{"id":9,"ContratoId":901,"CodigoFact":1002,"Fecha_Emision":"2026-10-28","Hora_Emision":"09:30:00","detalles":[{"id":7,"FacturaId":9,"ServicioId":904,"Cantidad":2,"PrecioVenta":700}]}""")
        val restored=InvoicePresentation.draft(data,module,catalogs)
        assertEquals("9",restored.id); assertEquals(BigDecimal("1400"),InvoicePresentation.total(restored,catalogs))
        val line=InvoicePresentation.payload(restored,module)["lineas"].asJsonArray[0].asJsonObject
        assertEquals(700,line["PrecioVenta"].asInt); assertFalse(line.has("id")); assertFalse(line.has("FacturaId"))
    }
    @Test fun requiredHeadersDatesAndServiceSelectionAreValidated() {
        assertTrue(InvoicePresentation.validate(draft(),module,catalogs).isEmpty())
        assertTrue(InvoicePresentation.validate(InvoiceDraft(),module,catalogs).keys.containsAll(listOf("ClienteId","TipoContratoId","CodigoFact","lineas")))
        assertTrue(InvoicePresentation.validate(draft().copy(id="9",values=draft().values+("Fecha_Emision" to "2026-02-30")),module,catalogs).containsKey("Fecha_Emision"))
        assertTrue(InvoicePresentation.validate(draft().copy(lines=listOf(InvoiceLine(1,"desconocido","1","10"))),module,catalogs).containsKey("line-1"))
    }
    @Test fun permissionsRemainSchemaAndExistingPolicyForAllRoles() {
        listOf(UserLevel.ADMIN,UserLevel.SUPERUSER,UserLevel.EMPLOYEE).forEach { role ->
            val actor=UserIdentity("1",role)
            assertTrue(AuthorizationPolicy.allowsWrite(actor,module,"create")); assertTrue(AuthorizationPolicy.allowsWrite(actor,module,"update")); assertFalse(AuthorizationPolicy.allowsWrite(actor,module,"delete"))
            assertFalse(AuthorizationPolicy.allowsWrite(actor,module.copy(permissions=Capability(true,false,false,false)),"create"))
        }
    }
    @Test fun localSearchAndFiltersUseHumanReferencesAndRealEmissionDate() {
        val row=InvoicePresentation.row(json("""{"id":9999,"CodigoFact":1002,"ContratoId":901,"EstadoId":906,"Fecha_Emision":"2026-10-28"}"""),catalogs)
        listOf("1002","perez","mensual").forEach { assertEquals(1,InvoicePresentation.visible(listOf(row),InvoiceFilters(query=it)).size) }
        assertTrue(InvoicePresentation.visible(listOf(row),InvoiceFilters(query="9999")).isEmpty())
        assertTrue(InvoicePresentation.visible(listOf(row),InvoiceFilters(state="Pagada")).isEmpty())
        assertTrue(InvoicePresentation.visible(listOf(row),InvoiceFilters(start="2026-11-01")).isEmpty())
    }
    @Test fun displayFormattingDoesNotChangeIsoValuesSentToApi() {
        assertTrue(InvoicePresentation.date("2026-10-28").contains("oct"))
        assertTrue(InvoicePresentation.time("00:00:00").contains("12:00"))
        assertEquals("2026-10-28",InvoicePresentation.payload(draft(),module)["Fecha_Emision"].asString)
        assertEquals("09:30:00",InvoicePresentation.payload(draft(),module)["Hora_Emision"].asString)
    }
    @Test fun nestedValidationErrorsAreHumanAndNeverExposeForeignKeys() {
        val errors=InvoicePresentation.errors("""{"lineas":[{"ServicioId":["Invalid pk 9999 - object does not exist."]}]}""")
        assertTrue(errors["lineas"]!!.contains("Servicio")); assertFalse(errors["lineas"]!!.contains("9999")); assertFalse(errors["lineas"]!!.contains("ServicioId"))
    }
}
