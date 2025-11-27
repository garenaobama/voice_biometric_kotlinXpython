package com.rhino.voiceauthenticatorxpython

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
            if (checkPermission()) {
                showAddUserDialog()
            } else {
                requestPermission()
            }
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
     * Hiển thị dialog để thêm user mới
     */
    private fun showAddUserDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "Nhập tên user"
        
        AlertDialog.Builder(this)
            .setTitle("Thêm User Mới")
            .setMessage("Nhập tên user và ghi âm ít nhất 3 lần để train model")
            .setView(input)
            .setPositiveButton("Bắt đầu") { _, _ ->
                val userName = input.text.toString().trim()
                if (userName.isEmpty()) {
                    Toast.makeText(this, "Tên user không được để trống", Toast.LENGTH_SHORT).show()
                } else if (userName == "unknown") {
                    Toast.makeText(this, "Tên 'unknown' không được phép", Toast.LENGTH_SHORT).show()
                } else {
                    startTrainingFlow(userName)
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    /**
     * Bắt đầu quy trình training: ghi âm nhiều lần và train model
     */
    private fun startTrainingFlow(userName: String) {
        val recordings = mutableListOf<String>()
        val minRecordings = 3
        var currentRecording = 0
        
        trainButton.isEnabled = false
        resultTextView.text = "Chuẩn bị ghi âm lần 1/$minRecordings..."
        
        fun recordNext() {
            if (currentRecording >= minRecordings) {
                // Đã ghi đủ, bắt đầu train
                resultTextView.text = "Đang train model cho user: $userName\nVui lòng đợi..."
                
                Thread {
                    val result = voiceService.trainUser(userName, recordings)
                    
                    runOnUiThread {
                        trainButton.isEnabled = true
                        
                        if (result.success) {
                            resultTextView.text = """
                                ✅ Thêm user thành công!
                                
                                User: $userName
                                ${result.message}
                                
                                Bạn có thể thử nhận diện ngay bây giờ
                            """.trimIndent()
                            
                            Toast.makeText(this, "Thêm user thành công!", Toast.LENGTH_SHORT).show()
                            
                            // Xóa các file recording tạm
                            voiceService.cleanupTempFiles(recordings)
                        } else {
                            resultTextView.text = """
                                ❌ Thêm user thất bại
                                
                                ${result.message}
                                
                                Vui lòng thử lại
                            """.trimIndent()
                            
                            Toast.makeText(this, "Thêm user thất bại: ${result.message}", Toast.LENGTH_LONG).show()
                            
                            // Xóa các file recording tạm
                            voiceService.cleanupTempFiles(recordings)
                        }
                    }
                }.start()
                return
            }
            
            currentRecording++
            resultTextView.text = "Đang ghi âm lần $currentRecording/$minRecordings...\nVui lòng nói rõ ràng"
            
            Thread {
                val filePath = voiceService.recordAudio(
                    durationSeconds = 3,
                    fileName = "train_${userName}_$currentRecording"
                )
                
                runOnUiThread {
                    if (filePath != null) {
                        recordings.add(filePath)
                        resultTextView.text = "✅ Đã ghi âm lần $currentRecording/$minRecordings\n\n"
                        
                        if (currentRecording < minRecordings) {
                            resultTextView.append("Chuẩn bị ghi âm lần ${currentRecording + 1}/$minRecordings...\nĐợi 2 giây...")
                            
                            // Đợi 2 giây trước khi ghi âm lần tiếp theo (sử dụng Handler)
                            resultTextView.postDelayed({
                                recordNext()
                            }, 2000)
                        } else {
                            recordNext() // Train model
                        }
                    } else {
                        trainButton.isEnabled = true
                        resultTextView.text = """
                            ❌ Lỗi ghi âm lần $currentRecording
                            
                            Vui lòng thử lại
                        """.trimIndent()
                        
                        Toast.makeText(this, "Lỗi ghi âm, vui lòng thử lại", Toast.LENGTH_SHORT).show()
                        
                        // Xóa các file đã ghi
                        voiceService.cleanupTempFiles(recordings)
                    }
                }
            }.start()
        }
        
        // Bắt đầu ghi âm lần đầu
        recordNext()
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
                // User có thể đã click recognize hoặc train button
                // Không tự động gọi để tránh confusion
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

