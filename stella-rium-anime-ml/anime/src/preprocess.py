import pandas as pd
from sklearn.preprocessing import LabelEncoder, MinMaxScaler
from deepctr.feature_column import SparseFeat, DenseFeat, VarLenSparseFeat
from tensorflow.keras.preprocessing.sequence import pad_sequences
import joblib
import os
from tqdm import tqdm

# === 全局配置 ===
TAG_MAX_LEN = 12

# === 特征定义 ===
SPARSE_FEATURES = ['user_id', 'anime_id', 'status', 'tags']

# 【修改点 1】新增 user_bias, anime_bias
DENSE_FEATURES = [
    'anime_avg_rating', 'completion_rate', 'seen_count',
    'user_avg_rating', 'user_collections', 'timestamp',
    'episodes', 'anime_year',
    'user_bias', 'anime_bias'  # <--- 新增偏差特征
]

TARGET = ['user_rating']


def load_data(data_path):
    print("正在读取 CSV 文件...")
    anime = pd.read_csv(os.path.join(data_path, 'anime_details.csv'))
    users = pd.read_csv(os.path.join(data_path, 'user_details.csv'))
    stats = pd.read_csv(os.path.join(data_path, 'anime_rating_stats.csv'))
    comments = pd.read_csv(os.path.join(data_path, 'user_ratings.csv'))

    print("正在去重...")
    anime = anime.drop_duplicates(subset=['anime_id'])
    stats = stats.drop_duplicates(subset=['anime_id'])

    # === 数据清洗 (保持不变) ===
    if 'completion_rate' in stats.columns:
        stats['completion_rate'] = stats['completion_rate'].astype(str).str.replace('%', '', regex=False)
        stats['completion_rate'] = pd.to_numeric(stats['completion_rate'], errors='coerce').fillna(0)
    if 'seen_count' in stats.columns:
        stats['seen_count'] = stats['seen_count'].astype(str).str.replace(',', '', regex=False)
        stats['seen_count'] = pd.to_numeric(stats['seen_count'], errors='coerce').fillna(0)
    if 'episodes' in anime.columns:
        anime['episodes'] = pd.to_numeric(anime['episodes'], errors='coerce').fillna(12)
    if 'date' in anime.columns:
        # 移除 format，让 pandas 自动推断
        anime['date'] = pd.to_datetime(anime['date'], errors='coerce')
        anime['anime_year'] = anime['date'].dt.year.fillna(2015).astype(int)
    else:
        anime['anime_year'] = 2015
    if 'timestamp' in comments.columns:
        comments['timestamp'] = pd.to_numeric(comments['timestamp'], errors='coerce').fillna(0)

    # === 重命名 ===
    anime = anime.rename(columns={'rating': 'anime_avg_rating'})
    users = users.rename(columns={'avg_rating': 'user_avg_rating', 'collections': 'user_collections'})
    comments = comments.rename(columns={'rating': 'user_rating'})

    # === 合并表 ===
    print("正在合并数据表...")
    df = pd.merge(comments, anime[['anime_id', 'title', 'tags', 'anime_avg_rating', 'episodes', 'anime_year']],
                  on='anime_id', how='left')
    df = pd.merge(df, users[['user_id', 'user_avg_rating', 'user_collections']], on='user_id', how='left')
    df = pd.merge(df, stats[['anime_id', 'completion_rate', 'seen_count']], on='anime_id', how='left')

    # === 【修改点 2】计算 Bias 特征 ===
    print("正在计算 Bias 特征...")
    # 1. 计算全局平均分 (基于训练数据)
    global_avg = df['user_rating'].mean()
    print(f"Global Average Rating: {global_avg:.4f}")

    # 2. 填充 NaN (防止做减法报错)
    df['user_avg_rating'] = df['user_avg_rating'].fillna(global_avg)
    df['anime_avg_rating'] = df['anime_avg_rating'].fillna(global_avg)

    # 3. 计算偏差
    # 用户偏差：这个用户打分比平均水平高多少？(正数表示宽容，负数表示苛刻)
    df['user_bias'] = df['user_avg_rating'] - global_avg
    # 动漫偏差：这部动漫比平均水平好多少？
    df['anime_bias'] = df['anime_avg_rating'] - global_avg

    # 保存全局平均分，供预测时使用 (简单存入 models 目录的一个文本文件或在这里打印出来手动填入 ranking.py)
    # 为了工程方便，我们这里存入 encoders 字典里
    # 注意：这个动作放到 process_features 里做更合适，但这里 df 比较全，就在这里算好即可

    return df, anime


