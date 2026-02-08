package pl.fithubapp

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
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
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

object WeightSyncHelper {

    private const val PREFS_NAME = "weight_sync_prefs"
    private const val KEY_LAST_WEIGHT = "last_weight"
    private const val KEY_LAST_SYNC_DATE = "last_sync_date"
    private const val WEIGHT_THRESHOLD = 0.1

    // ZMIANA: Atomowa flaga w pamięci RAM.
    // Działa szybciej niż Mutex i SharedPreferences.
    // Blokuje wywołanie, jeśli inne jest w toku w trakcie działania aplikacji.
    private val isSyncing = AtomicBoolean(false)

    suspend fun syncWeightOnAppStart(context: Context): Result<String> = withContext(Dispatchers.IO) {
        // Jeśli już trwa synchronizacja -> WYJDŹ NATYCHMIAST
        if (isSyncing.getAndSet(true)) {
            return@withContext Result.success("Pominięto - synchronizacja już trwa.")
        }

        try {
            if (!hasWeightPermission(context)) {
                return@withContext Result.failure(Exception("Brak uprawnień"))
            }

            val weightRecord = getLatestWeight(context) ?: return@withContext Result.failure(Exception("Brak wagi"))

            val weightInKg = weightRecord.weight.inKilograms
            val recordDate = weightRecord.time.atZone(ZoneId.systemDefault()).toLocalDate()

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastSyncDateStr = prefs.getString(KEY_LAST_SYNC_DATE, "")
            val lastWeight = prefs.getFloat(KEY_LAST_WEIGHT, 0f).toDouble()

            // Sprawdzenie lokalne
            if (lastSyncDateStr == recordDate.toString() && abs(weightInKg - lastWeight) < WEIGHT_THRESHOLD) {
                return@withContext Result.success("Już zsynchronizowano.")
            }

            // Wysyłamy do API
            val apiResult = uploadToDatabase(weightRecord)

            if (apiResult.isSuccess) {
                prefs.edit()
                    .putFloat(KEY_LAST_WEIGHT, weightInKg.toFloat())
                    .putString(KEY_LAST_SYNC_DATE, recordDate.toString())
                    .apply()
            }

            return@withContext apiResult

        } catch (e: Exception) {
            Log.e("WeightSync", "Błąd: ${e.message}")
            return@withContext Result.failure(e)
        } finally {
            // ZAWSZE zwalniamy blokadę na końcu
            isSyncing.set(false)
        }
    }

    // ... (reszta metod prywatnych: hasWeightPermission, getLatestWeight, uploadToDatabase - bez zmian) ...
    // Skopiuj je ze swojej poprzedniej wersji lub z kodu powyżej

    private suspend fun hasWeightPermission(context: Context): Boolean {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(HealthPermission.getReadPermission(WeightRecord::class))
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getLatestWeight(context: Context): WeightRecord? {
        val client = HealthConnectClient.getOrCreate(context)
        val endTime = Instant.now()
        val startTime = endTime.minus(30, ChronoUnit.DAYS)
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
            null
        }
    }

    private suspend fun uploadToDatabase(weightRecord: WeightRecord): Result<String> {
        // Tu wklej zawartość metody uploadToDatabase z poprzedniego rozwiązania
        return try {
            val weightInKg = weightRecord.weight.inKilograms
            val measuredAt = weightRecord.time.toString()
            val recordDate = weightRecord.time.atZone(ZoneId.systemDefault()).toLocalDate()

            val history = NetworkModule.api.getUserWeightHistory()
            val alreadyExistsInApi = history.any {
                val historyDate = Instant.parse(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalDate()
                historyDate.isEqual(recordDate)
            }

            if (alreadyExistsInApi) {
                return Result.success("Pominięto API - już istnieje.")
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

            Result.success("Zsynchronizowano: ${String.format("%.1f", weightInKg)} kg")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}