package com.softlutionic.mobile.ui.clients

import android.view.View
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonParser
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.mapper.toRecord
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.databinding.ViewClientsBinding
import com.softlutionic.mobile.domain.model.Record
import com.softlutionic.mobile.viewmodel.MainViewModel

/** Specialized client presentation with shared JSON records, cache and CRUD. */
class ClientsController(val binding: ViewClientsBinding, private val initialModule: Module, private val vm: MainViewModel) {
    private val context = binding.root.context
    private var records = emptyList<Record>()
    private val module get() = vm.modules.value.find { it.name == "clientes" } ?: initialModule
    private fun departments() = ClientPresentation.departments(vm.clientDepartments.value, records)
    private val sheets = ClientSheets(context, vm, { module }, ::departments) { message ->
        if(binding.root.isAttachedToWindow) Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
    private val adapter = ClientAdapter { row -> sheets.detail(row) }
    init {
        binding.records.layoutManager = LinearLayoutManager(context)
        binding.records.adapter = adapter
        binding.search.setText(vm.clientFilters.value.query)
        binding.search.doOnTextChanged { value, _, _, _ -> vm.clientFilters.value = vm.clientFilters.value.copy(query = value.toString()) }
        binding.filters.setOnClickListener { sheets.filters() }
        binding.refresh.setOnClickListener { if(!vm.busy.value) { vm.refresh(); vm.loadClientDepartments(module, true) } }
        binding.create.setOnClickListener {
            if(!vm.clientSaving.value && module.permissions.create) {
                vm.loadClientDepartments(module)
                sheets.form(null)
                binding.create.animate().scaleX(.97f).scaleY(.97f).setDuration(100).withEndAction {
                    binding.create.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
            }
        }
        vm.loadClientDepartments(module)
        renderList(); referencesChanged(); busyChanged()
    }
    fun submit(json: String?) {
        records = json?.let { JsonParser.parseString(it).asJsonArray.map { value -> value.asJsonObject.toRecord() } }.orEmpty()
        renderList()
    }
    fun referencesChanged() {
        renderList()
        val loading = vm.clientDepartmentsLoading.value
        val unavailable = vm.clientDepartmentsUnavailable.value
        binding.referenceStatus.visibility = if(loading || unavailable) View.VISIBLE else View.GONE
        binding.referenceStatus.setText(if(loading) R.string.clients_departments_loading else R.string.clients_department_retry)
        sheets.catalogChanged()
    }
    fun busyChanged() {
        val busy = vm.busy.value || vm.clientSaving.value
        binding.create.visibility = if(module.permissions.create) View.VISIBLE else View.GONE
        binding.create.isEnabled = !busy
        binding.refresh.isEnabled = !busy
        sheets.savingChanged(vm.clientSaving.value)
        renderList()
    }
    fun renderList() {
        val filter = vm.clientFilters.value
        val options = departments()
        val visible = records.map { ClientPresentation.row(it, options) }.filter { ClientPresentation.matches(it, filter) }
        adapter.submitList(visible)
        binding.count.text = context.getString(R.string.clients_count, visible.size, records.size)
        val filtered = filter.status != ClientStatusFilter.ALL || filter.departmentId != null
        binding.filters.setText(if(filtered) R.string.clients_filter_active else R.string.clients_filters)
        binding.empty.visibility = if(visible.isEmpty()) View.VISIBLE else View.GONE
        val searching = filtered || filter.query.isNotBlank()
        binding.emptyTitle.setText(when {
            records.isEmpty() && vm.busy.value -> R.string.clients_loading
            searching -> R.string.clients_no_matches
            else -> R.string.clients_empty_title
        })
        binding.emptyBody.setText(if(searching) R.string.clients_no_matches_body else R.string.clients_empty_body)
        binding.emptyBody.visibility = if(records.isEmpty() && vm.busy.value) View.GONE else View.VISIBLE
    }
    fun dispose() { sheets.dismiss() }
}
