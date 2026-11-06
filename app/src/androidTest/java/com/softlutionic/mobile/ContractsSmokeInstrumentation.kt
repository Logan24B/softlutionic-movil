package com.softlutionic.mobile

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.google.gson.JsonParser
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.ui.contracts.*
import com.softlutionic.mobile.viewmodel.MainViewModel
import java.io.File

/** Synthetic UI fixtures. No save/delete request is submitted to the backend. */
class ContractsSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun texts(view: View): List<String> = (if(view is TextView && view.visibility==View.VISIBLE) listOf(view.text.toString()) else emptyList()) +
        (if(view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList())
    private fun screen() = uiAutomation.rootInActiveWindow?.let { root ->
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo): List<String> =
            listOfNotNull(node.text?.toString()) + (0 until node.childCount).flatMap { index -> node.getChild(index)?.let(::visit).orEmpty() }
        visit(root).joinToString("\n")
    }.orEmpty()
    private fun capture(name: String) {
        Thread.sleep(300) // Finish the Material sheet transition before visual capture.
        val file=File(targetContext.getExternalFilesDir("verification"),"contracts-$name.png")
        file.parentFile?.mkdirs()
        uiAutomation.takeScreenshot()?.let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
    }
    override fun onStart() {
        var host: MainActivity?=null
        var originalConfiguration: android.content.res.Configuration?=null
        var controller: ContractsController?=null
        try {
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            host=activity
            originalConfiguration=android.content.res.Configuration(activity.resources.configuration)
            lateinit var vm: MainViewModel
            runOnMainSync { vm=ViewModelProvider(activity)[MainViewModel::class.java] }
            // Use fields already obtained from the authenticated schema; no fabricated production schema.
            repeat(150) { if(vm.modules.value.none { it.name=="contratos" }) Thread.sleep(100) }
            val original=vm.modules.value.single { it.name=="contratos" }
            val writable=original.copy(permissions=Capability(true,true,true,true))
            val fixture="""[{"id":900,"ClienteId":901,"TipoContratoId":902,"Fecha_Inc":"2026-10-01","Fecha_Fin":"2026-12-31","EstadoContrato":true,"Descripcion":"Prestación de soporte y mantenimiento para instalaciones empresariales. Esta descripción extensa comprueba que la tarjeta limita sus líneas y el detalle conserva el contenido completo, sin modificar lo almacenado.","cliente_detalle":{"id":901,"Nombre":"Carlos Alejandro","Apellido":"Martínez López de la Fuente"},"tipo_contrato_detalle":{"id":902,"Nombre":"Servicio puntual de mantenimiento y asistencia empresarial"}}]"""
            val record=JsonParser.parseString(fixture).asJsonArray[0].asJsonObject
            runOnMainSync {
                vm.modules.value=listOf(writable)
                vm.contractCatalogs.value=mapOf("clientes" to listOf(record.getAsJsonObject("cliente_detalle")),"tipos-contrato" to listOf(record.getAsJsonObject("tipo_contrato_detalle")))
                vm.contractFilters.value=ContractFilters()
                controller=ContractsController(activity,writable,vm)
                activity.setContentView(controller!!.root); controller!!.submit(fixture)
            }
            waitForIdleSync(); capture("list")
            val list=texts(controller!!.root).joinToString("\n")
            check(list.contains("Carlos Alejandro")); check(list.contains("Servicio puntual"))
            check(!list.contains("ClienteId")); check(!list.contains("TipoContratoId"))
            check(list.contains("Nuevo contrato") && list.contains("Actualizar"))
            runOnMainSync { vm.contractFilters.value=ContractFilters(query="martinez"); controller!!.renderList() }
            check(texts(controller!!.root).contains("1 de 1 contratos"))
            runOnMainSync { vm.contractFilters.value=ContractFilters(typeId="999"); controller!!.renderList() }
            check(texts(controller!!.root).contains("0 de 1 contratos"))
            runOnMainSync { vm.contractFilters.value=ContractFilters(); controller!!.renderList(); controller!!.detail(record) }
            waitForIdleSync(); capture("detail")
            check(screen().contains("DETALLE DEL CONTRATO")); check(!screen().contains("ClienteId"))
            runOnMainSync { controller!!.form(null) }
            waitForIdleSync(); capture("create")
            check(screen().contains("Nuevo contrato")); check(!screen().contains("ClienteId"))
            // Trigger validation only: required blank fields must prevent any POST.
            runOnMainSync {
                fun find(view: View): TextView? {
                    if(view is TextView && view.text=="Guardar contrato") return view
                    return if(view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
                }
                val dialogField=ContractsController::class.java.getDeclaredField("sheet").apply { isAccessible=true }
                val dialog=dialogField.get(controller) as com.google.android.material.bottomsheet.BottomSheetDialog
                find(dialog.window!!.decorView)?.performClick()
            }
            waitForIdleSync(); check(screen().contains("Selecciona un cliente."))
            runOnMainSync { controller!!.form(record) }
            waitForIdleSync(); capture("edit")
            check(screen().contains("Editar contrato")); check(screen().contains("Carlos Alejandro"))
            runOnMainSync {
                controller!!.dispose()
                vm.modules.value=listOf(original.copy(permissions=Capability(true,false,false,false)))
                controller=ContractsController(activity,vm.modules.value.single(),vm)
                activity.setContentView(controller!!.root); controller!!.submit(fixture)
            }
            check(texts(controller!!.root).none { it=="Nuevo contrato" })
            runOnMainSync { controller!!.detail(record) }
            waitForIdleSync()
            check(!screen().contains("Editar contrato")); check(!screen().contains("Eliminar contrato"))
            capture("read-only")
            runOnMainSync {
                controller!!.dispose()
                val configuration=android.content.res.Configuration(activity.resources.configuration).apply { fontScale=1.3f }
                @Suppress("DEPRECATION")
                activity.resources.updateConfiguration(configuration,activity.resources.displayMetrics)
                vm.modules.value=listOf(writable)
                controller=ContractsController(activity,writable,vm)
                activity.setContentView(controller!!.root)
                (controller!!.root.parent as View).setBackgroundColor(android.graphics.Color.rgb(9,14,32))
                controller!!.root.layoutParams=controller!!.root.layoutParams.apply { width=(320*activity.resources.displayMetrics.density).toInt() }
                controller!!.submit(fixture)
            }
            waitForIdleSync(); capture("narrow-large-text")
            check(texts(controller!!.root).joinToString("\n").contains("Carlos Alejandro"))
            runOnMainSync { controller!!.form(record) }
            waitForIdleSync()
            runOnMainSync {
                val dialogField=ContractsController::class.java.getDeclaredField("sheet").apply { isAccessible=true }
                val dialog=dialogField.get(controller) as com.google.android.material.bottomsheet.BottomSheetDialog
                fun edits(view: View): List<android.widget.EditText> = (if(view is android.widget.EditText) listOf(view) else emptyList()) +
                    (if(view is ViewGroup) (0 until view.childCount).flatMap { edits(view.getChildAt(it)) } else emptyList())
                val description=edits(dialog.window!!.decorView).last()
                description.requestFocus()
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(description,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            waitForIdleSync()
            runOnMainSync {
                val dialogField=ContractsController::class.java.getDeclaredField("sheet").apply { isAccessible=true }
                val dialog=dialogField.get(controller) as com.google.android.material.bottomsheet.BottomSheetDialog
                fun scroll(view: View): androidx.core.widget.NestedScrollView? = if(view is androidx.core.widget.NestedScrollView) view else
                    if(view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { scroll(view.getChildAt(it)) } else null
                scroll(dialog.window!!.decorView)?.fullScroll(View.FOCUS_DOWN)
            }
            waitForIdleSync(); capture("keyboard-large-text")
            check(screen().contains("Guardar contrato"))
            finish(-1,Bundle().apply { putString("result","List, relations, search, filters, detail, create validation, edit and schema permissions passed") })
        } catch(error: Throwable) {
            finish(0,Bundle().apply { putString("error",error.toString()) })
        } finally {
            runOnMainSync {
                controller?.dispose()
                host?.let { activity ->
                    @Suppress("DEPRECATION")
                    activity.resources.updateConfiguration(originalConfiguration,activity.resources.displayMetrics)
                    activity.finish()
                }
            }
        }
    }
}
