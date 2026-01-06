package pl.fithubapp

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.fithubapp.data.AddMealDto
import pl.fithubapp.data.ChallengeManager
import pl.fithubapp.data.ChallengeType
import pl.fithubapp.data.CreateFoodDto
import pl.fithubapp.data.FoodItemDto
import pl.fithubapp.data.MealDto
import pl.fithubapp.data.NutritionData
import pl.fithubapp.data.PointsManager
import pl.fithubapp.logic.UserCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object StepSyncHelper {

    //Pobieranie kroków z Health Connect
    suspend fun getTodaySteps(context: Context): Long {
        val client = HealthConnectClient.getOrCreate(context)
        val startTime = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endTime = Instant.now()

        return try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) { 0L }
    }

    //zapis do bazy
    suspend fun syncStepsToDatabase(context: Context, steps: Int): Result<String> = withContext(
        Dispatchers.IO) {
        try {
            val user = NetworkModule.api.getCurrentUser()
            val weight = user.profile.weightKg
            
            // Sprawdź czy waga jest poprawna
            if (weight <= 0.0) {
                Log.w("StepSync", "Brak poprawnej wagi w profilu użytkownika")
                return@withContext Result.failure(Exception("Brak wagi użytkownika w profilu"))
            }
            
            val caloriesBurned = UserCalculator().calculateCaloriesFromSteps(steps.toLong(), weight)

            val stepsFood = CreateFoodDto(
                name = "Kroki (${steps.formatWithSpaces()})",
                brand = "Smartwatch",
                barcode = null,
                nutritionPer100g = NutritionData(
                    calories = -caloriesBurned,
                    protein = 0.0, fat = 0.0, carbs = 0.0, fiber = 0.0, sugar = 0.0, sodium = 0.0
                ),
                category = "Exercise",
                addedBy = user.id
            )

            val createdFood = NetworkModule.api.createFood(stepsFood)
            val mealDto = MealDto(
                name = "Trening",
                foods = listOf(FoodItemDto(foodId = createdFood.id, quantity = 100.0))
            )

            NetworkModule.api.addMeal(LocalDate.now().toString(), AddMealDto(meal = mealDto))

            Result.success("Zsynchronizowano $steps kroków (${caloriesBurned.toInt()} kcal)")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Int.formatWithSpaces() = toString().reversed().chunked(3).joinToString(" ").reversed()
}