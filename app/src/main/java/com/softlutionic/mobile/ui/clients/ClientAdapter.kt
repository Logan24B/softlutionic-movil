package com.softlutionic.mobile.ui.clients

import android.view.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.softlutionic.mobile.R
import com.softlutionic.mobile.databinding.ItemClientBinding

class ClientAdapter(private val click: (ClientRow) -> Unit) : ListAdapter<ClientRow, ClientAdapter.Holder>(Diff) {
    class Holder(val binding: ItemClientBinding) : RecyclerView.ViewHolder(binding.root)
    private object Diff : DiffUtil.ItemCallback<ClientRow>() {
        override fun areItemsTheSame(old: ClientRow, new: ClientRow) = old.record.id == new.record.id
        override fun areContentsTheSame(old: ClientRow, new: ClientRow) = old == new
    }
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(ItemClientBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position); val b = holder.binding; val context = b.root.context
        fun display(value: String) = value.ifBlank { context.getString(R.string.clients_no_data) }
        b.avatar.text = row.initials
        b.name.text = row.name.ifBlank { context.getString(R.string.clients_no_name) }
        b.department.text = row.department.ifBlank { context.getString(R.string.clients_department_missing) }
        b.phone.text = context.getString(R.string.clients_phone) + ": " + display(row.record.payload.clientText("Telefono"))
        b.email.text = context.getString(R.string.clients_email) + ": " + display(row.record.payload.clientText("Correo"))
        b.idCard.text = context.getString(R.string.clients_id_card) + ": " + display(row.record.payload.clientText("Cedula"))
        b.state.setText(when (row.active) { true -> R.string.clients_active; false -> R.string.clients_inactive; null -> R.string.clients_unknown_state })
        b.state.setTextColor(ContextCompat.getColor(context, when (row.active) { true -> R.color.dashboard_green; false -> R.color.login_error; null -> R.color.login_muted }))
        b.root.setOnClickListener { click(row) }
    }
}
