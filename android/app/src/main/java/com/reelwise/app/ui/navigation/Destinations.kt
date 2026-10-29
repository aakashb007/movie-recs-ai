package com.reelwise.app.ui.navigation

import kotlinx.serialization.Serializable

object Dest {
    @Serializable data object Onboarding
    @Serializable data object Home
    @Serializable data object Ask
    @Serializable data object Watchlist
    @Serializable data object Profile
    @Serializable data class Detail(val type: String, val id: Int)
}
