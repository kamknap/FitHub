package pl.fithubapp.data

import android.util.Log
import pl.fithubapp.NetworkModule

object ChallengeManager {

    var onChallengeCompleted: ((String, String, Int) -> Unit)? = null

    suspend fun checkChallengeProgress(action: ChallengeType, value: Double = 1.0) {
        try {
            val userProgress = NetworkModule.api.getUserProgress()
            var activeChallenge = userProgress.activeChallenges ?: return

            val allChallenges = NetworkModule.api.getAllChallenges()
            val challengeDef = allChallenges.find { it.id == activeChallenge.challengeId } ?: return

            // Naprawa dla starych wyzwań WEIGHT_LOSS z niepoprawnym totalToFinish
            if (challengeDef.type == ChallengeType.WEIGHT_LOSS && activeChallenge.totalToFinish < 10) {
                val correctedTotalToFinish = challengeDef.targetValue * 10
                activeChallenge = activeChallenge.copy(totalToFinish = correctedTotalToFinish)
                val correctedProgress = userProgress.copy(activeChallenges = activeChallenge)
                NetworkModule.api.updateUserProgress(correctedProgress)
                Log.d("ChallengeManager", "Naprawiono totalToFinish: ${activeChallenge.totalToFinish} -> $correctedTotalToFinish")
            }

            if (challengeDef.type != action) return

            var newCounter = activeChallenge.counter
            var isCompleted = false

            when (action) {
                ChallengeType.STREAK -> {
                    newCounter = userProgress.loginStreak
                }
                ChallengeType.MEAL_COUNT -> {
                    newCounter += 1
                }
                ChallengeType.WEIGHT_LOSS -> {
                    newCounter = (value * 10).toInt().coerceAtLeast(0)
                }
                ChallengeType.TRAINING_COUNT -> {
                    newCounter += 1
                }
                ChallengeType.TRAINING_PLAN_COUNT -> {
                    newCounter += 1
                }
            }

            if (newCounter >= activeChallenge.totalToFinish) {
                newCounter = activeChallenge.totalToFinish
                isCompleted = true
            }

            val updatedActiveChallenge = activeChallenge.copy(counter = newCounter)
            val progressToUpdate = userProgress.copy(activeChallenges = updatedActiveChallenge)

            NetworkModule.api.updateUserProgress(progressToUpdate)
            Log.d("ChallengeManager", "Zaktualizowano postęp wyzwania: $newCounter / ${activeChallenge.totalToFinish}")

            if (isCompleted) {
                completeChallenge(challengeDef, userProgress)
            }

        } catch (e: Exception) {
            Log.e("ChallengeManager", "Błąd aktualizacji wyzwania", e)
        }
    }

    private suspend fun completeChallenge(
        challengeDto: ChallengeDto,
        currentProgress: UserProgressDto
    ) {
        PointsManager.addPoints(PointsManager.ActionType.CHALLENGE, customPoints = challengeDto.pointsForComplete)

        val refreshedProgress = NetworkModule.api.getUserProgress()

        val allBadges = NetworkModule.api.getAllBadges()
        val relatedBadge = allBadges.find { badge ->
            badge.name.equals(challengeDto.name, ignoreCase = true)
        }

        val newBadges = refreshedProgress.badges.toMutableList() // Używamy refreshedProgress
        if (relatedBadge != null && relatedBadge.id != null) {
            if (!newBadges.contains(relatedBadge.id)) {
                newBadges.add(relatedBadge.id)
            }
        }

        val newCompleted = refreshedProgress.completedChallenges.toMutableList() // Używamy refreshedProgress
        if (challengeDto.id != null && !newCompleted.contains(challengeDto.id)) {
            newCompleted.add(challengeDto.id)
        }

        val finalProgress = currentProgress.copy(
            badges = newBadges,
            completedChallenges = newCompleted,
            activeChallenges = null,
        )

        NetworkModule.api.updateUserProgress(finalProgress)

        onChallengeCompleted?.invoke(
            challengeDto.name,
            relatedBadge?.name ?: "Nieznana odznaka",
            challengeDto.pointsForComplete
        )

        Log.d("ChallengeManager", "✅ Wyzwanie '${challengeDto.name}' ukończone! Zdobyto ${challengeDto.pointsForComplete} pkt")
    }
}