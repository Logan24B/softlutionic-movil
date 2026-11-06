package com.softlutionic.mobile

import android.app.Instrumentation
import android.os.Bundle
import com.google.gson.Gson
import com.softlutionic.mobile.data.SessionManager
import com.softlutionic.mobile.data.remote.createApi
import kotlinx.coroutines.runBlocking

/** Read-only contract probe. Never logs tokens or customer records. */
class PaymentsContractInstrumentation: Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result=Bundle()
        try {
            runBlocking {
                val api=createApi(SessionManager(targetContext))
                runCatching { api.schema() }.onSuccess { result.putString("schema",Gson().toJson(it.filter { m -> m.name in setOf("pagos","facturas","estados-factura") })) }.onFailure { result.putString("schema_error",it.toString()) }
                for(route in listOf("facturas","pagos","estados-factura")) {
                    val data=runCatching { api.list(route) }.onFailure { result.putString("${route}_error",it.toString()) }.getOrNull() ?: continue
                    result.putInt("${route}_count",data.size())
                    result.putString("${route}_keys",data.flatMap { it.asJsonObject.keySet() }.distinct().sorted().joinToString(","))
                    if(route=="estados-factura") result.putString("states",data.toString())
                }
            }
            finish(-1,result)
        } catch(e: Exception) { result.putString("error",e.toString()); finish(0,result) }
    }
}
