## VoiceAuthenticatorxPython · Voice Biometric Authentication on Android with Python (Chaquopy)

VoiceAuthenticatorxPython là một bộ mã mẫu hoàn chỉnh giúp bạn:

- **Train & nhận diện giọng nói (voice biometrics) trên Android**, sử dụng:
  - **Python + scikit-learn + GMM** cho phần xử lý voice biometric.
  - **Chaquopy** để nhúng Python trực tiếp vào Android app (Kotlin).
- **Quản lý user bằng giọng nói**:
  - Thêm user mới (train bằng nhiều file ghi âm).
  - Nhận diện / xác thực user theo giọng nói.
  - Liệt kê & **xóa user** (đã được implement).
  - Phát hiện **“người lạ”** (stranger / imposter) không thuộc database.

Repo này mang tính chất **demo chất lượng cao**: code đã được chỉnh sửa để chạy thực tế trên Android, có xử lý permission, ghi âm WAV chuẩn, logging chi tiết và các cơ chế bảo mật cơ bản cho voice biometrics.

---

## 1. Kiến trúc tổng thể

### 1.1. Các thành phần chính

- **Android app (Kotlin)** – thư mục `app/`

  - `VoiceAuthActivity.kt`: Activity UI chính để:
    - Ghi âm và nhận diện giọng nói (login bằng voice).
    - Train user mới với nhiều lần ghi âm.
    - Liệt kê users + xóa user.
  - `VoiceBiometricService.kt`: lớp service trung gian:
    - Ghi âm audio bằng `AudioRecord` (PCM 16-bit, mono, 16 kHz).
    - Lưu file WAV hợp chuẩn (tự viết WAV header).
    - Gọi các hàm Python qua Chaquopy.

- **Python voice biometric core** – thư mục `app/src/main/python/`

  - `android_api.py`: API wrapper cho Android:
    - `train_user_voice(name, wav_files_list)`: train GMM và lưu model `.gmm`.
    - `recognize_voice_from_file(wav_file_path)`: nhận diện từ file WAV.
    - `get_all_users()`: liệt kê users (theo tên file `.gmm`).
    - `delete_user(name)`: xóa model `.gmm` tương ứng.
    - Quản lý **đường dẫn models** trong internal storage của Android.
    - Có **logging DEBUG chi tiết** (python.stdout trong Logcat).
  - `main_functions.py`: xử lý feature:
    - Trích xuất MFCC + delta MFCC bằng `python_speech_features`.
    - Chuẩn hóa bằng `sklearn.preprocessing`.
    - (Phần Face Recognition/TensorFlow được bọc `try/except` để không làm crash trên Android).

- **Tài liệu & ví dụ gốc** – thư mục `voice_biometric/`
  - `README.md`: tài liệu gốc của project voice biometric (desktop).
  - `QUICK_START_ANDROID.md`: hướng dẫn nhanh tích hợp Android.
  - `ANDROID_INTEGRATION.md`: hướng dẫn chi tiết từng bước.
  - `android_example/`: ví dụ Android ban đầu (tham khảo).

### 1.2. Dòng chảy dữ liệu (flow)

1. Người dùng bấm **“Thêm User”**:
   - `VoiceAuthActivity` mở dialog nhập tên user.
   - Sau đó gọi `startTrainingFlow(userName)` → ghi âm **3 lần** bằng `VoiceBiometricService.recordAudio`.
   - Sau khi đủ samples, gọi `voiceService.trainUser(userName, recordings)`.
2. Trong `VoiceBiometricService.trainUser`:
   - Service khởi động Python (Chaquopy) nếu chưa khởi động.
   - Gọi Python `android_api.train_user_voice(name, wavFiles)`.
   - Python:
     - Đọc từng file WAV bằng `scipy.io.wavfile.read`.
     - Trích xuất MFCC + delta features.
     - Train **Gaussian Mixture Model (GMM)** bằng `sklearn.mixture.GaussianMixture`.
     - Lưu model `.gmm` vào `internal storage` tại:
       - `/data/data/com.rhino.voiceauthenticatorxpython/files/gmm_models/<user>.gmm`.
