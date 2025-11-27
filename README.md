## VoiceAuthenticatorxPython · Voice Biometric Authentication on Android with Python (Chaquopy)

VoiceAuthenticatorxPython is a complete sample project that shows how to:

- **Train & recognize speaker identity (voice biometrics) on Android**, using:
  - **Python + scikit-learn + GMM** for the biometric logic.
  - **Chaquopy** to embed Python directly in an Android (Kotlin) app.
- **Manage users by voice**:
  - Enroll new users (train with multiple recordings).
  - Recognize / authenticate users by voice.
  - List & **delete users** (implemented).
  - Detect **strangers / imposters** who are not in the enrolled database.

This repo is a **production‑style demo**: the code has been adapted to run on real Android devices, handling permissions, proper WAV recording, detailed logging, and basic security mechanisms for voice biometrics.

---

## 1. Architecture Overview

### 1.1. Main components

- **Android app (Kotlin)** – folder `app/`
  - `VoiceAuthActivity.kt`: main UI Activity:
    - Record and recognize voice (voice login).
    - Train new users with multiple recordings.
    - List users and delete selected users.
  - `VoiceBiometricService.kt`: service layer between Android and Python:
    - Records audio using `AudioRecord` (16‑bit PCM, mono, 16 kHz).
    - Writes valid WAV files (manual WAV header).
    - Calls Python functions via Chaquopy.

- **Python voice biometric core** – folder `app/src/main/python/`
  - `android_api.py`: Android‑facing Python API:
    - `train_user_voice(name, wav_files_list)`: train a GMM and save `.gmm` model.
    - `recognize_voice_from_file(wav_file_path)`: recognize from a WAV file.
    - `get_all_users()`: list users (based on `.gmm` files).
    - `delete_user(name)`: delete the corresponding `.gmm` model.
    - Manages **model paths** in Android internal storage.
    - Includes **detailed DEBUG logging** (via `python.stdout` in Logcat).
  - `main_functions.py`: feature extraction:
    - Extracts MFCC + delta MFCC using `python_speech_features`.
    - Normalizes features with `sklearn.preprocessing`.
    - Face Recognition / TensorFlow is wrapped in `try/except` to avoid crashes on Android when TF is missing.

- **Original docs & examples** – folder `voice_biometric/`
  - `README.md`: original (desktop) voice biometrics documentation.
  - `QUICK_START_ANDROID.md`: Android quick‑start guide.
  - `ANDROID_INTEGRATION.md`: detailed Android integration guide.
  - `android_example/`: original Android sample (for reference).

### 1.2. Processing flow

1. User taps **“Add User”**:
   - `VoiceAuthActivity` opens a dialog to input the username.
   - Calls `startTrainingFlow(userName)` → records **3 times** via `VoiceBiometricService.recordAudio`.
   - When enough samples are collected, calls `voiceService.trainUser(userName, recordings)`.
2. In `VoiceBiometricService.trainUser`:
   - The service starts Python (Chaquopy) if it’s not started.
   - Calls Python `android_api.train_user_voice(name, wavFiles)`.
   - Python:
     - Reads each WAV file using `scipy.io.wavfile.read`.
     - Extracts MFCC + delta features.
     - Trains a **Gaussian Mixture Model (GMM)** using `sklearn.mixture.GaussianMixture`.
     - Saves the `.gmm` model into internal storage at:
       - `/data/data/com.rhino.voiceauthenticatorxpython/files/gmm_models/<user>.gmm`.
3. User taps **“Recognize Voice”**:
   - App records a `temp_recording.wav` file.
   - Calls `voiceService.recordAndRecognize(durationSeconds = 3)`.
   - The service calls `android_api.recognize_voice_from_file`.
   - Python:
     - Loads all `.gmm` models from the `gmm_models` directory.
     - Computes average log‑likelihood for the input audio under each model.
     - Picks the model with the highest score.
     - Applies a **stranger threshold** to reject unknown speakers.
4. Result is returned as `RecognitionResult` (Kotlin) with:
   - `success`, `identity`, `confidence`, `message`.

---

## 2. Implemented Features

