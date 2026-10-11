package com.neutronstar.glidercopilot.carto

import okio.Path.Companion.toOkioPath
import java.io.File

/** Appel Android inchangé : `PmTiles(File(...))`. */
fun PmTiles(file: File): PmTiles = PmTiles(file.toOkioPath())
