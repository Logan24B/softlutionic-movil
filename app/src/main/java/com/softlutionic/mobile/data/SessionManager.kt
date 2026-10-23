package com.softlutionic.mobile.data
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
class SessionManager(context: Context) {
 private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
 val authenticated = MutableStateFlow(access != null)
 private fun key(): SecretKey {
  val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  return (store.getKey("softlutionic_session", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder("softlutionic_session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 private fun read(name: String): String? = runCatching {
  val data = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
  Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0,12))); String(doFinal(data.copyOfRange(12,data.size)), Charsets.UTF_8) }
 }.getOrNull()
 val access get() = read("access")
 val refresh get() = read("refresh")
 @Synchronized fun save(access: String, refresh: String) {
  val edit = prefs.edit()
  mapOf("access" to access, "refresh" to refresh).forEach { (name,value) ->
   val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
   edit.putString(name, Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
  }
  edit.commit(); authenticated.value = true
 }
 @Synchronized fun clear() { prefs.edit().clear().commit(); authenticated.value = false }
}
