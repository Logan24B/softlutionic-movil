package com.softlutionic.mobile.ui.invoices

import android.content.Context
import android.content.res.ColorStateList
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.softlutionic.mobile.R

internal class InvoiceViews(context: Context) {
    val ctx=ContextThemeWrapper(context,R.style.ThemeOverlay_Softlutionic_Login)
    fun dp(n: Int)=(n*ctx.resources.displayMetrics.density).toInt()
    fun color(id: Int)=ContextCompat.getColor(ctx,id)
    fun column(padding: Int=16)=LinearLayout(ctx).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(padding),dp(8),dp(padding),dp(12)) }
    fun text(value: String,size: Float=16f,muted: Boolean=false)=TextView(ctx).apply {
        text=value; textSize=size; setTextColor(color(if(muted) R.color.login_muted else R.color.login_text)); setPadding(0,dp(5),0,dp(5))
        if(size>=20) { setTypeface(typeface,android.graphics.Typeface.BOLD); isAccessibilityHeading=true }
    }
    fun button(label: String,primary: Boolean=true,action: ()->Unit)=MaterialButton(ctx).apply {
        text=label; isAllCaps=false; cornerRadius=dp(14); minHeight=dp(52); insetTop=0; insetBottom=0; elevation=0f
        setPadding(dp(8),0,dp(8),0); letterSpacing=0f
        backgroundTintList=ColorStateList.valueOf(color(if(primary) R.color.contracts_primary else R.color.login_surface))
        setTextColor(color(if(primary) R.color.login_text else R.color.login_cyan))
        if(!primary) { strokeWidth=dp(1); strokeColor=ColorStateList.valueOf(color(R.color.login_cyan)) }
        rippleColor=ColorStateList.valueOf(0x3372DCEB); setOnClickListener { action() }
    }
    fun card()=MaterialCardView(ctx).apply {
        layoutParams=ViewGroup.MarginLayoutParams(-1,-2).apply { bottomMargin=dp(12) }
        radius=dp(18).toFloat(); setCardBackgroundColor(color(R.color.login_surface)); strokeWidth=dp(1); strokeColor=0x263D5277
    }
    fun badge(value: String)=text(value.uppercase(),12f).apply {
        setPadding(dp(12),dp(6),dp(12),dp(6)); setBackgroundResource(R.drawable.login_status)
        setTextColor(color(when {
            value.contains("pagad",true) -> R.color.dashboard_green
            value.contains("pendiente",true) -> R.color.dashboard_amber
            value.contains("vencid",true) || value.contains("mora",true) -> R.color.login_error
            else -> R.color.login_cyan
        }))
    }
}
