package com.softlutionic.mobile

import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.room.Room
import com.google.gson.*
import com.softlutionic.mobile.data.SessionManager
import com.softlutionic.mobile.data.local.CacheDatabase
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.data.repository.SoftlutionicRepository
import com.softlutionic.mobile.ui.consultation.*
import com.softlutionic.mobile.viewmodel.MainViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.File
import java.lang.reflect.Proxy

/** All records, cache and mutations are isolated fixtures, never production events. */
class ConsultationSmokeInstrumentation: Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun all(view: View): List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList())
    private fun capture(name: String) {
        waitForIdleSync(); Thread.sleep(700)
        val file=File(targetContext.getExternalFilesDir("verification"),"consultation-$name.png"); file.parentFile?.mkdirs()
        uiAutomation.takeScreenshot()?.let { b -> file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }; b.recycle() }
    }
    override fun onStart() {
        var host: MainActivity?=null; var db: CacheDatabase?=null; var model: MainViewModel?=null; var session: SessionManager?=null
        var active: ConsultationController?=null
        try {
            fun json(s: String)=JsonParser.parseString(s).asJsonObject
            val client=json("""{"id":882882,"Nombre":"Alejandra","Apellido":"Rodríguez García"}""")
            val invoice=json("""{"id":991991,"CodigoFact":1002,"EstadoId":42,"Fecha_Vencimiento":"2026-10-27","Hora_Vencimiento":"18:00:00"}""").apply { add("cliente_detalle",client) }
            val mora=json("""{"id":773773,"FacturaId":991991,"Monto_Mora":"20.25","EstadoMora":false,"Fecha_Inicio":"2026-10-27","Hora_Inicio":"18:00:00","Fecha_Final":"2026-10-28","Hora_Final":"10:00:00","DescripcionMora":"Período de mora registrado por el servidor."}""").apply { add("factura_detalle",invoice) }
            val notice=json("""{"id":774774,"ClienteId":882882,"Mensaje":"Aviso persistido de prueba","Fecha_Envio":"2026-10-28T16:00:00Z","EstadoNotificacion":false}""").apply { add("cliente_detalle",client) }
            val audit=json("""{"IdLog":775775,"Tabla":"Facturas","Accion":"UPDATE","ClavePrincipal":"991991","Usuario":"operador_registrado","Host":"equipo-prueba","Fecha":"2026-10-28T16:00:00Z","Detalle":"FacturaId=991991"}""")
            val read=Capability(true,false,false,false)
            val definitions=listOf(Module("moras",read,emptyList()),Module("notificaciones",Capability(true,true,true,true),listOf(Field("EstadoNotificacion","Leída",false,"BooleanField",null,null))),Module("auditoria",read,emptyList()),Module("facturas",read,emptyList()),Module("clientes",read,emptyList()),Module("estados-factura",read,emptyList()))
            val records=mutableMapOf("moras" to listOf(mora),"notificaciones" to listOf(notice),"auditoria" to listOf(audit),"facturas" to listOf(invoice),"clientes" to listOf(client),"estados-factura" to listOf(json("""{"id":42,"estado":"Vencida"}""")))
            var writes=0; var patches=0; var offline=false
            val api=Proxy.newProxyInstance(Api::class.java.classLoader,arrayOf(Api::class.java)) { _,method,args ->
                if(offline) throw java.io.IOException("fixture offline")
                when(method.name) {
                    "schema" -> definitions
                    "list" -> JsonArray().apply { records[args!![0]].orEmpty().forEach { add(it.deepCopy()) } }
                    "update" -> {
                        check(args!![0]=="notificaciones" && args[1]=="774774"); val payload=args[2] as JsonObject
                        check(payload.keySet()==setOf("EstadoNotificacion") && payload["EstadoNotificacion"].asBoolean)
                        patches++; notice.addProperty("EstadoNotificacion",true); notice.deepCopy()
                    }
                    "create","delete" -> { writes++; error("No financial/admin/event creation allowed") }
                    else -> error("Unexpected fixture method ${method.name}")
                }
            } as Api
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity; host=activity
            Thread.sleep(1000)
            val context=object: ContextWrapper(targetContext) { override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences("consultation-fixture-$name",mode) }
            val fixtureSession=SessionManager(context); session=fixtureSession; fixtureSession.save("fixture-access","fixture-refresh")
            val database=Room.inMemoryDatabaseBuilder(context,CacheDatabase::class.java).build(); db=database
            val repository=SoftlutionicRepository(api,fixtureSession,database.cache())
            runBlocking {
                repository.store("identity","""{"user":{"id":1,"Rol":true}}"""); repository.restoreIdentity(); repository.store("schema",Gson().toJson(definitions))
                records.forEach { (key,values) -> repository.store(key,Gson().toJson(values)) }
            }
            lateinit var vm: MainViewModel
            runOnMainSync { vm=MainViewModel(activity.application) }; model=vm
            MainViewModel::class.java.getDeclaredField("repository").apply { isAccessible=true }.set(vm,repository)
            MainViewModel::class.java.getDeclaredField("currentIdentity").apply { isAccessible=true }.set(vm,repository.currentIdentity)
            runOnMainSync {
                vm.modules.value=definitions
                vm.consultationCatalogs.value=mapOf("facturas" to listOf(invoice),"clientes" to listOf(client),"estados-factura" to records.getValue("estados-factura"))
            }
            fun show(route: String,empty: Boolean=false): ConsultationController {
                lateinit var controller: ConsultationController
                runOnMainSync {
                    active?.dispose(); controller=ConsultationController(activity,definitions.single { it.name==route },vm)
                    activity.setContentView(controller.root); controller.submit(if(empty) "[]" else Gson().toJson(records[route])); active=controller
                }; waitForIdleSync(); return controller
            }
            fun text(controller: ConsultationController): String {
                var result=""; runOnMainSync { result=all(controller.root).filterIsInstance<TextView>().filter { it.isShown }.joinToString("\n") { it.text } }; return result
            }
            val finance=show("moras"); capture("moras")
            check(text(finance).contains("Alejandra") && text(finance).contains("1002") && text(finance).contains("20.25"))
            check(!text(finance).contains("991991") && !text(finance).contains("FacturaId") && !text(finance).contains("Crear"))
            val emptyMora=show("moras",true); check(text(emptyMora).contains("No hay facturas en mora")); capture("moras-empty")
            val notices=show("notificaciones"); check(text(notices).contains("Aviso persistido de prueba") && !text(notices).contains("Crear")); capture("notificaciones")
            runOnMainSync { vm.markNoticeRead("774774"); vm.markNoticeRead("774774") }
            repeat(100) { if(vm.noticeSaving.value!=null) Thread.sleep(50) }
            check(patches==1 && notice["EstadoNotificacion"].asBoolean)
            val emptyNotices=show("notificaciones",true); check(text(emptyNotices).contains("Sin notificaciones")); capture("notificaciones-empty")
            val timeline=show("auditoria"); capture("auditoria")
            check(text(timeline).contains("operador_registrado") && text(timeline).contains("UPDATE") && !text(timeline).contains("991991") && !text(timeline).contains("775775"))
            runOnMainSync { all(timeline.root).filterIsInstance<TextView>().first { it.text.toString()=="Filtrar actividad" }.performClick() }; capture("auditoria-filters")
            runOnMainSync { timeline.dispose() }
            runOnMainSync { all(timeline.root).filterIsInstance<com.google.android.material.card.MaterialCardView>().first().performClick() }; capture("auditoria-detail")
            val emptyAudit=show("auditoria",true); check(text(emptyAudit).contains("Sin actividad registrada")); capture("auditoria-empty")
            runBlocking {
                for(route in listOf("moras","auditoria")) {
                    check(runCatching { repository.save(route,null,JsonObject()) }.isFailure)
                    check(runCatching { repository.delete(route,"1") }.isFailure)
                }
                offline=true
                check(repository.cachedOptions("moras").size==1)
                check(runCatching { repository.refresh("moras") }.isFailure)
                check(repository.cachedOptions("moras").size==1)
                repository.store("schema",Gson().toJson(definitions.map { if(it.name=="auditoria") it.copy(permissions=it.permissions.copy(read=false)) else it }))
                check(runCatching { repository.cachedOptions("auditoria") }.isFailure)
                check(repository.observe("auditoria").first()==null)
                repository.store("schema",Gson().toJson(definitions))
                repository.store("identity","""{"user":{"id":2,"Rol":false}}"""); repository.restoreIdentity()
                check(runCatching { repository.cachedOptions("auditoria") }.isFailure)
                check(repository.observe("auditoria").first()==null)
            }
            runOnMainSync { emptyAudit.stateChanged() }; check(text(emptyAudit).contains("No tienes permiso"))
            check(writes==0)
            finish(-1,Bundle().apply { putBoolean("readonly_mutations_blocked",true); putBoolean("audit_employee_cache_blocked",true); putBoolean("audit_schema_cache_blocked",true); putBoolean("offline_cache_preserved",true); putInt("notification_read_patches",patches); putInt("event_creates_or_deletes",writes) })
        } catch(e: Throwable) { finish(0,Bundle().apply { putString("error",e.toString()); putString("stack",e.stackTraceToString().take(2500)) }) }
        finally {
            runOnMainSync { active?.dispose(); model?.let { MainViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible=true }.invoke(it) }; host?.finish() }
            session?.clear(); db?.close()
        }
    }
}
