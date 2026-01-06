package pl.fithubapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import pl.fithubapp.NetworkModule
import pl.fithubapp.R
import java.time.LocalDate


class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString("USER_ID") ?: return Result.failure()
        val type = inputData.getString("TYPE") ?: return Result.failure()

        if (shouldSendNotification(userId, type)) {
            sendNotification(type)
        }

        return Result.success()
    }

    private suspend fun shouldSendNotification(userId: String, type: String): Boolean {
        return try {
            true
        } catch (e: Exception) {
            Log.e("ReminderWorker", "Błąd sprawdzania API: ${e.message}")
            true
        }
    }

    private fun sendNotification(type: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "fithub_reminders"

        val channel = NotificationChannel(channelId, "Przypomnienia FitHub", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)

        val (title, content) = when (type) {
            "WEIGHT" -> "Czas na ważenie!" to "Nie zapomnij zaktualizować swojej wagi w FitHub."
            "MEAL" -> "Pora na posiłek" to "Pamiętaj, aby dodać swoje posiłki do dziennika."
            "WORKOUT" -> "Czas na trening!" to "Dziś jest dobry dzień, żeby się poruszać."
            else -> "Przypomnienie" to "Sprawdź FitHub!"
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(type.hashCode(), notification)
    }
}

class DailyStepWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("DailyStepWorker", "Rozpoczynam synchronizację kroków...")

        return try {
            //Pobieranie kroków
            val stepsLong = StepSyncHelper.getTodaySteps(applicationContext)
            val steps = stepsLong.toInt()

            Log.d("DailyStepWorker", "Pobrano kroków: $steps")

            if (steps >= 1000) {
                val syncResult = StepSyncHelper.syncStepsToDatabase(applicationContext, steps)

                if (syncResult.isSuccess) {
                    Log.i("DailyStepWorker", "Sukces: ${syncResult.getOrNull()}")
                    Result.success()
                } else {
                    Log.e("DailyStepWorker", "Błąd zapisu: ${syncResult.exceptionOrNull()?.message}")
                    Result.retry()
                }
            } else {
                Log.d("DailyStepWorker", "Zbyt mało kroków ($steps), pomijam zapis.")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("DailyStepWorker", "Krytyczny błąd workera", e)
            Result.failure()
        }
    }
}

class DailyWeightWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("DailyWeightWorker", "Rozpoczynam sprawdzanie wagi w Health Connect...")

        return try {
            //Pobieranie wagi
            val latestWeight = WeightSyncHelper.getLatestWeight(applicationContext)

            if (latestWeight != null) {
                val syncResult = WeightSyncHelper.syncWeightToDatabase(applicationContext, latestWeight)

                if (syncResult.isSuccess) {
                    Log.i("DailyWeightWorker", "Sukces: ${syncResult.getOrNull()}")
                    Result.success()
                } else {
                    Log.e("DailyWeightWorker", "Błąd synchronizacji: ${syncResult.exceptionOrNull()?.message}")
                    Result.retry()
                }
            } else {
                Log.d("DailyWeightWorker", "Brak pomiarów wagi w Health Connect z ostatnich 30 dni.")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("DailyWeightWorker", "Krytyczny błąd workera wagi", e)
            Result.failure()
        }
    }
}