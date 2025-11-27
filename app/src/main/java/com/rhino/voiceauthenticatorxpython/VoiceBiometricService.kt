package com.rhino.voiceauthenticatorxpython

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Service để tương tác với Python voice biometric module
 */
class VoiceBiometricService(private val context: Context) {
    
    private var python: Python? = null
    private var initError: String? = null
    private val TAG = "VoiceBiometricService"
    
    // Cấu hình ghi âm cho file WAV (16kHz, Mono, 16-bit PCM)
    private val RECORDER_SAMPLE_RATE = 16000
    private val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
    private val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    
    init {
        initializePython()
    }
    
    private fun initializePython() {
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            python = Python.getInstance()
            Log.i(TAG, "Python initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Python", e)
            initError = e.toString()
        }
    }
    
    private fun checkPythonResult(result: com.chaquo.python.PyObject?): Map<String, Any?> {
        if (result == null) {
            return mapOf("success" to false, "message" to "Python function returned null (or Python not initialized)")
        }
        val map = result.asMap()
        
        fun getValue(keyName: String): Any? {
            val key = map.keys.find { it.toString() == keyName }
            return if (key != null) map[key] else null
        }

        val success = getValue("success")?.toString()?.toBoolean() ?: false
        val message = getValue("message")?.toString() ?: "No message"
        val identity = getValue("identity")?.toString()
        val confidence = getValue("confidence")?.toString()?.toDoubleOrNull()
        
        return mapOf(
            "success" to success,
            "message" to message,
            "identity" to identity,
            "confidence" to confidence
        )
    }

    /**
     * Hàm private để đọc và in output từ Python (stdout) nếu có
     * (Lưu ý: Chaquopy tự động redirect stdout vào Logcat, nhưng ta có thể log thêm ở đây nếu cần)
     */
    
    /**
     * Hàm private để ghi file WAV chuẩn sử dụng AudioRecord
     */
    private fun recordWavFile(outputFile: File, durationSeconds: Int): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission RECORD_AUDIO not granted")
            return false
        }

        var audioRecord: AudioRecord? = null
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                RECORDER_SAMPLE_RATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLE_RATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed")
                return false
            }

            val data = ByteArray(bufferSize)
            val fos = FileOutputStream(outputFile)

            // Ghi header tạm (sẽ cập nhật sau)
            writeWavHeader(fos, 0, 0, RECORDER_SAMPLE_RATE, 1)

            audioRecord.startRecording()
            Log.i(TAG, "Started recording WAV to ${outputFile.absolutePath}")
            
            val startTime = System.currentTimeMillis()
            var totalAudioLen: Long = 0

            while (System.currentTimeMillis() - startTime < durationSeconds * 1000) {
                val read = audioRecord.read(data, 0, bufferSize)
                if (read != AudioRecord.ERROR_INVALID_OPERATION && read > 0) {
                    fos.write(data, 0, read)
                    totalAudioLen += read
                }
            }

            audioRecord.stop()
            fos.close()

            // Cập nhật lại header với kích thước file thực tế
            updateWavHeader(outputFile, totalAudioLen, RECORDER_SAMPLE_RATE, 1)
            
            Log.i(TAG, "Finished recording WAV. Size: ${outputFile.length()} bytes")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error recording WAV", e)
            return false
        } finally {
            try { audioRecord?.release() } catch (e: Exception) {}
        }
    }

    private fun writeWavHeader(out: FileOutputStream, totalAudioLen: Long, totalDataLen: Long, longSampleRate: Int, channels: Int) {
        val byteRate = (RECORDER_SAMPLE_RATE * 16 * channels / 8).toLong()
        val header = ByteArray(44)

        header[0] = 'R'.toByte(); header[1] = 'I'.toByte(); header[2] = 'F'.toByte(); header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte(); header[9] = 'A'.toByte(); header[10] = 'V'.toByte(); header[11] = 'E'.toByte()
        header[12] = 'f'.toByte(); header[13] = 'm'.toByte(); header[14] = 't'.toByte(); header[15] = ' '.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (2 * 16 / 8).toByte(); header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.toByte(); header[37] = 'a'.toByte(); header[38] = 't'.toByte(); header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        out.write(header, 0, 44)
    }

    private fun updateWavHeader(file: File, totalAudioLen: Long, sampleRate: Int, channels: Int) {
        try {
            val randomAccessFile = RandomAccessFile(file, "rw")
            randomAccessFile.seek(0)
            
            val totalDataLen = totalAudioLen + 36
            val byteRate = (sampleRate * 16 * channels / 8).toLong()

            val header = ByteArray(44)
            // Rewrite header logic
            header[0] = 'R'.toByte(); header[1] = 'I'.toByte(); header[2] = 'F'.toByte(); header[3] = 'F'.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = (totalDataLen shr 8 and 0xff).toByte()
            header[6] = (totalDataLen shr 16 and 0xff).toByte()
            header[7] = (totalDataLen shr 24 and 0xff).toByte()
            header[8] = 'W'.toByte(); header[9] = 'A'.toByte(); header[10] = 'V'.toByte(); header[11] = 'E'.toByte()
            header[12] = 'f'.toByte(); header[13] = 'm'.toByte(); header[14] = 't'.toByte(); header[15] = ' '.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
            header[20] = 1; header[21] = 0
            header[22] = channels.toByte(); header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = (sampleRate shr 8 and 0xff).toByte()
            header[26] = (sampleRate shr 16 and 0xff).toByte()
            header[27] = (sampleRate shr 24 and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = (byteRate shr 8 and 0xff).toByte()
            header[30] = (byteRate shr 16 and 0xff).toByte()
            header[31] = (byteRate shr 24 and 0xff).toByte()
            header[32] = (channels * 16 / 8).toByte(); header[33] = 0
            header[34] = 16; header[35] = 0
            header[36] = 'd'.toByte(); header[37] = 'a'.toByte(); header[38] = 't'.toByte(); header[39] = 'a'.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = (totalAudioLen shr 8 and 0xff).toByte()
            header[42] = (totalAudioLen shr 16 and 0xff).toByte()
            header[43] = (totalAudioLen shr 24 and 0xff).toByte()

            randomAccessFile.write(header)
            randomAccessFile.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating WAV header", e)
        }
    }

    fun recognizeVoiceFromFile(wavFilePath: String): RecognitionResult {
        if (python == null) return RecognitionResult(false, null, null, "Lỗi khởi tạo Python: $initError")
        
        Log.d(TAG, "Calling recognize_voice_from_file with $wavFilePath")
        return try {
            val androidApi = python!!.getModule("android_api")
            val result = androidApi.callAttr("recognize_voice_from_file", wavFilePath)
            
            val data = checkPythonResult(result)
            val success = data["success"] as Boolean
            val message = data["message"] as String
            
            Log.d(TAG, "Recognize result: success=$success, identity=${data["identity"]}, confidence=${data["confidence"]}")
            
            RecognitionResult(success, data["identity"] as String?, data["confidence"] as Double?, message)
        } catch (e: Exception) {
            Log.e(TAG, "Error in recognizeVoiceFromFile", e)
            RecognitionResult(false, null, null, "Lỗi: ${e.message}")
        }
    }
    
    fun recordAndRecognize(durationSeconds: Int = 3): RecognitionResult {
        if (python == null) return RecognitionResult(false, null, null, "Lỗi khởi tạo Python: $initError")

        val outputFile = File(context.getExternalFilesDir(null), "temp_recording.wav")
        
        val recordSuccess = recordWavFile(outputFile, durationSeconds)
        
        if (!recordSuccess) {
             return RecognitionResult(false, null, null, "Lỗi ghi âm (AudioRecord failed)")
        }
        
        val result = recognizeVoiceFromFile(outputFile.absolutePath)
        outputFile.delete()
        return result
    }
    
    fun trainUser(name: String, wavFiles: List<String>): TrainingResult {
        if (python == null) return TrainingResult(false, "Lỗi khởi tạo Python: $initError")

        Log.d(TAG, "Calling train_user_voice for $name with ${wavFiles.size} files")
        return try {
            val androidApi = python!!.getModule("android_api")
            val result = androidApi.callAttr("train_user_voice", name, wavFiles)
            
            val data = checkPythonResult(result)
            val success = data["success"] as Boolean
            val message = data["message"] as String
            
            Log.d(TAG, "Train result: success=$success, message=$message")
            
            TrainingResult(success, message)
        } catch (e: Exception) {
            Log.e(TAG, "Error in trainUser", e)
            TrainingResult(false, "Lỗi: ${e.message}")
        }
    }
    
    fun deleteUser(name: String): DeleteResult {
        if (python == null) return DeleteResult(false, "Lỗi khởi tạo Python: $initError")

        return try {
            val androidApi = python!!.getModule("android_api")
            val result = androidApi.callAttr("delete_user", name)
            
            val data = checkPythonResult(result)
            DeleteResult(data["success"] as Boolean, data["message"] as String)
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteUser", e)
            DeleteResult(false, "Lỗi: ${e.message}")
        }
    }
    
    fun getAllUsers(): UsersListResult {
        if (python == null) return UsersListResult(false, emptyList(), "Lỗi khởi tạo Python: $initError")

        Log.d(TAG, "Calling get_all_users")
        return try {
            val androidApi = python!!.getModule("android_api")
            val result = androidApi.callAttr("get_all_users")
            
            val data = checkPythonResult(result)
            val map = result?.asMap() ?: emptyMap<Any?, Any?>()
            val usersKey = map.keys.find { it.toString() == "users" }
            val rawUsers = if (usersKey != null) map[usersKey] else null
            
            // Sửa logic parse list
            val users = if (rawUsers != null) {
                // Kiểm tra xem có phải List không
                if (rawUsers is List<*>) {
                    rawUsers.mapNotNull { it?.toString() }
                } else {
                    // Nếu là PyObject (PyList), convert to List
                    try {
                         // Ép kiểu PyObject thành List
                        val pyList = rawUsers as? com.chaquo.python.PyObject
                        pyList?.asList()?.map { it.toString() } ?: emptyList()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing users list", e)
                        emptyList()
                    }
                }
            } else {
                emptyList()
            }
            
            Log.d(TAG, "Get users result: count=${users.size}, users=$users")
            
            UsersListResult(data["success"] as Boolean, users, data["message"] as String)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getAllUsers", e)
            UsersListResult(false, emptyList(), "Lỗi: ${e.message}")
        }
    }
    
    fun recordAudio(durationSeconds: Int = 3, fileName: String): String? {
        val outputFile = File(context.getExternalFilesDir(null), "$fileName.wav")
        val success = recordWavFile(outputFile, durationSeconds)
        return if (success) outputFile.absolutePath else null
    }
    
    fun cleanupTempFiles(files: List<String>) {
        files.forEach { path ->
            try {
                val f = File(path)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
    }
}

data class RecognitionResult(val success: Boolean, val identity: String?, val confidence: Double?, val message: String)
data class TrainingResult(val success: Boolean, val message: String)
data class DeleteResult(val success: Boolean, val message: String)
data class UsersListResult(val success: Boolean, val users: List<String>, val message: String)
