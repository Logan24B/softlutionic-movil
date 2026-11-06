package com.softlutionic.mobile

import android.app.Instrumentation
import android.os.Bundle
import com.google.gson.Gson
import com.softlutionic.mobile.data.SessionManager
import com.softlutionic.mobile.data.remote.createApi
import kotlinx.coroutines.runBlocking

/** GET only; records schema and structure without exporting personal event content. */
class ConsultationContractInstrumentation: Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result=Bundle(); var complete=true
        runBlocking {
            val api=createApi(SessionManager(targetContext))
            runCatching { api.schema() }.onSuccess { result.putString("schema",Gson().toJson(it.filter { m -> m.name in setOf("moras","notificaciones","auditoria","facturas","usuarios") })) }
                .onFailure { complete=false; result.putString("schema_error",it.toString()) }
            for(route in listOf("moras","notificaciones","auditoria","facturas","clientes","usuarios")) {
                val data=runCatching { api.list(route) }.onFailure { complete=false; result.putString("${route}_error",it.toString()) }.getOrNull() ?: continue
                result.putInt("${route}_count",data.size())
                result.putString("${route}_keys",data.flatMap { it.asJsonObject.keySet() }.distinct().sorted().joinToString(","))
                if(route=="auditoria") for(key in listOf("Accion","Tabla")) result.putString("audit_$key",data.mapNotNull { it.asJsonObject[key]?.asString }.distinct().sorted().joinToString(" | "))
            }
        }
        finish(if(complete) -1 else 0,result)
    }
}
