package com.example.internalrecorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val REQ_PERM = 1
    private val REQ_CAPTURE = 2
    private lateinit var btn: Button
    private lateinit var status: TextView
    private var recording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { text = "جاهز"; textSize = 18f; gravity = Gravity.CENTER }
        btn = Button(this).apply { text = "ابدأ التسجيل"; setOnClickListener { toggle() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(status); addView(btn)
        })
        // RECORD_AUDIO مطلوب من النظام لـ AudioRecord، لكن التسجيل هنا من صوت النظام مش الميكروفون
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), REQ_PERM
        )
    }

    private fun toggle() {
        if (recording) {
            startService(Intent(this, RecordService::class.java).setAction("STOP"))
            recording = false
            btn.text = "ابدأ التسجيل"
            status.text = "تم الحفظ في مجلد Music داخل بيانات التطبيق"
        } else {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, RecordService::class.java)
                .putExtra("code", resultCode).putExtra("data", data)
            startForegroundService(i)
            recording = true
            btn.text = "إيقاف التسجيل"
            status.text = "جاري تسجيل الصوت الداخلي..."
        }
    }
}
