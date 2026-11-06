package com.softlutionic.mobile

import android.app.Instrumentation
import android.os.Bundle
import com.softlutionic.mobile.data.SessionManager
import com.softlutionic.mobile.data.remote.createApi
import kotlinx.coroutines.runBlocking

/** Read-only audit using the installed debug application's encrypted session.
 * Never prints credentials, passwords or users' personal values. */
class UsersContractInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            val session = SessionManager(targetContext)
            check(session.access != null) { "No session in target application" }
            runBlocking {
                val api = createApi(session)
                val identity = api.me()
                val module = api.schema().single { it.name == "usuarios" }
                val users = api.list("usuarios")
                val keys = users.flatMap { it.asJsonObject.keySet() }.distinct().sorted()
                finish(-1, Bundle().apply {
                    putString("base_url", BuildConfig.BASE_URL)
                    putString("schema", com.google.gson.Gson().toJson(module))
                    putString("identity_keys", identity.keySet().sorted().joinToString(","))
                    val current = identity.getAsJsonObject("user")
                    putString("identity_user_keys", current.keySet().sorted().joinToString(","))
                    putString("identity_role", current.get("Rol")?.toString())
                    putString("identity_superuser", current.get("is_superuser")?.toString() ?: "NOT_EXPOSED")
                    putString("identity_role_description", current.get("rol_descripcion")?.toString())
                    putInt("record_count", users.size())
                    putString("response_keys", keys.joinToString(","))
                    putBoolean("all_have_business_names", users.all {
                        it.asJsonObject.has("PrimerNombre") && it.asJsonObject.has("SegundoNombre")
                    })
                    putBoolean("no_password_in_response", users.none {
                        it.asJsonObject.has("password") || it.asJsonObject.has("Contrasena")
                    })
                })
            }
        } catch (error: Throwable) {
            finish(0, Bundle().apply { putString("error", error.javaClass.simpleName + ": " + error.message) })
        }
    }
}
