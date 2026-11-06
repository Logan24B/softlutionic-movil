package com.softlutionic.mobile

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import com.google.gson.*
import com.softlutionic.mobile.data.remote.Api
import com.softlutionic.mobile.data.remote.Capability
import com.softlutionic.mobile.data.repository.SoftlutionicRepository
import com.softlutionic.mobile.domain.auth.*
import com.softlutionic.mobile.ui.contracttypes.ContractTypesController
import com.softlutionic.mobile.viewmodel.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.lang.reflect.Proxy

/** Synthetic UI data; a proxy prevents any actual API mutation throughout the test. */
class ContractTypesSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun texts(view: View): List<String> = (if(view is TextView && view.visibility==View.VISIBLE) listOf(view.text.toString()) else emptyList()) +
        (if(view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList())
    private fun screen(): String {
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo): List<String> = listOfNotNull(node.text?.toString()) +
            (0 until node.childCount).flatMap { node.getChild(it)?.let(::visit).orEmpty() }
        return uiAutomation.rootInActiveWindow?.let { visit(it).joinToString("\n") }.orEmpty()
    }
    private fun capture(name: String) {
        Thread.sleep(300)
        val path=File(targetContext.getExternalFilesDir("verification"),"contract-types-$name.png")
        path.parentFile?.mkdirs()
        uiAutomation.takeScreenshot()?.let { bitmap -> path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
    }
    override fun onStart() {
        var host: MainActivity?=null
        var controller: ContractTypesController?=null
        var restore: (() -> Unit)?=null
        var originalConfiguration: android.content.res.Configuration?=null
        try {
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            host=activity; originalConfiguration=android.content.res.Configuration(activity.resources.configuration)
            lateinit var vm: MainViewModel
            runOnMainSync { vm=ViewModelProvider(activity)[MainViewModel::class.java] }
            repeat(200) { if(vm.busy.value || vm.modules.value.none { it.name=="tipos-contrato" }) Thread.sleep(100) }
            val originalModules=vm.modules.value
            val module=originalModules.single { it.name=="tipos-contrato" }.copy(permissions=Capability(true,true,true,true))
            val repoField=MainViewModel::class.java.getDeclaredField("repository").apply { isAccessible=true }
            val repo=repoField.get(vm) as SoftlutionicRepository
            val stateField=SoftlutionicRepository::class.java.getDeclaredField("identityState").apply { isAccessible=true }
            @Suppress("UNCHECKED_CAST")
            val identity=stateField.get(repo) as MutableStateFlow<UserIdentity?>
            val originalIdentity=identity.value
            val apiField=SoftlutionicRepository::class.java.getDeclaredField("api").apply { isAccessible=true }
            val originalApi=apiField.get(repo) as Api
            var mutationCalls=0
            val proxy=Proxy.newProxyInstance(Api::class.java.classLoader,arrayOf(Api::class.java)) { _,method,args ->
                if(method.name in setOf("create","update","delete")) { mutationCalls++; error("Unexpected mutation ${method.name}") }
                method.invoke(originalApi,*(args ?: emptyArray()))
            } as Api
            apiField.set(repo,proxy)
            restore={ identity.value=originalIdentity; apiField.set(repo,originalApi); vm.modules.value=originalModules }
            val fixture="""[{"id":9981,"Nombre":"Servicio mensual de mantenimiento y asistencia empresarial","Descripcion":"Modalidad recurrente para coordinar servicios de mantenimiento y atención especializada. Esta descripción extensa permite verificar el límite visual de la tarjeta y la consulta completa en el detalle sin recortar los datos originales."}]"""
            val item=JsonParser.parseString(fixture).asJsonArray[0].asJsonObject
            runOnMainSync {
                identity.value=UserIdentity("3",UserLevel.EMPLOYEE)
                vm.modules.value=listOf(module); vm.contractTypeQuery.value=""
                controller=ContractTypesController(activity,module,vm)
                activity.setContentView(controller!!.root); controller!!.submit(fixture)
            }
            waitForIdleSync(); capture("employee-list")
            val list=texts(controller!!.root).joinToString("\n")
            check(list.contains("Servicio mensual")); check(!list.contains("9981")); check(!list.contains("Nuevo tipo"))
            check(list.contains("Actualizar"))
            runOnMainSync { vm.contractTypeQuery.value="atencion"; controller!!.renderList() }
            check(texts(controller!!.root).joinToString("\n").contains("Servicio mensual"))
            runOnMainSync { controller!!.detail(item) }
            waitForIdleSync(); capture("employee-detail")
            check(screen().contains("DETALLE DEL TIPO DE CONTRATO")); check(!screen().contains("9981"))
            check(!screen().contains("Editar")); check(!screen().contains("Eliminar"))
            var denied=0
            runOnMainSync {
                val fail: (Exception)->Unit={ check(it is AuthorizationDenied); denied++ }
                vm.save("tipos-contrato",null,JsonObject(),{}, {},fail)
                vm.save("tipos-contrato","9981",JsonObject(),{}, {},fail)
                vm.delete("tipos-contrato","9981",failure=fail)
                // Synchronous callbacks prove these were rejected before runTask/Repository.
                check(denied==3); check(!vm.contractTypeSaving.value); check(mutationCalls==0)
                controller!!.dispose(); controller!!.submit("[]")
            }
            check(texts(controller!!.root).joinToString("\n").contains("No hay tipos de contrato disponibles."))
            check(texts(controller!!.root).none { it=="Nuevo tipo" })
            for(role in listOf(UserLevel.ADMIN,UserLevel.SUPERUSER)) {
                runOnMainSync {
                    identity.value=UserIdentity("1",role); vm.contractTypeQuery.value=""
                    controller!!.busyChanged(); controller!!.submit(fixture)
                    check(texts(controller!!.root).contains("Nuevo tipo"))
                    controller!!.detail(item)
                }
                waitForIdleSync(); check(screen().contains("Editar")); check(screen().contains("Eliminar"))
            }
            runOnMainSync { controller!!.form(null) }
            waitForIdleSync(); capture("create")
            check(screen().contains("Nuevo tipo de contrato")); check(!screen().contains("9981"))
            fun dialog()=(ContractTypesController::class.java.getDeclaredField("sheet").apply { isAccessible=true }.get(controller) as com.google.android.material.bottomsheet.BottomSheetDialog)
            runOnMainSync {
                fun find(view: View): TextView? = if(view is TextView && view.text=="Guardar tipo") view else
                    if(view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
                find(dialog().window!!.decorView)!!.performClick()
            }
            waitForIdleSync(); check(screen().contains("Completa nombre.")); check(mutationCalls==0)
            runOnMainSync { controller!!.form(item) }
            waitForIdleSync(); capture("edit")
            check(screen().contains("Editar tipo de contrato")); check(!screen().contains("9981"))
            runOnMainSync {
                controller!!.dispose()
                val config=android.content.res.Configuration(activity.resources.configuration).apply { fontScale=1.3f }
                @Suppress("DEPRECATION")
                activity.resources.updateConfiguration(config,activity.resources.displayMetrics)
                controller=ContractTypesController(activity,module,vm)
                activity.setContentView(controller!!.root)
                (controller!!.root.parent as View).setBackgroundColor(android.graphics.Color.rgb(9,14,32))
                controller!!.root.layoutParams=controller!!.root.layoutParams.apply { width=(320*activity.resources.displayMetrics.density).toInt() }
                controller!!.submit(fixture)
            }
            waitForIdleSync(); capture("narrow-large-text")
            runOnMainSync { controller!!.form(item) }
            waitForIdleSync()
            runOnMainSync {
                fun inputs(view: View): List<EditText> = (if(view is EditText) listOf(view) else emptyList()) +
                    (if(view is ViewGroup) (0 until view.childCount).flatMap { inputs(view.getChildAt(it)) } else emptyList())
                val input=inputs(dialog().window!!.decorView).last(); input.requestFocus()
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            waitForIdleSync(); capture("keyboard")
            check(screen().contains("Guardar tipo")); check(mutationCalls==0)
            finish(-1,Bundle().apply { putInt("employee_writes_blocked_before_repository",denied); putInt("api_mutation_calls",mutationCalls); putString("result","Employee UI, admin/superuser actions, search, ID hiding, forms, validation, narrow width and keyboard passed") })
        } catch(error: Throwable) { finish(0,Bundle().apply { putString("error",error.toString()) }) }
        finally { runOnMainSync { controller?.dispose(); restore?.invoke(); host?.let {
            @Suppress("DEPRECATION")
            it.resources.updateConfiguration(originalConfiguration,it.resources.displayMetrics)
            it.finish()
        } } }
    }
}
