package com.softlutionic.mobile

import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import com.google.gson.JsonParser
import com.softlutionic.mobile.data.mapper.toRecord
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.databinding.ViewServicesBinding
import com.softlutionic.mobile.ui.services.*
import com.softlutionic.mobile.viewmodel.MainViewModel
import java.io.File

/** Isolated fixtures only. Does not send valid POST/PATCH/DELETE requests. */
class ServicesSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private var root: View? = null
    override fun onStart() {
        var host: MainActivity? = null
        var controller: ServicesController? = null
        var sheets: ServiceSheets? = null
        try {
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            host = activity; root = activity.window.decorView
            val fixture = """[{"id":8,"Nombre_Servicio":"Mantenimiento de hardware","Descripcion":"Limpieza completa de las partes físicas del equipo, revisión de componentes y diagnóstico. Esta descripción comprueba que el listado limita sus líneas y el detalle conserva la información completa.","Precio":1200,"Duracion":"Según alcance acordado"},{"id":9,"Nombre_Servicio":"Soporte remoto","Descripcion":"Asistencia técnica y configuración de software.","Precio":850,"Duracion":"45 minutos"}]"""
            val records = JsonParser.parseString(fixture).asJsonArray.map { it.asJsonObject.toRecord() }
            val fields = listOf(Field("Nombre_Servicio", "Nombre_Servicio", true, "CharField", null, 50),
                Field("Descripcion", "Descripcion", true, "CharField", null, 200),
                Field("Precio", "Precio", true, "IntegerField", null, null), Field("Duracion", "Duracion", true, "CharField", null, 50),
                Field("id", "id", false, "IntegerField", null, null, true))
            var module = Module("servicios", Capability(true,true,true,true), fields)
            lateinit var vm: MainViewModel
            lateinit var b: ViewServicesBinding
            runOnMainSync { vm = ViewModelProvider(activity)[MainViewModel::class.java] }
            kotlinx.coroutines.runBlocking { vm.load() }
            runOnMainSync {
                vm.modules.value = listOf(module)
                b = ViewServicesBinding.inflate(activity.layoutInflater)
                activity.setContentView(b.root)
                controller = ServicesController(b, module, vm); controller!!.submit(fixture)
                sheets = ServiceSheets(activity, vm, { module }, {})
            }
            waitForIdleSync(); capture("services-list-test-fixture.png")
            check(text().contains("C$1,200")); check(!text().contains("Nombre_Servicio"))
            runOnMainSync { b.search.setText("configuracion"); controller!!.renderList() }
            waitForIdleSync(); check(b.records.adapter!!.itemCount == 1)
            runOnMainSync { b.search.setText(""); vm.serviceListState.value = ServiceListState(); controller!!.renderList(); b.sort.performClick() }
            waitForIdleSync(); capture("services-sort-test-fixture.png")
            click(getString(R.string.services_sort_price_asc))
            runOnMainSync { controller!!.renderList() }
            // ListAdapter computes diffs off the UI thread; idle UI alone is insufficient.
            repeat(20) {
                var sorted=false
                runOnMainSync { sorted=(b.records.adapter as ServiceAdapter).currentList.firstOrNull()?.id=="9" }
                if(!sorted) Thread.sleep(100)
            }
            waitForIdleSync(); check((b.records.adapter as ServiceAdapter).currentList.first().id == "9")
            runOnMainSync { b.create.performClick() }
            waitForIdleSync()
            check(!text().contains(getString(R.string.services_system)))
            click(getString(R.string.services_save))
            check(!vm.serviceSaving.value)
            check(text().contains(getString(R.string.services_name_required)))
            capture("services-form-validation-test-fixture.png")
            uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(getString(R.string.services_name))
                .firstOrNull { it.isEditable }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitForIdleSync(); capture("services-form-focus-test-fixture.png")
            check(uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(getString(R.string.services_save)).any { it.isVisibleToUser })
            click(getString(R.string.cancel))
            runOnMainSync { sheets!!.detail(records.first()) }
            waitForIdleSync(); capture("services-detail-test-fixture.png")
            check(text().contains("Según alcance acordado"))
            check(text().contains("el detalle conserva la información completa."))
            click(getString(R.string.services_edit))
            waitForIdleSync(); capture("services-edit-test-fixture.png")
            check(text().contains("Mantenimiento de hardware")); check(text().contains("1200"))
            click(getString(R.string.cancel))
            runOnMainSync {
                module = module.copy(permissions = Capability(true,false,false,false)); vm.modules.value = listOf(module)
                controller!!.busyChanged(); check(b.create.visibility == View.GONE); sheets!!.detail(records.first())
            }
            waitForIdleSync(); check(!text().contains(getString(R.string.services_edit))); check(!text().contains(getString(R.string.services_delete)))
            runOnMainSync {
                sheets!!.dismiss(); controller!!.dispose()
                val config = Configuration(activity.resources.configuration).apply { screenWidthDp = 320; fontScale = 1.4f }
                val ctx = ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_Softlutionic)
                val narrow = ViewServicesBinding.inflate(LayoutInflater.from(ctx))
                val frame = FrameLayout(activity)
                frame.addView(narrow.root, FrameLayout.LayoutParams((320 * ctx.resources.displayMetrics.density).toInt(), -1, Gravity.CENTER_HORIZONTAL))
                activity.setContentView(frame)
                controller = ServicesController(narrow, module, vm)
                controller!!.submit(fixture.replace("1200", "2147483647"))
                b = narrow
            }
            waitForIdleSync(); capture("services-narrow-test-fixture.png")
            check(text().contains("C$2,147,483,647"))
            runOnMainSync { controller!!.submit("[]") }
            waitForIdleSync(); check(text().contains(getString(R.string.services_empty_title)))
            finish(-1, Bundle().apply { putString("result", "Services: list, search, sort, required validation, detail, edit prefill, permissions, empty and 320dp/1.4 text passed; no writes") })
        } catch(error: Throwable) {
            finish(0, Bundle().apply { putString("error", error.stackTraceToString()) })
        } finally { host?.let { activity -> runOnMainSync { sheets?.dismiss(); controller?.dispose(); activity.finish() } } }
    }
    private fun getString(id: Int) = targetContext.getString(id)
    private fun text(): String {
        fun collect(node: AccessibilityNodeInfo?): List<String> = if(node == null) emptyList() else
            listOfNotNull(node.text?.toString(), node.error?.toString(), node.hintText?.toString()) + (0 until node.childCount).flatMap { collect(node.getChild(it)) }
        return collect(uiAutomation.rootInActiveWindow).joinToString("\n")
    }
    private fun click(value: String) {
        var node = uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(value).firstOrNull { it.text?.toString()?.equals(value,true) == true }
            ?: error("Missing action $value: ${text()}")
        while(!node.isClickable && node.parent != null) node = node.parent
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)); waitForIdleSync()
    }
    private fun capture(name: String) {
        val stable = java.util.concurrent.CountDownLatch(1)
        runOnMainSync { root?.postOnAnimation { root?.postOnAnimation { stable.countDown() } } }
        stable.await(2, java.util.concurrent.TimeUnit.SECONDS)
        android.os.SystemClock.sleep(350)
        uiAutomation.takeScreenshot()?.let { bitmap ->
            File(targetContext.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        }
    }
}
