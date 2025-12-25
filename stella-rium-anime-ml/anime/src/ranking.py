import pandas as pd
import numpy as np
import tensorflow as tf
from deepctr.models import DCNMix
from deepctr.feature_column import get_feature_names
from tensorflow.keras.callbacks import EarlyStopping, ReduceLROnPlateau
from tensorflow.keras.preprocessing.sequence import pad_sequences
import os

# 【配置】必须和 preprocess.py 中的 TAG_MAX_LEN 保持完全一致
TAG_MAX_LEN = 12
# 全局平均分 (来自训练集日志)
GLOBAL_AVG_RATING = 6.8033

# 显存按需分配
try:
    gpus = tf.config.experimental.list_physical_devices('GPU')
    for gpu in gpus:
        tf.config.experimental.set_memory_growth(gpu, True)
except:
    pass


def build_model_input(df, feature_columns):
    """
    构建 DeepCTR 输入字典，处理序列特征的堆叠
    """
    input_dict = {}
    for name in get_feature_names(feature_columns):
        if name == 'tags_seq':
            input_dict[name] = np.vstack(df[name].values)
        else:
            input_dict[name] = df[name].values
    return input_dict


def train_ranking_model(train_df, feature_columns, target_col='user_rating'):
    print("====== [Ranking] 准备训练数据 ======")
    train_model_input = build_model_input(train_df, feature_columns)

    # 使用 DCN-V2 模型
    model = DCNMix(linear_feature_columns=feature_columns,
                   dnn_feature_columns=feature_columns,
                   cross_num=2,
                   dnn_hidden_units=(256, 128),
                   dnn_dropout=0.5,
                   task='regression')

    model.compile("adam", "mse", metrics=['mse', 'mae'])

    print("====== [Ranking] 开始训练 (DCN-V2) ======")
    # 归一化目标值到 0-1
    target_values = train_df[target_col].values / 10.0

    callbacks = [
        EarlyStopping(monitor='val_mse', patience=2, restore_best_weights=True, verbose=1),
        ReduceLROnPlateau(monitor='val_mse', factor=0.5, patience=1, min_lr=0.00001, verbose=1)
    ]

    model.fit(train_model_input, target_values,
              batch_size=128, epochs=15,
              validation_split=0.2,
              callbacks=callbacks,
              verbose=1)

    os.makedirs('models', exist_ok=True)
    model.save_weights('models/deepfm_weights.h5')
    print("模型权重已保存。")
    return model


def load_ranking_model(feature_columns):
    # 重建模型结构
    model = DCNMix(linear_feature_columns=feature_columns,
                   dnn_feature_columns=feature_columns,
                   cross_num=2,
                   dnn_hidden_units=(256, 128),
                   dnn_dropout=0.5,
                   task='regression')
    try:
        model.load_weights('models/deepfm_weights.h5')
    except Exception as e:
        print(f"Warning: 权重加载失败 ({e})")
    return model


def predict_ranking(model, input_df, feature_columns):
    predict_input = build_model_input(input_df, feature_columns)
    # 预测并还原分数
    pred_ans = model.predict(predict_input, batch_size=128)
    return pred_ans.flatten() * 10.0


def create_ranking_input(user_id, candidate_ids, anime_meta_df, user_meta_df, encoders):
    """
    构造预测用的 DataFrame，包含最严格的数据清洗逻辑
    """
    import time

    # 1. 构造基础表
    df = pd.DataFrame({'anime_id': candidate_ids})
    df['user_id'] = user_id

    # 【修复 1】确保 anime_id 是 int 类型，方便 Merge
    df['anime_id'] = pd.to_numeric(df['anime_id'], errors='coerce').fillna(0).astype(int)

    # 【修复 2】元数据去重，防止 Merge 后数据膨胀
    anime_meta_df = anime_meta_df.drop_duplicates(subset=['anime_id'])

    # 2. 关联 Anime 特征
    df = pd.merge(df, anime_meta_df, on='anime_id', how='left')

    # 关联 User 特征
    if user_meta_df is not None and user_id in user_meta_df['user_id'].values:
        user_info = user_meta_df[user_meta_df['user_id'] == user_id].iloc[0]
        df['user_avg_rating'] = user_info.get('user_avg_rating', GLOBAL_AVG_RATING)
        df['user_collections'] = user_info.get('user_collections', 0)
    else:
        # 游客默认值
        df['user_avg_rating'] = GLOBAL_AVG_RATING
        df['user_collections'] = 0

    # 构造时间戳
    df['timestamp'] = time.time()

    # 3. 计算 Bias 特征
    # 先确保 anime_avg_rating 是数字
    df['anime_avg_rating'] = pd.to_numeric(df['anime_avg_rating'], errors='coerce').fillna(GLOBAL_AVG_RATING)

    df['user_bias'] = df['user_avg_rating'] - GLOBAL_AVG_RATING
    df['anime_bias'] = df['anime_avg_rating'] - GLOBAL_AVG_RATING

    # 4. 【修复 3】填充缺失值 & 强制转 Numeric (解决 String to Float 错误)
    dense_cols = [
        'anime_avg_rating', 'completion_rate', 'seen_count',
        'user_avg_rating', 'user_collections', 'timestamp',
        'episodes', 'anime_year',
        'user_bias', 'anime_bias'
    ]

    for col in dense_cols:
        if col not in df.columns: df[col] = 0
        # 核心：强制转 float，无法转换的变为 NaN 然后填 0
        df[col] = pd.to_numeric(df[col], errors='coerce').fillna(0).astype(float)

    # 5. 应用 Encoders
    # Sparse 特征
    for feat in ['user_id', 'anime_id']:
        lbe = encoders[feat]
        df[feat] = df[feat].apply(lambda x: lbe.transform([str(x)])[0] if str(x) in lbe.classes_ else 0)

    # Dense 特征
    mms = encoders['scaler']
    try:
        # 这里数据已经是纯 float 了，transform 应该不会报错
        df[dense_cols] = mms.transform(df[dense_cols])
    except Exception as e:
        print(f"[Warning] Scaler transform failed: {e}")
        # 如果失败，不要 pass，打印出来看，但为了不崩系统，暂时保留原始值
        pass

        # Tags 特征
    if 'tags' in encoders:
        tag_lbe = encoders['tags']
        tag_map = dict(zip(tag_lbe.classes_, tag_lbe.transform(tag_lbe.classes_) + 1))

        def transform_tags(tag_str):
            if not isinstance(tag_str, str): return []
            return [tag_map[t.strip()] for t in tag_str.split('|') if t.strip() in tag_map]

        tags_list = df['tags'].apply(transform_tags).tolist()
        tags_padded = pad_sequences(tags_list, maxlen=TAG_MAX_LEN, padding='post', truncating='post')
        df['tags_seq'] = list(tags_padded)
    else:
        df['tags_seq'] = [[0] * TAG_MAX_LEN] * len(df)

    # 补充 status
    if 'status' in encoders:
        df['status'] = 0

    return df