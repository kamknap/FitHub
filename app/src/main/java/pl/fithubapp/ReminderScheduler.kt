package pl.fithubapp

import android.content.Context
import androidx.work.*
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    fun scheduleAllReminders(context: Context, userId: String,
                             workoutEnabled: Boolean,
                             mealEnabled: Boolean,
                             measureEnabled: Boolean) {

        val workManager = WorkManager.getInstance(context)

        workManager.cancelAllWorkByTag("FITHUB_REMINDER")

        if (measureEnabled) {
            scheduleDailyWork(context, userId, "WEIGHT", 7, 0)
        }

        if (workoutEnabled) {
            scheduleDailyWork(context, userId, "WORKOUT", 18, 0)
        }

        if (mealEnabled) {
            scheduleDailyWork(context, userId, "MEAL", 8, 0)
            scheduleDailyWork(context, userId, "MEAL", 12, 0)
            scheduleDailyWork(context, userId, "MEAL", 17, 0)
        }
    }

    private fun scheduleDailyWork(context: Context, userId: String, type: String, hour: Int, minute: Int) {
        val now = ZonedDateTime.now()
        var target = now.toLocalDate().atTime(hour, minute, 0)
            .atZone(now.zone)

        if (target.isBefore(now) || target.isEqual(now)) {
            target = target.plusDays(1)
        }

        val initialDelay = java.time.Duration.between(now, target).toMillis()

        val data = Data.Builder()
            .putString("USER_ID", userId)
            .putString("TYPE", type)
            .build()

        val workRequest = PeriodicWorkRequest.Builder(
            ReminderWorker::class.java,
            24, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("FITHUB_REMINDER")
            .build()

        val uniqueName = "${type}_${hour}"

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun scheduleWeightSync(context: Context) {
        val workRequest = PeriodicWorkRequest.Builder(
            DailyWeightWorker::class.java,
            12, TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("DAILY_WEIGHT_SYNC")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_weight_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}