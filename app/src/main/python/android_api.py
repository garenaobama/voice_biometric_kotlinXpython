"""
Python API Wrapper cho Android App
File này cung cấp các hàm API đơn giản để Android có thể gọi từ Java/Kotlin
"""
import os
import pickle
import numpy as np
from scipy.io.wavfile import read
from sklearn.mixture import GMM
from sklearn import preprocessing
import python_speech_features as mfcc
import warnings
warnings.filterwarnings("ignore")

# Import các hàm từ main_functions
import sys
# Thêm đường dẫn hiện tại vào sys.path
current_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, current_dir)

from main_functions import extract_features, calculate_delta

# Đường dẫn mặc định cho models
# Trên Android, sẽ tự động detect và sử dụng đường dẫn phù hợp
import sys
import os

def get_model_path():
    """Lấy đường dẫn đến thư mục models, tự động detect Android hoặc desktop"""
    try:
        # Thử import android module (chỉ có trên Android với Chaquopy)
        from android import mActivity
        context = mActivity.getApplicationContext()
        # Trên Android, models sẽ ở trong assets hoặc internal storage
        internal_path = os.path.join(context.getFilesDir().getAbsolutePath(), "gmm_models")
        if not os.path.exists(internal_path):
            os.makedirs(internal_path, exist_ok=True)
        return internal_path
    except ImportError:
        # Chạy trên desktop Python
        return os.path.join(os.path.dirname(os.path.abspath(__file__)), "gmm_models")

DEFAULT_MODEL_PATH = get_model_path()
DEFAULT_VOICE_DB_PATH = "./voice_database/"

def recognize_voice_from_file(wav_file_path, model_path=None):
    """
    Nhận diện giọng nói từ file wav
    
    Args:
        wav_file_path: Đường dẫn đến file wav cần nhận diện
        model_path: Đường dẫn đến thư mục chứa GMM models (mặc định: ./gmm_models/)
    
    Returns:
        dict: {
            'success': bool,
            'identity': str hoặc None,
            'confidence': float hoặc None,
            'message': str
        }
    """
    try:
        if model_path is None:
            model_path = DEFAULT_MODEL_PATH
        
        # Kiểm tra file wav có tồn tại không
        if not os.path.exists(wav_file_path):
            return {
                'success': False,
                'identity': None,
                'confidence': None,
                'message': 'File wav không tồn tại'
            }
        
        # Load tất cả GMM models
        gmm_files = [os.path.join(model_path, fname) for fname in 
                    os.listdir(model_path) if fname.endswith('.gmm')]
        
        if len(gmm_files) == 0:
            return {
                'success': False,
                'identity': None,
                'confidence': None,
                'message': 'Không có user nào trong database'
            }
        
        models = [pickle.load(open(fname, 'rb')) for fname in gmm_files]
        speakers = [fname.split("/")[-1].split(".gmm")[0] for fname in gmm_files]
        
        # Đọc file audio
        sr, audio = read(wav_file_path)
        
        # Extract MFCC features
        vector = extract_features(audio, sr)
        log_likelihood = np.zeros(len(models))
        
        # Kiểm tra với từng model
        for i in range(len(models)):
            gmm = models[i]
            scores = np.array(gmm.score(vector))
            log_likelihood[i] = scores.sum()
        
        pred = np.argmax(log_likelihood)
        identity = speakers[pred]
        
        # Tính confidence (normalize log likelihood)
        max_score = log_likelihood[pred]
        min_score = np.min(log_likelihood)
        confidence = (max_score - min_score) / (max_score + abs(min_score) + 1e-10)
        
        # Kiểm tra threshold (có thể điều chỉnh)
        if identity == 'unknown' or confidence < 0.1:
            return {
                'success': False,
                'identity': None,
                'confidence': float(confidence),
                'message': 'Không nhận diện được giọng nói'
            }
        
        return {
            'success': True,
            'identity': identity,
            'confidence': float(confidence),
            'message': f'Nhận diện thành công: {identity}'
        }
        
    except Exception as e:
        return {
            'success': False,
            'identity': None,
            'confidence': None,
            'message': f'Lỗi: {str(e)}'
        }


