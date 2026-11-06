package com.softlutionic.mobile.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.softlutionic.mobile.MainActivity
import com.softlutionic.mobile.R

object DeviceNotices {
    const val OPEN="open_notifications"
    private const val CHANNEL="softlutionic_events"
    fun allowed(context: Context)=
        (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun show(context: Context,id: String,type: String?) {
        if(!allowed(context)) return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Avisos de Softlutionic",NotificationManager.IMPORTANCE_DEFAULT))
        val intent=Intent(context,MainActivity::class.java).putExtra(OPEN,true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title=when(type) {
            "factura_generada" -> "Nueva factura generada"
            "pago_registrado" -> "Nuevo pago registrado"
            else -> "Nueva notificación"
        }
        val notice=Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_notice)
            .setContentTitle(title).setContentText("Tienes un aviso pendiente. Abre Softlutionic para consultarlo.")
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build()
        manager.notify(id,0,notice)
    }
    fun cancel(context: Context,id: String)=context.getSystemService(NotificationManager::class.java).cancel(id,0)
    fun clear(context: Context)=context.getSystemService(NotificationManager::class.java).cancelAll()
}
