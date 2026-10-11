package com.neutronstar.glidy.flightcloud

import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.CloudQueueResult
import com.neutronstar.glidy.flightarchive.FlightCloudGateway

/** Valeur sûre par défaut : aucun réseau et aucun envoi tant que login et Supabase sont absents. */
object NoOpFlightCloudGateway : FlightCloudGateway {
    override val enabled: Boolean = false

    override suspend fun enqueueUpload(flight: ArchivedFlight): CloudQueueResult =
        CloudQueueResult.Disabled
}

