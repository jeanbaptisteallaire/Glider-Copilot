package com.neutronstar.glidercopilot.precog

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Ancien `java.time.Clock.fixed(instant, UTC)`. */
class FixedClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}
