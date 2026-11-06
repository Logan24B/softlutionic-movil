package com.softlutionic.mobile

import com.google.gson.*
import com.softlutionic.mobile.data.mapper.toRecord
import com.softlutionic.mobile.data.remote.Field
import com.softlutionic.mobile.ui.clients.*
import org.junit.Assert.*
import org.junit.Test

class ClientPresentationTest {
    private fun record(json: String) = JsonParser.parseString(json).asJsonObject.toRecord()
    private val client = record("""{"id":8,"Nombre":"Carlos","Apellido":"Martínez López","DepartamentoId":10,"departamento_detalle":{"id":10,"Nombre":"Managua"},"Estado":true,"Cedula":"001-010190-0001A","Telefono":"+505 8888 1234","Correo":"carlos@example.com"}""")
    @Test fun resolvesDepartmentsFromCatalogAndEmbeddedDetailsWithoutInventingNames() {
        val catalog = listOf(JsonParser.parseString("""{"id":10,"Nombre":"Nombre actualizado"}""").asJsonObject)
        assertEquals("Managua", ClientPresentation.row(client, emptyList()).department)
        val options = ClientPresentation.departments(catalog, listOf(client))
        assertEquals(1, options.size)
        assertEquals("Nombre actualizado", ClientPresentation.row(client, options).department)
        val missing = record("""{"id":9,"DepartamentoId":99,"Estado":false}""")
        assertEquals("", ClientPresentation.row(missing, options).department)
    }
    @Test fun cardsHaveFullNameInitialsAndSemanticState() {
        val row = ClientPresentation.row(client, emptyList())
        assertEquals("Carlos Martínez López", row.name)
        assertEquals("CM", row.initials)
        assertEquals(true, row.active)
        assertEquals(null, ClientPresentation.row(record("""{"id":9}"""), emptyList()).active)
    }
    @Test fun searchIsAccentInsensitiveAndUnderstandsFormattedPhoneAndIdentityNumbers() {
        val row = ClientPresentation.row(client, emptyList())
        for(query in listOf("martinez", "Carlos Lopez", "88881234", "0010101900001", "CARLOS@EXAMPLE.COM"))
            assertTrue(query, ClientPresentation.matches(row, ClientFilters(query)))
        assertFalse(ClientPresentation.matches(row, ClientFilters("otro correo")))
    }
    @Test fun combinesQueryStatusAndDepartmentWithoutTreatingUnknownAsInactive() {
        val row = ClientPresentation.row(client, emptyList())
        assertTrue(ClientPresentation.matches(row, ClientFilters("Carlos", ClientStatusFilter.ACTIVE, "10")))
        assertFalse(ClientPresentation.matches(row, ClientFilters(status = ClientStatusFilter.INACTIVE)))
        assertFalse(ClientPresentation.matches(row, ClientFilters(departmentId = "11")))
        val unknown = ClientPresentation.row(record("""{"id":9}"""), emptyList())
        assertFalse(ClientPresentation.matches(unknown, ClientFilters(status = ClientStatusFilter.INACTIVE)))
    }
    @Test fun payloadIntersectsSchemaAndNeverIncludesSystemOrReadonlyFields() {
        fun field(name: String, readOnly: Boolean = false) = Field(name, name, true, "CharField", null, 50, readOnly)
        val fields = listOf(field("id"), field("FechaRegistro"), field("HoraRegistro"), field("departamento_detalle"),
            field("Nombre"), field("Apellido", true), field("Estado"), field("DepartamentoId"))
        val values = mapOf("id" to JsonPrimitive(123), "FechaRegistro" to JsonPrimitive("2020-01-01"),
            "Nombre" to JsonPrimitive("Carlos"), "Apellido" to JsonPrimitive("No permitido"), "Estado" to JsonPrimitive(false),
            "DepartamentoId" to JsonPrimitive("10"), "Correo" to JsonPrimitive("not-in-schema@example.com"))
        val payload = ClientPresentation.payload(fields, values)
        assertEquals(setOf("Nombre", "Estado", "DepartamentoId"), payload.keySet())
        assertFalse(payload["Estado"].asBoolean)
        assertEquals("10", payload["DepartamentoId"].asString)
    }
    @Test fun validatesPhoneAccordingToExistingNicaraguanSerializer() {
        assertTrue(ClientPresentation.validPhone("8888-1234"))
        assertTrue(ClientPresentation.validPhone("+505 8888 1234"))
        assertEquals("+505 8888 1234", ClientPresentation.normalizedPhone("8888-1234"))
        for(value in listOf("", "123", "+502 8888 1234", "abcdefgh88881234")) assertFalse(value, ClientPresentation.validPhone(value))
    }
    @Test fun formatsSystemDateAndTimeAndPreservesUnrecognizedValues() {
        assertTrue(ClientPresentation.date("2026-10-24").contains("oct"))
        assertTrue(ClientPresentation.time("10:35:00").contains("10:35"))
        assertEquals("sin fecha", ClientPresentation.date("sin fecha"))
        assertEquals("", ClientPresentation.time(""))
    }
    @Test fun extractsOnlyKnownFieldErrorsAndRejectsHtml() {
        val errors = ClientPresentation.fieldErrors("""{"Correo":["Ya existe un cliente con este correo."],"DepartamentoId":["Seleccione un departamento."],"id":["Internal"],"detail":"Error"}""")
        assertEquals(setOf("Correo", "DepartamentoId"), errors.keys)
        assertTrue(ClientPresentation.fieldErrors("<html>Error del proxy</html>").isEmpty())
        assertEquals("Registro duplicado", ClientPresentation.generalError("""{"non_field_errors":["Registro duplicado"]}"""))
    }
}
