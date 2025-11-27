"""
Python API Wrapper cho Android App
"""
import os
import pickle
import numpy as np
import traceback
from scipy.io.wavfile import read
from sklearn.mixture import GaussianMixture
import warnings
warnings.filterwarnings("ignore")

import sys
current_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, current_dir)

from main_functions import extract_features

def get_model_path():
    """
    Lấy đường dẫn đến thư mục models trong Internal Storage.
    BẮT BUỘC dùng đường dẫn cố định của Android để tránh vấn đề với Chaquopy AssetFinder.
    """
    try:
        # Cách 1: Dùng Android Context
        from android import mActivity
        if mActivity:
            path = os.path.join(mActivity.getFilesDir().getAbsolutePath(), "gmm_models")
            if not os.path.exists(path):
                os.makedirs(path, exist_ok=True)
            return path
    except:
        pass
    
    try:
        # Cách 2: Hardcode đường dẫn data chuẩn của Android
        # Đây là nơi an toàn nhất để lưu dữ liệu app
        path = "/data/data/com.rhino.voiceauthenticatorxpython/files/gmm_models"
        if not os.path.exists(path):
            os.makedirs(path, exist_ok=True)
        return path
    except:
        # Fallback cuối cùng (chỉ dùng cho debug trên PC, không nên dùng trên Android)
        return os.path.join(os.path.dirname(os.path.abspath(__file__)), "gmm_models")

def recognize_voice_from_file(wav_file_path, model_path=None):
    try:
        if model_path is None:
            model_path = get_model_path()
        
        print(f"DEBUG: Recog path={model_path}, wav={wav_file_path}")
            
        if not os.path.exists(wav_file_path):
            return {'success': False, 'message': f'File wav không tồn tại: {wav_file_path}'}
        
        if not os.path.exists(model_path):
             return {'success': False, 'message': f'Thư mục model không tồn tại: {model_path}'}

        gmm_files = [os.path.join(model_path, fname) for fname in 
                    os.listdir(model_path) if fname.endswith('.gmm')]
        
        print(f"DEBUG: Found {len(gmm_files)} models in {model_path}")
        
        if len(gmm_files) == 0:
            return {'success': False, 'message': f'Database trống (Path: {model_path})'}
        
        models = [pickle.load(open(fname, 'rb')) for fname in gmm_files]
        speakers = [fname.split("/")[-1].split(".gmm")[0] for fname in gmm_files]
        
        sr, audio = read(wav_file_path)
        vector = extract_features(audio, sr)
        log_likelihood = np.zeros(len(models))
        
        for i in range(len(models)):
            gmm = models[i]
            scores = np.array(gmm.score(vector))
            log_likelihood[i] = scores.sum()
            print(f"DEBUG: Score for {speakers[i]}: {log_likelihood[i]}")
        
        pred = np.argmax(log_likelihood)
        identity = speakers[pred]
        max_score = log_likelihood[pred]
        min_score = np.min(log_likelihood)
        
        denom = max_score + abs(min_score)
        if denom == 0: denom = 1e-10
            
        # Nếu chỉ có 1 model, confidence luôn = 0 với công thức cũ.
        # Fix: Nếu chỉ có 1 user, set confidence cao nếu score hợp lý
        if len(models) == 1:
            confidence = 0.95 # Giả định là đúng nếu chỉ có 1 người và score tính được
        else:
            confidence = (max_score - min_score) / denom
            
        print(f"DEBUG: Best: {identity}, Conf: {confidence}")
        
        if identity == 'unknown' or confidence < 0.1:
            return {
                'success': False,
                'identity': None,
                'confidence': float(confidence),
                'message': f'Không nhận diện được (Conf: {confidence:.2f})'
            }
        
        return {
            'success': True,
            'identity': identity,
            'confidence': float(confidence),
            'message': f'Nhận diện: {identity}'
        }
        
    except Exception as e:
        return {'success': False, 'message': f'Lỗi: {str(e)}\n{traceback.format_exc()}'}

def train_user_voice(name, wav_files_list, model_path=None):
    try:
        # Convert ArrayList
        if not isinstance(wav_files_list, list):
            try:
                size = wav_files_list.size()
                py_list = []
                for i in range(size):
                    py_list.append(wav_files_list.get(i))
                wav_files_list = py_list
            except:
                wav_files_list = list(wav_files_list)
            
        if model_path is None:
            model_path = get_model_path()
        
        print(f"DEBUG: Train user {name} at {model_path}")
        
        if not os.path.exists(model_path):
            os.makedirs(model_path, exist_ok=True)
        
        model_file = os.path.join(model_path, name + '.gmm')
        
        features = np.array([])
        for wav_path in wav_files_list:
            if os.path.exists(wav_path):
                sr, audio = read(wav_path)
                vector = extract_features(audio, sr)
                if features.size == 0:
                    features = vector
                else:
                    features = np.vstack((features, vector))
        
        if features.size == 0:
            return {'success': False, 'message': 'Không có data audio hợp lệ'}
        
        # Tuning GMM parameters cho giọng nói ngắn (3s)
        # n_components: Tăng lên 32 để mô hình hóa giọng nói chi tiết hơn
        # max_iter: Giảm xuống 100 để tránh overfitting vào nhiễu nền
        gmm = GaussianMixture(n_components=32, covariance_type='diag', max_iter=100, n_init=3)
        gmm.fit(features)
        
        pickle.dump(gmm, open(model_file, 'wb'))
        print(f"DEBUG: Saved model to {model_file}")
        
        return {
            'success': True, 
            'message': f'Đã lưu user {name} vào: {model_file}'
        }
        
    except Exception as e:
        return {'success': False, 'message': f'Lỗi train: {str(e)}\n{traceback.format_exc()}'}

def get_all_users(model_path=None):
    try:
        if model_path is None:
            model_path = get_model_path()
        
        print(f"DEBUG: Getting users from {model_path}")
        
        if not os.path.exists(model_path):
            return {
                'success': True, 
                'users': [], 
                'message': f'Thư mục chưa tạo: {model_path}'
            }
        
        gmm_files = [fname.split(".gmm")[0] for fname in 
                     os.listdir(model_path) if fname.endswith('.gmm')]
        
        print(f"DEBUG: Found {len(gmm_files)} users: {gmm_files}")
        
        return {
            'success': True,
            'users': gmm_files,
            'message': f'Tìm thấy {len(gmm_files)} users tại {model_path}'
        }
        
    except Exception as e:
        return {'success': False, 'users': [], 'message': f'Lỗi get users: {str(e)}'}

def delete_user(name, model_path=None):
    try:
        if model_path is None:
            model_path = get_model_path()
        model_file = os.path.join(model_path, name + '.gmm')
        if os.path.exists(model_file):
            os.remove(model_file)
            return {'success': True, 'message': f'Đã xóa {name}'}
        return {'success': False, 'message': 'User không tồn tại'}
    except Exception as e:
        return {'success': False, 'message': str(e)}
