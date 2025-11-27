
import numpy as np
import os
import sys
import pickle
try:
    import tensorflow as tf
    import cv2
    from keras import backend as K
    from keras.models import load_model
    
    # Face recognition initialization
    K.set_image_data_format('channels_first')
    np.set_printoptions(threshold=sys.maxsize)

    #calculates triplet loss
    def triplet_loss(y_true, y_pred, alpha = 0.2):
        anchor, positive, negative = y_pred[0], y_pred[1], y_pred[2]
        
        # triplet loss formula 
        pos_dist = tf.reduce_sum( tf.square(tf.subtract(y_pred[0], y_pred[1])) )
        neg_dist = tf.reduce_sum( tf.square(tf.subtract(y_pred[0], y_pred[2])) )
        basic_loss = pos_dist - neg_dist + alpha
        
        loss = tf.maximum(basic_loss, 0.0)
       
        return loss

    # load the model
    try:
        model = load_model('facenet_model/model.h5', custom_objects={'triplet_loss': triplet_loss})
    except:
        model = None
        print("Could not load facenet model")

    #provides 128 dim embeddings for face
    def img_to_encoding(img):
        if model is None:
            return None
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        
        #converting img format to channel first
        img = np.around(np.transpose(img, (2,0,1))/255.0, decimals=12)

        x_train = np.array([img])

        #facial embedding from trained model
        embedding = model.predict_on_batch(x_train)
        return embedding

except ImportError:
    print("Tensorflow/Keras/OpenCV not found. Face recognition disabled.")
    model = None
    def img_to_encoding(img): return None
    def triplet_loss(y_true, y_pred): return 0


# import dependencies for voice biometrics
try:
    import pyaudio
    from IPython.display import Audio, display, clear_output
except ImportError:
    pass # Not needed for Android core logic

import wave
from scipy.io.wavfile import read
# from sklearn.mixture import GaussianMixture # Removed to avoid direct dependency in this file
import warnings
warnings.filterwarnings("ignore")

from sklearn import preprocessing
import python_speech_features as mfcc


#Calculate and returns the delta of given feature vector matrix
def calculate_delta(array):
    rows,cols = array.shape
    deltas = np.zeros((rows,20))
    N = 2
    for i in range(rows):
        index = []
        j = 1
        while j <= N:
            if i-j < 0:
                first = 0
            else:
                first = i-j
            if i+j > rows -1:
                second = rows -1
            else:
                second = i+j
            index.append((second,first))
            j+=1
        deltas[i] = ( array[index[0][0]]-array[index[0][1]] + (2 * (array[index[1][0]]-array[index[1][1]])) ) / 10
    return deltas

#convert audio to mfcc features
def extract_features(audio,rate):
    # 1. Voice Activity Detection (VAD) đơn giản dựa trên năng lượng (Amplitude)
    # Loại bỏ khoảng lặng để model tập trung vào giọng nói thực
    # Ngưỡng: 1% của max amplitude hoặc giá trị cố định
    if len(audio) > 0:
        max_amp = np.max(np.abs(audio))
        threshold = 0.02 * max_amp # 2% ngưỡng
        # Giữ lại các frame có tín hiệu lớn hơn ngưỡng
        # Tuy nhiên cắt kiểu này có thể làm gãy signal
        # Tốt hơn là dùng MFCC năng lượng (cột đầu tiên) để lọc sau khi extract
        pass

    # 2. Extract MFCC
    # winlen=0.025 (25ms), winstep=0.01 (10ms), numcep=20
    mfcc_feat = mfcc.mfcc(audio,rate, 0.025, 0.01, 20, appendEnergy=True, nfft=1103)
    
    # 3. Lọc bỏ các frame có năng lượng thấp (Silence removal dựa trên log energy - cột đầu tiên)
    # Log energy thường ở cột 0 hoặc appendEnergy=True sẽ thêm vào cuối? 
    # python_speech_features trả về [n_frames, n_cepstrum]
    # Nếu appendEnergy=True, cột 0 thường bị thay thế bởi log energy
    
    # Cách đơn giản hơn: Chuẩn hóa trước
    mfcc_feat = preprocessing.scale(mfcc_feat)
    
    # 4. Calculate Delta
    delta = calculate_delta(mfcc_feat)

    #combining both mfcc features and delta
    combined = np.hstack((mfcc_feat,delta)) 
    return combined
