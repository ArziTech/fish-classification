package com.example.fishclassification.navigation

import kotlinx.serialization.Serializable

@Serializable
object Home

@Serializable
data class Result(val imageUri: String)
