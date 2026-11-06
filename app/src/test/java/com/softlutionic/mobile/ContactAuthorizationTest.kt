package com.softlutionic.mobile

import com.google.gson.JsonParser
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.domain.auth.AuthorizationPolicy
import org.junit.Assert.*
import org.junit.Test

class ContactAuthorizationTest {
    @Test fun contactRequiresBusinessRoleAndSchemaAndNeverAllowsWrites() {
        val all=Module(ContactRequestDto.MODULE,Capability(true,true,true,true),emptyList())
        fun actor(roles: String)=AuthorizationPolicy.user(JsonParser.parseString("""{"id":1,$roles}""").asJsonObject)
        val employee=actor(""""Rol":false,"is_superuser":false,"is_staff":true""")
        assertEquals(Capability(false,false,false,false),AuthorizationPolicy.effective(employee,all).permissions)
        listOf(actor(""""Rol":true"""),actor(""""Rol":false,"is_superuser":true""")).forEach {
            assertEquals(Capability(true,false,false,false),AuthorizationPolicy.effective(it,all).permissions)
            assertEquals(Capability(false,false,false,false),AuthorizationPolicy.effective(it,all.copy(permissions=Capability(false,true,true,true))).permissions)
        }
    }
}
