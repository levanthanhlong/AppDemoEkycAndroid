package ic.kyc.demo.screen.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ic.kyc.demo.MainActivity
import ic.kyc.demo.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Button
import android.widget.EditText
import ic.kyc.demo.auth.getSessionTokenCA
import ic.kyc.demo.auth.getSessionTokenKala
import ic.kyc.demo.auth.loginCA
import ic.kyc.demo.util.DataUtil
import kotlin.coroutines.cancellation.CancellationException


class LoginActivity : AppCompatActivity() {
    private var isNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)


        val edtUsername = findViewById<EditText>(R.id.edtUsername)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val username = edtUsername.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            login(username, password)
        }
    }

    private fun login(username: String, password: String) {
        lifecycleScope.launch {
            try {
                delay(800)
                loginCA()
                getSessionTokenCA()
                getSessionTokenKala()
                if (isNavigated) return@launch
                isNavigated = true

                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                Log.e("Login thất bại", "Error: $e")
                Toast.makeText(
                    this@LoginActivity,
                    "Login thất bại: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}


data class GetTokenRequest(
    val verify_check: Boolean = false,
    val fraud_check: Boolean = true,
    val accept_flash: Boolean = false,
    val strict_quality_check: Boolean = true,
    val scan_full_information: Boolean = true,
    val allow_sdk_full_results: Boolean = true,
    val flow: String = "nfc_ekyc"
)

data class GetTokenResponse(
    val token: String,
    val short_token: String,
    val client_id: String,
    val flow: String,
    val document_type: String?,
    val verify_check: Boolean,
    val fraud_check: Boolean,
    val accept_flash: Boolean,
    val strict_quality_check: Boolean,
    val scan_full_information: Boolean
)

