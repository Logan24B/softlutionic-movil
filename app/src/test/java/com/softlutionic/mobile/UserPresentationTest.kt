package com.softlutionic.mobile

import com.google.gson.JsonParser
import com.softlutionic.mobile.data.remote.Field
import com.softlutionic.mobile.ui.users.*
import org.junit.Assert.*
import org.junit.Test

class UserPresentationTest {
    private val fields=UserPresentation.labels.keys.map { Field(it,it,false,"CharField",null,null) } +
        listOf("id","first_name","last_name","FechaRegistro","HoraRegistro","password","confirmation","is_superuser").map { Field(it,it,false,"CharField",null,null) }
    @Test fun createKeepsHistoricalKeysAndExactPasswordWhileExcludingInternalFields() {
        val values=fields.associate { it.name to "must not leak" } + mapOf("PrimerNombre" to " Andrés ","SegundoNombre" to " Avilés ",
            "Email" to "a@example.com","Usuario" to "", "Contrasena" to "  secret  ","Rol" to "false","is_active" to "true")
        val json=UserPresentation.payload(fields,values,true)
        assertEquals(UserPresentation.labels.keys,json.keySet())
        assertEquals("Avilés",json["SegundoNombre"].asString)
        assertEquals("Andrés",json["PrimerNombre"].asString)
        assertEquals("  secret  ",json["Contrasena"].asString)
        assertEquals("a@example.com",json["Usuario"].asString)
        assertTrue(json["is_active"].asBoolean); assertFalse(json["Rol"].asBoolean)
    }
    @Test fun editOmitsUnchangedPasswordAndHonorsReadonlySchema() {
        val schema=fields.map { if(it.name=="PrimerNombre") it.copy(read_only=true) else it }
        val payload=UserPresentation.payload(schema,mapOf("PrimerNombre" to "X","SegundoNombre" to "Y","Contrasena" to ""),false)
        assertEquals(setOf("SegundoNombre"),payload.keySet())
    }
    @Test fun confirmationIsRequiredOnlyForNewPasswordAndUsesExactWhitespace() {
        assertNull(UserPresentation.passwordError("","",false))
        assertNotNull(UserPresentation.passwordError("","",true))
        assertNotNull(UserPresentation.passwordError("abc ","abc",false))
        assertNull(UserPresentation.passwordError("abc ","abc ",false))
    }
    @Test(expected=IllegalArgumentException::class) fun createCannotOmitPassword() {
        UserPresentation.payload(fields,emptyMap(),true)
    }
    @Test fun searchFiltersBusinessNamesRoleAndStatusWithoutDjangoNames() {
        val users=JsonParser.parseString("""[{"PrimerNombre":"Andrés","SegundoNombre":"Avilés","Rol":true,"is_active":true},{"PrimerNombre":"Beto","SegundoNombre":"Otro","Rol":false,"is_active":false}]""").asJsonArray.map { it.asJsonObject }
        assertEquals(1,UserPresentation.visible(users,UserListState("aviles",1,1)).size)
        assertTrue(UserPresentation.visible(users,UserListState("aviles",2,0)).isEmpty())
    }
}
