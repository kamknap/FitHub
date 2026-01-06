package pl.fithubapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        if (AuthManager.isUserLoggedIn()) {
            AuthManager.currentUserId?.let { uid ->
                checkUserStatusAndRedirect(uid)
            }
        } else {
            navigateToLogin()
        }
    }

    private fun checkUserStatusAndRedirect(userId: String) {
        lifecycleScope.launch {
            try {
                val userDto = NetworkModule.api.getCurrentUser()

                val hasCompletedOnboarding = userDto.profile?.weightKg != null && 
                                            userDto.profile.heightCm != null
                
                if (hasCompletedOnboarding) {
                    navigateToMainApp()
                } else {
                    navigateToOnboarding(isCompletion = true)
                }
            } catch (e: Exception) {
                navigateToOnboarding(isCompletion = false)
            }
        }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun navigateToOnboarding(isCompletion: Boolean) {
        val intent = Intent(this, OnboardingActivity::class.java).apply {
            putExtra("IS_COMPLETION", isCompletion)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToMainApp() {
        startActivity(Intent(this, UserMainActivity::class.java))
        finish()
    }
}
