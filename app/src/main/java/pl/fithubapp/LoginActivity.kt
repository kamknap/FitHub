package pl.fithubapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPasswordConfirm: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var progressBar: ProgressBar

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        setupClickListeners()
        updateUIMode()
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener {
            if (isLoginMode) {
                performLogin()
            } else {
                toggleMode()
            }
        }

        btnRegister.setOnClickListener {
            if (isLoginMode) {
                toggleMode()
            } else {
                performRegistration()
            }
        }

        tvForgotPassword.setOnClickListener {
            performPasswordReset()
        }
    }

    private fun toggleMode() {
        isLoginMode = !isLoginMode
        updateUIMode()
    }

    private fun updateUIMode() {
        val tilPasswordConfirm = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPasswordConfirm)
        
        if (isLoginMode) {
            etPasswordConfirm.visibility = View.GONE
            tilPasswordConfirm.visibility = View.GONE
            btnLogin.text = "Zaloguj się"
            btnRegister.text = "Nie masz konta? Zarejestruj się"
            tvForgotPassword.visibility = View.VISIBLE
        } else {
            etPasswordConfirm.visibility = View.VISIBLE
            tilPasswordConfirm.visibility = View.VISIBLE
            btnLogin.text = "Masz już konto? Zaloguj się"
            btnRegister.text = "Zarejestruj się"
            tvForgotPassword.visibility = View.GONE
        }
    }

    private fun performLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (!validateInput(email, password)) {
            return
        }

        setLoading(true)
        
        lifecycleScope.launch {
            val result = AuthManager.loginWithEmail(email, password)
            
            result.onSuccess { user ->
//                Toast.makeText(this@LoginActivity, "Witaj ${user.email}!", Toast.LENGTH_SHORT).show()
                navigateToSplash()
            }.onFailure { exception ->
                setLoading(false)
                showError(exception.message ?: "Błąd logowania")
            }
        }
    }

    private fun performRegistration() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val passwordConfirm = etPasswordConfirm.text.toString().trim()

        if (!validateInput(email, password, passwordConfirm)) {
            return
        }

        if (password != passwordConfirm) {
            Toast.makeText(this, "Hasła nie są identyczne", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Hasło musi mieć minimum 6 znaków", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            val result = AuthManager.registerWithEmail(email, password)
            
            result.onSuccess { user ->
                Toast.makeText(this@LoginActivity, "Rejestracja udana! Witaj ${user.email}!", Toast.LENGTH_SHORT).show()
                // Po rejestracji przekieruj do onboardingu
                navigateToOnboarding()
            }.onFailure { exception ->
                setLoading(false)
                showError(parseFirebaseError(exception))
            }
        }
    }

    private fun performPasswordReset() {
        val email = etEmail.text.toString().trim()
        
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Wprowadź poprawny adres email", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            val result = AuthManager.sendPasswordResetEmail(email)
            
            result.onSuccess {
                setLoading(false)
                Toast.makeText(
                    this@LoginActivity, 
                    "Link do resetowania hasła został wysłany na: $email", 
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { exception ->
                setLoading(false)
                showError(exception.message ?: "Błąd wysyłania emaila")
            }
        }
    }

    private fun validateInput(email: String, password: String, passwordConfirm: String? = null): Boolean {
        if (email.isEmpty()) {
            Toast.makeText(this, "Wprowadź adres email", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Wprowadź poprawny adres email", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Wprowadź hasło", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!isLoginMode && passwordConfirm.isNullOrEmpty()) {
            Toast.makeText(this, "Potwierdź hasło", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !isLoading
        btnRegister.isEnabled = !isLoading
        etEmail.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading
        etPasswordConfirm.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun parseFirebaseError(exception: Throwable): String {
        val message = exception.message ?: ""
        return when {
            message.contains("email address is already in use", ignoreCase = true) -> 
                "Ten adres email jest już zarejestrowany"
            message.contains("password is invalid", ignoreCase = true) -> 
                "Nieprawidłowe hasło"
            message.contains("no user record", ignoreCase = true) -> 
                "Nie znaleziono użytkownika z tym adresem email"
            message.contains("network error", ignoreCase = true) -> 
                "Błąd połączenia z internetem"
            else -> "Błąd: $message"
        }
    }

    private fun navigateToOnboarding() {
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }

    private fun navigateToSplash() {
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }
}
