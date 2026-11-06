package com.softlutionic.mobile

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.softlutionic.mobile.data.remote.*
import com.softlutionic.mobile.ui.users.UsersController
import com.softlutionic.mobile.viewmodel.MainViewModel
import java.io.File

/** UI fixtures and invalid submits only; never creates or edits server users. */
class UsersSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        var host: MainActivity?=null
        var controller: UsersController?=null
        try {
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            host=activity
            val fields=listOf(Field("PrimerNombre","PrimerNombre",true,"CharField",null,50),Field("SegundoNombre","Primer apellido",true,"CharField",null,50),
                Field("Email","Email",true,"EmailField",null,null),Field("Usuario","Usuario",false,"CharField",null,null),
                Field("Contrasena","Contrasena",false,"CharField",null,null),Field("Rol","Rol",false,"BooleanField",null,null),Field("is_active","Activo",false,"BooleanField",null,null))
            val module=Module("usuarios",Capability(true,true,true,true),fields)
            lateinit var vm: MainViewModel
            runOnMainSync { vm=ViewModelProvider(activity)[MainViewModel::class.java] }
            kotlinx.coroutines.runBlocking { vm.load() }
            runOnMainSync {
                vm.modules.value=listOf(module)
                controller=UsersController(activity,module,vm)
                activity.setContentView(controller!!.root)
                controller!!.submit("""[{"id":99999,"PrimerNombre":"Andrés","SegundoNombre":"Avilés","Email":"fixture@example.com","Usuario":"fixture","Rol":false,"is_active":true,"FechaRegistro":"2026-10-27","password":"HASH_MUST_NOT_APPEAR","last_name":"INTERNAL_MUST_NOT_APPEAR"}]""")
            }
            waitForIdleSync(); capture("users-list-fixture.png")
            check(text().contains("Andrés Avilés")); check(!text().contains("HASH_MUST_NOT_APPEAR"))
            runOnMainSync { nodes(controller!!.root).filterIsInstance<TextView>().single { it.text.toString()=="+ Nuevo usuario" }.performClick() }
            waitForIdleSync(); capture("users-create-fixture.png")
            check(text().contains("Primer apellido")); check(text().contains("Repetir contraseña")); check(text().contains("Asignada por el servidor"))
            check(!text().contains("SegundoNombre")); check(!text().contains("last_name"))
            click("Guardar usuario"); check(text().contains("obligatorio")); capture("users-validation-fixture.png")
            click("Cancelar")
            runOnMainSync { nodes(controller!!.root).filterIsInstance<com.google.android.material.card.MaterialCardView>().first().performClick() }
            waitForIdleSync(); check(!text().contains("HASH_MUST_NOT_APPEAR")); check(!text().contains("INTERNAL_MUST_NOT_APPEAR"))
            click("Editar usuario"); waitForIdleSync(); capture("users-edit-fixture.png")
            check(text().contains("Avilés")); check(text().contains("conservarla")); check(!text().contains("HASH_MUST_NOT_APPEAR"))
            finish(-1,Bundle().apply { putString("result","Users list, business labels, invalid submit, detail, edit prefill and password/internal-field exclusion passed; no writes") })
        } catch(error: Throwable) { finish(0,Bundle().apply { putString("error",error.stackTraceToString()) }) }
        finally { host?.let { activity -> runOnMainSync { controller?.dispose(); activity.finish() } } }
    }
    private fun nodes(view: View): List<View> = listOf(view) + if(view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    private fun text(): String = uiAutomation.rootInActiveWindow?.let { root ->
        fun read(node: android.view.accessibility.AccessibilityNodeInfo): String = listOf(node.text?.toString().orEmpty(),node.hintText?.toString().orEmpty()).joinToString(" ")+" "+(0 until node.childCount).joinToString(" ") { node.getChild(it)?.let(::read).orEmpty() }
        read(root)
    }.orEmpty()
    private fun click(label: String) {
        val node=uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(label).first { it.text?.toString()==label }
        check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)); waitForIdleSync()
    }
    private fun capture(name: String) {
        val bitmap=uiAutomation.takeScreenshot() ?: return
        File(targetContext.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
