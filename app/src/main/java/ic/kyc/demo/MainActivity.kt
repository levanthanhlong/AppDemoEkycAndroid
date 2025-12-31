package ic.kyc.demo

import android.app.Activity
import android.content.Intent
import android.nfc.NfcManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ic.kyc.demo.util.AppConst
import android.util.Base64

import ic.kyc.demo.util.DataUtil

import com.mobilecs.cmcekyc_sdk.models.CmcEkycSdkMediaType
import com.mobilecs.cmcekyc_sdk.CmcEkycSdk
import com.mobilecs.cmcekyc_sdk.configs.CmcEkycConfig
import com.mobilecs.cmcekyc_sdk.handles.CmcRequestListener
import ic.kyc.demo.auth.logout
import ic.kyc.demo.screen.auth.LoginActivity
import ic.kyc.demo.screen.nfc.NfcResultActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import vn.kalapa.ekyc.KalapaSDKMediaType
import vn.kalapa.ekyc.managers.AESCryptor
import vn.kalapa.ekyc.models.NFCRawData
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startSDK()
            //startKalapaNfc()
        }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            logout(this)
        }

    }

    private fun isLoggedIn(): Boolean {
        return !DataUtil.TOKEN.isNullOrEmpty()
    }

    private fun startSDK() {
        val config = CmcEkycConfig(
            appId = AppConst.APP_ID,
            session = DataUtil.SESSION_ID_Kala,
            baseUrl = AppConst.BASEURL, // BASEURL_CA || BASEURL
            language = "vi",
            mainColor = "#62A583",
            backgroundColor = "#FFFFFF",
            mainTextColor = "#000000",
            btnTextColor = "#FFFFFF",
            livenessVersion = 3,
            valueNFCTimeoutSeconds = 180,
            flow = AppConst.FLOW, // nfc_only, nfc_ekyc, ekyc
            // ====== Callbacks kết quả ======
            onComplete = { result ->
                // EKYC thành công
                Log.d("CmcEkycSdk", "Complete result: $result")
                Log.d("CmcEkycSdk", "Complete nfc_data name : ${result.nfc_data?.name}")
                // Ví dụ lấy NFC data
                val nfcData = result.nfc_data
                val decision = result.decision
                DataUtil.NFC_VERIFIED_INFO = result.nfc_data
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Kết quả: $decision",
                        Toast.LENGTH_LONG
                    ).show()
                    if (nfcData != null) {
                        Log.w("CmcNfcResultScreen", "nfc_data is not null")
                        runOnUiThread {
                            startActivity(
                                Intent(this@MainActivity, NfcResultActivity::class.java)
                            )
                        }
                    } else {
                        Log.w("CmcNfcResultScreen", "nfc_data is null")
                    }
                }

            },

            onError = { error ->
                // Lỗi trong quá trình EKYC
                Log.e("CMC_EKYC", "Error: $error")
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Lỗi SDK")
                        .setMessage("SDK lỗi: ${error.vi}")
                        .setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            },

            onExpired = {
                // Session hết hạn
                Log.w("CMC_EKYC", "Session expired")
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Lỗi SDK")
                        .setMessage("SDK lỗi: Session hết hạn, vui lòng thử lại")
                        .setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                    logout(this)
                }

            },

            // ====== NFC error handling ======
            onNFCErrorHandle = { activity, error, callback ->
                // Hiển thị dialog cho user
                AlertDialog.Builder(activity)
                    .setTitle("Lỗi NFC")
                    .setMessage(error.name)
                    .setPositiveButton("Thử lại") { _, _ ->
                        callback.onRetry()
                    }
                    .setNegativeButton("Thoát") { _, _ ->
                        callback.close {}
                    }
                    .show()
            },

            onProcessCapture = { documentBase64, documentType, listener ->
                processCaptureValidate(
                    documentBase64,
                    documentType,
                    AppConst.BASEURL_CA,
                    DataUtil.SESSION_ID_CA.toString(),
                    DataUtil.TOKEN.toString(),
                )

                callDocumentScanApiKala(
                    documentBase64 = documentBase64,
                    documentType = documentType,
                    baseUrl = AppConst.BASEURL,
                    sessionId = DataUtil.SESSION_ID_Kala.toString(),
                    listener = listener
                )
            },

            onProcessNFC = { idCardNumber, nfcRawData, listener ->
                processNfcAndValidate(
                    nfcRawData,
                    AppConst.BASEURL_CA,
                    DataUtil.SESSION_ID_CA.toString(),
                    DataUtil.TOKEN.toString()
                )
                callNfcVerifyApiKala(
                    nfcRawData = nfcRawData,
                    baseUrl = AppConst.BASEURL,
                    sessionId = DataUtil.SESSION_ID_Kala.toString(),
                    listener = listener
                )
            },

            onProcessLiveness = { portraitBase64, listener ->
                processLivenessAndVerify(
                    portraitBase64,
                    AppConst.BASEURL_CA,
                    DataUtil.SESSION_ID_CA.toString(),
                    DataUtil.TOKEN.toString()
                )
                callLivenessCheckApiKala(
                    portraitBase64 = portraitBase64,
                    baseUrl = AppConst.BASEURL,
                    sessionId = DataUtil.SESSION_ID_Kala.toString(),
                    listener = listener
                )

            }
        )
        CmcEkycSdk.start(this, config)
    }


    // CMC API
    fun postJsonWithAuth(
        url: String,
        jsonBody: JSONObject,
        sessionId: String,
        token: String
    ): JSONObject {
        val client = OkHttpClient()

        val requestBody = jsonBody
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-EKYC-Session-Id", sessionId)
            .addHeader("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                Log.d(
                    "CMC_EKYC",
                    "POST $url -> code=${response.code}, body=${responseBody?.take(300)}"
                )

                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }

                if (responseBody.isNullOrEmpty()) {
                    throw IOException("Empty response body")
                }

                return JSONObject(responseBody)
            }
        } catch (e: Exception) {
            Log.e("CMC_EKYC", "POST $url FAILED: ${e.message}", e)
            throw e
        }
    }


    fun processCaptureValidate(
        documentBase64: String,
        documentType: CmcEkycSdkMediaType,
        baseUrl: String,
        sessionId: String,
        token: String,
    ) {
        Thread {
            try {
                // 1. Chọn URL theo loại giấy tờ
                val url = if (documentType == CmcEkycSdkMediaType.FRONT) {
                    "$baseUrl/api/ekyc/kalapa/scan-front"
                } else {
                    "$baseUrl/api/ekyc/kalapa/scan-back"
                }

                // 1. Build JSON body
                val jsonBody = JSONObject().apply {
                    put("image", documentBase64)
                }
                // 2. Call API
                val response = postJsonWithAuth(
                    url = url,
                    jsonBody = jsonBody,
                    sessionId = sessionId,
                    token = token
                )
                // 3. Check success
                val success = response.optBoolean("success", false)

                if (success) {
                    Log.d(TAG, "CMC check document - success: $response")
                } else {
                    val errorMessage = response
                        .optJSONArray("errors")
                        ?.optJSONObject(0)
                        ?.optString("message")
                        ?: "document verify failed"
                }

            } catch (e: Exception) {
                Log.e("CMC_EKYC", "processCaptureValidate error", e)
            }
        }.start()
    }

    fun processNfcAndValidate(
        nfcRawData: String,
        baseUrl: String,
        sessionId: String,
        token: String,
    ) {
        Thread {
            try {
                // 1. Parse NFC raw data
                val nfcData = NFCRawData.fromJson(nfcRawData)
                Log.d(TAG, "Received NFC Data: dg1=${nfcData.dg1}")

                // 2. Build JSON body
                val jsonBody = JSONObject().apply {
                    put("sodData", nfcData.sod)
                    put("dg1DataB64", nfcData.dg1)
                    put("dg2DataB64", nfcData.dg2)
                    put("dg13DataB64", nfcData.dg13)
                    put("dg14DataB64", nfcData.dg14)
                    put("dg15DataB64", nfcData.dg15)
                    put("dg16DataB64", nfcData.dg16)
                }

                Log.d(TAG, "Sending NFC data to API: $jsonBody")

                // 3. Call API
                val response = postJsonWithAuth(
                    url = "$baseUrl/api/ekyc/card/validate",
                    jsonBody = jsonBody,
                    sessionId = sessionId,
                    token = token
                )

                Log.d(TAG, "NFC API Response: $response")

                // 4. Check success
                val success = response.optBoolean("success", false)

                if (success) {
                    Log.d(TAG, " CMC check - Card validation successful")
                } else {
                    val errorMessage = response
                        .optJSONArray("errors")
                        ?.optJSONObject(0)
                        ?.optString("message")
                        ?: "NFC validation failed"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during NFC validation", e)
            }
        }.start()
    }

    fun processLivenessAndVerify(
        portraitBase64: String,
        baseUrl: String,
        sessionId: String,
        token: String,
    ) {
        Thread {
            try {
                // 1. Build JSON body
                val jsonBody = JSONObject().apply {
                    put("liveImage", portraitBase64)
                }
                // 2. Call API
                val response = postJsonWithAuth(
                    url = "$baseUrl/api/ekyc/face/verify",
                    jsonBody = jsonBody,
                    sessionId = sessionId,
                    token = token
                )
                // 3. Check success
                val success = response.optBoolean("success", false)

                if (success) {
                    Log.d(TAG, "CMC check - Face match")
                } else {
                    val errorMessage = response
                        .optJSONArray("errors")
                        ?.optJSONObject(0)
                        ?.optString("message")
                        ?: "Face verify failed"
                }

            } catch (e: Exception) {
                Log.e("CMC_EKYC", "processLivenessAndVerify error", e)
            }
        }.start()
    }


    // kalapa api
    fun postMultipart(
        url: String,
        headers: Map<String, String> = emptyMap(),
        formBuilder: MultipartBody.Builder.() -> Unit
    ): JSONObject {

        val client = OkHttpClient()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply(formBuilder)
            .build()

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("accept", "application/json")

        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        val request = requestBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                Log.d(
                    "CMC_EKYC",
                    "POST $url -> code=${response.code}, body=${responseBody?.take(300)}"
                )

                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.message}")
                }

                if (responseBody.isNullOrEmpty()) {
                    throw IOException("Empty response body")
                }

                return JSONObject(responseBody)
            }
        } catch (e: Exception) {
            Log.e("CMC_EKYC", "POST multipart FAILED: $url", e)
            throw e
        }
    }

    fun callNfcVerifyApiKala(
        nfcRawData: String,
        baseUrl: String,
        sessionId: String,
        listener: CmcRequestListener
    ) {
        Thread {
            try {
                val client = OkHttpClient()

                val encryptedData = AESCryptor.encryptText(nfcRawData)

                val body = JSONObject()
                    .put("data", encryptedData)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/api/nfc/verify")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", sessionId)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                        ?: throw IOException("Empty response body")

                    val json = JSONObject(responseBody)
                    Log.d("CMC_EKYC", "onProcessNFC response: $json")

                    if (json.has("error")) {
                        val errorObj = json.optJSONObject("error")
                        val errorCode = errorObj?.optInt("code") ?: 0
                        val errorMessage = errorObj?.optString("message") ?: ""

                        if (errorCode == 200) {
                            listener.onSuccess(json)
                        } else {
                            Log.w(
                                "CMC_EKYC",
                                "NFC business error: code=$errorCode, message=$errorMessage"
                            )
                            listener.onError(errorCode, errorMessage)
                        }
                    } else {
                        // Trường hợp backend không trả field error
                        listener.onSuccess(json)
                    }
                }

            } catch (e: Exception) {
                Log.e("CMC_EKYC", "callNfcVerifyApi exception", e)
                when {
                    e is java.net.SocketTimeoutException ->
                        listener.onTimeout()

                    else ->
                        listener.onError(400, e.message ?: "Unknown error")
                }
            }
        }.start()
    }

    fun callDocumentScanApiKala(
        documentBase64: String,
        documentType: CmcEkycSdkMediaType,
        baseUrl: String,
        sessionId: String,
        listener: CmcRequestListener
    ) {
        Thread {
            try {
                // 1. Chọn URL theo loại giấy tờ
                val url = if (documentType == CmcEkycSdkMediaType.FRONT) {
                    "$baseUrl/api/kyc/scan-front"
                } else {
                    "$baseUrl/api/kyc/scan-back"
                }

                // 2. Decode base64 → file tạm
                val imageBytes = Base64.decode(documentBase64, Base64.DEFAULT)

                val tempFile = File.createTempFile("document_", ".jpg")
                tempFile.writeBytes(imageBytes)

                // 3. Gọi API multipart
                val response = postMultipart(
                    url = url,
                    headers = mapOf(
                        "Authorization" to sessionId
                    )
                ) {
                    addFormDataPart(
                        name = "image",
                        filename = tempFile.name,
                        body = tempFile.asRequestBody("image/jpeg".toMediaType())
                    )
                }

                Log.d("CMC_EKYC", "onProcessCapture response: $response")

                // 4. Parse response (giữ nguyên logic cũ)
                if (response.has("error")) {
                    val errorObj = response.optJSONObject("error")
                    val errorCode = errorObj?.optInt("code") ?: 0
                    val errorMessage = errorObj?.optString("message") ?: ""

                    if (errorCode == 0) {
                        Log.w(
                            "CMC_EKYC",
                            "onProcessCapture pass: code=$errorCode, message=$errorMessage"
                        )
                        listener.onSuccess(response)
                    } else {
                        Log.w(
                            "CMC_EKYC",
                            "onProcessCapture error: code=$errorCode, message=$errorMessage"
                        )
                        listener.onError(errorCode, errorMessage)
                    }
                } else {
                    // Backend không trả error object
                    listener.onSuccess(response)
                }

                // 5. Cleanup
                tempFile.delete()

            } catch (e: Exception) {
                Log.e("CMC_EKYC", "callDocumentScanApi exception", e)
                when {
                    e is java.net.SocketTimeoutException ->
                        listener.onTimeout()

                    else ->
                        listener.onError(400, e.message ?: "Unknown error")
                }
            }
        }.start()
    }


    fun callLivenessCheckApiKala(
        portraitBase64: String,
        baseUrl: String,
        sessionId: String,
        listener: CmcRequestListener
    ) {
        Thread {
            try {
                // 1. Decode base64 → file tạm
                val imageBytes = Base64.decode(portraitBase64, Base64.DEFAULT)

                val tempFile = File.createTempFile("selfie_", ".jpg")
                tempFile.writeBytes(imageBytes)

                // 2. Gọi API multipart
                val response = postMultipart(
                    url = "$baseUrl/api/kyc/check-selfie",
                    headers = mapOf(
                        "Authorization" to sessionId
                    )
                ) {
                    addFormDataPart(
                        name = "image",
                        filename = tempFile.name,
                        body = tempFile.asRequestBody("image/jpeg".toMediaType())
                    )
                }

                // 3. Parse response (giữ nguyên logic cũ)
                if (response.has("error")) {
                    val errorObj = response.optJSONObject("error")
                    val errorCode = errorObj?.optInt("code") ?: 0
                    val errorMessage = errorObj?.optString("message") ?: ""

                    if (errorCode == 0) {
                        Log.d("CMC_EKYC", "onProcessLiveness response: $response")
                        listener.onSuccess(response)
                    } else {
                        Log.w(
                            "CMC_EKYC",
                            "onProcessLiveness error: code=$errorCode, message=$errorMessage"
                        )
                        listener.onError(errorCode, errorMessage)
                    }
                } else {
                    // Backend không trả error object
                    listener.onSuccess(response)
                }

                // 4. Cleanup
                tempFile.delete()

            } catch (e: Exception) {
                Log.e("CMC_EKYC", "callLivenessCheckApi exception", e)

                when {
                    e is java.net.SocketTimeoutException ->
                        listener.onTimeout()

                    else ->
                        listener.onError(400, e.message ?: "Unknown error")
                }
            }
        }.start()
    }


    private fun checkNfc(): Boolean {
        val manager = getSystemService(NFC_SERVICE) as? NfcManager
            ?: return showError("Thiết bị không có NFC")

        val adapter = manager.defaultAdapter
            ?: return showError("Thiết bị không hỗ trợ NFC")

        if (!adapter.isEnabled) {
            return showError("Vui lòng bật NFC")
        }
        return true
    }

    private fun showError(msg: String): Boolean {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        return false
    }
}
