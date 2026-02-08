package pl.fithubapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import pl.fithubapp.data.ChallengeType
import pl.fithubapp.data.PointsManager
import pl.fithubapp.data.ChallengeManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class UserMainActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private var isNavigating = false
    private var lastNavTime: Long = 0
    private val NAV_DEBOUNCE_TIME = 300L
    private var activeDialogs = mutableListOf<AlertDialog>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        setContentView(R.layout.activity_user_main)

        bottomNavigation = findViewById(R.id.bottom_navigation)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        ChallengeManager.onChallengeCompleted = { challengeName, badgeName, points ->
            showChallengeCompleteDialog(challengeName, badgeName, points)
        }

        PointsManager.onGoalAchieved = { message, points ->
            showGoalAchievedDialog(message, points)
        }

        val currentUserId = AuthManager.currentUserId
        if (currentUserId == null) {
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                try {
                    val result = PointsManager.checkDailyLogin()
                    if(result.isNewLogin){
                        if (result.streakBonus){
                            showStreakDialog(result.currentStreak, result.pointsAdded)
                        }
                        else{
                            Snackbar.make(
                                bottomNavigation,
                                "Witaj ponownie! Zdobyto +${result.pointsAdded} pkt.",
                                Snackbar.LENGTH_LONG
                            ).setAnchorView(bottomNavigation).show()
                        }
                        ChallengeManager.checkChallengeProgress(ChallengeType.STREAK)
                    }

                    if (result.levelUp) {
                        showLevelUpDialog()
                    }
                } catch (e: Exception) {
                    Log.d("UserMainActivity", "Pomijam checkDailyLogin dla nowego użytkownika: ${e.message}")
                }

                val user = NetworkModule.api.getCurrentUser()

                val notifSettings = user.settings?.notifications

                ReminderScheduler.scheduleAllReminders(
                    context = this@UserMainActivity,
                    userId = user.id,
                    workoutEnabled = notifSettings?.types?.workoutReminders ?: true,
                    mealEnabled = notifSettings?.types?.mealReminders ?: true,
                    measureEnabled = notifSettings?.types?.measureReminders ?: true
                )

//                ReminderScheduler.scheduleWeightSync(this@UserMainActivity)

                launch {
                    try {
                        StepSyncHelper.syncStepsOnAppStart(this@UserMainActivity)
                    } catch (e: Exception) { Log.e("Main", "Steps error", e) }
                }

                launch {
                    try {
                        val weightSyncResult = WeightSyncHelper.syncWeightOnAppStart(this@UserMainActivity)
                        if (weightSyncResult.isSuccess) {
                            Log.d("UserMainActivity", "Sync wagi OK: ${weightSyncResult.getOrNull()}")
                        } else {
                            Log.d("UserMainActivity", "Sync wagi pominięty/błąd: ${weightSyncResult.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("UserMainActivity", "Błąd wywołania sync wagi: ${e.message}")
                    }
                }


            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (savedInstanceState == null) {
            loadFragment(UserDiaryFragment())
        }

        setupBottomNavigation()

        onBackPressedDispatcher.addCallback(this) {
            val fm = supportFragmentManager
            if (fm.backStackEntryCount > 0) {
                fm.popBackStack()
            } else {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeDialogs.forEach { dialog ->
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
        activeDialogs.clear()
        ChallengeManager.onChallengeCompleted = null
        PointsManager.onGoalAchieved = null
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastNavTime < NAV_DEBOUNCE_TIME || isNavigating) {
                return@setOnItemSelectedListener false
            }
            
            lastNavTime = currentTime

            val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)

            val selectedFragment = when (item.itemId) {
                R.id.nav_diary -> UserDiaryFragment()
                R.id.nav_training -> UserTrainingFragment()
                R.id.nav_weight -> UserWeightFragment()
                R.id.nav_stats -> UserProgressFragment()
                R.id.nav_profile -> UserProfileFragment()
                else -> null
            }

            if (selectedFragment != null) {
                if (currentFragment != null && currentFragment::class == selectedFragment::class) {
                    return@setOnItemSelectedListener true
                }
                
                loadFragment(selectedFragment)
                return@setOnItemSelectedListener true
            }

            false
        }
    }

    private fun loadFragment(fragment: Fragment) {
        isNavigating = true
        
        try {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                setCustomAnimations(
                    android.R.anim.fade_in, 
                    android.R.anim.fade_out
                )
                replace(R.id.main_container, fragment)
                runOnCommit {
                    isNavigating = false
                }
            }
        } catch (e: Exception) {
            isNavigating = false
            e.printStackTrace()
        }
    }

    private fun showStreakDialog(days: Int, points: Int){
        if (isFinishing || isDestroyed) return

        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.login_streak_dialog, null)
            val tvDays = dialogView.findViewById<TextView>(R.id.tvStreakDays)
            val tvPoints = dialogView.findViewById<TextView>(R.id.tvStreakPoints)
            val btnClose = dialogView.findViewById<Button>(R.id.btnCloseDialog)

            tvDays.text = "$days dni z rzędu!"
            tvPoints.text = "Zdobyto +$points pkt"

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            activeDialogs.add(dialog)
            
            dialog.setOnDismissListener {
                activeDialogs.remove(dialog)
            }

            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
        } catch (e: Exception) {
            Log.e("UserMainActivity", "Błąd wyświetlania dialogu streak: ${e.message}")
        }
    }

    fun showLevelUpDialog() {
        if (isFinishing || isDestroyed) return

        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.level_up_dialog, null)
            val btnOk = dialogView.findViewById<Button>(R.id.btnOkLevel)

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()

            activeDialogs.add(dialog)
            
            dialog.setOnDismissListener {
                activeDialogs.remove(dialog)
            }

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnOk.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e("UserMainActivity", "Błąd wyświetlania dialogu level up: ${e.message}")
        }
    }

    fun showChallengeCompleteDialog(challengeName: String, badgeName: String, points: Int) {
        if (isFinishing || isDestroyed) return

        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.challenge_complete_dialog, null)

            val tvChallengeName = dialogView.findViewById<TextView>(R.id.tvChallengeName)
            val tvBadgeName = dialogView.findViewById<TextView>(R.id.tvBadgeName)
            val tvPoints = dialogView.findViewById<TextView>(R.id.tvPoints)
            val btnOk = dialogView.findViewById<Button>(R.id.btnOk)

            tvChallengeName.text = "Ukończono: $challengeName"
            tvBadgeName.text = "Zdobyto odznakę: $badgeName"
            tvPoints.text = "+$points pkt"

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            activeDialogs.add(dialog)
            
            dialog.setOnDismissListener {
                activeDialogs.remove(dialog)
            }

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnOk.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e("UserMainActivity", "Błąd wyświetlania dialogu challenge: ${e.message}")
        }
    }

    fun showGoalAchievedDialog(message: String, points: Int) {
        if (isFinishing || isDestroyed) return

        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.goal_achieved_dialog, null)

            val tvMessage = dialogView.findViewById<TextView>(R.id.tvGoalMessage)
            val tvPoints = dialogView.findViewById<TextView>(R.id.tvGoalPoints)
            val btnOk = dialogView.findViewById<Button>(R.id.btnOkGoal)

            tvMessage.text = message
            tvPoints.text = "+$points pkt"

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            activeDialogs.add(dialog)
            dialog.setOnDismissListener { activeDialogs.remove(dialog) }
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnOk.setOnClickListener { dialog.dismiss() }
            dialog.show()
        } catch (e: Exception) {
            Log.e("UserMainActivity", "Błąd wyświetlania dialogu goal: ${e.message}")
        }
    }
}