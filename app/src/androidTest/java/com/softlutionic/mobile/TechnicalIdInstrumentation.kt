package com.softlutionic.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.room.Room
import com.google.gson.*
import com.softlutionic.mobile.data.SessionManager
import com.softlutionic.mobile.data.local.CacheDatabase
import com.softlutionic.mobile.data.mapper.toRecord
import com.softlutionic.mobile.data.remote.Api
import com.softlutionic.mobile.data.repository.SoftlutionicRepository
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy

/** Fixture-only API and Room. Verifies mapping never changes write keys or foreign keys. */
class TechnicalIdInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val context=object: ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences("technical-id-fixture-$name",mode)
        }
        val session=SessionManager(context)
        val db=Room.inMemoryDatabaseBuilder(context,CacheDatabase::class.java).build()
        try {
            var created=false; var updated=false; var deleted=false
            val payload=JsonParser.parseString("""{"ClienteId":88102,"TipoContratoId":88103,"Descripcion":"Acuerdo de soporte"}""").asJsonObject
            val saved=payload.deepCopy().apply { addProperty("id",88101) }
            val api=Proxy.newProxyInstance(Api::class.java.classLoader,arrayOf(Api::class.java)) { _,method,args ->
                when(method.name) {
                    "create" -> { check(args!![0]=="contratos"); check((args[1] as JsonObject)["ClienteId"].asInt==88102); created=true; saved }
                    "update" -> { check(args!![1]=="88101"); check((args[2] as JsonObject)["TipoContratoId"].asInt==88103); updated=true; saved }
                    "delete" -> { check(args!![1]=="88101"); deleted=true; Unit }
                    "list" -> JsonArray().apply { if(!deleted) add(saved) }
                    else -> error("Unexpected API call: ${method.name}")
                }
            } as Api
            runBlocking {
                session.save("fixture-access","fixture-refresh")
                val repository=SoftlutionicRepository(api,session,db.cache())
                repository.store("identity","""{"user":{"id":88000,"Rol":true,"is_superuser":false}}""")
                repository.store("schema","""[{"name":"contratos","permissions":{"read":true,"create":true,"update":true,"delete":true},"fields":[]}]""")
                repository.restoreIdentity()
                repository.save("contratos",null,payload)
                val row=JsonParser.parseString(repository.cached("contratos")).asJsonArray.single().asJsonObject.toRecord()
                check(row.id=="88101"); check(row.payload["ClienteId"].asInt==88102)
                check(!row.detail.contains("88101") && !row.detail.contains("88102") && !row.title.contains("88101"))
                repository.save("contratos",row.id,row.payload.deepCopy().apply { remove("id") })
                repository.delete("contratos",row.id)
                check(created && updated && deleted)
                check(JsonParser.parseString(repository.cached("contratos")).asJsonArray.size()==0)
            }
            verifyUi()
            finish(-1,Bundle().apply { putString("result","CRUD keys preserved in fixture API; generic and specialized visible details contain no technical IDs") })
        } catch(error: Throwable) { finish(0,Bundle().apply { putString("error",error.toString()) }) }
        finally { session.clear(); db.close() }
    }
    private fun verifyUi() {
        val activity=startActivitySync(android.content.Intent(targetContext,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var closeSheet: (() -> Unit)?=null
        try {
            lateinit var vm: com.softlutionic.mobile.viewmodel.MainViewModel
            runOnMainSync { vm=androidx.lifecycle.ViewModelProvider(activity)[com.softlutionic.mobile.viewmodel.MainViewModel::class.java] }
            repeat(200) { if(vm.busy.value || vm.modules.value.isEmpty()) Thread.sleep(100) }
            fun json(value: String)=JsonParser.parseString(value).asJsonObject
            val client=json("""{"id":88770001,"Nombre":"Ana","Apellido":"Pérez","DepartamentoId":88770002,"departamento_detalle":{"id":88770002,"Nombre":"Managua"}}""")
            val service=json("""{"id":88770003,"Nombre_Servicio":"Soporte técnico","Descripcion":"Mantenimiento empresarial","Precio":150,"Duracion":"Una hora"}""")
            val kind=json("""{"id":88770004,"Nombre":"Mensual"}""")
            val contract=json("""{"id":88770005,"ClienteId":88770001,"TipoContratoId":88770004,"Descripcion":"Acuerdo de soporte"}""")
            val invoice=json("""{"id":88770006,"CodigoFact":"FAC-COMERCIAL","ClienteId":88770001,"cliente_detalle":{"id":88770001,"Nombre":"Ana","Apellido":"Pérez"}}""")
            fun screen(): String {
                fun visit(node: android.view.accessibility.AccessibilityNodeInfo): List<String> = listOfNotNull(node.text?.toString())+
                    (0 until node.childCount).flatMap { node.getChild(it)?.let(::visit).orEmpty() }
                return uiAutomation.rootInActiveWindow?.let { visit(it).joinToString("\n") }.orEmpty()
            }
            fun checkScreen() {
                waitForIdleSync()
                val text=screen()
                check(!text.contains("887700")) { "Technical PK leaked into UI" }
                listOf("ClienteId","DepartamentoId","ServicioId","FacturaId","TipoContratoId","IdLog:","ID:","Contrato #").forEach { check(!text.contains(it)) { "Technical label $it leaked" } }
            }
            fun capture(name: String) {
                Thread.sleep(300)
                val file=java.io.File(targetContext.getExternalFilesDir("verification"),"technical-ids-$name.png")
                file.parentFile?.mkdirs()
                uiAutomation.takeScreenshot()?.let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
            }
            val catalogs=mapOf("clientes" to listOf(client),"servicios" to listOf(service),"tipos-contrato" to listOf(kind),"facturas" to listOf(invoice))
            val generic=listOf("facturas" to invoice,"pagos" to json("""{"id":88770007,"NumeroRecibo":"REC-COMERCIAL","FacturaId":88770006}"""),
                "moras" to json("""{"id":88770008,"FacturaId":88770006,"Descripcion":"Saldo vencido"}"""),
                "notificaciones" to json("""{"id":88770009,"ClienteId":88770001,"Mensaje":"Aviso de vencimiento"}"""),
                "auditoria" to json("""{"IdLog":88770010,"Tabla":"Facturas","Accion":"UPDATE"}"""),
                "estados-factura" to json("""{"id":88770011,"estado":"Pendiente"}"""))
            runOnMainSync {
                activity.setContentView(android.widget.FrameLayout(activity).apply { setBackgroundColor(android.graphics.Color.rgb(9,14,32)) })
            }
            for((name,data) in generic) {
                val definition=vm.modules.value.find { it.name==name } ?: continue
                runOnMainSync {
                    MainActivity::class.java.getDeclaredField("module").apply { isAccessible=true }.set(activity,definition)
                    MainActivity::class.java.getDeclaredMethod("detail",com.softlutionic.mobile.domain.model.Record::class.java).apply { isAccessible=true }
                        .invoke(activity,data.toRecord(catalogs))
                }
                checkScreen()
                if(name=="facturas") { check(screen().contains("FAC-COMERCIAL")); capture("invoice-detail") }
                uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Cerrar").first().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                waitForIdleSync()
            }
            runOnMainSync {
                val sheet=com.softlutionic.mobile.ui.clients.ClientSheets(activity,vm,{ vm.modules.value.single { it.name=="clientes" } },{ listOf(com.softlutionic.mobile.ui.clients.DepartmentOption("88770002","Managua")) },{})
                sheet.detail(com.softlutionic.mobile.ui.clients.ClientPresentation.row(client.toRecord(),listOf(com.softlutionic.mobile.ui.clients.DepartmentOption("88770002","Managua"))))
                closeSheet={ sheet.dismiss() }
            }
            checkScreen(); capture("client-detail"); runOnMainSync { closeSheet?.invoke() }
            runOnMainSync {
                val sheet=com.softlutionic.mobile.ui.services.ServiceSheets(activity,vm,{ vm.modules.value.single { it.name=="servicios" } },{})
                sheet.detail(service.toRecord()); closeSheet={ sheet.dismiss() }
            }
            checkScreen(); capture("service-detail"); runOnMainSync { closeSheet?.invoke() }
            runOnMainSync {
                vm.contractCatalogs.value=catalogs
                val controller=com.softlutionic.mobile.ui.contracts.ContractsController(activity,vm.modules.value.single { it.name=="contratos" },vm)
                activity.setContentView(controller.root); controller.submit(JsonArray().apply { add(contract) }.toString())
                controller.detail(contract); closeSheet={ controller.dispose() }
            }
            checkScreen(); check(screen().contains("Ana Pérez")); check(screen().contains("Mensual")); capture("contract-detail")
        } finally { runOnMainSync { closeSheet?.invoke(); activity.finish() } }
    }
}
