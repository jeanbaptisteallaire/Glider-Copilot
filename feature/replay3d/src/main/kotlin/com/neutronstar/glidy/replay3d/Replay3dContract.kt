package com.neutronstar.glidy.replay3d

/** Aucun moteur 3D n'est embarqué en phase 1. */
sealed interface Replay3dAvailability {
    data object NotImplemented : Replay3dAvailability
}

