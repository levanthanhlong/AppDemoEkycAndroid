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
import ic.kyc.demo.services.MyRawDataDelegate
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
        }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            logout(this)
        }

    }

    private fun isLoggedIn(): Boolean {
        return !DataUtil.TOKEN.isNullOrEmpty()
    }

    private fun startSDK() {
        var rawDataDelegate = MyRawDataDelegate()
        val config = CmcEkycConfig(
            appId = AppConst.APP_ID,
            isUseCmcGateway = false,
            rawDataDelegate = rawDataDelegate,
            session = DataUtil.SESSION_ID_Kala,
            session_CA = DataUtil.SESSION_ID_CA,
            baseUrl_CA = AppConst.BASEURL_CA,
            token_CA = DataUtil.TOKEN,
            token_CA_KALA = DataUtil.TOKEN_CA_KLP,
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
        )
        CmcEkycSdk.start(this, config)
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
