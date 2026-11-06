package com.softlutionic.mobile.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.softlutionic.mobile.data.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers

class SoftlutionicMessagingService: FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val graph=AppGraph.get(this)
        graph.scope.launch {
            try {
                if(graph.repository.currentIdentity.value==null) graph.repository.restoreIdentity()
                if(DeviceNotices.allowed(this@SoftlutionicMessagingService)) graph.repository.registerPush(token)
            } catch(e: Exception) { if(e is CancellationException) throw e /* Retry at next authenticated startup. */ }
        }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val graph=AppGraph.get(this)
        val session=graph.session
        val id=message.data["notification_id"]?.takeIf { it.isNotBlank() } ?: return
        val user=message.data["user_id"] ?: return
        // Data-only messages are ignored after logout or account switch.
        if(session.access==null || session.pushUserId!=user) return
        val alreadyRead=runBlocking(Dispatchers.IO) {
            runCatching { graph.repository.cachedOptions("notificaciones").any {
                it["id"]?.asString==id && it["leida"]?.takeIf { value -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean }?.asBoolean==true
            } }.getOrDefault(false)
        }
        if(alreadyRead || session.pushUserId!=user) return
        DeviceNotices.show(this,id,message.data["tipo"])
        graph.noticeEvents.tryEmit(Unit)
    }
}
