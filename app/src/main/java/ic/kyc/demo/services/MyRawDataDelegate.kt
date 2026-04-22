package ic.kyc.demo.services

import com.mobilecs.cmcekyc_sdk.handles.CmcRawDataDelegate
import com.mobilecs.cmcekyc_sdk.models.CmcEkycSdkMediaType


import android.util.Log
import com.mobilecs.cmcekyc_sdk.CmcEkycSdk
import org.json.JSONObject
import vn.kalapa.ekyc.models.NFCRawData

class MyRawDataDelegate : CmcRawDataDelegate {

    companion object {
        private const val TAG = "CmcRawDataDelegate"
    }

    override fun handleLivenessData(portraitBase64: String) {
        // Xử lý ảnh chân dung (liveness)
        Log.d(TAG, "Liveness data received, length = ${portraitBase64.length}")
    }

    override fun handleCaptureData(
        documentBase64: String,
        documentType: CmcEkycSdkMediaType
    ) {
        // Xử lý ảnh giấy tờ
        Log.d(TAG, "Capture data received")
        Log.d(TAG, " - Type: $documentType")
        Log.d(TAG, " - Base64 length: ${documentBase64.length}")

        when (documentType) {
            CmcEkycSdkMediaType.FRONT -> {
                Log.d(TAG, "Processing FRONT document")
            }
            CmcEkycSdkMediaType.BACK -> {
                Log.d(TAG, "Processing BACK document")
            }
            CmcEkycSdkMediaType.PORTRAIT -> {
                Log.d(TAG, "Processing PORTRAIT image")
            }
            CmcEkycSdkMediaType.PASSPORT -> {
                Log.d(TAG, "Processing PASSPORT image")
            }
            CmcEkycSdkMediaType.NA -> {
                Log.w(TAG, "Unknown document type (NA)")
            }
        }
    }

    override fun handleNFCData(idCardNumber: String, nfcRawData: String) {

        // 1. Parse NFC raw data
        val nfcData = NFCRawData.fromJson(nfcRawData)
        Log.d("CmcEkycSdk.TAG", "Received NFC Data: dg1=${nfcData.dg1}")

        // 2. Build JSON body
        val jsonBody = JSONObject().apply {
            put("sod", nfcData.sod)
            put("dg1", nfcData.dg1)
            put("dg2", nfcData.dg2)
            put("dg13", nfcData.dg13)
            put("idCard", idCardNumber)
        }
        // Xử lý dữ liệu NFC
        Log.d(TAG, "NFC data received")
        Log.d(TAG, " - ID Card Number: $idCardNumber")
        Log.d(TAG, " - Raw NFC length: ${nfcRawData.length}")
    }
}