3. Người dùng bấm **“Nhận diện”**:
   - App ghi âm 1 file `temp_recording.wav`.
   - Gọi `voiceService.recordAndRecognize(durationSeconds = 3)`.
   - Service gọi `android_api.recognize_voice_from_file`.
   - Python:
     - Nạp tất cả `.gmm` trong thư mục `gmm_models`.
     - Tính log-likelihood trung bình của audio trên từng model.
     - Chọn model có score cao nhất.
     - Áp dụng **threshold chặn người lạ**.
4. Kết quả được trả về dưới dạng `RecognitionResult` (Kotlin) với:
   - `success`, `identity`, `confidence`, `message`.

---

## 2. Tính năng đã triển khai

- **Nhận diện giọng nói (Voice Authentication)**:

  - Ghi âm bằng `AudioRecord` → file WAV chuẩn.
  - Nhận diện user dựa trên GMM.
  - Hiển thị:
    - Tên user nhận diện.
    - Độ tin cậy (%).
    - Thông điệp chi tiết.

- **Train User mới**:

  - Ghi âm **ít nhất 3 mẫu** giọng nói cho mỗi user.
  - Train GMM với:
    - `n_components = 32`.
    - `covariance_type = 'diag'`.
    - `max_iter = 100`.
  - Lưu model `.gmm` trong internal storage an toàn.

- **Danh sách Users**:

  - Lấy danh sách tất cả users (tên file `.gmm`).
  - Hiển thị trong `AlertDialog` để chọn hành động.

- **Xóa User (Delete User)**:

  - Chọn user trong dialog danh sách.
  - Xác nhận, rồi **xóa file `.gmm`** tương ứng trong `gmm_models`.
  - Cập nhật UI và phản hồi toast + message chi tiết.

- **Phát hiện Người Lạ (Stranger Detection)**:

  - Dùng ngưỡng **`AUTH_THRESHOLD`** trên log-likelihood trung bình:
    - Nếu score thấp hơn threshold → coi là **stranger**, từ chối xác thực.
  - Tránh trường hợp người lạ bị gán nhầm vào user gần nhất.

- **Logging chi tiết**:
  - Python (`android_api.py`) in `DEBUG:` ra `python.stdout` trong Logcat:
    - Đường dẫn model path.
    - Số lượng models.
    - Score từng user.
    - Quyết định final (best, confidence, reject stranger).
  - Kotlin (`VoiceBiometricService.kt`) log:
    - Ghi âm WAV (bắt đầu/kết thúc, kích thước file).
    - Kết quả train / recognize / delete / list users.

---

## 3. Cấu trúc thư mục

```text
VoiceAuthenticatorxPython/
├─ app/
│  ├─ src/
│  │  ├─ main/
│  │  │  ├─ java/com/rhino/voiceauthenticatorxpython/
│  │  │  │  ├─ VoiceAuthActivity.kt         # Activity UI chính
│  │  │  │  └─ VoiceBiometricService.kt     # Service gọi Python + ghi âm WAV
│  │  │  ├─ python/
│  │  │  │  ├─ android_api.py              # API Python cho Android
│  │  │  │  └─ main_functions.py           # Hàm extract_features (MFCC)
│  │  │  └─ res/layout/activity_voice_auth.xml
│  │  └─ ...
│  └─ build.gradle (module)
├─ voice_biometric/
│  ├─ README.md
│  ├─ QUICK_START_ANDROID.md
│  ├─ ANDROID_INTEGRATION.md
│  └─ android_example/
│     ├─ README_ANDROID.md
│     └─ ...
└─ README.md (file bạn đang đọc)
```

---

## 4. Chuẩn bị môi trường

### 4.1. Yêu cầu

- **Android Studio** mới (Arctic Fox trở lên).
- **Python 3.x** trên máy để Chaquopy có thể build dependencies.
- Thiết bị hoặc emulator Android:
  - Nên là **thiết bị thật** để mic hoạt động ổn định.

