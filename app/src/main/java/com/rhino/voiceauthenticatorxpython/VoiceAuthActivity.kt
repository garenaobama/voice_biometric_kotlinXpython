package com.rhino.voiceauthenticatorxpython

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Activity mẫu để sử dụng Voice Biometric Service
 */
class VoiceAuthActivity : AppCompatActivity() {
    
    private lateinit var voiceService: VoiceBiometricService
    private lateinit var resultTextView: TextView
    private lateinit var recognizeButton: Button
    private lateinit var trainButton: Button
    private lateinit var listUsersButton: Button
    
    private val RECORD_AUDIO_PERMISSION = 100
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_auth)
        
        // Khởi tạo service
        voiceService = VoiceBiometricService(this)
        
        // Khởi tạo views
        resultTextView = findViewById(R.id.resultTextView)
        recognizeButton = findViewById(R.id.recognizeButton)
        trainButton = findViewById(R.id.trainButton)
        listUsersButton = findViewById(R.id.listUsersButton)
        
        // Setup click listeners
        recognizeButton.setOnClickListener {
            if (checkPermission()) {
                recognizeVoice()
            } else {
                requestPermission()
            }
        }
        
        trainButton.setOnClickListener {
            // TODO: Implement training UI
            Toast.makeText(this, "Tính năng training sẽ được implement sau", Toast.LENGTH_SHORT).show()
        }
        
        listUsersButton.setOnClickListener {
            listAllUsers()
        }
    }
    
    /**
     * Nhận diện giọng nói
     */
    private fun recognizeVoice() {
        recognizeButton.isEnabled = false
        resultTextView.text = "Đang ghi âm và nhận diện...\nVui lòng nói tên của bạn"
        
        // Chạy trên background thread để không block UI
        Thread {
            val result = voiceService.recordAndRecognize(durationSeconds = 3)
            
            // Cập nhật UI trên main thread
            runOnUiThread {
                recognizeButton.isEnabled = true
                
                if (result.success) {
                    resultTextView.text = """
                        ✅ Nhận diện thành công!
                        
                        User: ${result.identity}
                        Độ tin cậy: ${String.format("%.2f%%", (result.confidence ?: 0.0) * 100)}
                        
                        ${result.message}
                    """.trimIndent()
                    
                    Toast.makeText(this, "Xác thực thành công!", Toast.LENGTH_SHORT).show()
                } else {
                    resultTextView.text = """
                        ❌ Nhận diện thất bại
                        
                        ${result.message}
                        
                        Vui lòng thử lại
                    """.trimIndent()
                    
                    Toast.makeText(this, "Xác thực thất bại", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    /**
     * Liệt kê tất cả users trong database
     */
    private fun listAllUsers() {
        listUsersButton.isEnabled = false
        resultTextView.text = "Đang tải danh sách users..."
        
        Thread {
            val result = voiceService.getAllUsers()
            
            runOnUiThread {
                listUsersButton.isEnabled = true
                
                if (result.success) {
                    if (result.users.isEmpty()) {
                        resultTextView.text = "Database trống. Chưa có user nào."
                    } else {
                        val usersText = result.users.joinToString("\n", "Danh sách users:\n\n")
                        resultTextView.text = usersText
                    }
                } else {
                    resultTextView.text = "Lỗi: ${result.message}"
                }
            }
        }.start()
    }
    
    /**
     * Kiểm tra quyền ghi âm
     */
    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Yêu cầu quyền ghi âm
     */
    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_PERMISSION
        )
    }
    
    /**
     * Xử lý kết quả yêu cầu quyền
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recognizeVoice()
            } else {
                Toast.makeText(
                    this,
                    "Cần quyền ghi âm để sử dụng tính năng này",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

