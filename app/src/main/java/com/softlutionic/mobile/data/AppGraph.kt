package com.softlutionic.mobile.data

import android.content.Context
import androidx.room.Room
import com.softlutionic.mobile.data.local.CacheDatabase
import com.softlutionic.mobile.data.remote.createApi
import com.softlutionic.mobile.data.repository.SoftlutionicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** UI and Firebase service share the same session, Retrofit and Room instance. */
class AppGraph private constructor(context: Context) {
    val session=SessionManager(context)
    val repository=SoftlutionicRepository(createApi(session),session,
        Room.databaseBuilder(context,CacheDatabase::class.java,"softlutionic-cache.db").build().cache())
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val noticeEvents=kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity=1)
    companion object {
        @Volatile private var instance: AppGraph?=null
        fun get(context: Context): AppGraph=instance ?: synchronized(this) {
            instance ?: AppGraph(context.applicationContext).also { instance=it }
        }
    }
}
