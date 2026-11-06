package com.softlutionic.mobile.domain.auth

import com.google.gson.JsonObject
import com.softlutionic.mobile.data.remote.Capability
import com.softlutionic.mobile.data.remote.Module

enum class UserLevel { SUPERUSER, ADMIN, EMPLOYEE, UNKNOWN }
data class UserIdentity(val id: String, val level: UserLevel)
class AuthorizationDenied(val module: String) : IllegalStateException(
    when(module) {
        "servicios" -> "No tienes permiso para modificar servicios."
        "tipos-contrato" -> "No tienes permiso para modificar tipos de contrato."
        "contratos" -> "No tienes permiso para modificar contratos."
        "facturas" -> "No tienes permiso para modificar facturas."
        "pagos" -> "No tienes permiso para modificar pagos."
        else -> "No tienes permiso para administrar esta cuenta."
    }
)

/** Fail closed on missing/invalid identity. Never infer superuser from Rol or labels. */
object AuthorizationPolicy {
    val employeeReadOnlyModules: Set<String> = setOf("servicios", "tipos-contrato")
    private fun flag(user: JsonObject, key: String): Boolean? = user[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
    fun user(user: JsonObject?): UserIdentity? {
        user ?: return null
        val id=user["id"]?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } ?: return null
        val level=when {
            flag(user,"is_superuser")==true -> UserLevel.SUPERUSER
            flag(user,"Rol")==true -> UserLevel.ADMIN
            flag(user,"Rol")==false -> UserLevel.EMPLOYEE
            else -> UserLevel.UNKNOWN
        }
        return UserIdentity(id,level)
    }
    fun identity(response: JsonObject?) = user(response?.get("user")?.takeIf { it.isJsonObject }?.asJsonObject)
    fun canReadUsers(actor: UserIdentity?) = actor?.level in listOf(UserLevel.ADMIN,UserLevel.SUPERUSER)
    fun effective(actor: UserIdentity?, module: Module): Module {
        val p=module.permissions
        val known=actor!=null && actor.level!=UserLevel.UNKNOWN
        val caps=when {
            module.name in employeeReadOnlyModules -> Capability(p.read && known,p.create && known && actor?.level!=UserLevel.EMPLOYEE,
                p.update && known && actor?.level!=UserLevel.EMPLOYEE,p.delete && known && actor?.level!=UserLevel.EMPLOYEE)
            module.name=="usuarios" -> if(canReadUsers(actor)) p else Capability(false,false,false,false)
            module.name=="solicitudes-contacto" -> Capability(p.read && canReadUsers(actor),false,false,false)
            else -> p
        }
        return module.copy(permissions=caps)
    }
    fun canManageUser(actor: UserIdentity?, target: UserIdentity?): Boolean = when(actor?.level) {
        UserLevel.SUPERUSER -> target!=null && target.level!=UserLevel.UNKNOWN
        UserLevel.ADMIN -> target?.level==UserLevel.EMPLOYEE
        else -> false
    }
    fun canEditUser(actor: UserIdentity?, target: JsonObject, module: Module) =
        effective(actor,module).permissions.update && canManageUser(actor,user(target))
    fun roleOptions(actor: UserIdentity?) = when(actor?.level) {
        UserLevel.SUPERUSER -> listOf(false,true)
        UserLevel.ADMIN -> listOf(false)
        else -> emptyList()
    }
    fun label(target: JsonObject): String = when(user(target)?.level) {
        UserLevel.SUPERUSER -> "Superusuario"
        UserLevel.ADMIN -> "Administrador"
        UserLevel.EMPLOYEE -> "Empleado"
        else -> "Rol no disponible"
    }
    fun protection(actor: UserIdentity?, target: JsonObject): String? = when {
        user(target)?.level==UserLevel.SUPERUSER -> "Cuenta protegida"
        user(target)?.level==UserLevel.ADMIN && actor?.level!=UserLevel.SUPERUSER -> "Cuenta administrativa"
        user(target)?.level==UserLevel.UNKNOWN -> "Cuenta protegida"
        else -> null
    }
    fun allowsWrite(actor: UserIdentity?, module: Module, operation: String, target: JsonObject?=null, payload: JsonObject?=null): Boolean {
        val caps=effective(actor,module).permissions
        val permitted=when(operation) { "create" -> caps.create; "update" -> caps.update; "delete" -> caps.delete; else -> false }
        if(!permitted) return false
        if(module.name!="usuarios") return true
        if(operation!="create" && !canManageUser(actor,user(target))) return false
        if(payload!=null) {
            val allowed=setOf("PrimerNombre","SegundoNombre","Email","Usuario","Contrasena","Rol","is_active")
            if(payload.keySet().any { it !in allowed }) return false
            if(payload.has("Rol")) {
                val role=flag(payload,"Rol") ?: return false
                if(role !in roleOptions(actor)) return false
            }
        }
        return true
    }
}
