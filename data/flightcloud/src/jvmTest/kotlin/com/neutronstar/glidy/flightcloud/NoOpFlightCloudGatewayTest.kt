package com.neutronstar.glidy.flightcloud

import org.junit.Assert.assertFalse
import org.junit.Test

class NoOpFlightCloudGatewayTest {
    @Test
    fun cloudIsDisabledByDefault() {
        assertFalse(NoOpFlightCloudGateway.enabled)
    }
}

