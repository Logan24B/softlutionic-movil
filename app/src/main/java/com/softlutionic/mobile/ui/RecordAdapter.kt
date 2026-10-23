package com.softlutionic.mobile.ui
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.softlutionic.mobile.domain.model.Record
class RecordAdapter(private val records: List<Record>,private val click: (Record)->Unit): RecyclerView.Adapter<RecordAdapter.Holder>() {
 class Holder(val card: MaterialCardView,val text: TextView): RecyclerView.ViewHolder(card)
 override fun onCreateViewHolder(parent: ViewGroup,type: Int): Holder {
  val card = MaterialCardView(parent.context).apply { layoutParams=ViewGroup.MarginLayoutParams(-1,-2).apply { setMargins(4,6,4,6) }; radius=16f }
  val text=TextView(parent.context).apply { setPadding(24,24,24,24); textSize=16f }
  card.addView(text); return Holder(card,text)
 }
 override fun getItemCount()=records.size
 override fun onBindViewHolder(holder: Holder,position: Int) { val item=records[position]; holder.text.text=item.title + "\n" + item.detail.lineSequence().take(3).joinToString("\n"); holder.card.setOnClickListener { click(item) } }
}
