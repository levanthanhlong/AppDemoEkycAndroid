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
import android.os.Environment

import ic.kyc.demo.util.DataUtil


import com.mobilecs.cmcekyc_sdk.CmcEkycSdk
import com.mobilecs.cmcekyc_sdk.configs.CmcEkycConfig
import ic.kyc.demo.auth.logout
import ic.kyc.demo.screen.auth.LoginActivity
import ic.kyc.demo.screen.nfc.NfcResultActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import vn.kalapa.ekyc.KalapaHandler
import vn.kalapa.ekyc.KalapaSDK
import vn.kalapa.ekyc.KalapaSDKConfig
import vn.kalapa.ekyc.KalapaSDKResultCode
import vn.kalapa.ekyc.KalapaScanNFCCallback
import vn.kalapa.ekyc.KalapaScanNFCError
import vn.kalapa.ekyc.models.KalapaResult
import vn.kalapa.ekyc.models.NFCRawData
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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


            onProcessNFC = { idCardNumber, nfcRawData, listener ->
                // Chuyển đổi nfcRawData (String) thành đối tượng NFCRawData
                val nfcData = NFCRawData.fromJson(nfcRawData)

                // Bạn có thể log hoặc kiểm tra thông tin trong NFCRawData nếu cần
                Log.d(TAG, "Received NFC Data: ${nfcData.dg1}")
                // Chạy một background thread để gọi API
                    try {
                        // Log trước khi tạo JSON body
                        Log.d(TAG, "Preparing NFC data to send: $nfcData")

                        // Tạo JSON body cho API từ dữ liệu NFC
                        val jsonBody = JSONObject().apply {
                            put("sodData", nfcData.sod)
                            put("dg1DataB64", nfcData.dg1)
                            put("dg2DataB64", nfcData.dg2)
                            put("dg13DataB64", nfcData.dg13)
                            put("dg14DataB64", nfcData.dg14)
                            put("dg15DataB64", nfcData.dg15)
                            put("dg16DataB64", nfcData.dg16)
                        }

                        // Log dữ liệu JSON đã chuẩn bị
                        Log.d(TAG, "Sending NFC data to API: $jsonBody")

                        // Gọi API để gửi dữ liệu NFC
                        val response = sendToYourAPI(
                            url = "https://csign.cmcuat.cloud/api/ekyc/card/validate",
                            jsonBody = jsonBody,
                            sessionId = DataUtil.SESSION_ID_CA.toString(),
                            token = DataUtil.TOKEN.toString()
                        )

                        // Log phản hồi từ API
                        Log.d(TAG, "API Response: $response")

                        // Nếu thành công, gọi listener.onSuccess() để tiếp tục
                        listener.onSuccess(response)
                        Log.d(TAG, "Card validation successful")

                    } catch (e: Exception) {
                        // Log chi tiết lỗi
                        Log.e(TAG, "Error during NFC validation: ${e.message}", e)

                        // Xử lý lỗi nếu có
                        listener.onError(
                            errorCode = 500,
                            message = e.message ?: "NFC validation error"
                        )
                    }

            },

            onProcessLiveness = { portraitBase64, listener ->
                Thread {
                    try {
                        val response = callFaceVerifyApi(
                            liveImageBase64 = portraitBase64,
                            sessionId = DataUtil.SESSION_ID_CA.toString(),
                            token = DataUtil.TOKEN.toString()
                        )
                        listener.onSuccess(response)
                    } catch (e: Exception) {
                        Log.e("CMC_EKYC", "processLivenessData error", e)
                        listener.onError(
                            errorCode = 500,
                            message = e.message ?: "Face verify failed"
                        )
                    }
                }.start()
            }

        )
        CmcEkycSdk.start(this, config)
    }

    fun sendToYourAPI(url: String, jsonBody: JSONObject, sessionId: String, token: String): JSONObject {
        val client = OkHttpClient()

        // Tạo request body từ JSON
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        // Tạo request
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-EKYC-Session-Id", sessionId) // Thêm sessionId vào header
            .addHeader("Authorization", "Bearer $token") // Thêm token vào header
            .build()

        // Gửi yêu cầu và xử lý phản hồi
        try {
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                // Chuyển đổi phản hồi thành JSONObject và trả về
                val responseBody = response.body?.string()
                return JSONObject(responseBody)
            } else {
                // Nếu không thành công, trả về thông báo lỗi
                throw IOException("Error response: ${response.code}")
            }
        } catch (e: Exception) {
            throw IOException("Request failed: ${e.message}")
        }
    }

    fun callFaceVerifyApi(
        liveImageBase64: String,
        sessionId: String,
        token: String
    ): JSONObject {
        Log.e("CMC_EKYC", "Face verify -- START --")

        try {
            // Build JSON body
            val jsonBody = JSONObject().apply {
                put("liveImage", liveImageBase64)
            }
            val client = OkHttpClient()

            val requestBody = jsonBody
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://csign.cmcuat.cloud/api/ekyc/face/verify")
                .post(requestBody)
                .addHeader("X-EKYC-Session-Id", sessionId)
                .addHeader("Authorization", "Bearer $token")
                .build()

            Log.e("CMC_EKYC", "Face verify -- REQUEST SENT --")

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                Log.e(
                    "CMC_EKYC",
                    "Face verify -- RESPONSE code=${response.code}, body=${responseBody?.take(300)}"
                )

                if (!response.isSuccessful) {
                    throw IOException("Face verify failed: HTTP ${response.code}")
                }

                if (responseBody.isNullOrEmpty()) {
                    throw IOException("Face verify empty response body")
                }

                return JSONObject(responseBody)
            }

        } catch (e: Exception) {
            Log.e(
                "CMC_EKYC",
                "Face verify -- ERROR -- ${e.message}",
                e
            )
            throw e
        } finally {
            Log.e("CMC_EKYC", "Face verify -- END --")
        }
    }



    private fun writeDebugToFile(
        tag: String,
        sessionId: String,
        token: String,
        body: String
    ) {
        try {
            val dir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS
        )

            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "ekyc_debug.txt")

            val time = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
            ).format(Date())

            val content = """
            ===== $time =====
            TAG: $tag
            SESSION_ID: $sessionId
            TOKEN: $token
            BODY:
            $body
            
            
        """.trimIndent()

            file.appendText(content)
        } catch (e: Exception) {
            Log.e("CMC_EKYC", "Write file error", e)
        }
    }

    private fun startKalapaNfc() {
        if (!checkNfc()) return
        val sdkConfig = KalapaSDKConfig.KalapaSDKConfigBuilder(this)
            .withBackgroundColor("#FFFFFF")
            .withMainColor("#0066FF")
            .withBtnTextColor("#FFFFFF")
            .withMainTextColor("#000000")
            .withLanguage("vi") // "vi" | "en"
            .withLivenessVersion(2) // 1 | 2 | 3
            .withNFCTimeoutInSeconds(60)
            .withBaseURL(AppConst.BASEURL)
            .build()

        val kalapaHandler = object : KalapaHandler() {
            override fun onNFCErrorHandle(
                activity: Activity,
                error: KalapaScanNFCError,
                callback: KalapaScanNFCCallback
            ) {
                Log.e("KALAPA", "NFC error: $error")

                runOnUiThread {
                    AlertDialog.Builder(activity)
                        .setTitle("Lỗi NFC")
                        .setMessage(error.name)
                        .setPositiveButton("Thử lại") { _, _ ->
                            callback.onRetry()
                        }
                        .setNegativeButton("Thoát") { _, _ ->
                            callback.close { activity.finish() }
                        }
                        .show()
                }
            }

            override fun onComplete(kalapaResult: KalapaResult) {
                Log.d("KALAPA", "Complete result: $kalapaResult")
                Log.d("KALAPA", "Complete nfc_data name : ${kalapaResult.nfc_data?.name}")
                // Ví dụ lấy NFC data
                val nfcData = kalapaResult.nfc_data
                val decision = kalapaResult.decision

                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Kết quả: $decision",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // TODO: map data, chuyển màn, gửi Flutter EventChannel...
            }

            override fun onError(resultCode: KalapaSDKResultCode) {
                Log.e("KALAPA", "SDK error: $resultCode")

                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "SDK lỗi: ${resultCode.vi}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onExpired() {
                Log.w("KALAPA", "Session expired")

                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Session hết hạn, vui lòng thử lại",
                        Toast.LENGTH_LONG
                    ).show()
                }
                // TODO: init session mới rồi start lại SDK
            }
        }

        KalapaSDK.KalapaSDKBuilder(this, sdkConfig).build().start(
            DataUtil.SESSION_ID_Kala.toString(),
            "nfc_ekyc", // "ekyc" | "nfc_ekyc" | "nfc_only"
            kalapaHandler
        )
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
