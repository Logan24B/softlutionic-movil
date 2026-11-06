package com.softlutionic.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.*
import com.softlutionic.mobile.data.SessionManager
import com.softlutionic.mobile.data.local.CacheDatabase
import com.softlutionic.mobile.data.remote.Api
import com.softlutionic.mobile.data.repository.SoftlutionicRepository
import com.softlutionic.mobile.ui.invoices.*
import com.softlutionic.mobile.viewmodel.MainViewModel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

/** Isolated financial fixture: no production POST/PATCH/DELETE or financial cache entries. */
class InvoicesSmokeInstrumentation: Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun all(view: View): List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList())
    private fun capture(name: String) {
        Thread.sleep(350)
        val file=File(targetContext.getExternalFilesDir("verification"),"invoices-$name.png"); file.parentFile?.mkdirs()
        uiAutomation.takeScreenshot()?.let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
    }
    private fun text(): String {
        fun read(node: android.view.accessibility.AccessibilityNodeInfo): List<String> = listOfNotNull(node.text?.toString())+(0 until node.childCount).flatMap { node.getChild(it)?.let(::read).orEmpty() }
        return uiAutomation.rootInActiveWindow?.let { read(it).joinToString("\n") }.orEmpty()
    }
    override fun onStart() {
        var host: MainActivity?=null
        var database: CacheDatabase?=null
        var session: SessionManager?=null
        var restore: (() -> Unit)?=null
        try {
            fun json(value: String)=JsonParser.parseString(value).asJsonObject
            val contract=json("""{"id":901,"ClienteId":902,"TipoContratoId":903,"cliente_detalle":{"id":902,"Estado":true,"Cedula":"DOC-PRUEBA","Correo":"marina@example.test","Nombre":"Marina Alejandra","Apellido":"Pérez López"},"tipo_contrato_detalle":{"id":903,"Nombre":"Servicio mensual"},"Descripcion":"Asistencia empresarial","EstadoContrato":false}""")
            val services=listOf(json("""{"id":904,"Nombre_Servicio":"Mantenimiento preventivo","Descripcion":"Revisión de equipos y soporte empresarial.","Precio":850,"Duracion":"Una hora"}"""),json("""{"id":905,"Nombre_Servicio":"Soporte técnico","Descripcion":"Atención especializada.","Precio":1200,"Duracion":"Una hora"}"""))
            val state=json("""{"id":906,"estado":"Pendiente"}""")
            val remoteContracts=mutableListOf(contract)
            val immediate=json("""{"id":907,"Nombre":"Servicio unico","Descripcion":"Atenci\u00f3n inmediata"}""")
            val catalogs=mapOf("contratos" to remoteContracts,"clientes" to listOf(contract.getAsJsonObject("cliente_detalle")),"tipos-contrato" to listOf(contract.getAsJsonObject("tipo_contrato_detalle"),immediate),"servicios" to services,"estados-factura" to listOf(state))
            var previous: JsonObject?=null
            fun confirmed(payload: JsonObject,id: Int,total: String)=(previous?.deepCopy() ?: JsonObject()).apply {
                payload.entrySet().forEach { add(it.key,it.value.deepCopy()) }
                remove("lineas"); addProperty("id",id); addProperty("Monto_Total",total); addProperty("EstadoId",906)
                val bound=remoteContracts.single { it["id"]==get("ContratoId") }
                val type=bound.getAsJsonObject("tipo_contrato_detalle")
                addProperty("Fecha_Vencimiento",InvoiceTiming.dueDate(java.time.LocalDate.parse(get("Fecha_Emision").asString),type["Nombre"].asString).toString()); addProperty("Hora_Vencimiento","23:59:00")
                add("contrato_detalle",bound); add("estado_detalle",state)
                add("detalles",JsonArray().apply { payload["lineas"].asJsonArray.forEach { item -> add(item.asJsonObject.deepCopy().apply {
                    val service=services.single { it["id"]==get("ServicioId") }
                    if(!has("PrecioVenta")) add("PrecioVenta",service["Precio"])
                    addProperty("Subtotal",get("PrecioVenta").asInt*get("Cantidad").asInt)
                    addProperty("FacturaId",id); addProperty("id",990099); add("servicio_detalle",service)
                }) } })
            }
            val initialPayload=json("""{"ContratoId":901,"CodigoFact":777001,"Fecha_Emision":"2026-10-28","Hora_Emision":"09:30:00","lineas":[{"ServicioId":904,"Cantidad":2},{"ServicioId":905,"Cantidad":1}]}""")
            val remote=mutableListOf(confirmed(initialPayload,990100,"2900.00")); previous=remote.last()
            var mode="ok"; var contractPosts=0; var createdContract: JsonObject?=null; var postCount=0; var patchCount=0; var deleteCount=0; var received: JsonObject?=null
            val api=Proxy.newProxyInstance(Api::class.java.classLoader,arrayOf(Api::class.java)) { _,method,args -> when(method.name) {
                "list" -> JsonArray().apply { if(args!![0]=="facturas") remote.forEach { add(it) } else if(args[0]=="contratos") remoteContracts.forEach { add(it) } else catalogs[args[0]].orEmpty().forEach { add(it) } }
                "create","createInvoice","update" -> if(method.name=="create" && args!![0]=="contratos") {
                    check(method.name=="create"); contractPosts++
                    val value=(args[1] as JsonObject).deepCopy().apply {
                        addProperty("id",991); add("cliente_detalle",contract.getAsJsonObject("cliente_detalle"))
                        add("tipo_contrato_detalle",catalogs.getValue("tipos-contrato").single { it["id"]==get("TipoContratoId") })
                    }
                    createdContract=value.deepCopy(); remoteContracts.add(value); value
                } else {
                    if(method.name=="createInvoice") postCount++ else patchCount++
                    if(method.name=="update") check(args!![1]=="990101")
                    check(method.name=="createInvoice" || args!![0]=="facturas"); val data=args!![if(method.name=="createInvoice") 0 else 2] as JsonObject; received=data.deepCopy()
                    if(method.name=="update") check(!data.has("Fecha_Emision") && !data.has("Hora_Emision"))
                    check(!data.has("ClienteId") && !data.has("TipoContratoId"))
                    check(data.has("lineas")); check(!data.has("UsuarioId") && !data.has("EstadoId") && !data.has("Monto_Total"))
                    data["lineas"].asJsonArray.forEach { check(!it.asJsonObject.has("FacturaId")) }
                    @Suppress("UNCHECKED_CAST") val continuation=args.last() as Continuation<Any?>
                    val operationMode=mode
                    Handler(Looper.getMainLooper()).postDelayed({
                        when(operationMode) {
                            "offline" -> continuation.resumeWith(Result.failure(java.io.IOException("fixture offline")))
                            "badline" -> {
                                val error=retrofit2.Response.error<JsonObject>(400,"""{"lineas":[{"ServicioId":["Invalid pk 9999 - object does not exist."]}]}""".toResponseBody("application/json".toMediaType()))
                                continuation.resumeWith(if(method.name=="createInvoice") Result.success(error) else Result.failure(retrofit2.HttpException(error)))
                            }
                            else -> { val saved=confirmed(data,990101,"3010.00"); remote.removeAll { it["id"].asInt==990101 }; remote.add(saved); previous=saved; continuation.resumeWith(Result.success(if(method.name=="createInvoice") retrofit2.Response.success(201,saved) else saved)) }
                        }
                    },450)
                    COROUTINE_SUSPENDED
                }
                "delete" -> { deleteCount++; error("Delete must not be exposed") }
                else -> error("Unexpected fixture method ${method.name}")
            } } as Api
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            host=activity
            lateinit var vm: MainViewModel
            runOnMainSync { vm=ViewModelProvider(activity)[MainViewModel::class.java] }
            repeat(250) { if(vm.busy.value || vm.modules.value.none { it.name=="facturas" }) Thread.sleep(100) }
            val definitions=vm.modules.value; val invoiceModule=definitions.single { it.name=="facturas" }
            val context=object: ContextWrapper(targetContext) { override fun getSharedPreferences(name: String,mode: Int)=super.getSharedPreferences("invoice-financial-fixture-$name",mode) }
            val fixtureSession=SessionManager(context); session=fixtureSession; fixtureSession.save("fixture-access","fixture-refresh")
            val db=Room.inMemoryDatabaseBuilder(context,CacheDatabase::class.java).build(); database=db
            val repository=SoftlutionicRepository(api,fixtureSession,db.cache())
            runBlocking {
                repository.store("identity","""{"user":{"id":1,"Rol":true,"is_superuser":false}}"""); repository.restoreIdentity()
                repository.store("schema",Gson().toJson(definitions)); repository.store("facturas",Gson().toJson(remote))
            }
            var automaticTime=false; var automaticZone=true
            var instant=java.time.ZonedDateTime.of(2026,10,28,23,55,0,0,java.time.ZoneId.systemDefault()).toInstant()
            val timeField=MainViewModel::class.java.getDeclaredField("invoiceTimeSource").apply { isAccessible=true }
            val originalTime=timeField.get(vm)
            val source=object: InvoiceTimeSource {
                override fun status()=InvoiceClockStatus(automaticTime,automaticZone,true)
                override fun capture()=InvoiceTiming.capture(java.time.Clock.fixed(instant,java.time.ZoneId.systemDefault()))
            }
            timeField.set(vm,source)
            val field=MainViewModel::class.java.getDeclaredField("repository").apply { isAccessible=true }; val original=field.get(vm)
            restore={ timeField.set(vm,originalTime); field.set(vm,original); vm.invoiceDraft.value=null; vm.invoiceSaved.value=null; vm.invoiceCatalogs.value=emptyMap() }
            lateinit var controller: InvoicesController
            lateinit var sheets: InvoiceSheets
            runOnMainSync {
                field.set(vm,repository); vm.invoiceCatalogs.value=catalogs; vm.invoiceFilters.value=InvoiceFilters()
                MainActivity::class.java.getDeclaredMethod("navigateTo",String::class.java).apply { isAccessible=true }.invoke(activity,"facturas")
                controller=MainActivity::class.java.getDeclaredField("invoicesController").apply { isAccessible=true }.get(activity) as InvoicesController
                sheets=InvoicesController::class.java.getDeclaredField("sheets").apply { isAccessible=true }.get(controller) as InvoiceSheets
                controller.submit(Gson().toJson(remote))
            }
            waitForIdleSync(); capture("list")
            check(text().contains("777001") && text().contains("Marina")); check(!text().contains("990100"))
            runOnMainSync { vm.invoiceFilters.value=InvoiceFilters(query="perez"); controller.renderList() }
            waitForIdleSync(); Thread.sleep(200)
            check(text().contains("Marina"))
            runOnMainSync { vm.invoiceFilters.value=InvoiceFilters(query="990100"); controller.renderList() }
            waitForIdleSync(); Thread.sleep(200)
            check(text().contains("No hay coincidencias"))
            runOnMainSync { vm.invoiceFilters.value=InvoiceFilters(); controller.renderList(); sheets.filters(remote.map { InvoicePresentation.row(it,catalogs) }) }
            waitForIdleSync(); capture("filters")
            runOnMainSync {
                val active=InvoiceSheets::class.java.getDeclaredField("active").apply { isAccessible=true }.get(sheets) as BottomSheetDialog
                val hints=all(active.window!!.decorView).filterIsInstance<TextInputLayout>().map { it.hint.toString() }
                check(hints.containsAll(listOf("Estado","Cliente")))
            }
            runOnMainSync { sheets.dismiss() }
            runOnMainSync { vm.beginInvoice(); sheets.form() }
            fun dialog()=InvoiceSheets::class.java.getDeclaredField("active").apply { isAccessible=true }.get(sheets) as BottomSheetDialog
            fun input(hint: String,index: Int=0)=all(dialog().window!!.decorView).filterIsInstance<TextInputLayout>().filter { it.hint.toString().startsWith(hint) }[index].editText!!
            fun click(label: String) { runOnMainSync { all(dialog().window!!.decorView).filterIsInstance<TextView>().first { it.text.toString()==label }.performClick() }; waitForIdleSync() }
            fun chooseParty(hint: String,label: String,query: String?=null) {
                runOnMainSync { input(hint).performClick() }; waitForIdleSync()
                runOnMainSync {
                    val chooser=InvoiceSheets::class.java.getDeclaredField("chooser").apply { isAccessible=true }.get(sheets) as BottomSheetDialog
                    if(query!=null) all(chooser.window!!.decorView).filterIsInstance<TextInputLayout>().first().editText!!.setText(query)
                    var view: View=all(chooser.window!!.decorView).filterIsInstance<TextView>().first { it.text.toString()==label }
                    while(view !is MaterialCardView) view=view.parent as View
                    view.performClick()
                }; waitForIdleSync()
            }
            chooseParty("Cliente","Marina Alejandra Pérez López","marina@example.test")
            chooseParty("Tipo de contrato","Servicio mensual")
            check(vm.invoiceDraft.value!!.clientId=="902" && vm.invoiceDraft.value!!.typeId=="903")
            runOnMainSync {
                check(all(dialog().window!!.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("27 nov 2026") })
                check(all(dialog().window!!.decorView).filterIsInstance<TextInputLayout>().none { it.hint.toString().startsWith("Contrato") || it.hint.toString().startsWith("Fecha de emisión") || it.hint.toString().startsWith("Hora de emisión") })
            }
            chooseParty("Tipo de contrato","Servicio unico")
            runOnMainSync { check(all(dialog().window!!.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("28 oct 2026") && it.text.toString().contains("11:59") }) }
            chooseParty("Tipo de contrato","Servicio mensual")
            runOnMainSync { input("Código de factura").setText("777002"); vm.saveInvoice() }
            waitForIdleSync(); capture("clock-warning")
            check(postCount==0 && contractPosts==0)
            check(text().contains("Verifica la fecha y hora del dispositivo"))
            runOnMainSync {
                val warning=InvoiceSheets::class.java.getDeclaredField("clockDialog").apply { isAccessible=true }.get(sheets) as androidx.appcompat.app.AlertDialog
                check(warning.getButton(-1).text.toString().contains("configuración",true)); warning.getButton(-2).performClick()
            }
            waitForIdleSync()
            runOnMainSync { automaticTime=true; automaticZone=false; vm.saveInvoice() }
            waitForIdleSync(); check(text().contains("Verifica la fecha y hora del dispositivo")); check(postCount==0 && contractPosts==0)
            runOnMainSync {
                val warning=InvoiceSheets::class.java.getDeclaredField("clockDialog").apply { isAccessible=true }.get(sheets) as androidx.appcompat.app.AlertDialog
                warning.getButton(-2).performClick(); automaticZone=true
            }
            fun addService(name: String) {
                click("Agregar servicio")
                runOnMainSync {
                    val chooser=InvoiceSheets::class.java.getDeclaredField("chooser").apply { isAccessible=true }.get(sheets) as BottomSheetDialog
                    var view: View=all(chooser.window!!.decorView).filterIsInstance<TextView>().first { it.text.toString()==name }
                    while(view !is MaterialCardView) view=view.parent as View
                    view.performClick()
                }; waitForIdleSync()
            }
            addService("Mantenimiento preventivo"); runOnMainSync { input("Cantidad").setText("2") }; waitForIdleSync()
            addService("Soporte técnico"); capture("form-services")
            check(vm.invoiceDraft.value!!.lines.size==2)
            check(InvoicePresentation.total(vm.invoiceDraft.value!!,catalogs)==java.math.BigDecimal("2900"))
            runOnMainSync { all(dialog().window!!.decorView).first { it.contentDescription?.toString()=="Quitar Soporte técnico" }.performClick() }
            waitForIdleSync(); check(vm.invoiceDraft.value!!.lines.size==1)
            addService("Soporte técnico")
            runOnMainSync { input("Cantidad").setText("0") }; click("Generar factura")
            check(postCount==0 && vm.invoiceErrors.value.isNotEmpty())
            runOnMainSync { input("Cantidad").setText("2"); check(all(dialog().window!!.decorView).filterIsInstance<TextInputLayout>().none { it.hint.toString()=="Precio unitario" }) }
            waitForIdleSync()
            runOnMainSync { activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(dialog().window!!.decorView.windowToken,0); dialog().window!!.decorView.clearFocus() }
            waitForIdleSync()
            runOnMainSync { all(dialog().window!!.decorView).filterIsInstance<androidx.core.widget.NestedScrollView>().first().fullScroll(View.FOCUS_DOWN) }
            waitForIdleSync(); capture("form-summary")
            check(text().contains("TOTAL ESTIMADO") && text().contains("Generar factura"))
            runOnMainSync { activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(dialog().window!!.decorView.windowToken,0) }
            runOnMainSync {
                instant=instant.plusSeconds(600)
                vm.saveInvoice(); vm.saveInvoice()
            }
            check(vm.invoiceSaving.value)
            repeat(100) { if(vm.invoiceSaving.value) Thread.sleep(50) }
            waitForIdleSync(); runOnMainSync { controller.stateChanged() }; capture("confirmed-detail")
            check(postCount==1 && patchCount==0 && deleteCount==0 && contractPosts==1)
            check(received!!["ContratoId"].asInt==991)
            check(received!!["Fecha_Emision"].asString=="2026-10-29" && received!!["Hora_Emision"].asString=="00:05:00")
            check(createdContract!!["Fecha_Inc"].asString=="2026-10-29" && createdContract!!["Fecha_Fin"].asString=="2026-11-28")
            check(received!!["lineas"].asJsonArray.size()==2)
            check(!received!!["lineas"].asJsonArray[0].asJsonObject.has("PrecioVenta"))
            check(!received!!["lineas"].asJsonArray[1].asJsonObject.has("PrecioVenta"))
            check(text().contains("3,010.00")); check(!text().contains("990101")); check(!text().contains("Eliminar factura"))
            runOnMainSync { vm.beginInvoice(remote.last()); sheets.form() }
            waitForIdleSync(); capture("edit")
            check(vm.invoiceDraft.value!!.lines[0].price=="850")
            runOnMainSync { input("Cantidad").setText("3") }
            waitForIdleSync()
            runOnMainSync { check(all(dialog().window!!.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("3,750.00") }); input("Cantidad").setText("2") }
            runOnMainSync { vm.saveInvoice() }
            repeat(100) { if(vm.invoiceSaving.value) Thread.sleep(50) }
            check(patchCount==1 && postCount==1 && contractPosts==1)
            val before=runBlocking { repository.cached("facturas") }
            val valid=InvoicePresentation.draft(remote.last(),invoiceModule,catalogs).copy(id=null)
            for((failureIndex,failure) in listOf("offline","badline").withIndex()) {
                runBlocking { repository.store("invoice-pending","null") }
                runOnMainSync { mode=failure; vm.invoiceDraft.value=valid.copy(values=valid.values+("CodigoFact" to (777003+failureIndex).toString())); vm.invoiceSaved.value=null; sheets.form(); vm.saveInvoice() }
                repeat(100) { if(vm.invoiceSaving.value) Thread.sleep(50) }
                check(vm.invoiceSaved.value==null && vm.invoiceErrors.value.isNotEmpty())
                check(runBlocking { repository.cached("facturas") }==before)
                waitForIdleSync(); capture("error-$failure")
                runOnMainSync {
                    val shown=all(dialog().window!!.decorView).filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }
                    check(shown.contains(if(failure=="offline") "No se recibió confirmación" else "La opción seleccionada no está disponible"))
                    val error=all(dialog().window!!.decorView).filterIsInstance<TextView>().first { it.text.toString().contains(if(failure=="offline") "No se recibió confirmación" else "La opción seleccionada no está disponible") }
                    check(error.getGlobalVisibleRect(android.graphics.Rect())) { "Financial error must be visible after request completion" }
                }
            }
            check(remote.size==2)
            finish(-1,Bundle().apply {
                putString("result","Customer/type selection, automatic clock guards, midnight capture, web contract create/reuse, atomic invoice POST/PATCH, financial safety passed")
                putInt("prepared_contract_posts",contractPosts); putInt("confirmed_create_calls",1); putInt("independent_detail_posts",0)
            })
        } catch(error: Throwable) { finish(0,Bundle().apply { putString("error",error.toString()); putString("trace",error.stackTraceToString().take(1800)) }) }
        finally {
            runOnMainSync { restore?.invoke(); host?.finish() }; waitForIdleSync(); session?.clear(); database?.close()
        }
    }
}