def process_features(df, train_mode=True):
    encoders = {}

    # ... (Sparse 处理逻辑保持不变) ...
    simple_sparse = [f for f in SPARSE_FEATURES if f != 'tags']
    if train_mode:
        for feat in tqdm(simple_sparse, desc="Sparse Feats"):
            lbe = LabelEncoder()
            df[feat] = lbe.fit_transform(df[feat].fillna('-1').astype(str))
            encoders[feat] = lbe
        # Tags 字典
        all_tags = set()
        unique_tags_strs = df['tags'].fillna("").astype(str).unique()
        for tags_str in tqdm(unique_tags_strs, desc="Building Tag Dict"):
            for t in tags_str.split('|'):
                if t.strip(): all_tags.add(t.strip())
        tag_lbe = LabelEncoder()
        tag_lbe.fit(list(all_tags))
        encoders['tags'] = tag_lbe
        os.makedirs('models', exist_ok=True)
        # 顺便把 global_avg 存一下，虽然 ranking 里可能用硬编码更简单
        # 这里仅保存 encoders
        joblib.dump(encoders, 'models/encoders.pkl')
    else:
        encoders = joblib.load('models/encoders.pkl')
        for feat in simple_sparse:
            lbe = encoders[feat]
            mapping = dict(zip(lbe.classes_, lbe.transform(lbe.classes_)))
            df[feat] = df[feat].astype(str).map(lambda x: mapping.get(x, 0))

    # ... (Tags 序列处理逻辑保持不变) ...
    tag_lbe = encoders['tags']
    tag_map = dict(zip(tag_lbe.classes_, tag_lbe.transform(tag_lbe.classes_) + 1))

    def fast_transform_tags(tag_str):
        if not isinstance(tag_str, str): return []
        return [tag_map[t.strip()] for t in tag_str.split('|') if t.strip() in tag_map]

    tqdm.pandas(desc="Mapping Tags")
    unique_tags_series = df['tags'].fillna("").astype(str).drop_duplicates()
    unique_seqs = unique_tags_series.progress_apply(fast_transform_tags)
    str_to_seq_map = dict(zip(unique_tags_series, unique_seqs))
    tags_list = df['tags'].fillna("").astype(str).map(str_to_seq_map).tolist()
    tags_padded = pad_sequences(tags_list, maxlen=TAG_MAX_LEN, padding='post', truncating='post')
    df['tags_seq'] = list(tags_padded)

    # === 处理 Dense 特征 ===
    df[DENSE_FEATURES] = df[DENSE_FEATURES].fillna(0)

    if train_mode:
        mms = MinMaxScaler(feature_range=(0, 1))
        df[DENSE_FEATURES] = mms.fit_transform(df[DENSE_FEATURES])
        encoders['scaler'] = mms
        joblib.dump(encoders, 'models/encoders.pkl')
    else:
        mms = encoders['scaler']
        df[DENSE_FEATURES] = mms.transform(df[DENSE_FEATURES])

    # === Feature Columns ===
    fixlen_feature_columns = [SparseFeat(feat, vocabulary_size=df[feat].max() + 1, embedding_dim=4)
                              for feat in simple_sparse]
    varlen_feature_columns = [
        VarLenSparseFeat(
            SparseFeat('tags_seq', vocabulary_size=len(tag_lbe.classes_) + 2, embedding_dim=4),
            maxlen=TAG_MAX_LEN, combiner='mean'
        )
    ]
    dense_feature_columns = [DenseFeat(feat, 1) for feat in DENSE_FEATURES]
    feature_columns = fixlen_feature_columns + varlen_feature_columns + dense_feature_columns

    return df, feature_columns