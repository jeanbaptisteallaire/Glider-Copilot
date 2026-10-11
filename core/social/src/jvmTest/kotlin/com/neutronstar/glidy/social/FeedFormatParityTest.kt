package com.neutronstar.glidy.social

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** FeedFormat rend exactement les anciens String.format(Locale.FRANCE) de FeedScreen (Android inchangé). */
class FeedFormatParityTest {
    @Test fun sameTextsAsJavaFormat() {
        for (s in listOf(0L, 59, 60, 2_519, 3_600, 3_660, 11_100, 36_000, 86_399)) {
            val h = s / 3600; val m = s % 3600 / 60
            assertEquals(if (h == 0L) "$m min" else "%d h %02d".format(Locale.FRANCE, h, m), FeedFormat.duration(s))
        }
        for (mtr in listOf(0L, 49, 950, 12_345, 99_949, 99_950, 99_999, 100_000, 100_500, 101_500, 345_678)) {
            val java = if (mtr >= 100_000) "%.0f km".format(Locale.FRANCE, mtr / 1000.0) else "%.1f km".format(Locale.FRANCE, mtr / 1000.0)
            assertEquals(java, FeedFormat.km(mtr))
        }
    }
}
