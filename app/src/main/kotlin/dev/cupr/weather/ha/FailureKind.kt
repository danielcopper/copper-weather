package dev.cupr.weather.ha

import java.io.IOException
import java.io.InterruptedIOException

/**
 * What kind of failure an answer from Home Assistant, or its absence, was; the app's screen names it in plain words
 * (D28), the log keeps the technical reason.
 */
enum class FailureKind {
    /**
     * No connection, or a broken one: the host is unknown, refuses, or its TLS fails; or a gateway answers 502, 503 or
     * 504.
     */
    UNREACHABLE,

    /** No answer in time. */
    TIMEOUT,

    /** Home Assistant has no app sensor, or one without its `data`. */
    NOT_FOUND,

    /** Home Assistant refused the access token. */
    REFUSED,

    /** The app sensor's data is in a newer schema version than the app reads (D30). */
    NEWER_SCHEMA,

    /** Any other answer: another status, or one that is not what Home Assistant sends. */
    UNEXPECTED,
    ;

    companion object {
        /** A gateway's statuses: Home Assistant behind it is out of reach. */
        private val GATEWAY = setOf(502, 503, 504)

        /** An HTTP status no other rule names: a gateway's 502, 503 or 504 is no connection, any other unexpected. */
        fun ofStatus(status: Int): FailureKind = if (status in GATEWAY) UNREACHABLE else UNEXPECTED

        /** An OkHttp call's exception: a timeout (OkHttp's call timeout, a socket's read) or no connection. */
        fun of(e: Throwable): FailureKind =
            when (e) {
                is InterruptedIOException -> TIMEOUT
                is IOException -> UNREACHABLE
                else -> UNEXPECTED
            }
    }
}
