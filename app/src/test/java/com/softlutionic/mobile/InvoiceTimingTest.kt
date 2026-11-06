package com.softlutionic.mobile

import com.google.gson.JsonParser
import com.softlutionic.mobile.ui.invoices.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class InvoiceTimingTest {
    private fun json(text: String)=JsonParser.parseString(text).asJsonObject
    @Test fun capturesDateAndTimeFromTheSameInstantAcrossMidnight() {
        val zone=ZoneId.of("Pacific/Kiritimati")
        val stamp=InvoiceTiming.capture(Clock.fixed(Instant.parse("2026-10-28T10:05:12Z"),zone))
        assertEquals("2026-10-29",stamp.date); assertEquals("00:05:12",stamp.time)
        assertEquals(zone,stamp.captured.zone)
    }
    @Test fun freshCaptureDoesNotReuseTimeFromOpeningTheForm() {
        val first=InvoiceTiming.capture(Clock.fixed(Instant.parse("2026-10-28T23:55:00Z"),ZoneOffset.UTC))
        val sending=InvoiceTiming.capture(Clock.fixed(Instant.parse("2026-10-29T00:05:00Z"),ZoneOffset.UTC))
        assertNotEquals(first.date,sending.date); assertEquals("00:05:00",sending.time)
    }
    @Test fun defaultCaptureUsesCurrentDeviceZoneWithoutHardcodingCountry() {
        assertEquals(ZoneId.systemDefault(),InvoiceTiming.capture().captured.zone)
    }
    @Test fun bothAutomaticSettingsAndValidZoneAreRequired() {
        assertTrue(InvoiceClockStatus(true,true,true).ready)
        assertFalse(InvoiceClockStatus(false,true,true).ready)
        assertFalse(InvoiceClockStatus(true,false,true).ready)
        assertFalse(InvoiceClockStatus(true,true,false).ready)
    }
    @Test fun dueRuleIsExactlyTheExistingNamedMonthlyRuleNotAnInventedPeriodField() {
        val date=LocalDate.of(2026,10,28)
        assertEquals(LocalDate.of(2026,11,27),InvoiceTiming.dueDate(date,"  SERVICIO MENSUAL  "))
        assertEquals(date,InvoiceTiming.dueDate(date,"Servicio puntual"))
        assertEquals(date,InvoiceTiming.dueDate(date,"Crédito 60 días")) // No inference from arbitrary names/descriptions.
        assertEquals(LocalTime.of(23,59),InvoiceTiming.dueTime)
    }
    @Test fun resolvesOnlyMatchingActiveContractsInWebAscendingIdOrder() {
        val data=listOf(
            json("""{"id":1,"ClienteId":2,"TipoContratoId":3,"EstadoContrato":false}"""),
            json("""{"id":4,"ClienteId":2,"TipoContratoId":3,"EstadoContrato":true}"""),
            json("""{"id":2,"ClienteId":9,"TipoContratoId":3,"EstadoContrato":true}"""),
            json("""{"id":3,"ClienteId":2,"TipoContratoId":3,"EstadoContrato":true}"""))
        assertEquals(3,InvoicePresentation.matchingContract(data,"2","3")!!["id"].asInt)
        assertNull(InvoicePresentation.matchingContract(data,"2","8"))
    }
    @Test fun missingContractPayloadCopiesTheExistingWebFieldsAndDescriptionFallback() {
        val stamp=InvoiceTiming.capture(Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"),ZoneOffset.UTC))
        val type=json("""{"id":3,"Nombre":"Servicio mensual","Descripcion":""}""")
        val data=InvoicePresentation.contractPayload("2",type,stamp)
        assertEquals(setOf("ClienteId","TipoContratoId","Fecha_Inc","Fecha_Fin","EstadoContrato","Descripcion"),data.keySet())
        assertEquals("2026-10-28",data["Fecha_Inc"].asString); assertEquals("2026-11-27",data["Fecha_Fin"].asString)
        assertEquals("Servicio mensual",data["Descripcion"].asString)
    }
    @Test fun inactiveCustomersAreNotInvoiceChoices() {
        assertTrue(InvoicePresentation.activeClient(json("""{"Estado":true}""")))
        assertFalse(InvoicePresentation.activeClient(json("""{"Estado":false}""")))
    }
}
