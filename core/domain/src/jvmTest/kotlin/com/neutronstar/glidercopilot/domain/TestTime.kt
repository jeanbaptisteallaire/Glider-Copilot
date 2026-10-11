package com.neutronstar.glidercopilot.domain

import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

// iOS / KMP étape 1 : raccourcis java.time gardés pour que les tests existants restent lisibles tels quels.
fun Instant.plusSeconds(s: Long): Instant = this + s.seconds
fun Instant.plusSeconds(s: Int): Instant = this + s.seconds
fun Instant.isAfter(o: Instant): Boolean = this > o
fun Instant.plusMillis(ms: Long): Instant = this + ms.milliseconds
fun Instant.plusNanos(ns: Long): Instant = this + ns.nanoseconds
