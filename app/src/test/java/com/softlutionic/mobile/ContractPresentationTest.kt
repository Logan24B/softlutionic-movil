package com.softlutionic.mobile

import com.google.gson.JsonParser
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.domain.auth.*
import com.softlutionic.mobile.ui.contracts.*
import org.junit.Assert.*
import org.junit.Test

class ContractPresentationTest {
    private fun json(value: String)=JsonParser.parseString(value).asJsonObject
    private val record=json("""{"id":2,"ClienteId":4,"TipoContratoId":7,"Descripcion":"Soporte técnico"}""")
    private val clients=listOf(json("""{"id":4,"Nombre":"Carlos","Apellido":"Martínez López"}"""))
    private val types=listOf(json("""{"id":7,"Nombre":"Servicio puntual"}"""))
    private fun field(name: String,type: String="CharField",required: Boolean=true,relation: String?=null,readOnly: Boolean=false) = Field(name,name,required,type,relation,null,readOnly)
    @Test fun resolvesClientAndTypeFromRealCatalogs() {
        val row=ContractPresentation.row(record,ContractPresentation.options("clientes",clients,listOf(record)),ContractPresentation.options("tipos-contrato",types,listOf(record)))
        assertEquals("Carlos Martínez López",row.client); assertEquals("Servicio puntual",row.type)
        assertEquals("4",row.data.contractText("ClienteId"))
    }
    @Test fun embeddedRelationsWorkWithoutCatalogConnection() {
        val data=json("""{"id":2,"ClienteId":4,"TipoContratoId":7,"cliente_detalle":{"Nombre":"Ana","Apellido":"Pérez"},"tipo_contrato_detalle":{"Nombre":"Mensual"}}""")
        val row=ContractPresentation.row(data,ContractPresentation.options("clientes",emptyList(),listOf(data)),ContractPresentation.options("tipos-contrato",emptyList(),listOf(data)))
        assertEquals("Ana Pérez",row.client); assertEquals("Mensual",row.type)
    }
    @Test fun missingRelationsNeverExposeRawForeignKeys() {
        val row=ContractPresentation.row(record,emptyList(),emptyList())
        assertEquals("Cliente no disponible",row.client); assertEquals("Tipo no disponible",row.type)
        assertEquals("Sin tipo de contrato",ContractPresentation.row(json("""{"id":3,"TipoContratoId":null}"""),emptyList(),emptyList()).type)
    }
    @Test fun localSearchUsesReadableNamesTypesAndDescriptionIgnoringAccents() {
        val row=ContractPresentation.row(record,listOf(ContractOption("4","Carlos Martínez López")),listOf(ContractOption("7","Servicio puntual")))
        listOf("martinez","PUNTUAL","tecnico").forEach { assertEquals(1,ContractPresentation.visible(listOf(row),ContractFilters(query=it)).size) }
        assertTrue(ContractPresentation.visible(listOf(row),ContractFilters(query="inexistente")).isEmpty())
    }
    @Test fun filtersUseIdsWithoutChangingDisplayedNames() {
        val row=ContractPresentation.row(record,emptyList(),emptyList())
        assertEquals(1,ContractPresentation.visible(listOf(row),ContractFilters(clientId="4",typeId="7")).size)
        assertTrue(ContractPresentation.visible(listOf(row),ContractFilters(clientId="9")).isEmpty())
    }
    @Test fun payloadPreservesForeignKeyNumbersAndExcludesReadOnlyAndId() {
        val fields=listOf(field("ClienteId",relation="clientes"),field("TipoContratoId",required=false,relation="tipos-contrato"),field("id"),field("generado",readOnly=true))
        val payload=ContractPresentation.payload(fields,mapOf("ClienteId" to "4","TipoContratoId" to "7","id" to "2","generado" to "no"))
        assertEquals(setOf("ClienteId","TipoContratoId"),payload.keySet())
        assertTrue(payload["ClienteId"].asJsonPrimitive.isNumber)
        assertEquals(7,payload["TipoContratoId"].asInt)
    }
    @Test fun optionalTypeCanBeClearedAndServerBooleanDefaultIsNotInvented() {
        val fields=listOf(field("TipoContratoId",required=false,relation="tipos-contrato"),field("EstadoContrato","BooleanField",false))
        val payload=ContractPresentation.payload(fields,mapOf("TipoContratoId" to "","EstadoContrato" to ""))
        assertTrue(payload["TipoContratoId"].isJsonNull); assertFalse(payload.has("EstadoContrato"))
    }
    @Test fun requiredRelationsAndRealDateFormatsAreValidated() {
        assertEquals("Selecciona un cliente.",ContractPresentation.validate(field("ClienteId",relation="clientes"),""))
        assertNull(ContractPresentation.validate(field("TipoContratoId",required=false,relation="tipos-contrato"),""))
        assertNotNull(ContractPresentation.validate(field("Fecha_Inc","DateField"),"2026-02-30"))
        assertNull(ContractPresentation.validate(field("Fecha_Fin","DateField"),"2026-10-28"))
    }
    @Test fun contractCrudUsesSchemaForEveryExistingRoleWithoutNewRoleRestriction() {
        UserLevel.entries.filter { it!=UserLevel.UNKNOWN }.forEach { role ->
            listOf(Capability(true,true,false,true),Capability(true,false,true,false)).forEach { permission ->
                val module=Module("contratos",permission,emptyList())
                val actor=UserIdentity("1",role)
                assertEquals(permission,AuthorizationPolicy.effective(actor,module).permissions)
                assertEquals(permission.create,AuthorizationPolicy.allowsWrite(actor,module,"create"))
                assertEquals(permission.update,AuthorizationPolicy.allowsWrite(actor,module,"update"))
                assertEquals(permission.delete,AuthorizationPolicy.allowsWrite(actor,module,"delete"))
            }
        }
    }
    @Test fun backendValidationMapsToIndividualFields() {
        assertEquals("Selecciona un cliente.",ContractPresentation.errors("""{"ClienteId":["Selecciona un cliente."]}""")["ClienteId"])
    }
}
