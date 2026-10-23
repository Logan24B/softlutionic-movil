package com.softlutionic.mobile.domain.model
import com.google.gson.JsonObject
data class Record(val id: String, val title: String, val detail: String, val payload: JsonObject)