### 4.2. Chaquopy & Python dependencies

Trong `app/build.gradle` (module), phần cấu hình Chaquopy nên tương tự:

```gradle
plugins {
    id 'com.android.application'
    id 'com.chaquo.python'
}

android {
    defaultConfig {
        // ...
        ndk {
            abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"
        }
    }
}

python {
    buildPython "python3"
    pip {
        install "numpy==1.18.1"
        install "scipy==1.4.1"
        install "scikit-learn"
        install "python-speech-features==0.6"
    }
}
```

> Lưu ý: Version thực tế có thể khác tuỳ môi trường; nên giữ `numpy/scipy` tương thích với Chaquopy.

---

## 5. Cách build & chạy

1. **Clone repo**:

```bash
git clone <YOUR_REPO_URL>
cd VoiceAuthenticatorxPython
```

2. **Mở bằng Android Studio**:

   - `File → Open...` → chọn thư mục repo.

3. **Sync Gradle**:

   - Android Studio sẽ tự động sync; nếu Chaquopy thiếu Python, chỉnh lại `buildPython`.

4. **Chạy app**:

   - Kết nối thiết bị thật (USB / ADB).
   - Chọn module `app`, bấm Run.

5. **Cấp quyền**:
   - Lần đầu chạy, app sẽ yêu cầu quyền `RECORD_AUDIO`.
   - Bạn phải **Allow** để sử dụng voice biometric.

---

## 6. Hướng dẫn sử dụng trong app

### 6.1. Thêm user mới (Train)

1. Mở app `VoiceAuthenticatorxPython`.
2. Bấm **“Thêm User”**.
3. Nhập tên user (ví dụ: `nam`, `phuong`):
   - Không để trống.
   - Không dùng từ khóa `"unknown"`.
4. App sẽ:
   - Ghi âm 3 lần, mỗi lần 3 giây.
   - Hiển thị trạng thái từng lần ghi.
   - Train GMM model từ 3 mẫu ghi.
5. Sau khi xong:
   - Model `.gmm` được lưu.
   - UI báo **Thêm user thành công**.

### 6.2. Liệt kê & xóa users

1. Bấm **“Danh sách Users”**.
2. App sẽ:
   - Gọi Python `get_all_users`.
   - Hiển thị dialog list user (mỗi dòng là 1 tên).
3. Bấm vào tên user:
   - App hiển thị dialog xác nhận Xóa User.
4. Xác nhận:
   - Gọi `deleteUser(name)` → Python xoá file `.gmm`.
   - UI báo kết quả + có thể load lại danh sách.

### 6.3. Nhận diện / Xác thực người dùng

1. Bấm **“Nhận Diện Giọng Nói”**.
2. App ghi âm ~3 giây:
   - Text: “Đang ghi âm và nhận diện… Vui lòng nói tên của bạn”.
3. Service gọi Python:
   - Load tất cả GMM models.
   - Tính score cho từng model.
4. Python trả về:
   - Nếu score tốt và vượt threshold:
     - `success = true`, `identity = tên user`, `confidence ≈ 0.8–1.0`.
   - Nếu không:
     - `success = false`, `identity = None hoặc "Unknown"`, `confidence thấp`.
5. UI hiển thị:
   - “✅ Nhận diện thành công” hoặc “❌ Nhận diện thất bại”.
   - Độ tin cậy dưới dạng phần trăm.

---

## 7. Chi tiết kỹ thuật voice biometric

### 7.1. Trích xuất đặc trưng (Features)

Trong `main_functions.py`:

- **MFCC**:
  - `mfcc.mfcc(audio, rate, 0.025, 0.01, 20, appendEnergy=True, nfft=1103)`
  - Window 25ms, step 10ms, 20 hệ số MFCC.
- **Chuẩn hóa**:
  - `preprocessing.scale(mfcc_feat)` → zero-mean, unit-variance.
- **Delta MFCC**:
  - `calculate_delta(mfcc_feat)` → đạo hàm bậc nhất.
