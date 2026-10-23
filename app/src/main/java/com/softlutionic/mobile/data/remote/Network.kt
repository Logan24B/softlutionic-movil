package com.softlutionic.mobile.data.remote
import com.softlutionic.mobile.BuildConfig
import com.softlutionic.mobile.data.SessionManager
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
class AuthInterceptor(private val session: SessionManager): Interceptor {
 override fun intercept(chain: Interceptor.Chain): Response {
  val request = chain.request().newBuilder()
  session.access?.let { request.header("Authorization", "Bearer $it") }
  return chain.proceed(request.build())
 }
}
class TokenAuthenticator(private val session: SessionManager): Authenticator {
 private val refreshClient = OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS).build()
 @Synchronized override fun authenticate(route: Route?, response: Response): Request? {
  if ((response.request.url.encodedPath.endsWith("/auth/login/") || response.request.url.encodedPath.endsWith("/auth/refresh/")) || response.priorResponse != null) return null
  val current = session.access ?: return null
  if (response.request.header("Authorization") != "Bearer $current") return response.request.newBuilder().header("Authorization", "Bearer $current").build()
  val refresh = session.refresh ?: return null
  val body = com.google.gson.JsonObject().apply { addProperty("refresh",refresh) }.toString().toRequestBody("application/json".toMediaType())
  try {
   refreshClient.newCall(Request.Builder().url(BuildConfig.BASE_URL + "api/mobile/v1/auth/refresh/").post(body).build()).execute().use {
    if (!it.isSuccessful) { if(it.code in listOf(400,401,403)) session.clear(); return null }
    val json = JsonParser.parseString(it.body!!.string()).asJsonObject
    session.save(json["access"].asString, json.get("refresh")?.asString ?: refresh)
   }
  } catch (_: java.io.IOException) { return null }
  return response.request.newBuilder().header("Authorization", "Bearer ${session.access}").build()
 }
}
fun createApi(session: SessionManager): Api = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL)
 .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(session)).authenticator(TokenAuthenticator(session))
 .retryOnConnectionFailure(false).connectTimeout(20,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).build())
 .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
