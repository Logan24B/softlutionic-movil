package com.softlutionic.mobile

import com.google.gson.JsonParser
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.domain.auth.*
import com.softlutionic.mobile.ui.contracttypes.*
import com.softlutionic.mobile.ui.contracts.*
import org.junit.Assert.*
import org.junit.Test

class ContractTypePresentationTest {
    private fun json(value: String)=JsonParser.parseString(value).asJsonObject
    private val item=json("""{"id":9981,"Nombre":"Servicio mensual","Descripcion":"Asistencia técnica recurrente"}""")
    private val fields=listOf(Field("Nombre","Nombre",true,"CharField",null,80),Field("Descripcion","Descripcion",false,"CharField",null,250))
    @Test fun presentationContainsOnlyNameAndDescriptionWithoutTechnicalId() {
        val row=ContractTypePresentation.row(item)
        assertEquals("Servicio mensual",row.name); assertEquals("Asistencia técnica recurrente",row.description)
        assertFalse(row.toString().contains("9981")); assertFalse(row.toString().contains("id:"))
    }
    @Test fun localSearchUsesBothFieldsWithoutAccentOrCaseSensitivity() {
        listOf("MENSUAL","tecnica"," recurrente ").forEach { assertEquals(1,ContractTypePresentation.visible(listOf(item),it).size) }
        assertTrue(ContractTypePresentation.visible(listOf(item),"desconocido").isEmpty())
    }
    @Test fun fullDescriptionIsPreserved() {
        val long="Descripción extensa ".repeat(30)
        val data=item.deepCopy().apply { addProperty("Descripcion",long) }
        assertEquals(long,ContractTypePresentation.row(data).description)
    }
    @Test fun payloadNeverContainsIdOrReadOnlyFields() {
        val schema=fields+Field("id","Id",false,"IntegerField",null,null,true)
        val data=ContractTypePresentation.payload(schema,mapOf("id" to "9981","Nombre" to " Nombre ","Descripcion" to "Texto"))
        assertEquals(setOf("Nombre","Descripcion"),data.keySet()); assertEquals("Nombre",data["Nombre"].asString)
        assertFalse(ContractTypePresentation.payload(fields.map { it.copy(read_only=true) },mapOf("Nombre" to "No permitido")).has("Nombre"))
    }
    @Test fun schemaRequiredAndLengthAreRespected() {
        assertNotNull(ContractTypePresentation.validate(fields[0],""))
        assertNull(ContractTypePresentation.validate(fields[1],""))
        assertNotNull(ContractTypePresentation.validate(fields[0],"x".repeat(81)))
        assertNotNull(ContractTypePresentation.validate(fields[1],"x".repeat(251)))
    }
    @Test fun employeeRemainsReadOnlyWithWritableSchema() {
        val module=Module("tipos-contrato",Capability(true,true,true,true),fields)
        val actor=UserIdentity("3",UserLevel.EMPLOYEE)
        assertEquals(Capability(true,false,false,false),AuthorizationPolicy.effective(actor,module).permissions)
        listOf("create","update","delete").forEach { assertFalse(AuthorizationPolicy.allowsWrite(actor,module,it)) }
    }
    @Test fun administratorsAndSuperusersKeepSchemaCapabilities() {
        listOf(UserLevel.ADMIN,UserLevel.SUPERUSER).forEach { role ->
            listOf(Capability(true,true,true,true),Capability(true,false,true,false)).forEach { caps ->
                val module=Module("tipos-contrato",caps,fields); val actor=UserIdentity("1",role)
                assertEquals(caps,AuthorizationPolicy.effective(actor,module).permissions)
                assertEquals(caps.create,AuthorizationPolicy.allowsWrite(actor,module,"create"))
                assertEquals(caps.update,AuthorizationPolicy.allowsWrite(actor,module,"update"))
                assertEquals(caps.delete,AuthorizationPolicy.allowsWrite(actor,module,"delete"))
            }
        }
    }
    @Test fun contractRelationStillResolvesTypeNameFromUnchangedRawRecords() {
        val options=ContractPresentation.options("tipos-contrato",listOf(item),emptyList())
        val contract=json("""{"id":5,"TipoContratoId":9981}""")
        assertEquals("Servicio mensual",ContractPresentation.row(contract,emptyList(),options).type)
        assertEquals("9981",ContractTypePresentation.text(item,"id"))
    }
}
