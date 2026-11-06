package com.softlutionic.mobile

import android.app.Instrumentation
import android.os.Bundle
import com.google.gson.Gson
import com.softlutionic.mobile.data.SessionManager
import com.softlutionic.mobile.data.remote.createApi
import kotlinx.coroutines.runBlocking

/** Authenticated GET-only audit. Never outputs tokens or customer values. */
class ContractsContractInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            runBlocking {
                val session = SessionManager(targetContext)
                check(session.access != null) { "No authenticated session" }
                val api = createApi(session)
                val module = api.schema().single { it.name == "contratos" }
                val records = api.list("contratos")
                val clients = api.list("clientes")
                val types = api.list("tipos-contrato")
                finish(-1, Bundle().apply {
                    putString("schema", Gson().toJson(module))
                    putString("record_keys", records.flatMap { it.asJsonObject.keySet() }.distinct().sorted().joinToString(","))
                    putString("client_keys", clients.flatMap { it.asJsonObject.keySet() }.distinct().sorted().joinToString(","))
                    putString("type_keys", types.flatMap { it.asJsonObject.keySet() }.distinct().sorted().joinToString(","))
                    putInt("contract_count", records.size())
                })
            }
        } catch (error: Throwable) {
            finish(0, Bundle().apply { putString("error", error.javaClass.simpleName + ": " + error.message) })
        }
    }
}
