package ic.kyc.demo.screen.nfc

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mobilecs.cmcekyc_sdk.results.CmcNFCResultData
import ic.kyc.demo.R
import ic.kyc.demo.auth.logout
import ic.kyc.demo.util.DataUtil

class NfcResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_result)

        // nút X
        findViewById<ImageView>(R.id.btnClose).setOnClickListener {
            logout(this)
            finish()
        }

        val data: CmcNFCResultData =
            DataUtil.NFC_VERIFIED_INFO ?: return

        bindData(data)
    }

    private fun bindData(data: CmcNFCResultData) {

        fun set(id: Int, value: String?) {
            findViewById<TextView>(id).text = value ?: "-"
        }

        set(R.id.tvName, "Họ tên: ${data.name}")
        set(R.id.tvId, "CCCD: ${data.id_number}")
        set(R.id.tvOldId, "CMND cũ: ${data.old_id_number}")
        set(R.id.tvDob, "Ngày sinh: ${data.date_of_birth}")
        set(R.id.tvGender, "Giới tính: ${data.gender}")
        set(R.id.tvNationality, "Quốc tịch: ${data.nationality}")
        set(R.id.tvNation, "Dân tộc: ${data.nation}")
        set(R.id.tvReligion, "Tôn giáo: ${data.religion}")

        set(R.id.tvHometown, "Quê quán: ${data.hometown}")
        set(R.id.tvAddress, "Địa chỉ: ${data.address}")

        set(R.id.tvPersonalId, "Đặc điểm: ${data.personal_identification}")
        set(R.id.tvIssueDate, "Ngày cấp: ${data.date_of_issuance}")
        set(R.id.tvExpiryDate, "Hết hạn: ${data.date_of_expiry}")
        set(R.id.tvMrz, "MRZ: ${data.mrz}")

        set(R.id.tvFather, "Cha: ${data.father_name}")
        set(R.id.tvMother, "Mẹ: ${data.mother_name}")
        set(R.id.tvSpouse, "Vợ/Chồng: ${data.spouse_name}")

        // FACE IMAGE
        data.face_image?.takeIf { it.isNotBlank() }?.let {
            val bytes = Base64.decode(it, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            findViewById<ImageView>(R.id.ivFace).setImageBitmap(bitmap)
        }
    }
}
