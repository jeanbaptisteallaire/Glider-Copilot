package com.neutronstar.glidercopilot.domain

data class VigilanceStatus(
    val departement: String,
    /** 1 vert, 2 jaune, 3 orange, 4 rouge. */
    val colorId: Int,
    val colorLabel: String,
    val phenomena: List<String>,
)
