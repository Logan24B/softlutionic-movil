package com.softlutionic.mobile

import android.app.Instrumentation
import android.os.Bundle
import com.google.gson.*
import com.softlutionic.mobile.data.SessionManager
import com.softlutionic.mobile.data.remote.*
import kotlinx.coroutines.runBlocking

/** GET/OPTIONS only. No financial writes, credentials or customer values in output. */
class InvoicesContractInstrumentation: Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        var phase="session"
        try {
            runBlocking {
                val session=SessionManager(targetContext); check(session.access!=null) { "No authenticated session" }
                val api=createApi(session)
                phase="GET schema"
                val schema=api.schema().single { it.name=="facturas" }
                phase="GET facturas"
                val records=api.list("facturas")
                phase="GET estados-factura"
                val states=api.list("estados-factura")
                phase="GET tipos-contrato"
                val types=api.list("tipos-contrato")
                // DRF metadata describes writable/read-only fields without sending a POST.
                phase="OPTIONS facturas"
                val options=okhttp3.OkHttpClient.Builder().addInterceptor(AuthInterceptor(session)).callTimeout(30,java.util.concurrent.TimeUnit.SECONDS).build()
                    .newCall(okhttp3.Request.Builder().url(BuildConfig.BASE_URL+"api/mobile/v1/facturas/").method("OPTIONS",null).build()).execute().use {
                        check(it.isSuccessful); JsonParser.parseString(it.body!!.string()).asJsonObject
                    }
                finish(-1,Bundle().apply {
                    putString("schema",Gson().toJson(schema))
                    putString("type_keys",types.flatMap { it.asJsonObject.keySet() }.distinct().sorted().joinToString(","))
                    putString("type_names",types.map { it.asJsonObject["Nombre"]?.asString }.joinToString(" | "))
                    putString("options",options["actions"]?.toString())
                    putString("invoice_keys",records.flatMap { it.asJsonObject.keySet() }.distinct().sorted().joinToString(","))
                    val lines=records.flatMap { it.asJsonObject["detalles"]?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty() }
                    putString("line_keys",lines.flatMap { it.asJsonObject.keySet() }.distinct().sorted().joinToString(","))
                    putInt("invoice_count",records.size())
                    putBoolean("totals_match_line_sums",records.all { value ->
                        val invoice=value.asJsonObject
                        val total=invoice["detalles"].asJsonArray.fold(java.math.BigDecimal.ZERO) { sum,line ->
                            sum+line.asJsonObject["PrecioVenta"].asBigDecimal*line.asJsonObject["Cantidad"].asBigDecimal
                        }
                        total.compareTo(invoice["Monto_Total"].asBigDecimal)==0
                    })
                    putString("codes",records.map { it.asJsonObject["CodigoFact"]?.toString() }.joinToString(","))
                    putString("states",states.map { it.asJsonObject["estado"]?.asString }.joinToString(","))
                    putString("contract_detail_keys",records.flatMap { it.asJsonObject["contrato_detalle"]?.takeIf { it.isJsonObject }?.asJsonObject?.keySet().orEmpty() }.distinct().joinToString(","))
                })
            }
        } catch(error: Throwable) {
            val result=Bundle().apply { putString("phase",phase); putString("error",error.toString()) }
            // Local evidence is labelled as cached, never reported as a successful live probe.
            runBlocking {
                val db=androidx.room.Room.databaseBuilder(targetContext,com.softlutionic.mobile.data.local.CacheDatabase::class.java,"softlutionic-cache.db").build()
                try {
                    db.cache().get("schema")?.let { entry ->
                        val module=Gson().fromJson(entry.json,Array<Module>::class.java).single { it.name=="facturas" }
                        result.putString("cached_invoice_schema",Gson().toJson(module)); result.putLong("cached_schema_at",entry.updatedAt)
                    }
                    db.cache().get("tipos-contrato")?.let { entry ->
                        val types=JsonParser.parseString(entry.json).asJsonArray
                        result.putString("cached_type_keys",types.flatMap { it.asJsonObject.keySet() }.distinct().sorted().joinToString(","))
                        result.putString("cached_type_names",types.map { it.asJsonObject["Nombre"]?.asString }.joinToString(" | "))
                        result.putLong("cached_types_at",entry.updatedAt)
                    }
                } finally { db.close() }
            }
            finish(0,result)
        }
    }
}
