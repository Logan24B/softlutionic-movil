package com.softlutionic.mobile.ui.users

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.*
import com.softlutionic.mobile.domain.auth.AuthorizationPolicy
import com.softlutionic.mobile.domain.auth.AuthorizationDenied
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.viewmodel.MainViewModel

/** Users-only presentation. All writes use the existing ViewModel/Repository/API. */
class UsersController(context: Context, private val initial: Module, private val vm: MainViewModel) {
    private val ctx = android.view.ContextThemeWrapper(context, R.style.ThemeOverlay_Softlutionic_Login)
    val root = column()
    private val module get() = vm.effectiveModule(vm.modules.value.find { it.name == "usuarios" } ?: initial)
    private fun canForm(user: JsonObject?) = if(user==null) module.permissions.create else AuthorizationPolicy.canEditUser(vm.currentIdentity.value,user,module)
    private var users = emptyList<JsonObject>()
    private var sheet: BottomSheetDialog? = null
    private var saveButton: MaterialButton? = null
    private val formControls = mutableListOf<View>()
    private val count = text("", 14f)
    private val empty = text("Cargando usuarios…", 16f)
    private val create = button("+ Nuevo usuario") { form(null) }
    private val refresh = button("Actualizar") { if(!vm.busy.value) vm.refresh() }
    private val adapter = UsersAdapter()
    private fun dp(value: Int) = (value * ctx.resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
    private fun text(value: String, size: Float = 16f) = TextView(ctx).apply {
        text = value; textSize = size; setTextColor(ContextCompat.getColor(ctx,R.color.login_text)); setPadding(0,dp(6),0,dp(6))
    }
    private fun button(value: String, action: () -> Unit) = MaterialButton(ctx).apply {
        text = value; isAllCaps = false; minHeight = dp(48); setOnClickListener { action() }
    }
    private fun selector(labels: List<String>, selected: Int, change: (Int) -> Unit) = Spinner(ctx).apply {
        adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, labels)
        minimumHeight = dp(48); setSelection(selected)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { change(position) }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    init {
        root.setBackgroundColor(ContextCompat.getColor(ctx,R.color.login_bg))
        root.addView(text("USUARIOS", 24f)); root.addView(text("Gestiona las cuentas de tu equipo", 14f))
        val searchLayout = TextInputLayout(ctx).apply { hint = "Buscar nombre, correo o usuario" }
        val search = TextInputEditText(ctx).apply { setSingleLine(); setText(vm.userListState.value.query) }
        searchLayout.addView(search); root.addView(searchLayout)
        search.doOnTextChanged { value, _, _, _ -> vm.userListState.value = vm.userListState.value.copy(query=value.toString()); renderList() }
        val filters = LinearLayout(ctx)
        filters.addView(selector(listOf("Todos los roles","Administrador","Empleado"), vm.userListState.value.role) {
            vm.userListState.value = vm.userListState.value.copy(role=it); renderList()
        }, LinearLayout.LayoutParams(0,dp(48),1f))
        filters.addView(selector(listOf("Todos los estados","Activo","Inactivo"), vm.userListState.value.status) {
            vm.userListState.value = vm.userListState.value.copy(status=it); renderList()
        }, LinearLayout.LayoutParams(0,dp(48),1f))
        root.addView(filters); root.addView(count)
        val actions = LinearLayout(ctx).apply { setPadding(0,dp(4),0,dp(12)) }
        listOf(create,refresh).forEach { action ->
            action.cornerRadius=dp(14)
            action.insetTop=0; action.insetBottom=0
            action.minimumWidth=0; action.minWidth=0
            action.setPadding(dp(10),0,dp(10),0)
            action.setSingleLine(true); action.letterSpacing=0f
            action.typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(action,11,14,1,android.util.TypedValue.COMPLEX_UNIT_SP)
            action.elevation=0f; action.stateListAnimator=null
        }
        create.backgroundTintList=ContextCompat.getColorStateList(ctx,R.color.users_primary_background)
        create.setTextColor(ContextCompat.getColorStateList(ctx,R.color.users_primary_foreground))
        create.rippleColor=android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#33FFFFFF"))
        create.strokeWidth=0
        refresh.backgroundTintList=ContextCompat.getColorStateList(ctx,R.color.users_secondary_background)
        refresh.setTextColor(ContextCompat.getColorStateList(ctx,R.color.users_secondary_foreground))
        refresh.strokeWidth=dp(1)
        refresh.strokeColor=ContextCompat.getColorStateList(ctx,R.color.users_secondary_foreground)
        refresh.setIconResource(R.drawable.ic_refresh); refresh.iconSize=dp(18); refresh.iconPadding=dp(6)
        refresh.iconGravity=MaterialButton.ICON_GRAVITY_TEXT_START
        refresh.iconTint=ContextCompat.getColorStateList(ctx,R.color.users_secondary_foreground)
        refresh.rippleColor=android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2272DCEB"))
        actions.addView(create, LinearLayout.LayoutParams(0,dp(52),.62f).apply { marginEnd=dp(10) })
        actions.addView(refresh,LinearLayout.LayoutParams(0,dp(52),.38f))
        root.addView(actions); root.addView(empty)
        root.addView(RecyclerView(ctx).apply { layoutManager=LinearLayoutManager(ctx); adapter=this@UsersController.adapter }, LinearLayout.LayoutParams(-1,0,1f))
        busyChanged()
    }
    fun submit(json: String?) { users=json?.let { JsonParser.parseString(it).asJsonArray.map { row -> row.asJsonObject } }.orEmpty(); renderList() }
    fun renderList() {
        val visible=UserPresentation.visible(users, vm.userListState.value)
        adapter.items=visible; adapter.notifyDataSetChanged()
        count.text="${visible.size} de ${users.size} usuarios"
        empty.visibility=if(visible.isEmpty()) View.VISIBLE else View.GONE
        empty.text=when { vm.busy.value && users.isEmpty() -> "Cargando usuarios…"; users.isEmpty() -> "No hay usuarios disponibles."; else -> "No hay coincidencias. Revisa la búsqueda y los filtros." }
    }
    fun busyChanged() {
        create.visibility=if(module.permissions.create) View.VISIBLE else View.GONE
        create.isEnabled=!vm.busy.value && !vm.userSaving.value
        refresh.isEnabled=create.isEnabled || (!vm.busy.value && !vm.userSaving.value)
        val saving=vm.userSaving.value
        formControls.forEach { it.isEnabled=!saving }
        saveButton?.text=if(saving) "Guardando…" else "Guardar usuario"
        sheet?.setCancelable(!saving); sheet?.setCanceledOnTouchOutside(!saving)
        renderList()
    }
    fun dispose() { sheet?.dismiss(); sheet=null; saveButton=null; formControls.clear() }
    private fun notify(message: String) { if(root.isAttachedToWindow) Snackbar.make(root,message,Snackbar.LENGTH_LONG).show() }
    private fun show(body: LinearLayout) {
        dispose()
        sheet=BottomSheetDialog(ctx,R.style.ThemeOverlay_Softlutionic_Sheet).apply {
            setContentView(androidx.core.widget.NestedScrollView(ctx).apply { isFillViewport=true; addView(body) })
            window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setOnDismissListener { formControls.clear(); saveButton=null }
            show(); behavior.state=BottomSheetBehavior.STATE_EXPANDED; behavior.skipCollapsed=true
        }
    }
    private fun detail(user: JsonObject) {
        val body=column()
        body.addView(text(UserPresentation.name(user),24f))
        listOf("PrimerNombre","SegundoNombre","Email","Usuario").forEach { key -> body.addView(text("${UserPresentation.labels[key]}\n${user.userText(key)}")) }
        body.addView(text("Rol\n${AuthorizationPolicy.label(user)}"))
        AuthorizationPolicy.protection(vm.currentIdentity.value,user)?.let { body.addView(text(it,14f)) }
        body.addView(text("Estado\n${if(user.userFlag("is_active")) "Activo" else "Inactivo"}"))
        body.addView(text("Fecha de registro\n${user.userText("FechaRegistro").ifBlank { "No disponible" }}"))
        if(canForm(user)) body.addView(button("Editar usuario") { if(!vm.userSaving.value) form(user) })
        body.addView(button("Cerrar") { dispose() }); show(body)
    }
    internal fun form(user: JsonObject?) {
        if(vm.userSaving.value || !canForm(user)) return
        val fields=UserPresentation.editable(module.fields)
        val body=column(); body.addView(text(if(user==null) "Nuevo usuario" else "Editar usuario",24f))
        val inputs=linkedMapOf<String,TextInputEditText>()
        val layouts=linkedMapOf<String,TextInputLayout>()
        val values=mutableMapOf<String,String>()
        val controls=mutableListOf<View>()
        fun input(key: String, label: String, password: Boolean=false) {
            val layout=TextInputLayout(ctx).apply { hint=label; boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE }
            val edit=TextInputEditText(ctx).apply {
                setSingleLine(); inputType=when { password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    key=="Email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
                if(key=="Usuario") inputType=InputType.TYPE_CLASS_TEXT
                // Never load either a password or hash, even if a malformed response supplies one.
                if(!password) setText(user?.userText(key).orEmpty())
            }
            if(password) layout.endIconMode=TextInputLayout.END_ICON_PASSWORD_TOGGLE
            layout.addView(edit); body.addView(layout,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
            inputs[key]=edit; layouts[key]=layout; controls.add(edit)
        }
        fields.forEach { field ->
            if(field.name in listOf("Rol","is_active")) {
                body.addView(text(UserPresentation.labels.getValue(field.name)))
                val flag=user?.userFlag(field.name) ?: (field.name=="is_active")
                values[field.name]=flag.toString()
                if(field.name=="Rol") {
                    val options=AuthorizationPolicy.roleOptions(vm.currentIdentity.value)
                    values[field.name]=(flag && true in options).toString()
                    if(options.size==1) body.addView(text("Empleado"))
                    else controls.add(selector(options.map { if(it) "Administrador" else "Empleado" },options.indexOf(flag).coerceAtLeast(0)) { values[field.name]=options[it].toString() }.also { body.addView(it) })
                } else controls.add(selector(listOf("Inactivo","Activo"),if(flag) 1 else 0) { values[field.name]=(it==1).toString() }.also { body.addView(it) })
            } else {
                input(field.name,UserPresentation.labels.getValue(field.name),field.name=="Contrasena")
                if(field.name=="Contrasena") {
                    layouts[field.name]?.helperText=if(user==null) "Obligatoria al crear" else "Deja ambos campos vacíos para conservarla"
                    input("confirmation","Repetir contraseña",true)
                }
            }
        }
        body.addView(text("Fecha de registro\n${user?.userText("FechaRegistro")?.ifBlank { "No disponible" } ?: "Asignada por el servidor"}",14f))
        val error=text("",14f).apply { setTextColor(ContextCompat.getColor(ctx,R.color.login_error)); accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
        body.addView(error)
        val save=button("Guardar usuario") {
            if(vm.userSaving.value || !canForm(user)) return@button
            layouts.values.forEach { it.error=null }; error.text=""
            inputs.forEach { (key, edit) -> values[key]=edit.text.toString() }
            var valid=true
            fields.forEach { field ->
                if(field.name in listOf("Rol","is_active","Contrasena")) return@forEach
                val value=values[field.name].orEmpty().trim()
                val problem=when {
                    field.required && value.isEmpty() -> "Este campo es obligatorio."
                    field.max_length!=null && value.length>field.max_length -> "Máximo ${field.max_length} caracteres."
                    field.name=="Email" && value.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches() -> "Ingresa un correo válido."
                    else -> null
                }
                layouts[field.name]?.error=problem; if(problem!=null) valid=false
            }
            UserPresentation.passwordError(values["Contrasena"].orEmpty(),values["confirmation"].orEmpty(),user==null)?.let {
                layouts["confirmation"]?.error=it; valid=false
            }
            if(!valid) return@button
            val payload=runCatching { UserPresentation.payload(module.fields,values,user==null) }.getOrElse { error.text=it.message; return@button }
            vm.save("usuarios",user?.userText("id"),payload,{ dispose(); notify("Usuario guardado") },{ busyChanged() },{ failure ->
                val errors=if(failure is retrofit2.HttpException) runCatching {
                    JsonParser.parseString(failure.response()?.errorBody()?.string().orEmpty()).asJsonObject
                }.getOrNull() else null
                errors?.entrySet()?.filter { it.key in UserPresentation.labels }?.forEach { (key,value) ->
                    layouts[key]?.error=(if(value.isJsonArray) value.asJsonArray.filter { it.isJsonPrimitive }.joinToString(" ") { it.asString } else if(value.isJsonPrimitive) value.asString else "Revisa este campo.").take(350)
                }
                error.text=when { failure is java.io.IOException -> "No se pudo confirmar el guardado. Comprueba la conexión y actualiza antes de reintentar."
                    failure is AuthorizationDenied || (failure is retrofit2.HttpException && failure.code()==403) -> "No tienes permiso para administrar esta cuenta."
                    failure is retrofit2.HttpException && failure.code()==401 -> "Inicia sesión nuevamente."
                    else -> "No se pudo guardar. Revisa los campos e inténtalo nuevamente." }
            })
            busyChanged()
        }
        body.addView(save); val cancel=button("Cancelar") { dispose() }; body.addView(cancel)
        show(body); saveButton=save; formControls.addAll(controls+save+cancel); busyChanged()
    }
    private inner class UsersAdapter : RecyclerView.Adapter<UsersAdapter.Holder>() {
        var items=emptyList<JsonObject>()
        inner class Holder(val card: MaterialCardView, val name: TextView, val info: TextView) : RecyclerView.ViewHolder(card)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val card=MaterialCardView(ctx).apply {
                radius=dp(16).toFloat(); setCardBackgroundColor(ContextCompat.getColor(ctx,R.color.login_surface))
                layoutParams=RecyclerView.LayoutParams(-1,-2).apply { bottomMargin=dp(10) }; isClickable=true; isFocusable=true
            }
            val body=column(); val name=text("",19f); val info=text("",14f)
            body.addView(name); body.addView(info); card.addView(body); return Holder(card,name,info)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val user=items[position]; holder.name.text=UserPresentation.name(user)
            holder.info.text=listOf(user.userText("Email"),"@${user.userText("Usuario")}",
                "${AuthorizationPolicy.label(user)} · ${if(user.userFlag("is_active")) "Activo" else "Inactivo"}",
                AuthorizationPolicy.protection(vm.currentIdentity.value,user).orEmpty(),
                if(AuthorizationPolicy.user(user)?.id==vm.currentIdentity.value?.id) "Tú" else "").filter { it.isNotBlank() }.joinToString("\n")
            holder.card.setOnClickListener { if(!vm.userSaving.value) detail(user) }
        }
        override fun getItemCount()=items.size
    }
}
