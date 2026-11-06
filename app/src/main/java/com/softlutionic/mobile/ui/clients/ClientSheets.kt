package com.softlutionic.mobile.ui.clients

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.util.Patterns
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.databinding.*
import com.softlutionic.mobile.domain.model.Record
import com.softlutionic.mobile.viewmodel.MainViewModel
import com.google.gson.*
import retrofit2.HttpException

/** Client-only sheets. All writes go through the shared ViewModel / Repository CRUD. */
class ClientSheets(
    private val context: Context,
    private val vm: MainViewModel,
    private val currentModule: () -> Module,
    private val departments: () -> List<DepartmentOption>,
    private val notify: (Int) -> Unit
) {
    private var active: BottomSheetDialog? = null
    private var updateSaving: ((Boolean) -> Unit)? = null
    private var updateCatalog: (() -> Unit)? = null
    private fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(context, id)
    private fun label(key: String) = context.getString(labels.getValue(key))
    fun dismiss() { active?.dismiss(); active = null; updateSaving = null; updateCatalog = null }
    fun savingChanged(saving: Boolean) { updateSaving?.invoke(saving) }
    fun catalogChanged() { updateCatalog?.invoke() }
    private fun text(ctx: Context, value: String, heading: Boolean = false) = TextView(ctx).apply {
        text = value; textSize = if (heading) 18f else 15f
        setTextColor(color(if (heading) R.color.login_text else R.color.login_muted))
        setPadding(0, dp(if (heading) 18 else 6), 0, dp(6))
        isAccessibilityHeading = heading
    }
    private fun sheet(): BottomSheetDialog {
        dismiss()
        return BottomSheetDialog(context, R.style.ThemeOverlay_Softlutionic_ClientSheet).also { dialog ->
            active = dialog
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.setOnDismissListener { if (active === dialog) { active = null; updateSaving = null; updateCatalog = null } }
        }
    }
    private fun showLarge(dialog: BottomSheetDialog, root: View) {
        dialog.setContentView(root)
        dialog.behavior.isFitToContents = true
        dialog.behavior.skipCollapsed = true
        dialog.setOnShowListener {
            val container = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            container?.layoutParams = container?.layoutParams?.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
    }
    private fun dropdown(parent: LinearLayout, title: String, options: List<String>, selected: Int, changed: (Int) -> Unit): Pair<TextInputLayout, MaterialAutoCompleteTextView> {
        val input = ClientDropdownBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val layout = input.root
        layout.hint = title
        val dropdown = input.input.apply {
            inputType = InputType.TYPE_NULL
            isCursorVisible = false
            setTextColor(color(R.color.login_text))
            setDropDownBackgroundDrawable(android.graphics.drawable.ColorDrawable(color(R.color.login_surface)))
            setAdapter(ArrayAdapter(parent.context, R.layout.client_dropdown_item, options))
            setText(options.getOrNull(selected).orEmpty(), false)
            setOnItemClickListener { _, _, position, _ -> layout.error = null; changed(position) }
            setOnClickListener { showDropDown() }
        }
        parent.addView(layout)
        return layout to dropdown
    }
    fun filters() {
        val dialog = sheet()
        val body = LinearLayout(dialog.context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(20)); setBackgroundColor(color(R.color.login_bg)) }
        body.addView(text(body.context, context.getString(R.string.clients_filters), true))
        val original = vm.clientFilters.value
        var status = original.status
        var department = original.departmentId
        val states = listOf(R.string.clients_all, R.string.clients_active_filter, R.string.clients_inactive_filter).map(context::getString)
        dropdown(body, label("Estado"), states, status.ordinal) { status = ClientStatusFilter.entries[it] }
        val options = departments().toMutableList()
        department?.let { id -> if (options.none { it.id == id }) options.add(DepartmentOption(id, context.getString(R.string.clients_department_missing))) }
        dropdown(body, label("DepartamentoId"), listOf(context.getString(R.string.clients_all_departments)) + options.map { it.name }, options.indexOfFirst { it.id == department } + 1) {
            department = if (it == 0) null else options[it - 1].id
        }
        fun button(res: Int, action: () -> Unit) = com.google.android.material.button.MaterialButton(body.context).apply {
            setText(res); isAllCaps = false; setOnClickListener { action() }; body.addView(this)
        }
        button(R.string.apply) { vm.clientFilters.value = original.copy(status = status, departmentId = department); dialog.dismiss() }
        button(R.string.clients_clear) { vm.clientFilters.value = ClientFilters(query = original.query); dialog.dismiss() }
        button(R.string.cancel) { dialog.dismiss() }
        dialog.setContentView(ScrollView(dialog.context).apply { addView(body) }); dialog.show()
    }
    fun detail(row: ClientRow) {
        val m = currentModule(); val dialog = sheet()
        val b = BottomSheetClientDetailBinding.inflate(LayoutInflater.from(dialog.context))
        val header = ItemClientBinding.inflate(LayoutInflater.from(b.root.context), b.content, false)
        header.avatar.text = row.initials
        header.name.text = row.name.ifBlank { context.getString(R.string.clients_no_name) }
        header.department.text = row.department.ifBlank { context.getString(R.string.clients_department_missing) }
        header.state.setText(when(row.active) { true -> R.string.clients_active; false -> R.string.clients_inactive; null -> R.string.clients_unknown_state })
        header.state.setTextColor(color(when(row.active) { true -> R.color.dashboard_green; false -> R.color.login_error; null -> R.color.login_muted }))
        listOf(header.phone, header.email, header.idCard).forEach { it.visibility = View.GONE }
        header.root.isClickable = false; header.root.isFocusable = false
        b.content.addView(header.root)
        fun section(res: Int, fields: List<String>) {
            b.content.addView(text(b.root.context, context.getString(res), true))
            fields.forEach { key ->
                val raw = row.record.payload.clientText(key)
                val value = when(key) { "FechaRegistro" -> ClientPresentation.date(raw); "HoraRegistro" -> ClientPresentation.time(raw); else -> raw }
                b.content.addView(text(b.root.context, label(key) + "\n" + value.ifBlank { context.getString(R.string.clients_no_data) }))
            }
        }
        section(R.string.clients_personal, listOf("Nombre", "Apellido", "Cedula"))
        section(R.string.clients_contact, listOf("Telefono", "Correo", "Direccion"))
        section(R.string.clients_system, listOf("FechaRegistro", "HoraRegistro"))
        b.edit.visibility = if(m.permissions.update) View.VISIBLE else View.GONE
        b.delete.visibility = if(m.permissions.delete) View.VISIBLE else View.GONE
        b.edit.setOnClickListener { if(!vm.clientSaving.value) { dialog.dismiss(); form(row.record) } }
        b.close.setOnClickListener { dialog.dismiss() }
        b.delete.setOnClickListener {
            if(vm.clientSaving.value || !currentModule().permissions.delete) return@setOnClickListener
            MaterialAlertDialogBuilder(dialog.context).setTitle(R.string.clients_delete_title).setMessage(R.string.clients_delete_message)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.clients_delete) { _, _ ->
                    if(!currentModule().permissions.delete || vm.clientSaving.value) return@setPositiveButton
                    vm.delete("clientes", row.record.id,
                        success = { dialog.dismiss(); notify(R.string.clients_deleted) },
                        finished = {}, failure = { error ->
                            val message = errorMessage(error, true)
                            if(dialog.isShowing) MaterialAlertDialogBuilder(dialog.context).setMessage(message).setPositiveButton(R.string.clients_close, null).show()
                        })
                }.show()
        }
        updateSaving = { saving ->
            b.edit.visibility = if(currentModule().permissions.update) View.VISIBLE else View.GONE
            b.delete.visibility = if(currentModule().permissions.delete) View.VISIBLE else View.GONE
            b.edit.isEnabled = !saving; b.delete.isEnabled = !saving; b.close.isEnabled = !saving
            b.progress.visibility = if(saving) View.VISIBLE else View.GONE; dialog.setCancelable(!saving)
        }
        updateSaving?.invoke(vm.clientSaving.value)
        showLarge(dialog, b.root)
    }
    fun form(record: Record?) {
        val m = currentModule()
        if(if(record == null) !m.permissions.create else !m.permissions.update) return
        val dialog = sheet()
        val b = BottomSheetClientFormBinding.inflate(LayoutInflater.from(dialog.context))
        b.title.setText(if(record == null) R.string.clients_new_title else R.string.clients_edit_title)
        b.subtitle.setText(if(record == null) R.string.clients_new_subtitle else R.string.clients_edit_subtitle)
        val fields = ClientPresentation.editable(m.fields)
        val readers = mutableMapOf<String, () -> JsonElement>()
        val validators = mutableListOf<() -> Boolean>()
        val layouts = mutableMapOf<String, TextInputLayout>()
        val controls = mutableListOf<View>()
        val sections = listOf(R.string.clients_personal to listOf("Nombre", "Apellido", "Cedula"),
            R.string.clients_contact to listOf("Telefono", "Correo", "Direccion"),
            R.string.clients_location to listOf("DepartamentoId"), R.string.clients_configuration to listOf("Estado"))
        var refreshDropdown: (() -> Unit)? = null
        sections.forEach { (section, names) ->
            val entries = fields.filter { it.name in names }
            if(entries.isNotEmpty()) b.fields.addView(text(b.root.context, context.getString(section), true))
            entries.forEach { field ->
                val key = field.name; val existing = record?.payload?.clientText(key).orEmpty()
                val hint = if(field.required) context.getString(R.string.clients_required_label, label(key)) else label(key)
                when(key) {
                    "Estado" -> {
                        val switch = MaterialSwitch(b.root.context).apply {
                            // MaterialComponents themes may enable showText without defining on/off text.
                            showText = false; textOn = ""; textOff = ""
                            setText(R.string.clients_active_switch); setTextColor(color(R.color.login_text))
                            isChecked = if(record == null) true else existing == "true" || existing == "1"
                            minHeight = dp(56)
                        }
                        b.fields.addView(switch); controls.add(switch); readers[key] = { JsonPrimitive(switch.isChecked) }
                    }
                    "DepartamentoId" -> {
                        var selected = existing.takeIf { it.isNotBlank() }
                        var choices = emptyList<DepartmentOption>()
                        val (layout, dropdown) = dropdown(b.fields, hint, emptyList(), -1) { position -> selected = choices.getOrNull(position)?.id }
                        layouts[key] = layout; controls.add(dropdown)
                        refreshDropdown = {
                            choices = departments().toMutableList().also { options ->
                                selected?.let { id -> if(options.none { it.id == id }) {
                                    val name = record?.payload?.get("departamento_detalle")?.takeIf { it.isJsonObject }?.asJsonObject?.clientText("Nombre").orEmpty()
                                    options.add(DepartmentOption(id, name.ifBlank { context.getString(R.string.clients_department_missing) }))
                                } }
                            }
                            dropdown.setAdapter(ArrayAdapter(dropdown.context, R.layout.client_dropdown_item, choices.map { it.name }))
                            dropdown.setText(choices.find { it.id == selected }?.name.orEmpty(), false)
                            layout.helperText = when {
                                vm.clientDepartmentsLoading.value -> context.getString(R.string.clients_departments_loading)
                                vm.clientDepartmentsUnavailable.value -> context.getString(R.string.clients_department_retry)
                                else -> null
                            }
                        }
                        refreshDropdown?.invoke()
                        readers[key] = { choices.find { it.id == selected }?.id?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE }
                        validators.add {
                            val valid = (!field.required && selected == null) || choices.any { it.id == selected }
                            layout.error = if(valid) null else context.getString(R.string.clients_select_department); valid
                        }
                    }
                    else -> {
                        val inputBinding = ClientInputBinding.inflate(LayoutInflater.from(b.root.context), b.fields, false)
                        val layout = inputBinding.root; val input = inputBinding.input
                        layout.hint = hint; layouts[key] = layout; controls.add(input)
                        input.inputType = when(key) {
                            "Nombre", "Apellido" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                            "Telefono" -> InputType.TYPE_CLASS_PHONE
                            "Correo" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                            "Direccion" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                        }
                        if(key == "Direccion") { input.minLines = 2; input.maxLines = 3 } else input.setSingleLine(true)
                        if(key == "Telefono") layout.helperText = context.getString(R.string.clients_phone_help)
                        field.max_length?.let { length -> layout.isCounterEnabled = true; layout.counterMaxLength = length }
                        input.setText(existing)
                        input.doOnTextChanged { _, _, _, _ -> layout.error = null }
                        b.fields.addView(layout)
                        readers[key] = { JsonPrimitive(input.text.toString().trim()) }
                        validators.add {
                            val value = input.text.toString().trim()
                            val error = when {
                                field.required && value.isEmpty() -> context.getString(R.string.clients_required)
                                field.max_length != null && value.length > field.max_length -> context.getString(R.string.clients_max_length, field.max_length)
                                key == "Correo" && value.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(value).matches() -> context.getString(R.string.clients_email_invalid)
                                key == "Telefono" && value.isNotEmpty() && !ClientPresentation.validPhone(value) -> context.getString(R.string.clients_phone_invalid)
                                else -> null
                            }
                            layout.error = error; error == null
                        }
                    }
                }
            }
        }
        updateCatalog = { refreshDropdown?.invoke() }
        fun showError(message: String) { b.error.text = message; b.error.visibility = View.VISIBLE }
        fun focusError() { layouts.values.firstOrNull { !it.error.isNullOrBlank() }?.editText?.requestFocus() }
        if(fields.isEmpty()) { showError(context.getString(R.string.clients_unavailable_form)); b.save.isEnabled = false }
        b.cancel.setOnClickListener { dialog.dismiss() }
        updateSaving = { saving ->
            b.save.isEnabled = !saving && fields.isNotEmpty(); b.cancel.isEnabled = !saving
            b.save.setText(if(saving) R.string.clients_saving else R.string.clients_save)
            b.progress.visibility = if(saving) View.VISIBLE else View.GONE
            controls.forEach { it.isEnabled = !saving }; dialog.setCancelable(!saving)
        }
        updateSaving?.invoke(vm.clientSaving.value)
        b.save.setOnClickListener {
            if(vm.clientSaving.value || (if(record == null) !currentModule().permissions.create else !currentModule().permissions.update)) return@setOnClickListener
            b.error.visibility = View.GONE
            val results = validators.map { it() }
            if(results.any { !it }) { focusError(); return@setOnClickListener }
            val payload = ClientPresentation.payload(fields, readers.mapValues { it.value() })
            updateSaving?.invoke(true)
            vm.save("clientes", record?.id, payload,
                success = { dialog.dismiss(); notify(if(record == null) R.string.clients_created else R.string.clients_updated) },
                finished = { if(dialog.isShowing) updateSaving?.invoke(false) },
                failure = { error ->
                    val body = if(error is HttpException && error.code() in listOf(400, 409, 422)) error.response()?.errorBody()?.string() else null
                    val errors = ClientPresentation.fieldErrors(body)
                    errors.forEach { (key, message) -> layouts[key]?.error = friendly(message) }
                    val general = ClientPresentation.generalError(body)?.let(::friendly)
                    if(dialog.isShowing) { showError(general ?: context.getString(errorMessage(error, false))); focusError() }
                })
        }
        showLarge(dialog, b.root)
    }
    private fun friendly(message: String): String {
        var result = message
        ClientPresentation.editableNames.forEach { key -> result = result.replace(key, label(key)) }
        return when {
            result.contains("unique", true) || result.contains("already exists", true) -> context.getString(R.string.clients_duplicate)
            result.contains("required", true) || result.contains("blank", true) -> context.getString(R.string.clients_required)
            result.contains("valid email", true) -> context.getString(R.string.clients_email_invalid)
            else -> result
        }
    }
    private fun errorMessage(error: Exception, deleting: Boolean): Int = when(error) {
        is HttpException -> when(error.code()) {
            401 -> R.string.clients_session_expired
            403 -> R.string.clients_forbidden
            409 -> if(deleting) R.string.clients_delete_related else R.string.clients_duplicate
            400, 422 -> R.string.clients_invalid
            else -> R.string.clients_server_error
        }
        is java.io.IOException -> R.string.clients_offline_error
        else -> R.string.clients_server_error
    }
    companion object {
        val labels = mapOf("Nombre" to R.string.clients_name, "Apellido" to R.string.clients_surname,
            "Cedula" to R.string.clients_id_card, "Telefono" to R.string.clients_phone, "Correo" to R.string.clients_email,
            "Direccion" to R.string.clients_address, "DepartamentoId" to R.string.clients_department, "Estado" to R.string.clients_state,
            "id" to R.string.clients_id, "FechaRegistro" to R.string.clients_registered_date, "HoraRegistro" to R.string.clients_registered_time)
    }
}
