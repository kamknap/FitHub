package pl.fithubapp.logic

import pl.fithubapp.data.UserGoalDto
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

data class GoalProgressResult(
    val label: String,
    val value: String,
    val remaining: String?,
    val fullDesc: String
)
class UserCalculator {
     fun calculateBMI(weight: Double, height: Double): Double? {
        if (weight <= 0 || height <= 0) return null
        val heightInMeters = height / 100
        return weight / (heightInMeters * heightInMeters)
    }

    fun getBMICategory(bmi: Double): String {
        return when {
            bmi < 18.5 -> "Niedowaga"
            bmi < 25.0 -> "Prawidłowa waga"
            bmi < 30.0 -> "Nadwaga"
            else -> "Otyłość"
        }
    }

    fun calculateBMR(weight: Double, height: Double, age: Double, sex: String): Double?{
        if (weight <= 0 || height <= 0 || age <= 0) return null
        return when (sex) {
            "Male" -> (13.397 * height) + (4.799 * weight) - (5.677 * age) + 88.362
            "Female" -> (9.247 * height) + (3.098 * weight) - (4.330 * age) + 447.593
            else -> null
        }
    }

    fun calculateEnergyExpenditure(weight: Double, mets: Double, minutes: Double): Double? {
        if (weight <= 0 || mets <= 0 || minutes <= 0) return null
        return ((mets * 3.5 * weight) / 200) * minutes
    }

    fun calculateAge(birthDate: String): Int {
        return try {
            val date = if (birthDate.contains("T")) {
                LocalDate.parse(birthDate.substring(0, 10))
            } else {
                LocalDate.parse(birthDate)
            }

            val today = LocalDate.now()
            var age = today.year - date.year

            if (today.monthValue < date.monthValue ||
                (today.monthValue == date.monthValue && today.dayOfMonth < date.dayOfMonth)) {
                age--
            }

            age
        } catch (e: Exception) {
            0
        }
    }

    fun calculateGoalProgress(currentWeight: Double, goal: UserGoalDto): GoalProgressResult {
        val startWeight = goal.firstWeightKg
        val targetWeight = goal.targetWeightKg

        fun fmt(v: Double) = String.format("%.1f", abs(v))
        return when (goal.type) {
            "lose_weight" -> {
                val progress = startWeight - currentWeight
                val remaining = currentWeight - targetWeight
                GoalProgressResult(
                    label = "Schudnięto",
                    value = "${fmt(progress)} kg",
                    remaining = "${fmt(remaining)} kg",
                    fullDesc = "Schudnięto: ${fmt(progress)}kg, pozostało: ${fmt(remaining)}kg"
                )
            }

            "gain_weight" -> {
                val progress = currentWeight - startWeight
                val remaining = targetWeight - currentWeight
                GoalProgressResult(
                    label = "Przybrano",
                    value = "${fmt(progress)} kg",
                    remaining = "${fmt(remaining)} kg",
                    fullDesc = "Przybrano: ${fmt(progress)}kg, pozostało: ${fmt(remaining)}kg"
                )
            }

            else -> {
                val diff = startWeight - currentWeight
                val sign = if (diff > 0) "-" else "+"
                GoalProgressResult(
                    label = "Zmiana wagi",
                    value = "$sign${fmt(diff)} kg",
                    remaining = null,
                    fullDesc = "Różnica względem wagi początkowej: $sign${fmt(diff)}kg"
                )
            }
        }
    }

    fun calculateCaloriesFromSteps(steps: Long, weight: Double): Double {
        if (steps <= 0L || weight <= 0.0) return 0.0
        // 0.0005 kcal na krok na kg wagi
        return steps * 0.0005 * weight
    }

    data class MacroTargets(val protein: Int, val fat: Int, val carbs: Int)

    fun calculateMacroTargets(calorieGoal: Double): MacroTargets {
        val p = (calorieGoal * 0.20 / 4).roundToInt()
        val f = (calorieGoal * 0.30 / 9).roundToInt()
        val w = (calorieGoal * 0.50 / 4).roundToInt()
        return MacroTargets(p, f, w)
    }
}