package pl.fithubapp

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.fithubapp.data.CreateWeightMeasurementDto
import pl.fithubapp.data.UpdateProfileData
import pl.fithubapp.data.UpdateUserDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

object WeightSyncHelper {

    private const val PREFS_NAME = "weight_sync_prefs"
    private const val KEY_LAST_WEIGHT = "last_weight"
    private const val KEY_LAST_SYNC_DATE = "last_sync_date"
    private const val WEIGHT_THRESHOLD = 0.1

    private val mutex = Mutex()

    private suspend fun hasWeightPermission(context: Context): Boolean {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(HealthPermission.getReadPermission(WeightRecord::class))
        } catch (e: Exception) {
            Log.e("WeightSync", "Błąd sprawdzania uprawnień: ${e.message}")
            false
        }
    }

    suspend fun getLatestWeight(context: Context): WeightRecord? {
        if (!hasWeightPermission(context)) return null

        val client = HealthConnectClient.getOrCreate(context)

        val startTime = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endTime = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

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
                return@withContext Result.success("Pominięto - pomiar z dnia $recordDate już istnieje.")
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
        mutex.withLock {
            try {
                if (!hasWeightPermission(context)) {
                    return@withLock Result.success("Brak uprawnień")
                }

                val weightRecord = getLatestWeight(context)
                if (weightRecord == null) {
                    return@withLock Result.success("Brak danych w Health Connect")
                }

                val weightInKg = weightRecord.weight.inKilograms
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val today = LocalDate.now().toString()

                val lastSyncDate = prefs.getString(KEY_LAST_SYNC_DATE, "")
                val lastWeight = prefs.getFloat(KEY_LAST_WEIGHT, 0f).toDouble()

                if (today == lastSyncDate && abs(weightInKg - lastWeight) < WEIGHT_THRESHOLD) {
                    return@withLock Result.success("Waga już zsynchronizowana dzisiaj")
                }

                val syncResult = syncWeightToDatabase(context, weightRecord)

                if (syncResult.isSuccess) {
                    prefs.edit()
                        .putFloat(KEY_LAST_WEIGHT, weightInKg.toFloat())
                        .putString(KEY_LAST_SYNC_DATE, today)
                        .apply()

                    Result.success("Zsynchronizowano wagę: ${String.format("%.1f", weightInKg)} kg")
                } else {
                    syncResult
                }

            } catch (e: Exception) {
                Log.e("WeightSync", "Błąd syncWeightOnAppStart: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}