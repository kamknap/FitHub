package pl.fithubapp.data

import com.google.gson.annotations.SerializedName

data class UserWeightHistoryDto(
    @SerializedName("_id") val id: String,
    val userId: String,
    val weightKg: Double,
    val measuredAt: String,
    val createdAt: String,
    val updatedAt: String
)

data class CreateWeightMeasurementDto(
    val userId: String,
    val weightKg: Double,
    val measuredAt: String
)