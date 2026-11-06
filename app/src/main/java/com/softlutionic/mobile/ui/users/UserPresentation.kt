package com.softlutionic.mobile.ui.users

import com.google.gson.JsonObject
import com.softlutionic.mobile.data.remote.Field
import java.text.Normalizer
import java.util.Locale

data class UserListState(val query: String = "", val role: Int = 0, val status: Int = 0)
internal fun JsonObject.userText(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
internal fun JsonObject.userFlag(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

object UserPresentation {
    val labels = linkedMapOf("PrimerNombre" to "Primer nombre", "SegundoNombre" to "Primer apellido",
        "Email" to "Correo electrónico", "Usuario" to "Usuario", "Contrasena" to "Contraseña",
        "Rol" to "Rol", "is_active" to "Estado")
    fun editable(fields: List<Field>) = labels.keys.mapNotNull { key -> fields.find { it.name == key && !it.read_only } }
    fun name(user: JsonObject) = "${user.userText("PrimerNombre")} ${user.userText("SegundoNombre")}".trim().ifBlank { user.userText("Usuario") }
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)
    fun visible(users: List<JsonObject>, state: UserListState) = users.filter { user ->
        val text = normalize(listOf(name(user), user.userText("Email"), user.userText("Usuario")).joinToString(" "))
        normalize(state.query.trim()).split("\\s+".toRegex()).all(text::contains) &&
            (state.role == 0 || user.userFlag("Rol") == (state.role == 1)) &&
            (state.status == 0 || user.userFlag("is_active") == (state.status == 1))
    }.sortedBy { normalize(name(it)) }
    /** Explicit business allowlist, intersected with the real editable schema. */
    fun payload(fields: List<Field>, values: Map<String, String>, creating: Boolean): JsonObject = JsonObject().apply {
        editable(fields).forEach { field ->
            values[field.name]?.let { raw ->
                when(field.name) {
                    "Contrasena" -> if(raw.isNotEmpty()) addProperty(field.name, raw)
                    "Rol", "is_active" -> addProperty(field.name, raw == "true")
                    "Usuario" -> addProperty(field.name, raw.trim().ifEmpty { values["Email"].orEmpty().trim() })
                    else -> addProperty(field.name, raw.trim())
                }
            }
        }
        require(!creating || has("Contrasena")) { "Ingresa una contraseña para crear el usuario." }
    }
    fun passwordError(password: String, confirmation: String, creating: Boolean): String? = when {
        creating && password.isEmpty() -> "Ingresa una contraseña para crear el usuario."
        password != confirmation -> "Las contraseñas no coinciden."
        else -> null
    }
}
