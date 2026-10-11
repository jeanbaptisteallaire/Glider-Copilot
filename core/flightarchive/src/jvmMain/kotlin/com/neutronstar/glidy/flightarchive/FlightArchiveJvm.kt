package com.neutronstar.glidy.flightarchive

import okio.source
import java.io.InputStream
import java.io.Reader

/*
 * iOS / KMP — appels Android inchangés : les contrats communs prennent un flux Okio, ces adaptateurs
 * acceptent toujours les flux java.io.
 */

/** `parser.parse(reader)` comme avant la migration. */
fun IgcFlightParser.parse(reader: Reader): IgcParseResult = parse(reader.readText())

suspend fun CompletedFlightGateway.submitCompletedIgc(fileName: String, input: InputStream): CompletedFlightResult =
    submitCompletedIgc(fileName, input.source())

suspend fun FlightArchiveRepository.importIgc(fileName: String, input: InputStream): ImportIgcResult =
    importIgc(fileName, input.source())
