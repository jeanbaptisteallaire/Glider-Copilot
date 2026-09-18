package com.neutronstar.glidy.myflights

/** Contrat de navigation stable pour la future page Mes vols. */
interface MyFlightsNavigator {
    fun openFlight(flightId: String)
    fun closeMyFlights()
}

