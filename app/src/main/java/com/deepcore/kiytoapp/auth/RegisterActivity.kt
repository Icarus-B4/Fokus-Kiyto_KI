package com.deepcore.kiytoapp.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.deepcore.kiytoapp.MainActivity
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.databinding.ActivityRegisterBinding
import com.google.android.material.snackbar.Snackbar
import com.deepcore.kiytoapp.base.BaseActivity

class RegisterActivity : BaseActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        authManager = AuthManager(this)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.registerButton.setOnClickListener {
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()
            val confirmPassword = binding.confirmPasswordInput.text.toString()

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                showError(getString(R.string.error_fields_empty))
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                showError(getString(R.string.error_invalid_email))
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                showError(getString(R.string.error_passwords_not_match))
                return@setOnClickListener
            }

            // Registrierungsversuch
            if (authManager.register(email, password)) {
                showSuccess(getString(R.string.registration_success))
                // Automatischer Login nach erfolgreicher Registrierung
                if (authManager.login(email, password)) {
                    startMainActivity()
                }
            } else {
                showError(getString(R.string.error_registration_failed))
            }
        }

        binding.loginButton.setOnClickListener {
            finish() // Zurück zum Login
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(getColor(R.color.error))
            .show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(getColor(R.color.success))
            .show()
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
} 