- **Feature vector**:
  - `combined = np.hstack((mfcc_feat, delta))` → 40-dim per frame.

### 7.2. GMM Training

Trong `android_api.py`:

```python
gmm = GaussianMixture(
    n_components=32,
    covariance_type='diag',
    max_iter=100,
    n_init=3
)
gmm.fit(features)
pickle.dump(gmm, open(model_file, 'wb'))
```

- Mỗi user có 1 GMM model riêng.
- Sử dụng covariance dạng `diag` để tối ưu tốc độ.

### 7.3. Scoring & Confidence

Nhận diện:

```python
avg_score = gmm.score(vector)  # average log-likelihood per frame
```

- Tính `avg_score` cho từng user.
- Chọn user có `avg_score` lớn nhất (`best_score`).

**Stranger detection (AUTH_THRESHOLD)**:

```python
AUTH_THRESHOLD = -40.0
if best_score < AUTH_THRESHOLD:
    # coi là người lạ, reject
```

**Confidence tương đối**:

```python
max_score = best_score
min_score = np.min(log_likelihood)
denom = max_score + abs(min_score) or 1e-10
confidence = (max_score - min_score) / denom  # nếu có >= 2 user
```

- Khi chỉ có 1 user, confidence được set cao sau khi vượt threshold.

---

## 8. Troubleshooting (Lỗi thường gặp)

- **Lỗi: `File format ... not understood. Only 'RIFF' and 'RIFX' supported.`**

  - Nguyên nhân: Ghi âm bằng `MediaRecorder` với định dạng 3GP/AMR → không phải WAV.
  - Fix: Ở repo này đã chuyển sang `AudioRecord` + ghi header WAV thủ công.

- **Lỗi: Không thấy user trong danh sách dù train thành công**

  - Kiểm tra log:
    - Python log: `Saved model to /data/data/.../gmm_models/user.gmm`.
    - Nếu `get_all_users` dùng path khác, đã được fix bằng `get_model_path()` trả về internal storage cố định.

- **Lỗi: `ImportError: cannot import name 'GMM' from 'sklearn.mixture'`**

  - Đã được fix bằng cách dùng `GaussianMixture` (API mới).

- **Lỗi: `No module named 'tensorflow'`**

  - Đã được bọc `try/except` trong `main_functions.py` để vô hiệu hóa phần Face Recognition khi thiếu TensorFlow.

- **Nhận diện sai / không ổn định**
  - Thu thêm nhiều mẫu train hơn (3–5 lần).
  - Ghi âm trong môi trường ít ồn, mic ổn định.
  - Điều chỉnh `AUTH_THRESHOLD` (ví dụ -45, -35) cho phù hợp dữ liệu thực tế.

---

## 9. Roadmap / Ý tưởng phát triển thêm

- **UI/UX đẹp hơn**:

  - Dùng Material Design, ProgressBar khi train/recognize.
  - Hiển thị lịch sử đăng nhập bằng giọng nói.

- **Multi-session training**:

  - Cho phép bổ sung thêm mẫu giọng nói cho user đã tồn tại (incremental training).

- **Model export / import**:

  - Đồng bộ `.gmm` models lên server hoặc giữa nhiều thiết bị.

- **Kết hợp 2 yếu tố (2FA)**:

  - Voice + PIN hoặc Voice + Face Recognition.

- **Thử nghiệm các thuật toán khác**:
  - i-vector, x-vector, d-vector, hoặc embedding từ mô hình Deep Learning mới hơn.

---

## 10. Đóng góp (Contributing)

Pull Request / Issues rất được hoan nghênh:

- Thêm test tự động cho phần Python (unit test trên desktop).
- Cải thiện UX trong `VoiceAuthActivity`.
- Tối ưu hiệu năng (giảm thời gian load models, caching…).

Nếu bạn build được một POC hoặc sản phẩm thực tế từ repo này, rất mong bạn chia sẻ lại để repo tiếp tục được cải thiện 🎯
