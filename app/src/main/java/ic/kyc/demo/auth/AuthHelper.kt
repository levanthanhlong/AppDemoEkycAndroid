package ic.kyc.demo.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.security.SecureRandom
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import ic.kyc.demo.screen.auth.LoginActivity
import ic.kyc.demo.util.AppConst
import ic.kyc.demo.util.DataUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

data class GetTokenRequest(
    val ekycSessionId: String = DataUtil.ekycSessionId.toString(),
    val verify_check: Boolean = true,
    val fraud_check: Boolean = true,
    val accept_flash: Boolean = false,
    val strict_quality_check: Boolean = true,
    val scan_full_information: Boolean = true,
    val allow_sdk_full_results: Boolean = true,
    val flow: String = AppConst.FLOW,
    val clientTransactionId: String = generateClientTransactionId()
)


fun generateClientTransactionId(): String {
    val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    val timestamp = dateFormat.format(Date()) // 14 ký tự

    val secureRandom = SecureRandom()
    val randomPart = secureRandom.nextInt(90000) + 10000 // 5 chữ số

    return "${timestamp}_${randomPart}" // đúng 20 ký tự
}

data class GetTokenResponse(
    val token: String?,
    val short_token: String?,
    val ekycSessionId: String?,
)

/* =======================
   API CALL
   ======================= */

suspend fun getSessionTokenKala(): String = withContext(Dispatchers.IO) {

    // BASEURL_CA || BASEURL
    val url = "${AppConst.BASEURL_CA}/api/ekyc/init"
    //val url = "${AppConst.BASEURL_CA}/api/ekyc/init"
    val jsonBody = Gson().toJson(GetTokenRequest())
    val body = jsonBody.toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url(url)
        .post(body)
        .addHeader("Authorization", "Bearer ${DataUtil.ACCESS_TOKEN_KALA}") // TOKEN || ACCESS_TOKEN_KALA
        .addHeader("Content-Type", "application/json")
        .build()

    val client = OkHttpClient()
    client.newCall(request).execute().use { response ->

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }

        val responseBody = response.body?.string()
            ?: throw Exception("Empty response")

        val result = Gson().fromJson(responseBody, GetTokenResponse::class.java)

        // GÁN SESSION_ID
        DataUtil.SESSION_ID_Kala =  result.short_token // short_token || ekycSessionId
        Log.d("SESSION_ID", "Complete SESSION_ID: ${DataUtil.SESSION_ID_Kala}")
        return@withContext result.short_token.toString()
    }
}


suspend fun getSessionTokenCA(): String = withContext(Dispatchers.IO) {

    val url = "${AppConst.BASEURL_CA}/api/ekyc/init"
    val jsonBody = Gson().toJson(GetTokenRequest())

    val body = jsonBody.toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url(url)
        .post(body)
        .addHeader("Authorization", "Bearer ${DataUtil.TOKEN}") // TOKEN || ACCESS_TOKEN_KALA
        .addHeader("Content-Type", "application/json")
        .build()

    val client = OkHttpClient()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }

        val responseBody = response.body?.string()
            ?: throw Exception("Empty response")

        val result = Gson().fromJson(responseBody, GetTokenResponse::class.java)

        // GÁN ekycSessionId
        DataUtil.ekycSessionId =  result.ekycSessionId
        Log.d("SESSION_ID_CA", "Complete SESSION_ID_CA: ${DataUtil.ekycSessionId}")
        // 👉 SAU KHI CÓ SESSION → GỌI LẤY TOKEN KLP
        val token = getTokenSessionCAFromKLP()
        val token2 = getTokenSessionKLPFromKLP()
        // ✅ GÁN TOKEN
        DataUtil.TOKEN_CA_KLP = token
        Log.d("TOKEN_CA_KLP", "Complete TOKEN_CA_KLP: ${DataUtil.TOKEN_CA_KLP}")
        return@withContext result.short_token.toString()
    }
}

suspend fun getTokenSessionCAFromKLP(): String = withContext(Dispatchers.IO) {
    val url = "${AppConst.BASEURL_CA}/api/ekyc/kalapa/init-session"

    val bodyObject = GetTokenRequest(
        ekycSessionId = DataUtil.ekycSessionId.toString(),
        verify_check = false,
        fraud_check = true,
        accept_flash = false,
        strict_quality_check = true,
        scan_full_information = true,
        allow_sdk_full_results = true,
        flow = AppConst.FLOW
    )

    val jsonBody = Gson().toJson(bodyObject)
    val body = jsonBody.toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url(url)
        .post(body)
        .addHeader("Authorization", "Bearer ${DataUtil.TOKEN}")
        .addHeader("Content-Type", "application/json")
        .build()

    val client = OkHttpClient()

    client.newCall(request).execute().use { response ->

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }

        val responseBody = response.body?.string()
            ?: throw Exception("Empty response")

        // ✅ Log raw response (giống Swift)
        Log.d("TOKEN_CA_KLP", "Raw response: $responseBody")

        val result = Gson().fromJson(responseBody, GetTokenResponse::class.java)

        DataUtil.SESSION_ID_CA = result.short_token

        return@withContext result.token.toString()
    }
}

suspend fun getTokenSessionKLPFromKLP(): String = withContext(Dispatchers.IO) {
    val url = "${AppConst.BASEURL_CA}/api/ekyc/kalapa/init-session"

    val bodyObject = GetTokenRequest(
        ekycSessionId = DataUtil.ekycSessionId.toString(),
        verify_check = false,
        fraud_check = true,
        accept_flash = false,
        strict_quality_check = true,
        scan_full_information = true,
        allow_sdk_full_results = true,
        flow = AppConst.FLOW
    )

    val jsonBody = Gson().toJson(bodyObject)
    val body = jsonBody.toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url(url)
        .post(body)
        .addHeader("Authorization", "Bearer ${DataUtil.TOKEN}")
        .addHeader("Content-Type", "application/json")
        .build()

    val client = OkHttpClient()

    client.newCall(request).execute().use { response ->

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }

        val responseBody = response.body?.string()
            ?: throw Exception("Empty response")

        // ✅ Log raw response (giống Swift)
        Log.d("TOKEN_CA_KLP", "Raw response: $responseBody")

        val result = Gson().fromJson(responseBody, GetTokenResponse::class.java)

        DataUtil.SESSION_ID_Kala = result.short_token

        return@withContext result.token.toString()
    }
}


fun logout(context: Context) {
    // Clear local token
    // Clear session id
    DataUtil.SESSION_ID_Kala = null
    DataUtil.SESSION_ID_CA = null

    DataUtil.TOKEN = null

    // Quay về LoginActivity
    val intent = Intent(context, LoginActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    context.startActivity(intent)
}


suspend fun loginCA(username: String, password: String ): String = withContext(Dispatchers.IO) {
    val url = "${AppConst.BASEURL_CA}/api/auth/login"

    val jsonBody = Gson().toJson(LoginRequest(username, password))

    val body = jsonBody.toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url(url)
        .post(body)
        .addHeader("Content-Type", "application/json")
        .build()

    val client = OkHttpClient()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }

        val responseBody = response.body?.string()
            ?: throw Exception("Empty response")

        val result = Gson().fromJson(responseBody, ApiResponse::class.java)

        // Gán TOKEN
        DataUtil.TOKEN = result.data.token
        Log.d("TOKEN", "Complete TOKEN: ${DataUtil.TOKEN}")
        return@withContext result.data.token
    }
}
data class LoginRequest(
    val username: String,
    val password: String
)
data class ApiResponse(
    val success: Boolean,
    val data: Data
)

data class Data(
    val token: String
)
