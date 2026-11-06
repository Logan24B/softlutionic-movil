package com.softlutionic.mobile.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.softlutionic.mobile.R
import java.math.BigDecimal
import kotlin.math.max

/** Lightweight chart: full values also appear in the accessible legend below it. */
class FinancialChartView(context: Context, private val points: List<ChartPoint>, private val donut: Boolean, private val monetary: Boolean, private val diagonalLabels: Boolean=false): View(context) {
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private var chosen=-1
 private val density=resources.displayMetrics.density
 private fun dp(value: Float)=value*density
 private fun color(id: Int)=ContextCompat.getColor(context,id)
 private fun rightInset(): Float {
  if(!diagonalLabels || points.isEmpty()) return dp(10f)
  paint.textSize=dp(12f)
  return dp(10f)+points.maxOf { paint.measureText(it.label) }*.5f
 }
 private val palette=listOf(R.color.login_accent,R.color.login_cyan,R.color.dashboard_green,R.color.dashboard_amber,R.color.login_error)
 fun pointColor(index: Int): Int {
  val label=points[index].label.lowercase(java.util.Locale.ROOT)
  return color(when {
   label.contains("no pag") || label.contains("pendiente") -> R.color.dashboard_amber
   label.contains("pagad") -> R.color.dashboard_green
   label.contains("mora") || label.contains("vencid") -> R.color.login_error
   else -> palette[index%palette.size]
  })
 }
 init {
  minimumHeight=dp(190f).toInt()
  isClickable=true; isFocusable=true
  contentDescription=points.joinToString("; ") { it.label+": "+if(monetary) FinancialFormat.money(it.value) else FinancialFormat.count(it.value) }
 }
 override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
  setMeasuredDimension(resolveSize(dp(280f).toInt(),widthMeasureSpec),resolveSize(dp(210f).toInt(),heightMeasureSpec))
 }
 override fun onDraw(canvas: Canvas) {
  super.onDraw(canvas)
  val positive=points.map { it.value.max(BigDecimal.ZERO).toDouble() }
  val total=positive.sum()
  if(points.isEmpty() || (donut && total<=0.0)) {
   paint.color=color(R.color.login_muted); paint.textSize=dp(13f)
   canvas.drawText(context.getString(R.string.no_series),dp(8f),height/2f,paint); return
  }
  if(donut) {
   val radius=minOf(width*0.32f,height*0.35f)
   val box=RectF(width/2f-radius,height/2f-radius,width/2f+radius,height/2f+radius)
   paint.style=Paint.Style.STROKE; paint.strokeWidth=dp(24f)
   var angle=-90f
   points.indices.forEach { i ->
    val sweep=(positive[i]/total*360).toFloat()
    paint.color=pointColor(i); canvas.drawArc(box,angle,sweep,false,paint); angle+=sweep
   }
   paint.style=Paint.Style.FILL; paint.color=color(R.color.login_text); paint.textAlign=Paint.Align.CENTER; paint.textSize=dp(22f)
   canvas.drawText(FinancialFormat.count(points.fold(BigDecimal.ZERO) { acc,p -> acc+p.value }),width/2f,height/2f+dp(8f),paint)
  } else {
   val maxValue=max(positive.maxOrNull() ?: 0.0,1.0)
   // Reserve measured space for complete department names at a 60 degree angle.
   paint.textSize=dp(12f)
   val labelSpace=if(diagonalLabels) points.maxOf { paint.measureText(it.label) }*.866f+dp(24f) else dp(28f)
   val left=dp(10f); val bottom=height-labelSpace; val top=dp(36f)
   val step=(width-left-rightInset())/points.size
   paint.strokeWidth=dp(1f); paint.color=color(R.color.login_muted); paint.alpha=30
   for(i in 0..3) { val y=top+(bottom-top)*i/3; canvas.drawLine(left,y,width-left,y,paint) }
   paint.alpha=255
   points.indices.forEach { i ->
    paint.color=if(i==chosen) color(R.color.login_cyan) else color(R.color.login_accent)
    val h=((positive[i]/maxValue)*(bottom-top)).toFloat()
    canvas.drawRoundRect(left+i*step+step*.18f,bottom-h,left+(i+1)*step-step*.18f,bottom,dp(4f),dp(4f),paint)
    paint.textAlign=Paint.Align.CENTER; paint.textSize=dp(11f); paint.color=color(R.color.login_muted)
    if(diagonalLabels) {
     canvas.save()
     canvas.translate(left+(i+.5f)*step,bottom+dp(12f))
     canvas.rotate(60f); paint.textAlign=Paint.Align.LEFT; paint.textSize=dp(12f)
     canvas.drawText(points[i].label,0f,0f,paint)
     canvas.restore()
    } else canvas.drawText(points[i].label,left+(i+.5f)*step,height-dp(8f),paint)
   }
   val index=chosen.takeIf { it in points.indices } ?: positive.indices.maxByOrNull { positive[it] } ?: 0
   paint.textAlign=Paint.Align.LEFT; paint.textSize=dp(12f); paint.color=color(R.color.login_text)
   val point=points[index]
   canvas.drawText(point.label+" · "+if(monetary) FinancialFormat.money(point.value) else FinancialFormat.count(point.value),left,dp(20f),paint)
  }
  paint.textAlign=Paint.Align.LEFT
 }
 override fun onTouchEvent(event: MotionEvent): Boolean {
  if(event.action==MotionEvent.ACTION_UP) {
   if(!donut && points.isNotEmpty()) {
    chosen=((event.x-dp(10f))/((width-dp(10f)-rightInset())/points.size)).toInt().coerceIn(points.indices)
    announceForAccessibility(points[chosen].label+": "+if(monetary) FinancialFormat.money(points[chosen].value) else FinancialFormat.count(points[chosen].value))
    invalidate()
   }
   performClick(); return true
  }
  return event.action==MotionEvent.ACTION_DOWN || super.onTouchEvent(event)
 }
 override fun performClick(): Boolean { super.performClick(); return true }
}
