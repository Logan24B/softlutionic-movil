package com.softlutionic.mobile.ui.contracttypes

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.*
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.databinding.ClientInputBinding
import com.softlutionic.mobile.ui.contracts.ContractPresentation
import com.softlutionic.mobile.viewmodel.MainViewModel
import retrofit2.HttpException

/** Catalog presentation only. IDs remain in raw records for shared CRUD/relations. */
class ContractTypesController(context: Context,private val initial: Module,private val vm: MainViewModel) {
    private val ctx=ContextThemeWrapper(context,R.style.ThemeOverlay_Softlutionic_Login)
    val root=column()
    private val module get()=vm.effectiveModule(vm.modules.value.find { it.name=="tipos-contrato" } ?: initial)
    private var records=emptyList<JsonObject>()
    private var sheet: BottomSheetDialog?=null
    private var sheetBusy: (() -> Unit)?=null
    private val empty=text("")
    private val emptyCreate=button("Nuevo tipo") { form(null) }
    private val create=button("Nuevo tipo") { form(null) }
    private val refresh=button("Actualizar",false) { if(!vm.busy.value && !vm.contractTypeSaving.value) vm.refresh() }
    private val adapter=TypeAdapter()
    private fun dp(n: Int)=(n*ctx.resources.displayMetrics.density).toInt()
    private fun color(id: Int)=ContextCompat.getColor(ctx,id)
    private fun column()=LinearLayout(ctx).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(12)) }
    private fun text(value: String,size: Float=16f,muted: Boolean=false)=TextView(ctx).apply {
        text=value; textSize=size; setTextColor(color(if(muted) R.color.login_muted else R.color.login_text))
        setPadding(0,dp(6),0,dp(6))
        if(size>=20) { setTypeface(typeface,android.graphics.Typeface.BOLD); isAccessibilityHeading=true }
    }
    private fun button(label: String,primary: Boolean=true,action: ()->Unit)=MaterialButton(ctx).apply {
        text=label; isAllCaps=false; minHeight=dp(52); insetTop=0; insetBottom=0; cornerRadius=dp(14)
        backgroundTintList=ColorStateList.valueOf(color(if(primary) R.color.contracts_primary else R.color.login_surface))
        setTextColor(color(if(primary) R.color.login_text else R.color.login_cyan))
        if(!primary) { strokeWidth=dp(1); strokeColor=ColorStateList.valueOf(color(R.color.login_cyan)) }
        elevation=0f; rippleColor=ColorStateList.valueOf(0x3372DCEB)
        setOnClickListener { action() }
    }
    init {
        root.setBackgroundColor(color(R.color.login_bg))
        root.addView(text("TIPOS DE CONTRATO",25f))
        root.addView(text("Administra las modalidades disponibles para los contratos.",14f,true))
        val search=ClientInputBinding.inflate(LayoutInflater.from(ctx),root,false)
        search.root.hint="Buscar tipos de contrato…"; search.input.setSingleLine(true)
        search.input.setText(vm.contractTypeQuery.value)
        search.input.doOnTextChanged { value,_,_,_ -> vm.contractTypeQuery.value=value.toString() }
        root.addView(search.root)
        val actions=LinearLayout(ctx).apply { isBaselineAligned=false; setPadding(0,dp(12),0,dp(12)) }
        create.setIconResource(R.drawable.ic_contract_add); create.iconSize=dp(18)
        create.iconTint=ColorStateList.valueOf(color(R.color.login_text))
        refresh.setIconResource(R.drawable.ic_refresh); refresh.iconSize=dp(16)
        refresh.iconTint=ColorStateList.valueOf(color(R.color.login_cyan))
        listOf(create,refresh).forEach {
            it.maxLines=1; it.letterSpacing=0f; it.setPadding(dp(6),0,dp(6),0); it.iconPadding=dp(4)
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(it,10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        actions.addView(create,LinearLayout.LayoutParams(0,dp(52),.63f).apply { marginEnd=dp(10) })
        actions.addView(refresh,LinearLayout.LayoutParams(0,dp(52),.37f))
        root.addView(actions); root.addView(empty); root.addView(emptyCreate)
        root.addView(RecyclerView(ctx).apply { layoutManager=LinearLayoutManager(ctx); adapter=this@ContractTypesController.adapter },LinearLayout.LayoutParams(-1,0,1f))
        busyChanged()
    }
    fun submit(json: String?) {
        if(json!=null) runCatching { JsonParser.parseString(json).asJsonArray.map { it.asJsonObject } }.onSuccess { records=it }
        renderList()
    }
    fun renderList() {
        adapter.items=ContractTypePresentation.visible(records,vm.contractTypeQuery.value); adapter.notifyDataSetChanged()
        empty.visibility=if(adapter.items.isEmpty()) View.VISIBLE else View.GONE
        empty.text=when {
            vm.busy.value && records.isEmpty() -> "Cargando tipos de contrato…"
            records.isEmpty() -> "No hay tipos de contrato registrados\n"+(if(module.permissions.create) "Agrega una modalidad para comenzar." else "No hay tipos de contrato disponibles.")
            else -> "No hay coincidencias. Prueba otra búsqueda."
        }
        emptyCreate.visibility=if(records.isEmpty() && !vm.busy.value && module.permissions.create) View.VISIBLE else View.GONE
    }
    fun busyChanged() {
        create.visibility=if(module.permissions.create) View.VISIBLE else View.GONE
        val enabled=!vm.busy.value && !vm.contractTypeSaving.value
        listOf(create,refresh,emptyCreate).forEach { it.isEnabled=enabled }
        sheetBusy?.invoke(); renderList()
    }
    fun dispose() { sheet?.dismiss(); sheet=null; sheetBusy=null }
    private fun show(body: LinearLayout,onBusy: (() -> Unit)?=null) {
        dispose(); sheetBusy=onBusy; body.setBackgroundColor(color(R.color.login_bg))
        sheet=BottomSheetDialog(ctx,R.style.ThemeOverlay_Softlutionic_ClientSheet).apply {
            setContentView(androidx.core.widget.NestedScrollView(context).apply { isFillViewport=true; addView(body) })
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setOnDismissListener { sheetBusy=null }
            show(); behavior.skipCollapsed=true; behavior.state=BottomSheetBehavior.STATE_EXPANDED
        }
        sheetBusy?.invoke()
    }
    private fun notify(message: String) { if(root.isAttachedToWindow) Snackbar.make(root,message,Snackbar.LENGTH_LONG).show() }
    fun detail(data: JsonObject) {
        val row=ContractTypePresentation.row(data); val body=column()
        body.addView(text("DETALLE DEL TIPO DE CONTRATO",22f))
        body.addView(text("Nombre",13f,true)); body.addView(text(row.name,22f))
        body.addView(text("Descripción",13f,true)); body.addView(text(row.description.ifBlank { "Sin descripción" }))
        val edit=button("Editar") { if(!vm.contractTypeSaving.value && module.permissions.update) form(data) }
        val delete=button("Eliminar",false) { confirmDelete(data) }
        val close=button("Cerrar",false) { dispose() }
        body.addView(edit); body.addView(delete); body.addView(close)
        show(body) {
            edit.visibility=if(module.permissions.update) View.VISIBLE else View.GONE
            delete.visibility=if(module.permissions.delete) View.VISIBLE else View.GONE
            listOf(edit,delete,close).forEach { it.isEnabled=!vm.contractTypeSaving.value }
            sheet?.setCancelable(!vm.contractTypeSaving.value)
        }
    }
    private fun confirmDelete(data: JsonObject) {
        if(vm.contractTypeSaving.value || !module.permissions.delete) return
        MaterialAlertDialogBuilder(ctx).setTitle("¿Eliminar tipo de contrato?")
            .setMessage("Este tipo puede estar relacionado con contratos existentes.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Eliminar") { _,_ ->
                if(vm.contractTypeSaving.value || !module.permissions.delete) return@setPositiveButton
                vm.delete("tipos-contrato",ContractTypePresentation.text(data,"id"),
                    success={ dispose(); notify("Tipo de contrato eliminado correctamente.") },
                    failure={ error -> MaterialAlertDialogBuilder(ctx).setMessage(errorMessage(error,true)).setPositiveButton("Cerrar",null).show() })
            }.show()
    }
    fun form(data: JsonObject?) {
        if(vm.contractTypeSaving.value || (if(data==null) !module.permissions.create else !module.permissions.update)) return
        val fields=ContractTypePresentation.editable(module.fields)
        val body=column()
        body.addView(text(if(data==null) "Nuevo tipo de contrato" else "Editar tipo de contrato",24f))
        body.addView(text("Define una modalidad que podrá utilizarse al crear contratos.",14f,true))
        body.addView(text("INFORMACIÓN",13f,true))
        val layouts=linkedMapOf<String,com.google.android.material.textfield.TextInputLayout>()
        fields.forEach { field ->
            val input=ClientInputBinding.inflate(LayoutInflater.from(ctx),body,false)
            input.root.hint=ContractTypePresentation.label(field)+(if(field.required) " *" else "")
            input.input.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or (if(field.name=="Descripcion") InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
            if(field.name=="Descripcion") { input.input.minLines=3; input.input.maxLines=6 } else input.input.setSingleLine(true)
            field.max_length?.let { input.root.isCounterEnabled=true; input.root.counterMaxLength=it }
            input.input.setText(data?.let { ContractTypePresentation.text(it,field.name) }.orEmpty())
            input.input.doOnTextChanged { _,_,_,_ -> input.root.error=null }
            layouts[field.name]=input.root; body.addView(input.root)
        }
        val error=text("",14f).apply { setTextColor(color(R.color.login_error)); visibility=View.GONE }; body.addView(error)
        val save=button("Guardar tipo") {}; val cancel=button("Cancelar",false) { dispose() }
        body.addView(save); body.addView(cancel)
        save.setOnClickListener {
            if(vm.contractTypeSaving.value || (if(data==null) !module.permissions.create else !module.permissions.update)) return@setOnClickListener
            val values=layouts.mapValues { it.value.editText?.text.toString().trim() }
            var valid=true
            fields.forEach { field -> val message=ContractTypePresentation.validate(field,values[field.name].orEmpty()); layouts[field.name]?.error=message; if(message!=null) valid=false }
            if(!valid) { layouts.values.firstOrNull { it.error!=null }?.editText?.requestFocus(); return@setOnClickListener }
            error.visibility=View.GONE
            vm.save("tipos-contrato",data?.let { ContractTypePresentation.text(it,"id") },ContractTypePresentation.payload(fields,values),
                success={ dispose(); notify(if(data==null) "Tipo de contrato registrado correctamente." else "Tipo de contrato actualizado correctamente.") },
                finished={ if(sheet?.isShowing==true) sheetBusy?.invoke() },
                failure={ failure ->
                    val errors=if(failure is HttpException) ContractPresentation.errors(failure.response()?.errorBody()?.string()) else emptyMap()
                    errors.forEach { (key,value) -> layouts[key]?.error=value }
                    error.text=errors["non_field_errors"] ?: errors["detail"] ?: errorMessage(failure)
                    error.visibility=View.VISIBLE; layouts.values.firstOrNull { it.error!=null }?.editText?.requestFocus()
                })
        }
        show(body) {
            val busy=vm.contractTypeSaving.value
            layouts.values.forEach { it.isEnabled=!busy }
            save.isEnabled=!busy && fields.isNotEmpty() && (if(data==null) module.permissions.create else module.permissions.update)
            save.text=if(busy) "Guardando…" else "Guardar tipo"; cancel.isEnabled=!busy
            sheet?.setCancelable(!busy); sheet?.setCanceledOnTouchOutside(!busy)
        }
    }
    private fun errorMessage(error: Exception,deleting: Boolean=false)=when {
        error is HttpException && error.code()==409 && deleting -> "No se puede eliminar porque existen contratos relacionados."
        error is HttpException && error.code()==409 -> "Ya existe un tipo de contrato con ese nombre."
        error is HttpException && error.code() in listOf(400,422) -> "Revisa los campos indicados."
        error is HttpException && error.code()==403 -> "No tienes permiso para modificar tipos de contrato."
        error is java.io.IOException -> "Sin conexión. Los datos guardados se conservan. Inténtalo nuevamente."
        else -> "No se pudo completar la operación. Inténtalo nuevamente."
    }
    private inner class TypeAdapter : RecyclerView.Adapter<TypeAdapter.Holder>() {
        var items=emptyList<JsonObject>()
        inner class Holder(val card: MaterialCardView,val body: LinearLayout): RecyclerView.ViewHolder(card)
        override fun getItemCount()=items.size
        override fun onCreateViewHolder(parent: ViewGroup,type: Int): Holder {
            val card=MaterialCardView(ctx).apply {
                layoutParams=RecyclerView.LayoutParams(-1,-2).apply { bottomMargin=dp(12) }
                radius=dp(18).toFloat(); setCardBackgroundColor(color(R.color.login_surface))
                strokeWidth=dp(1); strokeColor=0x263D5277; isClickable=true; isFocusable=true
            }
            val body=column(); card.addView(body); return Holder(card,body)
        }
        override fun onBindViewHolder(holder: Holder,position: Int) {
            val item=items[position]; val row=ContractTypePresentation.row(item); holder.body.removeAllViews()
            holder.body.addView(text(row.name,21f).apply { maxLines=2; ellipsize=TextUtils.TruncateAt.END })
            holder.body.addView(text(row.description,14f,true).apply { maxLines=3; ellipsize=TextUtils.TruncateAt.END })
            holder.body.addView(text("›",20f).apply { gravity=Gravity.END; setTextColor(color(R.color.login_cyan)); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO })
            holder.card.setOnClickListener { detail(item) }
        }
    }
}
