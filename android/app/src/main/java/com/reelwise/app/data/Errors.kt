package com.reelwise.app.data

import java.io.IOException
import retrofit2.HttpException

/** A sentence a person can act on, rather than an exception class name. */
fun Throwable.userMessage(): String = when (this) {
    is IOException -> "Can't reach Reelwise. Check your connection and try again."
    is HttpException -> when (code()) {
        404 -> "We couldn't find that title."
        in 500..599 -> "Our recommendation service is having trouble. Try again in a moment."
        else -> "Something went wrong (${code()})."
    }
    else -> "Something went wrong. Try again."
}
