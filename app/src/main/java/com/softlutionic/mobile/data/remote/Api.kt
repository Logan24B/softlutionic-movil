package com.softlutionic.mobile.data.remote
import com.google.gson.*
import retrofit2.http.*
data class LoginRequest(val username: String, val password: String)
data class Tokens(val access: String, val refresh: String)
data class Capability(val read: Boolean, val create: Boolean, val update: Boolean, val delete: Boolean)
data class Field(val name: String, val label: String, val required: Boolean, val type: String, val relation: String?, val max_length: Int?, val read_only: Boolean = false)
data class Module(val name: String, val permissions: Capability, val fields: List<Field>)
interface Api {
 @GET("api/analytics/v1/resumen-financiero/") suspend fun analyticsSummary(@Query("start") start: String? = null, @Query("end") end: String? = null): JsonObject
 @GET("api/analytics/v1/ranking-servicios/") suspend fun analyticsServices(@Query("start") start: String? = null, @Query("end") end: String? = null): JsonObject
 @POST("api/mobile/v1/push/devices/") suspend fun registerPush(@Body body: JsonObject): JsonObject
 @POST("api/mobile/v1/push/devices/unregister/") suspend fun unregisterPush(@Body body: JsonObject): retrofit2.Response<Unit>
 @GET("api/mobile/v1/notificaciones/no-leidas/count/") suspend fun unreadNoticeCount(): JsonObject
 @PATCH("api/mobile/v1/notificaciones/{id}/leer/") suspend fun readNotice(@Path("id") id: String): JsonObject
 @POST("api/mobile/v1/auth/login/") suspend fun login(@Body body: LoginRequest): JsonObject
 @GET("api/mobile/v1/auth/me/") suspend fun me(): JsonObject
 @GET("api/mobile/v1/schema/") suspend fun schema(): List<Module>
 @GET("api/mobile/v1/{module}/") suspend fun list(@Path("module") module: String): JsonArray
 @POST("api/mobile/v1/{module}/") suspend fun create(@Path("module") module: String, @Body body: JsonObject): JsonObject
 @POST("api/mobile/v1/facturas/") suspend fun createInvoice(@Body body: JsonObject): retrofit2.Response<JsonObject>
 @PATCH("api/mobile/v1/{module}/{id}/") suspend fun update(@Path("module") module: String, @Path("id") id: String, @Body body: JsonObject): JsonObject
 @DELETE("api/mobile/v1/{module}/{id}/") suspend fun delete(@Path("module") module: String, @Path("id") id: String)
 @GET("api/mobile/v1/dashboard/{section}/") suspend fun dashboard(@Path("section") section: String, @Query("start") start: String? = null, @Query("end") end: String? = null): JsonObject
}
