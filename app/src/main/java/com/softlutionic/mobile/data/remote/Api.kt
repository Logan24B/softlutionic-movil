package com.softlutionic.mobile.data.remote
import com.google.gson.*
import retrofit2.http.*
data class LoginRequest(val username: String, val password: String)
data class Tokens(val access: String, val refresh: String)
data class Capability(val read: Boolean, val create: Boolean, val update: Boolean, val delete: Boolean)
data class Field(val name: String, val label: String, val required: Boolean, val type: String, val relation: String?, val max_length: Int?)
data class Module(val name: String, val permissions: Capability, val fields: List<Field>)
interface Api {
 @POST("api/mobile/v1/auth/login/") suspend fun login(@Body body: LoginRequest): JsonObject
 @GET("api/mobile/v1/auth/me/") suspend fun me(): JsonObject
 @GET("api/mobile/v1/schema/") suspend fun schema(): List<Module>
 @GET("api/mobile/v1/{module}/") suspend fun list(@Path("module") module: String): JsonArray
 @POST("api/mobile/v1/{module}/") suspend fun create(@Path("module") module: String, @Body body: JsonObject): JsonObject
 @PATCH("api/mobile/v1/{module}/{id}/") suspend fun update(@Path("module") module: String, @Path("id") id: String, @Body body: JsonObject): JsonObject
 @DELETE("api/mobile/v1/{module}/{id}/") suspend fun delete(@Path("module") module: String, @Path("id") id: String)
 @GET("api/mobile/v1/dashboard/{section}/") suspend fun dashboard(@Path("section") section: String): JsonObject
}
