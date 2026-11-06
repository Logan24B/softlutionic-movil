package com.softlutionic.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.softlutionic.mobile.data.SessionManager
import com.softlutionic.mobile.data.local.CacheDatabase
import com.softlutionic.mobile.data.remote.Api
import com.softlutionic.mobile.data.remote.Capability
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.data.repository.SoftlutionicRepository
import com.softlutionic.mobile.domain.auth.AuthorizationDenied
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy

/** Local fixtures only: separate preferences, in-memory Room, no HTTP client. */
class CatalogAuthorizationInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val fixtureContext = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("catalog-authorization-fixture-$name", mode)
        }
        val session = SessionManager(fixtureContext)
        val database = Room.inMemoryDatabaseBuilder(targetContext, CacheDatabase::class.java).build()
        var apiCalls = 0
        var deniedWrites = 0
        try {
            val api = Proxy.newProxyInstance(Api::class.java.classLoader, arrayOf(Api::class.java)) { _, method, _ ->
                apiCalls++
                check(method.name == "list") { "Unexpected API call: ${method.name}" }
                JsonArray().apply { add(JsonObject().apply { addProperty("id", 1); addProperty("Nombre", "Catalogo") }) }
            } as Api
            runBlocking {
                session.save("fixture-access", "fixture-refresh")
                val repository = SoftlutionicRepository(api, session, database.cache())
                repository.store("identity", """{"user":{"id":3,"Rol":false,"is_superuser":false}}""")
                repository.restoreIdentity()
                val modules = listOf("servicios", "tipos-contrato").map {
                    Module(it, Capability(true, true, true, true), emptyList())
                }
                repository.store("schema", Gson().toJson(modules))
                for (module in modules) {
                    val beforeRead = apiCalls
                    repository.refresh(module.name)
                    check(apiCalls == beforeRead + 1)
                    check(repository.cachedOptions(module.name).single()["id"].asInt == 1)
                    check(repository.options(module.name).size == 1)
                    val beforeWrite = apiCalls
                    for (operation in listOf("create", "update", "delete")) {
                        try {
                            when (operation) {
                                "create" -> repository.save(module.name, null, JsonObject())
                                "update" -> repository.save(module.name, "1", JsonObject())
                                else -> repository.delete(module.name, "1")
                            }
                            error("Employee $operation was allowed for ${module.name}")
                        } catch (denied: AuthorizationDenied) {
                            check(denied.module == module.name)
                            deniedWrites++
                        }
                        check(apiCalls == beforeWrite) { "Denied operation reached Api" }
                    }
                }
            }
            finish(-1, Bundle().apply {
                putInt("blocked_writes_without_api_calls", deniedWrites)
                putInt("allowed_catalog_reads", apiCalls)
            })
        } catch (error: Throwable) {
            finish(0, Bundle().apply { putString("error", error.toString()) })
        } finally {
            session.clear()
            database.close()
        }
    }
}
