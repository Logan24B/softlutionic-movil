package com.softlutionic.mobile.ui.services

import android.content.Context
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import com.google.android.material.bottomsheet.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.databinding.*
import com.softlutionic.mobile.domain.model.Record
import com.softlutionic.mobile.viewmodel.MainViewModel
import retrofit2.HttpException

/** Service views only: mutations use the existing shared save/delete functions. */
class ServiceSheets(private val context: Context, private val vm: MainViewModel,
    private val module: () -> Module, private val notify: (Int) -> Unit) {
    private fun permissions() = vm.effectiveModule(module()).permissions
    private var active: BottomSheetDialog? = null
    private var saving: ((Boolean) -> Unit)? = null
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun dismiss() { active?.dismiss(); active = null; saving = null }
    fun savingChanged() { saving?.invoke(vm.serviceSaving.value) }
    private fun sheet() = BottomSheetDialog(context, R.style.ThemeOverlay_Softlutionic_ClientSheet).also { dialog ->
        dismiss(); active = dialog
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnDismissListener { if(active === dialog) { active = null; saving = null } }
    }
    private fun show(dialog: BottomSheetDialog, root: View) {
        dialog.setContentView(root); dialog.behavior.skipCollapsed = true
        dialog.setOnShowListener {
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { container ->
                container.layoutParams = container.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            }
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
    }
    private fun text(ctx: Context, value: String, heading: Boolean = false) = TextView(ctx).apply {
        text = value; textSize = if(heading) 18f else 15f
        setTextColor(ContextCompat.getColor(ctx, if(heading) R.color.login_text else R.color.login_muted))
        setPadding(0, dp(16), 0, dp(8)); isAccessibilityHeading = heading
    }
    fun sort() {
        val dialog = sheet()
        val body = LinearLayout(dialog.context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(20))
            setBackgroundColor(ContextCompat.getColor(context, R.color.login_bg))
        }
        body.addView(text(body.context, context.getString(R.string.services_sort_title), true))
        val choices = RadioGroup(body.context)
        sortLabels.forEachIndexed { index, label ->
            choices.addView(MaterialRadioButton(body.context).apply {
                id = View.generateViewId(); setText(label); textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.login_text)); minHeight = dp(56)
                isChecked = vm.serviceListState.value.sort.ordinal == index
                setOnClickListener { vm.serviceListState.value = vm.serviceListState.value.copy(sort = ServiceSort.entries[index]); dialog.dismiss() }
            })
        }
        body.addView(choices)
        body.addView(MaterialButton(body.context).apply { setText(R.string.cancel); isAllCaps = false; setOnClickListener { dialog.dismiss() } })
        dialog.setContentView(ScrollView(dialog.context).apply { addView(body) }); dialog.show()
    }
    fun detail(record: Record) {
        val dialog = sheet()
        val b = BottomSheetServiceDetailBinding.inflate(LayoutInflater.from(dialog.context))
        val card = ItemServiceBinding.inflate(LayoutInflater.from(b.root.context), b.content, false)
        ServiceAdapter.bind(card, record, true); b.content.addView(card.root)
        b.close.setOnClickListener { dialog.dismiss() }
        b.edit.setOnClickListener { if(!vm.serviceSaving.value && permissions().update) { dialog.dismiss(); form(record) } }
        b.delete.setOnClickListener {
            if(vm.serviceSaving.value || !permissions().delete) return@setOnClickListener
            MaterialAlertDialogBuilder(dialog.context).setTitle(R.string.services_delete_title).setMessage(R.string.services_delete_message)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.services_delete) { _, _ ->
                    if(vm.serviceSaving.value || !permissions().delete) return@setPositiveButton
                    vm.delete("servicios", record.id, success = { dialog.dismiss(); notify(R.string.services_deleted) },
                        failure = { error -> if(dialog.isShowing) MaterialAlertDialogBuilder(dialog.context)
                            .setMessage(message(error, true)).setPositiveButton(R.string.services_close, null).show() })
                }.show()
        }
        saving = { value ->
            b.edit.visibility = if(permissions().update) View.VISIBLE else View.GONE
            b.delete.visibility = if(permissions().delete) View.VISIBLE else View.GONE
            b.edit.isEnabled = !value; b.delete.isEnabled = !value; b.close.isEnabled = !value
            b.progress.visibility = if(value) View.VISIBLE else View.GONE; dialog.setCancelable(!value)
        }
        savingChanged(); show(dialog, b.root)
    }
    fun form(record: Record?) {
        if(if(record == null) !permissions().create else !permissions().update) return
        val fields = ServicePresentation.editable(module().fields)
        val dialog = sheet()
        val b = BottomSheetServiceFormBinding.inflate(LayoutInflater.from(dialog.context))
        b.title.setText(if(record == null) R.string.services_new_title else R.string.services_edit_title)
        b.subtitle.setText(if(record == null) R.string.services_new_subtitle else R.string.services_edit_subtitle)
        val layouts = linkedMapOf<String, TextInputLayout>()
        val validators = mutableListOf<() -> Boolean>()
        fields.forEach { field ->
            val key = field.name
            if(key == "Nombre_Servicio" || key == "Precio") b.fields.addView(text(b.root.context, context.getString(
                if(key == "Precio") R.string.services_commercial else R.string.services_information), true))
            val input = ClientInputBinding.inflate(LayoutInflater.from(b.root.context), b.fields, false)
            val label = context.getString(labels.getValue(key))
            input.root.hint = if(field.required) context.getString(R.string.services_required_label, label) else label
            input.input.inputType = when(key) {
                "Precio" -> InputType.TYPE_CLASS_NUMBER or (if(ServicePresentation.decimal(field)) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
                "Descripcion" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            if(key == "Descripcion") { input.input.minLines = 3; input.input.maxLines = 6 } else input.input.setSingleLine(true)
            if(key == "Precio") {
                input.root.prefixText = context.getString(R.string.services_currency)
                if(!ServicePresentation.decimal(field)) input.root.helperText = context.getString(R.string.services_integer_help)
            }
            if(key == "Duracion") input.root.helperText = context.getString(R.string.services_duration_help)
            field.max_length?.let { input.root.isCounterEnabled = true; input.root.counterMaxLength = it }
            input.input.setText(record?.payload?.serviceText(key).orEmpty())
            input.input.doOnTextChanged { _, _, _, _ -> input.root.error = null }
            layouts[key] = input.root; b.fields.addView(input.root)
            validators.add {
                val value = input.input.text.toString().trim()
                val error = when {
                    field.required && value.isEmpty() -> context.getString(if(key == "Nombre_Servicio") R.string.services_name_required else R.string.services_required)
                    field.max_length != null && value.length > field.max_length -> context.getString(R.string.services_max_length, field.max_length)
                    key == "Precio" && (value.isNotEmpty() || field.required) && ServicePresentation.priceValue(value, field) == null -> context.getString(R.string.services_price_invalid)
                    else -> null
                }
                input.root.error = error; error == null
            }
        }
        fun error(text: String) { b.error.text = text; b.error.visibility = View.VISIBLE }
        fun focusError() { layouts.values.firstOrNull { !it.error.isNullOrBlank() }?.editText?.requestFocus() }
        if(fields.isEmpty()) error(context.getString(R.string.services_unavailable_form))
        b.cancel.setOnClickListener { dialog.dismiss() }
        saving = { value ->
            b.save.isEnabled = !value && fields.isNotEmpty(); b.cancel.isEnabled = !value
            b.save.setText(if(value) R.string.services_saving else R.string.services_save)
            b.progress.visibility = if(value) View.VISIBLE else View.GONE
            layouts.values.forEach { it.editText?.isEnabled = !value }; dialog.setCancelable(!value)
        }
        savingChanged()
        b.save.setOnClickListener {
            if(vm.serviceSaving.value || (if(record == null) !permissions().create else !permissions().update)) return@setOnClickListener
            b.error.visibility = View.GONE
            if(validators.map { it() }.any { !it }) { focusError(); return@setOnClickListener }
            val values = layouts.mapValues { it.value.editText?.text.toString() }
            val payload = ServicePresentation.payload(fields, values)
            saving?.invoke(true)
            vm.save("servicios", record?.id, payload,
                success = { dialog.dismiss(); notify(if(record == null) R.string.services_created else R.string.services_updated) },
                finished = { if(dialog.isShowing) saving?.invoke(false) },
                failure = { failure ->
                    val body = if(failure is HttpException && failure.code() in listOf(400,409,422)) failure.response()?.errorBody()?.string() else null
                    val errors = ServicePresentation.errors(body)
                    errors.forEach { (key,value) -> layouts[key]?.error = friendly(value) }
                    if(dialog.isShowing) { error((errors["non_field_errors"] ?: errors["detail"])?.let(::friendly) ?: context.getString(message(failure))); focusError() }
                })
        }
        show(dialog, b.root)
    }
    private fun friendly(raw: String): String {
        var text = raw
        labels.forEach { (key, id) -> text = text.replace(key, context.getString(id)) }
        return when {
            text.contains("unique", true) || text.contains("already exists", true) -> context.getString(R.string.services_duplicate)
            text.contains("required", true) || text.contains("blank", true) -> context.getString(R.string.services_required)
            text.contains("valid integer", true) || text.contains("valid number", true) -> context.getString(R.string.services_price_invalid)
            else -> text
        }
    }
    private fun message(error: Exception, deleting: Boolean = false) = when(error) {
        is HttpException -> when(error.code()) { 401 -> R.string.services_session_expired; 403 -> R.string.services_forbidden
            409 -> if(deleting) R.string.services_delete_related else R.string.services_duplicate
            400,422 -> R.string.services_invalid; else -> R.string.services_error }
        is com.softlutionic.mobile.domain.auth.AuthorizationDenied -> R.string.services_forbidden
        is java.io.IOException -> if(deleting) R.string.services_offline_delete else R.string.services_offline_error
        else -> R.string.services_error
    }
    companion object {
        val labels = mapOf("Nombre_Servicio" to R.string.services_name, "Descripcion" to R.string.services_description,
            "Precio" to R.string.services_price, "Duracion" to R.string.services_duration)
        val sortLabels = listOf(R.string.services_sort_az, R.string.services_sort_za, R.string.services_sort_price_asc, R.string.services_sort_price_desc)
    }
}
