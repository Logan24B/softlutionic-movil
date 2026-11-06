package com.softlutionic.mobile

import android.app.Instrumentation
import android.os.Bundle
import com.google.gson.Gson
import com.softlutionic.mobile.data.SessionManager
import com.softlutionic.mobile.data.remote.createApi
import kotlinx.coroutines.runBlocking

/** Authenticated read-only audit. No tokens or catalog contents are printed. */
class ContractTypesContractInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            runBlocking {
                val session=SessionManager(targetContext)
                check(session.access!=null) { "No authenticated session" }
                val api=createApi(session)
                val module=api.schema().single { it.name=="tipos-contrato" }
                val records=api.list("tipos-contrato")
                finish(-1,Bundle().apply {
                    putString("schema",Gson().toJson(module))
                    putString("record_keys",records.flatMap { it.asJsonObject.keySet() }.distinct().sorted().joinToString(","))
                    putInt("record_count",records.size())
                })
            }
        } catch(error: Throwable) { finish(0,Bundle().apply { putString("error",error.toString()) }) }
    }
}
