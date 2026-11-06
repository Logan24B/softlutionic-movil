package com.softlutionic.mobile.ui
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.softlutionic.mobile.domain.model.Record
import androidx.core.content.ContextCompat
import com.softlutionic.mobile.R
class RecordAdapter(private val records: List<Record>,private val click: (Record)->Unit): RecyclerView.Adapter<RecordAdapter.Holder>() {
 class Holder(val card: MaterialCardView,val text: TextView): RecyclerView.ViewHolder(card)
 override fun onCreateViewHolder(parent: ViewGroup,type: Int): Holder {
  val density=parent.resources.displayMetrics.density
  fun dp(value: Int)=(value*density).toInt()
  val card = MaterialCardView(parent.context).apply {
   layoutParams=ViewGroup.MarginLayoutParams(-1,-2).apply { setMargins(dp(4),dp(6),dp(4),dp(6)) }
   radius=dp(20).toFloat(); setCardBackgroundColor(ContextCompat.getColor(context,R.color.login_surface))
   strokeColor=ContextCompat.getColor(context,R.color.login_muted); strokeWidth=dp(1)
   isClickable=true
  }
  val text=TextView(parent.context).apply { setPadding(dp(16),dp(16),dp(16),dp(16)); textSize=16f; setTextColor(ContextCompat.getColor(context,R.color.login_text)) }
  card.addView(text); return Holder(card,text)
 }
 override fun getItemCount()=records.size
 override fun onBindViewHolder(holder: Holder,position: Int) { val item=records[position]; holder.text.text=item.title + "\n" + item.detail.lineSequence().take(3).joinToString("\n"); holder.card.setOnClickListener { click(item) } }
}
