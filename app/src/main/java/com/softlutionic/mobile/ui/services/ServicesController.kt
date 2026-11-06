package com.softlutionic.mobile.ui.services

import android.view.View
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonParser
import com.softlutionic.mobile.R
import com.softlutionic.mobile.data.mapper.toRecord
import com.softlutionic.mobile.data.remote.Module
import com.softlutionic.mobile.databinding.ViewServicesBinding
import com.softlutionic.mobile.domain.model.Record
import com.softlutionic.mobile.viewmodel.MainViewModel

class ServicesController(val binding: ViewServicesBinding, private val initialModule: Module, private val vm: MainViewModel) {
    private val context = binding.root.context
    private val module get() = vm.effectiveModule(vm.modules.value.find { it.name == "servicios" } ?: initialModule)
    private var records = emptyList<Record>()
    private val sheets = ServiceSheets(context, vm, { module }) { message ->
        if(binding.root.isAttachedToWindow) Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
    private val adapter = ServiceAdapter { sheets.detail(it) }
    init {
        binding.records.layoutManager = LinearLayoutManager(context); binding.records.adapter = adapter
        binding.search.setText(vm.serviceListState.value.query)
        binding.search.doOnTextChanged { text, _, _, _ -> vm.serviceListState.value = vm.serviceListState.value.copy(query = text.toString()) }
        binding.sort.setOnClickListener { sheets.sort() }
        binding.refresh.setOnClickListener { if(!vm.busy.value) vm.refresh() }
        binding.create.setOnClickListener {
            if(!vm.serviceSaving.value && module.permissions.create) sheets.form(null)
        }
        busyChanged()
    }
    fun submit(json: String?) {
        records = json?.let { JsonParser.parseString(it).asJsonArray.map { value -> value.asJsonObject.toRecord() } }.orEmpty()
        renderList()
    }
    fun busyChanged() {
        binding.create.visibility = if(module.permissions.create) View.VISIBLE else View.GONE
        binding.create.isEnabled = !vm.busy.value && !vm.serviceSaving.value
        binding.refresh.isEnabled = !vm.busy.value && !vm.serviceSaving.value
        sheets.savingChanged(); renderList()
    }
    fun renderList() {
        val state = vm.serviceListState.value
        val visible = ServicePresentation.visible(records, state)
        adapter.submitList(visible)
        binding.count.text = context.getString(R.string.services_count, visible.size, records.size)
        binding.sort.contentDescription = context.getString(R.string.services_sort_current, context.getString(ServiceSheets.sortLabels[state.sort.ordinal]))
        binding.empty.visibility = if(visible.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyTitle.setText(when {
            records.isEmpty() && vm.busy.value -> R.string.services_loading
            state.query.isNotBlank() -> R.string.services_no_matches
            else -> R.string.services_empty_title
        })
        binding.emptyBody.setText(if(state.query.isNotBlank()) R.string.services_no_matches_body else R.string.services_empty_body)
        binding.emptyBody.visibility = if(records.isEmpty() && vm.busy.value) View.GONE else View.VISIBLE
    }
    fun dispose() { sheets.dismiss() }
}
