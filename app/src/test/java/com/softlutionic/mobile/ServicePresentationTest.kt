package com.softlutionic.mobile

import com.google.gson.*
import com.softlutionic.mobile.data.mapper.toRecord
import com.softlutionic.mobile.data.remote.Field
import com.softlutionic.mobile.ui.services.*
import org.junit.Assert.*
import org.junit.Test

class ServicePresentationTest {
    private fun record(json: String) = JsonParser.parseString(json).asJsonObject.toRecord()
    private val integer = Field("Precio", "Precio", true, "IntegerField", null, null)
    @Test fun formatsIntegerPriceWithoutInventingCents() {
        assertEquals("C$1,200", ServicePresentation.price(record("""{"id":1,"Precio":1200}""")))
        assertEquals("C$0", ServicePresentation.price(record("""{"id":1,"Precio":0}""")))
        assertNull(ServicePresentation.price(record("""{"id":1}""")))
    }
    @Test fun integerInputRejectsFractionsNegativesAndOverflowButAcceptsZero() {
        assertEquals(0, ServicePresentation.priceValue("0", integer)!!.asInt)
        assertEquals(2147483647, ServicePresentation.priceValue("2147483647", integer)!!.asInt)
        for(raw in listOf("-1", "850.50", "1,200", "1e3", "2147483648", "abc", "")) assertNull(raw, ServicePresentation.priceValue(raw, integer))
    }
    @Test fun decimalInputIsUsedOnlyWhenSchemaExplicitlyAllowsIt() {
        val decimal = integer.copy(type = "DecimalField")
        assertEquals("850.25", ServicePresentation.priceValue("850.25", decimal)!!.asBigDecimal.toPlainString())
        assertNull(ServicePresentation.priceValue("850.25", integer))
    }
    @Test fun payloadExcludesIdAndReadOnlyFieldsAndKeepsDurationTextAndNumericPrice() {
        val fields = listOf(integer, Field("Duracion", "", true, "CharField", null, 50),
            Field("id", "", false, "IntegerField", null, null), Field("Nombre_Servicio", "", true, "CharField", null, 50),
            Field("Descripcion", "", true, "CharField", null, 200, true))
        val payload = ServicePresentation.payload(fields, mapOf("id" to "900", "Precio" to "850", "Duracion" to "Según alcance acordado",
            "Nombre_Servicio" to " Soporte ", "Descripcion" to "No editable", "Inventado" to "No"))
        assertEquals(setOf("Precio", "Duracion", "Nombre_Servicio"), payload.keySet())
        assertTrue(payload["Precio"].asJsonPrimitive.isNumber)
        assertEquals(850, payload["Precio"].asInt)
        assertEquals("Según alcance acordado", payload["Duracion"].asString)
        assertEquals("Soporte", payload["Nombre_Servicio"].asString)
    }
    private val records = listOf(
        record("""{"id":1,"Nombre_Servicio":"Ágil","Descripcion":"Revisión de equipo","Precio":1200}"""),
        record("""{"id":2,"Nombre_Servicio":"Soporte","Descripcion":"Instalación remota","Precio":50}"""),
        record("""{"id":3,"Nombre_Servicio":"Sin precio","Descripcion":"Pendiente"}"""))
    @Test fun searchMatchesNameAndDescriptionWithoutAccentsOrCase() {
        assertEquals(listOf("1"), ServicePresentation.visible(records, ServiceListState("AGIL revision")).map { it.id })
        assertEquals(listOf("2"), ServicePresentation.visible(records, ServiceListState("instalacion")).map { it.id })
        assertTrue(ServicePresentation.visible(records, ServiceListState("no existe")).isEmpty())
    }
    @Test fun pricesSortNumericallyAndMissingPricesStayLastInBothDirections() {
        assertEquals(listOf("2", "1", "3"), ServicePresentation.visible(records, ServiceListState(sort = ServiceSort.PRICE_ASC)).map { it.id })
        assertEquals(listOf("1", "2", "3"), ServicePresentation.visible(records, ServiceListState(sort = ServiceSort.PRICE_DESC)).map { it.id })
    }
    @Test fun namesSortInBothDirectionsWithoutChangingRecordsUsedForInvoiceOptions() {
        assertEquals(listOf("1", "3", "2"), ServicePresentation.visible(records, ServiceListState()).map { it.id })
        assertEquals(listOf("2", "3", "1"), ServicePresentation.visible(records, ServiceListState(sort = ServiceSort.NAME_DESC)).map { it.id })
        assertEquals(1200, records.first().payload["Precio"].asInt)
        assertEquals("1", records.first().id)
    }
    @Test fun validationErrorsAcceptOnlyKnownFieldsAndJsonDetails() {
        assertEquals(mapOf("Nombre_Servicio" to "Ya existe este servicio", "detail" to "Duplicado"),
            ServicePresentation.errors("""{"Nombre_Servicio":["Ya existe este servicio"],"id":["Interno"],"detail":"Duplicado"}"""))
        assertTrue(ServicePresentation.errors("<html>Proxy error</html>").isEmpty())
    }
}