def recognize_voice_from_bytes(audio_bytes, sample_rate, model_path=None):
    """
    Nhận diện giọng nói từ audio bytes (từ Android MediaRecorder)
    
    Args:
        audio_bytes: Bytes của audio data
        sample_rate: Sample rate của audio (thường là 44100)
        model_path: Đường dẫn đến thư mục chứa GMM models
    
    Returns:
        dict: Kết quả nhận diện tương tự recognize_voice_from_file
    """
    try:
        import tempfile
        import wave
        
        # Tạo file tạm để lưu audio
        temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.wav')
        temp_path = temp_file.name
        temp_file.close()
        
        # Ghi audio bytes vào file wav
        with wave.open(temp_path, 'wb') as wav_file:
            wav_file.setnchannels(1)  # Mono
            wav_file.setsampwidth(2)  # 16-bit
            wav_file.setframerate(sample_rate)
            wav_file.writeframes(audio_bytes)
        
        # Nhận diện từ file
        result = recognize_voice_from_file(temp_path, model_path)
        
        # Xóa file tạm
        os.unlink(temp_path)
        
        return result
        
    except Exception as e:
        return {
            'success': False,
            'identity': None,
            'confidence': None,
            'message': f'Lỗi xử lý audio bytes: {str(e)}'
        }


def train_user_voice(name, wav_files_list, model_path=None):
    """
    Train GMM model cho user mới từ danh sách file wav
    
    Args:
        name: Tên user
        wav_files_list: Danh sách đường dẫn đến các file wav (tối thiểu 3 files)
        model_path: Đường dẫn đến thư mục lưu GMM models
    
    Returns:
        dict: {
            'success': bool,
            'message': str
        }
    """
    try:
        if model_path is None:
            model_path = DEFAULT_MODEL_PATH
        
        # Kiểm tra tên user
        if name == 'unknown' or name == '':
            return {
                'success': False,
                'message': 'Tên user không hợp lệ'
            }
        
        # Kiểm tra số lượng file
        if len(wav_files_list) < 3:
            return {
                'success': False,
                'message': 'Cần tối thiểu 3 file wav để train'
            }
        
        # Kiểm tra user đã tồn tại chưa
        model_file = os.path.join(model_path, name + '.gmm')
        if os.path.exists(model_file):
            return {
                'success': False,
                'message': 'User đã tồn tại trong database'
            }
        
        # Extract features từ tất cả files
        features = np.array([])
        
        for wav_path in wav_files_list:
            if not os.path.exists(wav_path):
                continue
            
            sr, audio = read(wav_path)
            vector = extract_features(audio, sr)
            
            if features.size == 0:
                features = vector
            else:
                features = np.vstack((features, vector))
        
        if features.size == 0:
            return {
                'success': False,
                'message': 'Không thể extract features từ các file wav'
            }
        
        # Train GMM model
        gmm = GMM(n_components=16, n_iter=200, covariance_type='diag', n_init=3)
        gmm.fit(features)
        
        # Lưu model
        os.makedirs(model_path, exist_ok=True)
        pickle.dump(gmm, open(model_file, 'wb'))
        
        return {
            'success': True,
            'message': f'User {name} đã được thêm thành công'
        }
        
    except Exception as e:
        return {
            'success': False,
            'message': f'Lỗi train model: {str(e)}'
        }


def delete_user(name, model_path=None):
    """
    Xóa user khỏi database
    
    Args:
        name: Tên user cần xóa
        model_path: Đường dẫn đến thư mục chứa GMM models
    
    Returns:
        dict: {
            'success': bool,
            'message': str
        }
    """
    try:
        if model_path is None:
            model_path = DEFAULT_MODEL_PATH
        
        model_file = os.path.join(model_path, name + '.gmm')
        
        if not os.path.exists(model_file):
            return {
                'success': False,
                'message': 'User không tồn tại trong database'
            }
        
        os.remove(model_file)
        
        return {
            'success': True,
            'message': f'User {name} đã được xóa thành công'
        }
        
    except Exception as e:
        return {
            'success': False,
            'message': f'Lỗi xóa user: {str(e)}'
        }


def get_all_users(model_path=None):
    """
    Lấy danh sách tất cả users trong database
    
    Args:
        model_path: Đường dẫn đến thư mục chứa GMM models
    
    Returns:
        dict: {
            'success': bool,
            'users': list hoặc None,
            'message': str
        }
    """
    try:
        if model_path is None:
            model_path = DEFAULT_MODEL_PATH
        
        if not os.path.exists(model_path):
            return {
                'success': True,
                'users': [],
                'message': 'Database trống'
            }
        
        gmm_files = [fname.split(".gmm")[0] for fname in 
                     os.listdir(model_path) if fname.endswith('.gmm')]
        
        return {
            'success': True,
            'users': gmm_files,
            'message': f'Tìm thấy {len(gmm_files)} users'
        }
        
    except Exception as e:
        return {
            'success': False,
            'users': None,
            'message': f'Lỗi: {str(e)}'
        }

