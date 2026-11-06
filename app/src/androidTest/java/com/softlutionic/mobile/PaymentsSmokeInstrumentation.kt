package com.softlutionic.mobile

import android.app.Instrumentation
import android.content.*
import android.os.*
import android.view.*
import android.widget.*
import android.graphics.Bitmap
import androidx.room.Room
import com.google.gson.*
import com.softlutionic.mobile.data.SessionManager
import com.softlutionic.mobile.data.local.CacheDatabase
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.data.repository.SoftlutionicRepository
import com.softlutionic.mobile.ui.invoices.*
import com.softlutionic.mobile.ui.payments.*
import com.softlutionic.mobile.viewmodel.MainViewModel
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import java.io.File

/** Isolated fixture: all financial requests go to a fake API and in-memory Room. */
class PaymentsSmokeInstrumentation: Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun all(view: View): List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList())
    private fun capture(name: String) {
        waitForIdleSync(); Thread.sleep(700)
        val file=File(targetContext.getExternalFilesDir("verification"),"payments-$name.png"); file.parentFile?.mkdirs()
        uiAutomation.takeScreenshot()?.let { b -> file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }; b.recycle() }
    }
    override fun onStart() {
        var activity: MainActivity?=null; var db: CacheDatabase?=null; var fixtureSession: SessionManager?=null; var vm: MainViewModel?=null
        try {
            fun json(s: String)=JsonParser.parseString(s).asJsonObject
            val states=listOf(json("""{"id":41,"estado":"Pendiente"}"""),json("""{"id":52,"estado":"Vencida"}"""),json("""{"id":63,"estado":"Pagada"}"""),json("""{"id":74,"estado":"Anulada"}"""))
            fun invoice(id: Int,code: Int,state: Int)=json("""{"id":$id,"CodigoFact":$code,"EstadoId":$state,"Monto_Total":"4402.00","Fecha_Vencimiento":"2026-11-27","Hora_Vencimiento":"18:00:00","cliente_detalle":{"id":987654,"Nombre":"Alejandra","Apellido":"Rodríguez García"}}""")
            val invoices=mutableListOf(invoice(111111,1002,41),invoice(222222,1003,52),invoice(333333,1004,63))
            val payments=mutableListOf(json("""{"id":444444,"FacturaId":333333,"NumeroRecibo":30,"Fecha_Pago":"2026-10-28T16:00:00Z","Monto_Pagado":"4402.00","EstadoPago":true,"MetodoPago":false}"""))
            val fields=PaymentPresentation.writable.map { Field(it,it,true,if(it in setOf("EstadoPago","MetodoPago")) "BooleanField" else "",if(it=="FacturaId") "facturas" else null,null) }
            val definitions=listOf(Module("pagos",Capability(true,true,true,false),fields),Module("facturas",Capability(true,false,false,false),emptyList()),Module("estados-factura",Capability(true,false,false,false),emptyList()))
            var posts=0; var patches=0; var lastPayload: JsonObject?=null; var fail=false
            val api=Proxy.newProxyInstance(Api::class.java.classLoader,arrayOf(Api::class.java)) { _,method,args -> when(method.name) {
                "list" -> JsonArray().apply { (when(args!![0]) { "facturas" -> invoices; "pagos" -> payments; "estados-factura" -> states; else -> emptyList() }).forEach { add(it.deepCopy()) } }
                "create","update" -> {
                    check(args!![0]=="pagos")
                    if(method.name=="create") posts++ else patches++
                    val payload=(args[if(method.name=="create") 1 else 2] as JsonObject).deepCopy(); lastPayload=payload
                    @Suppress("UNCHECKED_CAST") val continuation=args.last() as Continuation<Any?>
                    Handler(Looper.getMainLooper()).postDelayed({
                        if(fail) continuation.resumeWith(Result.failure(java.io.IOException("fixture timeout")))
                        else {
                            val saved=if(method.name=="create") payload.deepCopy().apply { addProperty("id",555555) }
                                else payments.single { it["id"].asString==args[1] }.deepCopy().apply { payload.entrySet().forEach { add(it.key,it.value) } }
                            payments.removeAll { it["id"]==saved["id"] }; payments.add(saved)
                            val confirmed=payments.any { it["FacturaId"]==saved["FacturaId"] && it["EstadoPago"].asBoolean }
                            invoices.first { it["id"]==saved["FacturaId"] }.addProperty("EstadoId",if(confirmed) 63 else 41)
                            continuation.resumeWith(Result.success(saved))
                        }
                    },500)
                    COROUTINE_SUSPENDED
                }
                else -> error("Unexpected financial fixture request ${method.name}")
            } } as Api
            val host=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity; activity=host
            Thread.sleep(1200)
            val context=object: ContextWrapper(targetContext) { override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences("payments-fixture-$name",mode) }
            val session=SessionManager(context); fixtureSession=session; session.save("fixture-access","fixture-refresh")
            val database=Room.inMemoryDatabaseBuilder(context,CacheDatabase::class.java).build(); db=database
            val repository=SoftlutionicRepository(api,session,database.cache())
            runBlocking { repository.store("schema",Gson().toJson(definitions)); repository.store("identity","""{"user":{"id":1,"Rol":true}}"""); repository.restoreIdentity() }
            lateinit var model: MainViewModel
            runOnMainSync { model=MainViewModel(host.application) }; vm=model
            MainViewModel::class.java.getDeclaredField("repository").apply { isAccessible=true }.set(model,repository)
            MainViewModel::class.java.getDeclaredField("paymentConnection").apply { isAccessible=true }.set(model,{true})
            MainViewModel::class.java.getDeclaredField("invoiceTimeSource").apply { isAccessible=true }.set(model,object: InvoiceTimeSource {
                override fun status()=InvoiceClockStatus(true,true,true)
                override fun capture()=InvoiceEmission(java.time.ZonedDateTime.parse("2026-10-28T10:00:00-06:00[America/Guatemala]"))
            })
            lateinit var controller: PaymentsController
            runOnMainSync {
                model.modules.value=definitions
                controller=PaymentsController(host,definitions.first(),model); host.setContentView(controller.root)
            }
            fun waitSave() { repeat(100) { if(model.paymentSaving.value) Thread.sleep(50) }; check(!model.paymentSaving.value) }
            repeat(100) { if(model.paymentLoading.value) Thread.sleep(50) }
            runOnMainSync { controller.stateChanged() }
            capture("list")
            runOnMainSync {
                val text=all(controller.root).filterIsInstance<TextView>().joinToString("\n") { it.text }
                check(!text.contains("FacturaId") && !text.contains("ClienteId") && !text.contains("EstadoId") && !text.contains("111111"))
                model.paymentFilters.value=PaymentFilters("Alejandra","52"); controller.stateChanged()
            }
            waitForIdleSync()
            runOnMainSync {
                check(all(controller.root).filterIsInstance<TextView>().any { it.text.toString()=="1003" })
                model.paymentFilters.value=PaymentFilters(); controller.stateChanged()
                all(controller.root).filterIsInstance<TextView>().first { it.text.toString()=="Registrar pago" }.performClick()
            }
            capture("form")
            // Call the actual ViewModel twice within the same main-thread action.
            runOnMainSync { model.savePayment("111111",null,"31",true); model.savePayment("111111",null,"31",true) }
            waitSave(); check(posts==1); check(model.paymentSaved.value!=null)
            check(lastPayload?.get("FacturaId")?.asInt==111111 && lastPayload?.has("EstadoId")==false)
            check(model.paymentCatalogs.value.getValue("facturas").first { it["id"].asInt==111111 }["EstadoId"].asInt==63)
            runOnMainSync { controller.stateChanged() }; capture("confirmed")
            val saved=payments.single { it["id"].asInt==555555 }.deepCopy()
            runOnMainSync { model.savePayment("111111",saved,"31",false,"4402.00",false) }
            waitSave(); check(patches==1)
            check(model.paymentCatalogs.value.getValue("facturas").first { it["id"].asInt==111111 }["EstadoId"].asInt==41)
            runOnMainSync { controller.stateChanged() }
            MainViewModel::class.java.getDeclaredField("paymentConnection").apply { isAccessible=true }.set(model,{false})
            runOnMainSync { model.paymentSaved.value=null; model.savePayment("222222",null,"32",false) }
            check(posts==1 && model.paymentSaved.value==null && model.paymentError.value.contains("Sin conexión"))
            MainViewModel::class.java.getDeclaredField("paymentConnection").apply { isAccessible=true }.set(model,{true})
            fail=true
            runOnMainSync { model.savePayment("222222",null,"32",false) }; waitSave()
            check(posts==2 && model.paymentSaved.value==null)
            runOnMainSync { model.savePayment("222222",null,"32",false) }; waitSave(); check(posts==2)
            runOnMainSync {
                model.modules.value=definitions.map { if(it.name=="pagos") it.copy(permissions=Capability(true,false,false,false)) else it }
                model.savePayment("222222",null,"32",false)
            }
            check(posts==2 && patches==1)
            runOnMainSync { controller.dispose() }
            finish(-1,Bundle().apply { putInt("posts_including_timeout_fixture",posts); putBoolean("double_tap_single_post",true); putBoolean("offline_no_post",true); putBoolean("uncertain_no_retry",true); putBoolean("permissions_preserved",true) })
        } catch(e: Throwable) { finish(0,Bundle().apply { putString("error",e.toString()); putString("stack",e.stackTraceToString().take(2500)) }) }
        finally {
            vm?.let { model -> runOnMainSync { MainViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible=true }.invoke(model) } }
            fixtureSession?.clear(); db?.close(); activity?.let { runOnMainSync { it.finish() } }
        }
    }
}
