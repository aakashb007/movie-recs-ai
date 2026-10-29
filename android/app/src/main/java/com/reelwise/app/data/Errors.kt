package com.reelwise.app.data

import com.reelwise.app.engine.MissingKeyException
import com.reelwise.app.engine.TmdbAuthException
import com.reelwise.app.engine.TmdbException
import java.io.IOException

/** A sentence a person can act on, rather than an exception class name. */
fun Throwable.userMessage(): String = when (this) {
    is MissingKeyException -> "Add your TMDB key in Settings"
    is TmdbAuthException -> "Your TMDB key was rejected. Check it in Settings."
    is TmdbException -> when (status) {
        404 -> "We couldn't find that title."
        429 -> "TMDB is busy. Try again in a moment."
        else -> "TMDB is having trouble (${status}). Try again in a moment."
    }
    is IOException -> "Can't reach TMDB. Check your connection and try again."
    else -> "Something went wrong. Try again."
}