- **Voice Authentication**:
  - Record using `AudioRecord` → valid WAV file.
  - Recognize enrolled users via GMM models.
  - Shows:
    - Recognized username.
    - Confidence (as percentage).
    - Detailed message from Python.

- **User Enrollment (Training)**:
  - Records **at least 3 samples** per user.
  - Trains a GMM with:
    - `n_components = 32`.
    - `covariance_type = 'diag'`.
    - `max_iter = 100`.
  - Saves `.gmm` models into Android internal storage.

- **User Listing**:
  - Fetches all users (by `.gmm` filenames).
  - Shows them in an `AlertDialog` where you can choose a user.

- **Delete User**:
  - Select a user from the dialog.
  - Confirm, then **delete the `.gmm` file** in `gmm_models`.
  - Updates UI and shows toast + status message.

- **Stranger Detection**:
  - Uses an **`AUTH_THRESHOLD`** on average log‑likelihood:
    - If best score is below threshold → treat as **stranger** and reject auth.
  - Prevents mapping an unknown speaker to the closest enrolled model.

- **Detailed Logging**:
  - Python (`android_api.py`) logs `DEBUG:` messages via `python.stdout` in Logcat:
    - Model path.
    - Number of models.
    - Score for each user.
    - Final decision (best, confidence, stranger rejected or not).
  - Kotlin (`VoiceBiometricService.kt`) logs:
    - WAV recording start/finish and file size.
    - Results of train / recognize / delete / list operations.

---

## 3. Project Structure

```text
VoiceAuthenticatorxPython/
├─ app/
│  ├─ src/
│  │  ├─ main/
│  │  │  ├─ java/com/rhino/voiceauthenticatorxpython/
│  │  │  │  ├─ VoiceAuthActivity.kt         # Main Activity
│  │  │  │  └─ VoiceBiometricService.kt     # Service to call Python + record WAV
│  │  │  ├─ python/
│  │  │  │  ├─ android_api.py               # Python API for Android
│  │  │  │  └─ main_functions.py            # MFCC & feature extraction
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
└─ README.md (this file)
```

---

## 4. Environment Setup

### 4.1. Requirements

- Recent **Android Studio** (Arctic Fox or newer).
- **Python 3.x** installed so Chaquopy can build Python dependencies.
- Android device or emulator:
  - Prefer a **real device** for stable microphone input.

### 4.2. Chaquopy & Python dependencies

In `app/build.gradle` (module), Chaquopy configuration should look like:

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

> Note: Versions may vary; keep `numpy/scipy` compatible with Chaquopy’s supported versions.

---

## 5. Build & Run

1. **Clone the repo**:

```bash
git clone <YOUR_REPO_URL>
cd VoiceAuthenticatorxPython
```

2. **Open in Android Studio**:
   - `File → Open...` → select the repo folder.

3. **Sync Gradle**:
   - Android Studio will sync; if Chaquopy complains about Python, adjust `buildPython`.

4. **Run the app**:
   - Connect a real device (USB / ADB).
   - Select module `app`, press Run.

5. **Grant permissions**:
   - On first run, the app will request `RECORD_AUDIO` permission.
   - You must **Allow** it to use voice biometrics.

---

## 6. In‑App Usage Guide

### 6.1. Enroll a new user (Train)

1. Open the `VoiceAuthenticatorxPython` app.
2. Tap **“Add User”**.
3. Enter username (e.g. `nam`, `phuong`):
   - Must not be empty.
   - Must not be `"unknown"`.
4. The app will:
   - Record 3 times, ~3 seconds each.
   - Show status for each recording.
   - Train a GMM model from these 3 samples.
5. After training:
   - The `.gmm` model is saved.
   - UI shows **User added successfully**.

### 6.2. List & delete users

1. Tap **“List Users”**.
2. The app will:
   - Call Python `get_all_users`.
   - Show a dialog with the usernames.
3. Tap on a username:
   - The app shows a confirmation dialog to delete that user.
4. Confirm:
   - Calls `deleteUser(name)` → Python deletes the `.gmm` file.
   - UI shows the result and you can refresh the list if needed.

### 6.3. Recognize / authenticate users

1. Tap **“Recognize Voice”**.
2. The app records ~3 seconds:
   - Text: “Recording and recognizing… Please say your name”.
