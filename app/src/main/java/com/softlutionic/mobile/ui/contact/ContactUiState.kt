package com.softlutionic.mobile.ui.contact

enum class ContactPhase { LOADING, SUCCESS, EMPTY, ERROR }
data class ContactUiState(val phase: ContactPhase=ContactPhase.LOADING,val error: String="",val denied: Boolean=false)
