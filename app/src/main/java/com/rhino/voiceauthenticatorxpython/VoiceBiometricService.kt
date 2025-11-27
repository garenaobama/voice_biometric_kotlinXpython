package com.rhino.voiceauthenticatorxpython

import android.content.Context
import android.media.MediaRecorder
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

/**
 * Service để tương tác với Python voice biometric module
 */
class VoiceBiometricService(private val context: Context) {
    
    private var python: Python? = null
    
    init {
        initializePython()
    }
    
    private fun initializePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        python = Python.getInstance()
    }
    
    /**
     * Nhận diện giọng nói từ file wav
     * @param wavFilePath Đường dẫn đến file wav
     * @return Kết quả nhận diện
     */
    fun recognizeVoiceFromFile(wavFilePath: String): RecognitionResult {
        return try {
            val androidApi = python?.getModule("android_api")
            val result = androidApi?.callAttr("recognize_voice_from_file", wavFilePath)
            
            RecognitionResult(
                success = result?.get("success")?.toBoolean() ?: false,
                identity = result?.get("identity")?.toString(),
                confidence = result?.get("confidence")?.toDouble(),
                message = result?.get("message")?.toString() ?: ""
            )
        } catch (e: Exception) {
            RecognitionResult(
                success = false,
                identity = null,
                confidence = null,
                message = "Lỗi: ${e.message}"
            )
        }
    }
    
    /**
     * Ghi âm và nhận diện giọng nói
     * @param durationSeconds Thời gian ghi âm (giây)
     * @return Kết quả nhận diện
     */
    fun recordAndRecognize(durationSeconds: Int = 3): RecognitionResult {
        val outputFile = File(context.getExternalFilesDir(null), "temp_recording.wav")
        
        return try {
            // Ghi âm sử dụng MediaRecorder
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            
            // Đợi cho đến khi ghi âm xong
            Thread.sleep(durationSeconds * 1000L)
            
            recorder.apply {
                stop()
                release()
            }
            
            // Nhận diện giọng nói
            val result = recognizeVoiceFromFile(outputFile.absolutePath)
            
            // Xóa file tạm
            outputFile.delete()
            
            result
        } catch (e: Exception) {
            RecognitionResult(
                success = false,
                identity = null,
                confidence = null,
                message = "Lỗi ghi âm: ${e.message}"
            )
        }
    }
    
    /**
     * Train model cho user mới
     * @param name Tên user
     * @param wavFiles Danh sách đường dẫn đến các file wav (tối thiểu 3 files)
     * @return Kết quả training
     */
    fun trainUser(name: String, wavFiles: List<String>): TrainingResult {
        return try {
            val androidApi = python?.getModule("android_api")
            val result = androidApi?.callAttr("train_user_voice", name, wavFiles)
            
            TrainingResult(
                success = result?.get("success")?.toBoolean() ?: false,
                message = result?.get("message")?.toString() ?: ""
            )
        } catch (e: Exception) {
            TrainingResult(
                success = false,
                message = "Lỗi: ${e.message}"
            )
        }
    }
    
    /**
     * Xóa user khỏi database
     * @param name Tên user cần xóa
     * @return Kết quả xóa
     */
    fun deleteUser(name: String): DeleteResult {
        return try {
            val androidApi = python?.getModule("android_api")
            val result = androidApi?.callAttr("delete_user", name)
            
            DeleteResult(
                success = result?.get("success")?.toBoolean() ?: false,
                message = result?.get("message")?.toString() ?: ""
            )
        } catch (e: Exception) {
            DeleteResult(
                success = false,
                message = "Lỗi: ${e.message}"
            )
        }
    }
    
    /**
     * Lấy danh sách tất cả users trong database
     * @return Danh sách users
     */
    fun getAllUsers(): UsersListResult {
        return try {
            val androidApi = python?.getModule("android_api")
            val result = androidApi?.callAttr("get_all_users")
            
            val usersList = result?.get("users")?.asList()?.map { it.toString() } ?: emptyList()
            
            UsersListResult(
                success = result?.get("success")?.toBoolean() ?: false,
                users = usersList,
                message = result?.get("message")?.toString() ?: ""
            )
        } catch (e: Exception) {
            UsersListResult(
                success = false,
                users = emptyList(),
                message = "Lỗi: ${e.message}"
            )
        }
    }
    
    /**
     * Ghi âm giọng nói và lưu ra file, dùng cho quá trình training
     * @param durationSeconds Thời gian ghi âm (giây)
     * @param fileName Tên file (không cần đuôi, sẽ tự thêm .wav)
     * @return Đường dẫn tuyệt đối tới file ghi âm, hoặc null nếu lỗi
     */
    fun recordAudio(durationSeconds: Int = 3, fileName: String): String? {
        val outputFile = File(context.getExternalFilesDir(null), "$fileName.wav")
        
        return try {
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            
            // Ghi âm trong khoảng thời gian yêu cầu
            Thread.sleep(durationSeconds * 1000L)
            
            recorder.apply {
                stop()
                release()
            }
            
            outputFile.absolutePath
        } catch (e: Exception) {
            try {
                if (outputFile.exists()) {
                    outputFile.delete()
                }
            } catch (_: Exception) {
            }
            null
        }
    }
    
    /**
     * Xóa các file ghi âm tạm sau khi train / nhận diện xong
     */
    fun cleanupTempFiles(files: List<String>) {
        files.forEach { path ->
            try {
                val f = File(path)
                if (f.exists()) {
                    f.delete()
                }
            } catch (_: Exception) {
                // Bỏ qua lỗi xóa file
            }
        }
    }
}

/**
 * Data class cho kết quả nhận diện
 */
data class RecognitionResult(
    val success: Boolean,
    val identity: String?,
    val confidence: Double?,
    val message: String
)

/**
 * Data class cho kết quả training
 */
data class TrainingResult(
    val success: Boolean,
    val message: String
)

/**
 * Data class cho kết quả xóa user
 */
data class DeleteResult(
    val success: Boolean,
    val message: String
)

/**
 * Data class cho danh sách users
 */
data class UsersListResult(
    val success: Boolean,
    val users: List<String>,
    val message: String
)

