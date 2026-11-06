package com.softlutionic.mobile

import com.google.gson.JsonParser
import com.softlutionic.mobile.domain.auth.*
import com.softlutionic.mobile.data.remote.*
import org.junit.Assert.*
import org.junit.Test

class AuthorizationPolicyTest {
    private fun json(value: String)=JsonParser.parseString(value).asJsonObject
    private val employee=json("""{"id":3,"Rol":false,"is_superuser":false}""")
    private val admin=json("""{"id":2,"Rol":true,"is_superuser":false}""")
    private val superuser=json("""{"id":1,"Rol":true,"is_superuser":true}""")
    private val all=Capability(true,true,true,true)
    private fun module(name: String,p: Capability=all)=Module(name,p,emptyList())
    @Test fun realBooleanRoleMappingAndExplicitSuperuserPrecedence() {
        assertEquals(UserLevel.EMPLOYEE,AuthorizationPolicy.user(employee)!!.level)
        assertEquals(UserLevel.ADMIN,AuthorizationPolicy.user(admin)!!.level)
        assertEquals(UserLevel.SUPERUSER,AuthorizationPolicy.user(superuser)!!.level)
    }
    @Test fun missingSuperuserDoesNotInventSuperuserFromAdminDescription() {
        assertEquals(UserLevel.ADMIN,AuthorizationPolicy.user(json("""{"id":1,"Rol":true,"rol_descripcion":"Superusuario"}"""))!!.level)
        assertEquals(UserLevel.UNKNOWN,AuthorizationPolicy.user(json("""{"id":1,"Rol":"Administrador"}"""))!!.level)
        assertNull(AuthorizationPolicy.user(json("""{"Rol":true}""")))
    }
    @Test fun identityIsNestedAuthMeUserAndNeverUsernameOrEmail() {
        assertEquals(UserLevel.EMPLOYEE,AuthorizationPolicy.identity(json("""{"user":{"id":3,"Rol":false,"Usuario":"superadmin","Email":"admin@example.com"}}"""))!!.level)
        assertNull(AuthorizationPolicy.identity(admin))
    }
    @Test fun employeeServicesReadOnlyEvenWithAllBackendPermissions() {
        val actor=AuthorizationPolicy.user(employee)
        assertEquals(Capability(true,false,false,false),AuthorizationPolicy.effective(actor,module("servicios")).permissions)
        listOf("create","update","delete").forEach { assertFalse(AuthorizationPolicy.allowsWrite(actor,module("servicios"),it)) }
    }
    @Test fun employeeCannotReadUsersOrCreateAnyAccount() {
        val actor=AuthorizationPolicy.user(employee)
        assertFalse(AuthorizationPolicy.canReadUsers(actor))
        assertEquals(Capability(false,false,false,false),AuthorizationPolicy.effective(actor,module("usuarios")).permissions)
        assertFalse(AuthorizationPolicy.allowsWrite(actor,module("usuarios"),"create"))
    }
    @Test fun employeeContractTypesRemainVisibleAndReadOnlyDespiteWritableSchema() {
        val definition=module("tipos-contrato")
        val actor=AuthorizationPolicy.user(employee)
        assertEquals(Capability(true,false,false,false),AuthorizationPolicy.effective(actor,definition).permissions)
        listOf("create","update","delete").forEach {
            assertFalse(AuthorizationPolicy.allowsWrite(actor,definition,it))
        }
        assertEquals(all,definition.permissions)
        assertEquals("No tienes permiso para modificar tipos de contrato.",AuthorizationDenied("tipos-contrato").message)
    }
    @Test fun privilegedActorsKeepCatalogCrudAccordingToSchema() {
        listOf(admin,superuser,json("""{"id":1,"Rol":false,"is_superuser":true}""")).forEach { user ->
            val actor=AuthorizationPolicy.user(user)
            listOf("servicios","tipos-contrato").forEach { name ->
                assertEquals(all,AuthorizationPolicy.effective(actor,module(name)).permissions)
                listOf("create","update","delete").forEach {
                    assertTrue(AuthorizationPolicy.allowsWrite(actor,module(name),it))
                }
                val limited=module(name,Capability(true,true,false,false))
                assertEquals(limited.permissions,AuthorizationPolicy.effective(actor,limited).permissions)
                assertTrue(AuthorizationPolicy.allowsWrite(actor,limited,"create"))
                assertFalse(AuthorizationPolicy.allowsWrite(actor,limited,"update"))
                assertFalse(AuthorizationPolicy.allowsWrite(actor,limited,"delete"))
            }
        }
    }
    @Test fun adminCanManageEmployeeForAllBackendAllowedOperations() {
        val actor=AuthorizationPolicy.user(admin)
        assertTrue(AuthorizationPolicy.allowsWrite(actor,module("usuarios"),"create",payload=json("""{"Rol":false}""")))
        listOf("update","delete").forEach { assertTrue(AuthorizationPolicy.allowsWrite(actor,module("usuarios"),it,employee)) }
    }
    @Test fun adminCannotManageAdministratorOrSuperuserOrUnknownTarget() {
        val actor=AuthorizationPolicy.user(admin)
        listOf(admin,superuser,json("""{"id":4}""")).forEach { target ->
            listOf("update","delete").forEach { assertFalse(AuthorizationPolicy.allowsWrite(actor,module("usuarios"),it,target)) }
        }
        assertFalse(AuthorizationPolicy.allowsWrite(actor,module("usuarios"),"update"))
    }
    @Test fun adminCannotPromoteEmployeeOrCreateAdministrator() {
        val actor=AuthorizationPolicy.user(admin)
        listOf("create","update").forEach { assertFalse(AuthorizationPolicy.allowsWrite(actor,module("usuarios"),it,employee,json("""{"Rol":true}"""))) }
        assertEquals(listOf(false),AuthorizationPolicy.roleOptions(actor))
    }
    @Test fun privilegedDjangoKeysCannotBypassRoleGuard() {
        listOf("is_superuser","is_staff","password","last_name").forEach { key ->
            assertFalse(AuthorizationPolicy.allowsWrite(AuthorizationPolicy.user(admin),module("usuarios"),"update",employee,json("""{"$key":true}""")))
        }
    }
    @Test fun superuserCanManageAdministratorsOnlyWhenBackendPermits() {
        val actor=AuthorizationPolicy.user(superuser)
        assertTrue(AuthorizationPolicy.allowsWrite(actor,module("usuarios"),"update",admin,json("""{"Rol":true}""")))
        assertTrue(AuthorizationPolicy.allowsWrite(actor,module("usuarios"),"create",payload=json("""{"Rol":true}""")))
        assertEquals(listOf(false,true),AuthorizationPolicy.roleOptions(actor))
        assertFalse(AuthorizationPolicy.allowsWrite(actor,module("usuarios",Capability(true,false,false,false)),"update",admin))
    }
    @Test fun schemaDenialsAreNeverOverriddenForAnyActor() {
        listOf(employee,admin,superuser).forEach { user ->
            listOf("servicios","tipos-contrato","usuarios").forEach { name ->
                val definition=module(name,Capability(false,false,false,false))
                assertFalse(AuthorizationPolicy.effective(AuthorizationPolicy.user(user),definition).permissions.read)
                listOf("create","update","delete").forEach { assertFalse(AuthorizationPolicy.allowsWrite(AuthorizationPolicy.user(user),definition,it,employee)) }
            }
        }
    }
    @Test fun absentIdentityDeniesSensitiveAccessAndWritesIncludingCachedSchema() {
        listOf("servicios","tipos-contrato","usuarios").forEach { name ->
            listOf(null,UserIdentity("4",UserLevel.UNKNOWN)).forEach { actor ->
                assertEquals(Capability(false,false,false,false),AuthorizationPolicy.effective(actor,module(name)).permissions)
                listOf("create","update","delete").forEach {
                    assertFalse(AuthorizationPolicy.allowsWrite(actor,module(name),it))
                }
            }
        }
    }
    @Test fun protectedLabelsAreDerivedFromFlagsNotDescription() {
        assertEquals("Cuenta administrativa",AuthorizationPolicy.protection(AuthorizationPolicy.user(admin),admin))
        assertEquals("Superusuario",AuthorizationPolicy.label(superuser))
        assertEquals("Cuenta protegida",AuthorizationPolicy.protection(AuthorizationPolicy.user(admin),superuser))
    }
}
