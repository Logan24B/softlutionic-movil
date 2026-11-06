package com.softlutionic.mobile.ui.contracts

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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.*
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.databinding.ClientDropdownBinding
import com.softlutionic.mobile.databinding.ClientInputBinding
import com.softlutionic.mobile.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneOffset
import retrofit2.HttpException

/** Presentation only. Network and persistence stay in MainViewModel/Repository. */
class ContractsController(context: Context, private val initial: Module, private val vm: MainViewModel) {
    private val host = context as androidx.fragment.app.FragmentActivity
    private val ctx = ContextThemeWrapper(context, R.style.ThemeOverlay_Softlutionic_Login)
    val root = column()
    private val module get() = vm.effectiveModule(vm.modules.value.find { it.name == "contratos" } ?: initial)
    private var records = emptyList<JsonObject>()
    private var sheet: BottomSheetDialog? = null
    private var sheetBusy: (() -> Unit)? = null
    private val count = text("", 13f)
    private val notice = text("", 13f)
    private val empty = text("Cargando contratos…")
    private val emptyCreate = button("Nuevo contrato") { form(null) }
    private val create = button("Nuevo contrato") { form(null) }
    private val refresh = button("Actualizar", false) {
        if(!vm.busy.value && !vm.contractSaving.value) { vm.refresh(); vm.loadContractCatalogs(module, true) }
    }
    private val adapter = ContractAdapter()
    private fun dp(n: Int) = (n * ctx.resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(ctx, id)
    private fun column() = LinearLayout(ctx).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(12)) }
    private fun text(value: String, size: Float = 16f, muted: Boolean = false) = TextView(ctx).apply {
        text=value; textSize=size; setTextColor(color(if(muted) R.color.login_muted else R.color.login_text))
        setPadding(0,dp(5),0,dp(5))
        if(size>=20) { setTypeface(typeface,android.graphics.Typeface.BOLD); isAccessibilityHeading=true }
    }
    private fun button(label: String, primary: Boolean = true, action: () -> Unit) = MaterialButton(ctx).apply {
        text=label; isAllCaps=false; cornerRadius=dp(14); minHeight=dp(52)
        insetTop=0; insetBottom=0; setPadding(dp(10),0,dp(10),0)
        backgroundTintList=ColorStateList.valueOf(color(if(primary) R.color.contracts_primary else R.color.login_surface))
        setTextColor(color(if(primary) R.color.login_text else R.color.login_cyan))
        if(!primary) { strokeWidth=dp(1); strokeColor=ColorStateList.valueOf(color(R.color.login_cyan)) }
        rippleColor=ColorStateList.valueOf(0x3372DCEB); elevation=0f
        setOnClickListener { action() }
    }
    private fun clients() = ContractPresentation.options("clientes", vm.contractCatalogs.value["clientes"].orEmpty(), records)
    private fun types() = ContractPresentation.options("tipos-contrato", vm.contractCatalogs.value["tipos-contrato"].orEmpty(), records)
    init {
        root.setBackgroundColor(color(R.color.login_bg))
        root.addView(text("CONTRATOS",26f))
        root.addView(text("Gestiona los acuerdos de servicio con tus clientes.",14f,true))
        val search=ClientInputBinding.inflate(LayoutInflater.from(ctx),root,false)
        search.root.hint="Buscar contratos…"; search.input.setSingleLine(true)
        search.input.setText(vm.contractFilters.value.query)
        search.input.doOnTextChanged { value,_,_,_ -> vm.contractFilters.value=vm.contractFilters.value.copy(query=value.toString()) }
        root.addView(search.root)
        val actions=LinearLayout(ctx).apply { isBaselineAligned=false; gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(12),0,dp(6)) }
        create.setIconResource(R.drawable.ic_contract_add); create.iconSize=dp(18)
        create.iconTint=ColorStateList.valueOf(color(R.color.login_text)); create.iconPadding=dp(5)
        refresh.setIconResource(R.drawable.ic_refresh); refresh.iconSize=dp(18)
        refresh.iconTint=ColorStateList.valueOf(color(R.color.login_cyan)); refresh.iconPadding=dp(4)
        refresh.setPadding(dp(6),0,dp(6),0)
        refresh.iconSize=dp(16)
        listOf(create,refresh).forEach {
            it.maxLines=1; it.letterSpacing=0f
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(it,10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        actions.addView(create,LinearLayout.LayoutParams(0,dp(52),.63f).apply { marginEnd=dp(10) })
        actions.addView(refresh,LinearLayout.LayoutParams(0,dp(52),.37f))
        root.addView(actions)
        root.addView(button("Filtrar por cliente o tipo",false) { filters() },LinearLayout.LayoutParams(-1,dp(48)))
        root.addView(count); root.addView(notice); root.addView(empty); root.addView(emptyCreate)
        root.addView(RecyclerView(ctx).apply { layoutManager=LinearLayoutManager(ctx); adapter=this@ContractsController.adapter },LinearLayout.LayoutParams(-1,0,1f))
        vm.loadContractCatalogs(module); busyChanged()
    }
    fun submit(json: String?) {
        if(json!=null) runCatching { JsonParser.parseString(json).asJsonArray.map { it.asJsonObject } }.onSuccess { records=it }
        renderList()
    }
    fun renderList() {
        val rows=records.map { ContractPresentation.row(it,clients(),types()) }
        adapter.rows=ContractPresentation.visible(rows,vm.contractFilters.value); adapter.notifyDataSetChanged()
        count.text="${adapter.rows.size} de ${records.size} contratos"
        empty.visibility=if(adapter.rows.isEmpty()) View.VISIBLE else View.GONE
        empty.text=when {
            vm.busy.value && records.isEmpty() -> "Cargando contratos…"
            records.isEmpty() -> "No hay contratos registrados\nRegistra un contrato para comenzar."
            else -> "No hay coincidencias. Revisa la búsqueda y los filtros."
        }
        emptyCreate.visibility=if(records.isEmpty() && !vm.busy.value && module.permissions.create) View.VISIBLE else View.GONE
        notice.text=when {
            vm.contractCatalogsLoading.value -> "Actualizando catálogos…"
            vm.contractCatalogsUnavailable.value -> "Sin conexión · mostrando datos guardados. Algunos catálogos pueden no estar disponibles."
            else -> ""
        }
        notice.visibility=if(notice.text.isBlank()) View.GONE else View.VISIBLE
    }
    fun busyChanged() {
        create.visibility=if(module.permissions.create) View.VISIBLE else View.GONE
        val enabled=!vm.busy.value && !vm.contractSaving.value
        create.isEnabled=enabled; refresh.isEnabled=enabled; emptyCreate.isEnabled=enabled
        sheetBusy?.invoke(); renderList()
    }
    fun dispose() { sheet?.dismiss(); sheet=null; sheetBusy=null }
    private fun show(body: LinearLayout, onBusy: (() -> Unit)? = null) {
        dispose()
        body.setBackgroundColor(color(R.color.login_bg))
        sheetBusy=onBusy
        sheet=BottomSheetDialog(ctx,R.style.ThemeOverlay_Softlutionic_ClientSheet).apply {
            setContentView(androidx.core.widget.NestedScrollView(context).apply { isFillViewport=true; addView(body) })
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setOnDismissListener { sheetBusy=null }
            show(); behavior.skipCollapsed=true; behavior.state=BottomSheetBehavior.STATE_EXPANDED
            findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                it.layoutParams=it.layoutParams.apply { height=ViewGroup.LayoutParams.MATCH_PARENT }
            }
        }
        sheetBusy?.invoke()
    }
    private fun notify(value: String) { if(root.isAttachedToWindow) Snackbar.make(root,value,Snackbar.LENGTH_LONG).show() }
    private fun filters() {
        val body=column(); body.addView(text("Filtrar contratos",22f))
        fun selector(label: String, options: List<ContractOption>, selected: String?, select: (String?) -> Unit) {
            val input=ClientDropdownBinding.inflate(LayoutInflater.from(ctx),body,false)
            input.root.hint=label
            val choices=listOf(ContractOption("","Todos"))+options
            input.input.setAdapter(ArrayAdapter(ctx,R.layout.client_dropdown_item,choices.map { it.label }))
            input.input.setText(choices.find { it.id==selected }?.label ?: "Todos",false)
            input.input.setOnItemClickListener { _,_,index,_ -> select(choices[index].id.ifBlank { null }) }
            body.addView(input.root)
        }
        selector("Cliente",clients(),vm.contractFilters.value.clientId) { vm.contractFilters.value=vm.contractFilters.value.copy(clientId=it) }
        selector("Tipo de contrato",types(),vm.contractFilters.value.typeId) { vm.contractFilters.value=vm.contractFilters.value.copy(typeId=it) }
        body.addView(button("Limpiar filtros",false) { vm.contractFilters.value=vm.contractFilters.value.copy(clientId=null,typeId=null); dispose() })
        body.addView(button("Ver contratos") { dispose() }); show(body)
    }
    fun detail(data: JsonObject) {
        val row=ContractPresentation.row(data,clients(),types()); val body=column()
        body.addView(text("DETALLE DEL CONTRATO",22f))
        fun info(label: String, value: String) { body.addView(text(label.uppercase(),12f,true)); body.addView(text(value.ifBlank { "No disponible" })) }
        info("Cliente",row.client); info("Tipo de contrato",row.type)
        info("Detalle del servicio",row.description)
        listOf("Fecha_Inc","Fecha_Fin").forEach { key -> if(data.has(key)) info(ContractPresentation.labels.getValue(key),data.contractText(key)) }
        if(data.has("EstadoContrato")) info("Estado del contrato",data.contractText("estado_descripcion").ifBlank {
            when(data.contractText("EstadoContrato")) { "true" -> "Activo"; "false" -> "Inactivo"; else -> "No disponible" }
        })
        val displayed=setOf("id","ClienteId","TipoContratoId","Descripcion","Fecha_Inc","Fecha_Fin","EstadoContrato","estado_descripcion","cliente_detalle","tipo_contrato_detalle")
        module.fields.filter { it.name !in displayed && !com.softlutionic.mobile.ui.RecordPresentation.isTechnicalIdField(it.name) }.forEach { field -> info(ContractPresentation.label(field),data.contractText(field.name)) }
        val edit=button("Editar contrato") { if(!vm.contractSaving.value && module.permissions.update) form(data) }
        val delete=button("Eliminar contrato",false) { delete(data) }
        val close=button("Cerrar",false) { dispose() }
        body.addView(edit); body.addView(delete); body.addView(close)
        show(body) {
            edit.visibility=if(module.permissions.update) View.VISIBLE else View.GONE
            delete.visibility=if(module.permissions.delete) View.VISIBLE else View.GONE
            listOf(edit,delete,close).forEach { it.isEnabled=!vm.contractSaving.value }
            sheet?.setCancelable(!vm.contractSaving.value)
        }
    }
    private fun delete(data: JsonObject) {
        if(vm.contractSaving.value || !module.permissions.delete) return
        MaterialAlertDialogBuilder(ctx).setTitle("¿Eliminar contrato?")
            .setMessage("Esta acción puede afectar registros relacionados.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Eliminar") { _,_ ->
                if(vm.contractSaving.value || !module.permissions.delete) return@setPositiveButton
                vm.delete("contratos",data.contractText("id"),success={ dispose(); notify("Contrato eliminado correctamente.") },
                    failure={ error -> MaterialAlertDialogBuilder(ctx).setMessage(errorMessage(error,true)).setPositiveButton("Cerrar",null).show() })
            }.show()
    }
    fun form(data: JsonObject?) {
        if(vm.contractSaving.value || (if(data==null) !module.permissions.create else !module.permissions.update)) return
        val fields=ContractPresentation.editable(module.fields)
        val body=column(); body.addView(text(if(data==null) "Nuevo contrato" else "Editar contrato",24f))
        body.addView(text(if(data==null) "Registra un acuerdo de servicio para un cliente." else "Actualiza los datos del acuerdo de servicio.",14f,true))
        val layouts=linkedMapOf<String,TextInputLayout>(); val readers=linkedMapOf<String,()->String>()
        val controls=mutableListOf<View>(); var missingOptions=false
        fields.forEach { field ->
            val label=ContractPresentation.label(field)+(if(field.required) " *" else "")
            val existing=data?.contractText(field.name).orEmpty()
            if(field.relation!=null || field.type=="BooleanField") {
                val input=ClientDropdownBinding.inflate(LayoutInflater.from(ctx),body,false)
                input.root.hint=label
                val catalog=when(field.relation) { "clientes" -> clients(); "tipos-contrato" -> types(); else -> emptyList() }
                val options=if(field.type=="BooleanField") listOf(ContractOption("true","Activo"),ContractOption("false","Inactivo")) else catalog
                val choices=listOf(ContractOption("",if(field.required) "Seleccionar" else if(field.type=="BooleanField") "Predeterminado" else "Sin tipo de contrato"))+options
                var selected=choices.find { it.id==existing }?.id.orEmpty()
                if(existing.isNotBlank() && options.none { it.id==existing }) {
                    input.root.helperText="La opción guardada no está disponible. Actualiza los catálogos antes de editar."
                    missingOptions=true
                }
                if(field.required && field.relation!=null && options.isEmpty()) { missingOptions=true; input.root.helperText="Catálogo no disponible. Cierra y pulsa Actualizar." }
                input.input.setAdapter(ArrayAdapter(ctx,R.layout.client_dropdown_item,choices.map { it.label }))
                input.input.setText(choices.find { it.id==selected }?.label,false)
                input.input.setOnItemClickListener { _,_,index,_ -> selected=choices[index].id; input.root.error=null }
                readers[field.name]={ selected }; layouts[field.name]=input.root; controls.add(input.input); body.addView(input.root)
            } else {
                val input=ClientInputBinding.inflate(LayoutInflater.from(ctx),body,false); input.root.hint=label
                input.input.inputType=when(field.type) {
                    "DateField" -> InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE
                    "IntegerField" -> InputType.TYPE_CLASS_NUMBER
                    else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or (if(field.name=="Descripcion") InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
                }
                if(field.name=="Descripcion") { input.input.minLines=3; input.input.maxLines=6 } else input.input.setSingleLine(true)
                field.max_length?.let { input.root.isCounterEnabled=true; input.root.counterMaxLength=it }
                input.input.setText(existing); input.input.doOnTextChanged { _,_,_,_ -> input.root.error=null }
                if(field.type=="DateField") {
                    input.root.helperText="AAAA-MM-DD"; input.root.endIconMode=TextInputLayout.END_ICON_CUSTOM
                    input.root.setEndIconDrawable(R.drawable.ic_contract_calendar)
                    input.root.endIconContentDescription="Seleccionar ${ContractPresentation.label(field).lowercase()}"
                    input.root.setEndIconOnClickListener {
                        val picker=MaterialDatePicker.Builder.datePicker().setTitleText(ContractPresentation.label(field)).build()
                        picker.addOnPositiveButtonClickListener { input.input.setText(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                        picker.show(host.supportFragmentManager,"contract-${field.name}")
                    }
                }
                readers[field.name]={ input.input.text.toString().trim() }; layouts[field.name]=input.root; controls.add(input.input); body.addView(input.root)
            }
        }
        val error=text("",14f).apply { setTextColor(color(R.color.login_error)); visibility=View.GONE }; body.addView(error)
        if(missingOptions) { error.text="Actualiza los catálogos para completar el formulario sin perder relaciones."; error.visibility=View.VISIBLE }
        val save=button("Guardar contrato") {}; val cancel=button("Cancelar",false) { dispose() }
        body.addView(save); body.addView(cancel)
        save.setOnClickListener {
            if(vm.contractSaving.value || (if(data==null) !module.permissions.create else !module.permissions.update)) return@setOnClickListener
            var valid=true
            fields.forEach { field -> val message=ContractPresentation.validate(field,readers[field.name]?.invoke().orEmpty()); layouts[field.name]?.error=message; if(message!=null) valid=false }
            if(!valid) { layouts.values.firstOrNull { it.error!=null }?.editText?.requestFocus(); return@setOnClickListener }
            error.visibility=View.GONE
            val payload=ContractPresentation.payload(fields,readers.mapValues { it.value() })
            vm.save("contratos",data?.contractText("id"),payload,
                success={ dispose(); notify(if(data==null) "Contrato registrado correctamente." else "Contrato actualizado correctamente.") },
                finished={ if(sheet?.isShowing==true) sheetBusy?.invoke() },
                failure={ failure ->
                    val errors=if(failure is HttpException) ContractPresentation.errors(failure.response()?.errorBody()?.string()) else emptyMap()
                    errors.forEach { (key,value) -> layouts[key]?.error=value }
                    error.text=errors["non_field_errors"] ?: errors["detail"] ?: errorMessage(failure)
                    error.visibility=View.VISIBLE; layouts.values.firstOrNull { it.error!=null }?.editText?.requestFocus()
                })
        }
        show(body) {
            val busy=vm.contractSaving.value
            controls.forEach { it.isEnabled=!busy }; layouts.values.forEach { it.isEnabled=!busy }
            save.isEnabled=!busy && !missingOptions && fields.isNotEmpty() && (if(data==null) module.permissions.create else module.permissions.update)
            cancel.isEnabled=!busy; save.text=if(busy) "Guardando…" else "Guardar contrato"
            sheet?.setCancelable(!busy); sheet?.setCanceledOnTouchOutside(!busy)
        }
    }
    private fun errorMessage(error: Exception, deleting: Boolean=false) = when {
        error is HttpException && error.code()==409 && deleting -> "No se puede eliminar este contrato porque tiene registros relacionados."
        error is HttpException && error.code()==403 -> "No tienes permiso para esta operación."
        error is HttpException && error.code() in listOf(400,422) -> "Revisa los campos indicados."
        error is java.io.IOException -> "Sin conexión. Los datos guardados se conservan. Inténtalo nuevamente."
        else -> "No se pudo completar la operación. Inténtalo nuevamente."
    }
    private inner class ContractAdapter : RecyclerView.Adapter<ContractAdapter.Holder>() {
        var rows=emptyList<ContractRow>()
        inner class Holder(val card: MaterialCardView,val body: LinearLayout) : RecyclerView.ViewHolder(card)
        override fun getItemCount()=rows.size
        override fun onCreateViewHolder(parent: ViewGroup,type: Int): Holder {
            val card=MaterialCardView(ctx).apply {
                layoutParams=RecyclerView.LayoutParams(-1,-2).apply { bottomMargin=dp(12) }
                radius=dp(18).toFloat(); setCardBackgroundColor(color(R.color.login_surface))
                strokeWidth=dp(1); strokeColor=0x263D5277; isClickable=true; isFocusable=true
            }
            val body=column(); card.addView(body); return Holder(card,body)
        }
        override fun onBindViewHolder(holder: Holder,position: Int) {
            val row=rows[position]; holder.body.removeAllViews()
            holder.body.addView(text(row.client,21f).apply { maxLines=2; ellipsize=TextUtils.TruncateAt.END })
            holder.body.addView(text(row.type,14f).apply { setTextColor(color(R.color.login_cyan)); maxLines=2; ellipsize=TextUtils.TruncateAt.END })
            holder.body.addView(text(row.description,14f,true).apply { maxLines=3; ellipsize=TextUtils.TruncateAt.END })
            holder.body.addView(text("Ver detalles  ›",14f).apply { gravity=Gravity.END; setTextColor(color(R.color.login_accent)) })
            holder.card.setOnClickListener { detail(row.data) }
        }
    }
}
