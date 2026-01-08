package pl.fithubapp

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.fithubapp.data.CreateWeightMeasurementDto
import pl.fithubapp.data.UpdateProfileData
import pl.fithubapp.data.UpdateUserDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

object WeightSyncHelper {
    suspend fun getLatestWeight(context: Context): WeightRecord? {
        val client = HealthConnectClient.getOrCreate(context)

        val startTime = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endTime = Instant.now()

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    ascendingOrder = false,
                    pageSize = 1
                )
            )
            response.records.firstOrNull()
        } catch (e: Exception) {
            Log.e("WeightSync", "Błąd odczytu z Health Connect: ${e.message}")
            null
        }
    }

    suspend fun syncWeightToDatabase(context: Context, weightRecord: WeightRecord): Result<String> = withContext(Dispatchers.IO) {
        try {
            val weightInKg = weightRecord.weight.inKilograms
            val measuredAt = weightRecord.time.toString()

            val history = NetworkModule.api.getUserWeightHistory()
            val recordDate = weightRecord.time.atZone(ZoneId.systemDefault()).toLocalDate()

            val alreadyExistsToday = history.any {
                val historyDate = Instant.parse(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalDate()
                historyDate.isEqual(recordDate)
            }

            if (alreadyExistsToday) {
                Log.d("WeightSync", "Pomiar z dnia $recordDate już istnieje w bazie - pomijam.")
                return@withContext Result.success("Pominięto - pomiar z dzisiaj już istnieje.")
            }

            val user = NetworkModule.api.getCurrentUser()

            val historyDto = CreateWeightMeasurementDto(
                userId = user.id,
                weightKg = weightInKg,
                measuredAt = measuredAt
            )
            NetworkModule.api.createWeightMeasurement(historyDto)

            if (abs(user.profile.weightKg - weightInKg) > 0.01) {
                val updateUserDto = UpdateUserDto(
                    profile = UpdateProfileData(
                        sex = user.profile.sex,
                        birthDate = user.profile.birthDate,
                        heightCm = user.profile.heightCm,
                        weightKg = weightInKg
                    )
                )
                NetworkModule.api.updateUser(updateUserDto)
            }

            Result.success("Zsynchronizowano nową wagę: ${String.format("%.1f", weightInKg)} kg")
        } catch (e: Exception) {
            Log.e("WeightSync", "Błąd synchronizacji z bazą: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncWeightOnAppStart(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val weightRecord = getLatestWeight(context)

            if (weightRecord == null) {
                return@withContext Result.success("Brak nowych danych o wadze w Health Connect")
            }

            return@withContext syncWeightToDatabase(context, weightRecord)

        } catch (e: Exception) {
            Log.e("WeightSync", "Błąd syncWeightOnAppStart: ${e.message}", e)
            Result.failure(e)
        }
    }
}