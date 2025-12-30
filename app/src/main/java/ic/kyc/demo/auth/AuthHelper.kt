package ic.kyc.demo.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import ic.kyc.demo.screen.auth.LoginActivity
import ic.kyc.demo.util.AppConst
import ic.kyc.demo.util.DataUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class GetTokenRequest(
    val verify_check: Boolean = false,
    val fraud_check: Boolean = true,
    val accept_flash: Boolean = false,
    val strict_quality_check: Boolean = true,
    val scan_full_information: Boolean = true,
    val allow_sdk_full_results: Boolean = true,
    val flow: String = AppConst.FLOW,
    val clientTransactionId: String = "132001"
)

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
    val url = "${AppConst.BASEURL}/api/auth/get-token"
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

    // BASEURL_CA || BASEURL
    //val url = "${AppConst.BASEURL}/api/auth/get-token"
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

        // GÁN SESSION_ID
        DataUtil.SESSION_ID_CA =  result.ekycSessionId // short_token || ekycSessionId
        Log.d("SESSION_ID_CA", "Complete SESSION_ID_CA: ${DataUtil.SESSION_ID_CA}")
        return@withContext result.ekycSessionId.toString()
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


suspend fun loginCA(): String = withContext(Dispatchers.IO) {
    val url = "${AppConst.BASEURL_CA}/api/auth/login"

    val jsonBody = Gson().toJson(LoginRequest("org01", "org123"))

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
