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
//import ic.kyc.demo.auth.getSessionTokenKala
import ic.kyc.demo.auth.loginCA
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
                loginCA(username, password)
                getSessionTokenCA()
                //getSessionTokenKala()
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

