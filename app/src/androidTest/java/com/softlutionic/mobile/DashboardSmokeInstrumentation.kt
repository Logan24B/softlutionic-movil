package com.softlutionic.mobile

import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.*
import android.widget.TextView
import com.softlutionic.mobile.databinding.*
import com.softlutionic.mobile.ui.*
import java.io.File

/** Isolated renderer smoke check. Fixtures exist only in the test APK, never in the app. */
class DashboardSmokeInstrumentation: Instrumentation() {
 override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
 override fun onStart() {
  var activity: android.app.Activity?=null
  try {
   activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   waitForIdleSync()
   val host=activity
   val fixture=DashboardData.parse("""{"facturacion":{"cards":{"total_facturado":5868,"total_pagado":1466,"saldo_pendiente":4402,"total_mora":0},"charts":{"facturado_por_mes":[{"label":"2026-10","value":5868}],"facturas_por_estado":[{"label":"Pagada","value":1},{"label":"Pendiente","value":1}]},"tables":{"facturas":[{"CodigoFact":"PRUEBA","Fecha_Emision":"2026-10-22","Monto_Total":1466,"estado":"Pagada"}]}},"comercial":{"cards":{"cantidad_clientes":3,"cantidad_contratos":4,"ticket_promedio":2934,"ingresos_por_servicio":5868},"charts":{"ingresos_por_servicio":[{"label":"Servicio de prueba","value":5868}],"contratos_por_tipo":[{"label":"Mensual","value":4}],"clientes_por_departamento":[{"label":"Departamento de Chinandega","value":3},{"label":"Regi\u00f3n Aut\u00f3noma de la Costa Caribe Norte","value":5},{"label":"Matagalpa","value":2}]}}}""")
   lateinit var screen: ViewDashboardBinding
   runOnMainSync {
    val view=ViewDashboardBinding.inflate(host.layoutInflater)
    screen=view
    view.tabs.addTab(view.tabs.newTab().setText(R.string.billing_tab))
    view.tabs.addTab(view.tabs.newTab().setText(R.string.commercial_tab))
    val renderer=DashboardRenderer(view,{})
    renderer.render(null,false)
    check(view.empty.visibility==View.VISIBLE)
    renderer.render(fixture,false)
    check(view.empty.visibility==View.GONE)
    host.setContentView(view.root)
    fun texts(node: View): List<String> = if(node is ViewGroup) (0 until node.childCount).flatMap { texts(node.getChildAt(it)) } else if(node is TextView) listOf(node.text.toString()) else emptyList()
    check(texts(view.root).contains("C$5,868.00"))
    fun findMonth(node: View): android.widget.Button? {
     if(node is android.widget.Button && node.text.toString().startsWith("Mes: ")) return node
     if(node is ViewGroup) for(i in 0 until node.childCount) findMonth(node.getChildAt(i))?.let { return it }
     return null
    }
    check(findMonth(view.root)!=null)

    check(texts(view.root).any { it.contains("Pendiente") })
    renderer.render(fixture,true)
    check(texts(view.root).any { it.contains("Servicio de prueba") })
    renderer.render(fixture,false)
    // Verify the fallback at 320dp and enlarged text without changing emulator settings.
    val config=Configuration(host.resources.configuration).apply { screenWidthDp=320; fontScale=1.4f }
    val context=ContextThemeWrapper(host.createConfigurationContext(config),R.style.Theme_Softlutionic)
    val narrow=ViewDashboardBinding.inflate(LayoutInflater.from(context))
    DashboardRenderer(narrow,null).render(fixture,true)
    val width=(320*context.resources.displayMetrics.density).toInt()
    narrow.root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY))
    check(narrow.panels.childCount>0)
    val modules=BottomSheetModulesBinding.inflate(host.layoutInflater)
    check(modules.logout.text==host.getString(R.string.logout))
   }
   waitForIdleSync()
   fun screenshot(name: String) {
    val frames=java.util.concurrent.CountDownLatch(1)
    runOnMainSync { screen.root.postOnAnimation { screen.root.postOnAnimation { frames.countDown() } } }
    check(frames.await(3,java.util.concurrent.TimeUnit.SECONDS))
    android.os.SystemClock.sleep(250)
    uiAutomation.takeScreenshot()?.let { bitmap ->
     File(targetContext.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
     bitmap.recycle()
    }
   }
   screenshot("dashboard-test-fixture.png")
   runOnMainSync {
    screen.root.descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS
    screen.root.isFocusableInTouchMode=true; screen.root.requestFocus()
    screen.root.scrollTo(0,screen.panels.top+screen.panels.getChildAt(5).top)
    check(screen.root.scrollY>0)
   }
   waitForIdleSync()
   screenshot("dashboard-charts-test-fixture.png")
   runOnMainSync { host.finish() }
   finish(-1,Bundle().apply { putString("result","Dashboard renderer: financial, commercial, empty, narrow/large text and module layout passed") })
  } catch(error: Throwable) {
   activity?.let { host -> runOnMainSync { host.finish() } }
   finish(0,Bundle().apply { putString("error",error.stackTraceToString()) })
  }
 }
}
