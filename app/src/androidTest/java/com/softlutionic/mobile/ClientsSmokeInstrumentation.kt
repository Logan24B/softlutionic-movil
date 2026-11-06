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
import com.softlutionic.mobile.databinding.ViewClientsBinding
import com.softlutionic.mobile.ui.clients.*
import com.softlutionic.mobile.viewmodel.MainViewModel
import java.io.File

/** Fixtures stay in androidTest. This runner never submits a valid POST/PATCH/DELETE. */
class ClientsSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        var activity: MainActivity? = null
        var controller: ClientsController? = null
        var sheets: ClientSheets? = null
        try {
            val host = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = host
            root = host.window.decorView
            val fixture = """[{"id":8,"Nombre":"Carlos","Apellido":"Martínez López","Cedula":"001-010190-0001A","Telefono":"+505 8888 1234","Correo":"cliente.prueba@example.com","Direccion":"Dirección de prueba","Estado":true,"DepartamentoId":10,"departamento_detalle":{"id":10,"Nombre":"Managua"},"FechaRegistro":"2026-10-24","HoraRegistro":"10:35:00"},{"id":9,"Nombre":"Ana","Apellido":"Gómez","Telefono":"+505 7777 4321","Correo":"ana.prueba@example.com","Estado":false,"DepartamentoId":11,"departamento_detalle":{"id":11,"Nombre":"Matagalpa"}}]"""
            val records = JsonParser.parseString(fixture).asJsonArray.map { it.asJsonObject.toRecord() }
            val fields = ClientPresentation.editableNames.map { name -> Field(name, name, name != "Estado", if(name == "Estado") "BooleanField" else "CharField", if(name == "DepartamentoId") "departamentos" else null, 100) } +
                listOf("id", "FechaRegistro", "HoraRegistro").map { Field(it, it, false, "CharField", null, null, true) }
            var module = Module("clientes", Capability(true, true, true, true), fields)
            lateinit var vm: MainViewModel
            lateinit var b: ViewClientsBinding
            runOnMainSync {
                vm = ViewModelProvider(host)[MainViewModel::class.java]
                vm.modules.value = listOf(module)
                vm.clientDepartments.value = listOf("""{"id":10,"Nombre":"Managua"}""", """{"id":11,"Nombre":"Matagalpa"}""").map { JsonParser.parseString(it).asJsonObject }
                b = ViewClientsBinding.inflate(host.layoutInflater)
                host.setContentView(b.root)
                controller = ClientsController(b, module, vm)
                controller!!.submit(fixture)
                sheets = ClientSheets(host, vm, { module }, { ClientPresentation.departments(vm.clientDepartments.value, records) }, {})
            }
            waitForIdleSync()
            capture("clients-list-test-fixture.png")
            check(windowText().contains("Carlos Martínez López"))
            check(!windowText().contains("DepartamentoId"))
            runOnMainSync { b.search.setText("martinez"); controller!!.renderList() }
            waitForIdleSync()
            check(b.records.adapter!!.itemCount == 1)
            runOnMainSync { vm.clientFilters.value = ClientFilters(status = ClientStatusFilter.INACTIVE, departmentId = "11"); controller!!.renderList() }
            waitForIdleSync()
            check(b.records.adapter!!.itemCount == 1)
            runOnMainSync { vm.clientFilters.value = ClientFilters(); b.search.setText(""); controller!!.renderList(); b.filters.performClick() }
            waitForIdleSync()
            capture("clients-filters-test-fixture.png")
            clickText(getString(R.string.cancel))
            runOnMainSync { b.create.performClick() }
            waitForIdleSync()
            check(windowText().contains(getString(R.string.clients_new_title)))
            check(!windowText().contains(getString(R.string.clients_registered_date)))
            clickText(getString(R.string.clients_save))
            waitForIdleSync()
            check(!vm.clientSaving.value) // Required-field validation prevented a network write.
            check(windowText().contains(getString(R.string.clients_required)))
            capture("clients-form-validation-test-fixture.png")
            clickText(getString(R.string.cancel))
            runOnMainSync { sheets!!.detail(ClientPresentation.row(records.first(), ClientPresentation.departments(vm.clientDepartments.value, records))) }
            waitForIdleSync()
            capture("clients-detail-test-fixture.png")
            clickText(getString(R.string.clients_edit))
            waitForIdleSync()
            check(windowText().contains("Carlos"))
            check(windowText().contains("Martínez López"))
            capture("clients-edit-test-fixture.png")
            clickText(getString(R.string.cancel))
            runOnMainSync {
                module = module.copy(permissions = Capability(true, false, false, false))
                vm.modules.value = listOf(module)
                controller!!.busyChanged()
                check(b.create.visibility == View.GONE)
                sheets!!.detail(ClientPresentation.row(records.first(), emptyList()))
            }
            waitForIdleSync()
            check(!windowText().contains(getString(R.string.clients_edit)))
            check(!windowText().contains(getString(R.string.clients_delete)))
            runOnMainSync {
                sheets!!.dismiss(); controller!!.dispose()
                val config = Configuration(host.resources.configuration).apply { screenWidthDp = 320; fontScale = 1.4f }
                val ctx = ContextThemeWrapper(host.createConfigurationContext(config), R.style.Theme_Softlutionic)
                val narrow = ViewClientsBinding.inflate(LayoutInflater.from(ctx))
                val frame = FrameLayout(host)
                frame.addView(narrow.root, FrameLayout.LayoutParams((320 * ctx.resources.displayMetrics.density).toInt(), -1, Gravity.CENTER_HORIZONTAL))
                host.setContentView(frame)
                controller = ClientsController(narrow, module, vm)
                controller!!.submit(fixture)
            }
            waitForIdleSync()
            capture("clients-narrow-test-fixture.png")
            runOnMainSync { controller!!.submit("[]") }
            waitForIdleSync()
            check(windowText().contains(getString(R.string.clients_empty_title)))
            finish(-1, Bundle().apply { putString("result", "Clients: cards, local search, filters, validation, prefill, detail, permissions, empty and narrow/large text passed; no writes") })
        } catch(error: Throwable) {
            finish(0, Bundle().apply { putString("error", error.stackTraceToString()) })
        } finally {
            activity?.let { host -> runOnMainSync { sheets?.dismiss(); controller?.dispose(); host.finish() } }
        }
    }
    private fun getString(id: Int) = targetContext.getString(id)
    private fun windowText(): String {
        fun all(node: AccessibilityNodeInfo?): List<String> {
            if(node == null) return emptyList()
            return listOfNotNull(node.text?.toString(), node.error?.toString(), node.hintText?.toString()) + (0 until node.childCount).flatMap { all(node.getChild(it)) }
        }
        return all(uiAutomation.rootInActiveWindow).joinToString("\n")
    }
    private fun clickText(text: String) {
        val node = uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(text).firstOrNull { it.text?.toString()?.equals(text, true) == true }
            ?: error("Missing action: $text; ${windowText()}")
        var clickable = node
        while(!clickable.isClickable && clickable.parent != null) clickable = clickable.parent
        check(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitForIdleSync()
    }
    private fun capture(name: String) {
        val stable = java.util.concurrent.CountDownLatch(1)
        runOnMainSync { activityRoot()?.postOnAnimation { activityRoot()?.postOnAnimation { stable.countDown() } } }
        stable.await(2, java.util.concurrent.TimeUnit.SECONDS)
        android.os.SystemClock.sleep(300) // Let RecyclerView and Material sheet transitions settle.
        uiAutomation.takeScreenshot()?.let { bitmap ->
            File(targetContext.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private var root: View? = null
    private fun activityRoot(): View? = root
}
