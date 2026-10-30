package com.reelwise.app.ui.navigation

import kotlinx.serialization.Serializable

object Dest {
    /** API key setup; `fromProfile` means there is a screen to go back to. */
    @Serializable data class Keys(val fromProfile: Boolean = false)
    @Serializable data object Onboarding
    @Serializable data object Home
    @Serializable data object Browse
    @Serializable data object Ask
    @Serializable data object Watchlist
    @Serializable data object Profile
    @Serializable data class Detail(val type: String, val id: Int)
}

enum class Start { KEYS, QUIZ, HOME }
