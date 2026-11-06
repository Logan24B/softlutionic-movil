package com.softlutionic.mobile.ui.services

import android.text.TextUtils
import android.view.*
import androidx.recyclerview.widget.*
import com.softlutionic.mobile.R
import com.softlutionic.mobile.databinding.ItemServiceBinding
import com.softlutionic.mobile.domain.model.Record

class ServiceAdapter(private val click: (Record) -> Unit) : ListAdapter<Record, ServiceAdapter.Holder>(Diff) {
    class Holder(val binding: ItemServiceBinding) : RecyclerView.ViewHolder(binding.root)
    private object Diff : DiffUtil.ItemCallback<Record>() {
        override fun areItemsTheSame(a: Record, b: Record) = a.id == b.id
        override fun areContentsTheSame(a: Record, b: Record) = a == b
    }
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(ItemServiceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val record = getItem(position)
        bind(holder.binding, record)
        holder.binding.root.setOnClickListener { click(record) }
    }
    companion object {
        fun bind(b: ItemServiceBinding, record: Record, full: Boolean = false) {
            val ctx = b.root.context
            b.name.text = record.payload.serviceText("Nombre_Servicio").ifBlank { ctx.getString(R.string.services_no_name) }
            b.price.text = ServicePresentation.price(record) ?: ctx.getString(R.string.services_no_data)
            b.price.contentDescription = ctx.getString(R.string.services_price) + ": " + b.price.text
            b.description.text = record.payload.serviceText("Descripcion").ifBlank { ctx.getString(R.string.services_no_data) }
            b.description.maxLines = if(full) Int.MAX_VALUE else 3
            b.description.ellipsize = if(full) null else TextUtils.TruncateAt.END
            b.duration.text = ctx.getString(R.string.services_duration_value, record.payload.serviceText("Duracion").ifBlank { ctx.getString(R.string.services_no_data) })
            if(full) { b.root.isClickable = false; b.root.isFocusable = false }
        }
    }
}
