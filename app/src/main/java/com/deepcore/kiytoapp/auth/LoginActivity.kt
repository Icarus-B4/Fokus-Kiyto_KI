package com.deepcore.kiytoapp.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deepcore.kiytoapp.MainActivity
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.base.BaseActivity
import com.deepcore.kiytoapp.databinding.ActivityLoginBinding
import com.google.android.material.snackbar.Snackbar

class LoginActivity : BaseActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        authManager = AuthManager(this)

        // Wenn bereits eingeloggt, direkt zur MainActivity
        if (authManager.isLoggedIn()) {
            startMainActivity()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                showError(getString(R.string.error_fields_empty))
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                showError(getString(R.string.error_invalid_email))
                return@setOnClickListener
            }

            // Login-Versuch
            if (authManager.login(email, password)) {
                startMainActivity()
            } else {
                showError(getString(R.string.error_invalid_credentials))
            }
        }

        binding.registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.forgotPasswordButton.setOnClickListener {
            val email = binding.emailInput.text.toString()
            if (email.isEmpty()) {
                showError(getString(R.string.error_enter_email_reset))
                return@setOnClickListener
            }
            // TODO: Implementiere Passwort-Reset-Logik
            showMessage(getString(R.string.password_reset_sent))
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

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
} 