3. The service calls Python:
   - Loads all GMM models.
   - Computes a score for each model.
4. Python returns:
   - If score is good and above threshold:
     - `success = true`, `identity = username`, `confidence ≈ 0.8–1.0`.
   - Otherwise:
     - `success = false`, `identity = None or "Unknown"`, low `confidence`.
5. UI shows:
   - “✅ Recognized successfully” or “❌ Recognition failed”.
   - Confidence as percentage.

---

## 7. Voice Biometric Technical Details

### 7.1. Feature Extraction (MFCC)

In `main_functions.py`:

- **MFCC**:
  - `mfcc.mfcc(audio, rate, 0.025, 0.01, 20, appendEnergy=True, nfft=1103)`
  - 25 ms window, 10 ms step, 20 MFCC coefficients.
- **Normalization**:
  - `preprocessing.scale(mfcc_feat)` → zero‑mean, unit‑variance.
- **Delta MFCC**:
  - `calculate_delta(mfcc_feat)` → first‑order derivative.
- **Feature vector**:
  - `combined = np.hstack((mfcc_feat, delta))` → 40‑dimensional vector per frame.

### 7.2. GMM Training

In `android_api.py`:

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

– Each user has a separate GMM model.  
– Uses `diag` covariance to optimize speed on mobile.

### 7.3. Scoring & Confidence

Recognition:

```python
avg_score = gmm.score(vector)  # average log-likelihood per frame
```

– Compute `avg_score` for each user.  
– Select the user with highest `avg_score` (`best_score`).

**Stranger detection (AUTH_THRESHOLD)**:

```python
AUTH_THRESHOLD = -40.0
if best_score < AUTH_THRESHOLD:
    # treat as stranger and reject
```

**Relative confidence**:

```python
max_score = best_score
min_score = np.min(log_likelihood)
denom = max_score + abs(min_score) or 1e-10
confidence = (max_score - min_score) / denom  # when >= 2 users
```

– When there is only one user, confidence is forced high once the score passes the threshold.

---

## 8. Troubleshooting

- **Error: `File format ... not understood. Only 'RIFF' and 'RIFX' supported.`**
  - Cause: recorded with `MediaRecorder` in 3GP/AMR format → not WAV.
  - Fix: this repo uses `AudioRecord` + manual WAV header, so recordings are valid WAV files.

- **Error: User not shown in list after training success**
  - Check logs:
    - Python log: `Saved model to /data/data/.../gmm_models/user.gmm`.
    - If `get_all_users` reads another path, this has been fixed by using a stable `get_model_path()` in internal storage.

- **Error: `ImportError: cannot import name 'GMM' from 'sklearn.mixture'`**
  - Fixed by migrating to `GaussianMixture` (modern scikit‑learn API).

- **Error: `No module named 'tensorflow'`**
  - Face Recognition code is wrapped in `try/except` in `main_functions.py`, so lack of TF simply disables that part instead of crashing.

- **Poor or unstable recognition**
  - Collect more training samples per user (3–5 or more).
  - Record in a quieter environment with a consistent mic distance.
  - Tune `AUTH_THRESHOLD` (e.g. -45, -35) based on real‑world data.

---

## 9. Roadmap / Ideas

- **Better UI/UX**:
  - Material Design styling, progress bars during train/recognize.
  - Voice login history / audit trail screen.

- **Multi‑session training**:
  - Allow adding additional recordings to existing users (incremental training).

- **Model export / import**:
  - Sync `.gmm` models to a backend service or between devices.

- **Two‑factor auth (2FA)**:
  - Voice + PIN, or Voice + Face Recognition.

- **Try other algorithms**:
  - i‑vector, x‑vector, d‑vector, or modern deep‑learning speaker embeddings.

---

## 10. Contributing

Pull Requests / Issues are very welcome:

- Add desktop unit tests for the Python voice biometric logic.
- Improve UX inside `VoiceAuthActivity` and the overall app flow.
- Optimize performance (faster model loading, caching strategies, etc.).

If you build a PoC or a production app on top of this repo, please consider sharing back so the project can keep evolving 🎯


