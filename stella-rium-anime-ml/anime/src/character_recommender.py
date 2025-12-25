# src/character_recommender.py
import random

import pandas as pd
import numpy as np
import os
import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.neighbors import NearestNeighbors


class CharacterRecommender:
    def __init__(self, data_path='data/character_translated.csv', model_dir='models/'):
        self.data_path = data_path
        self.model_dir = model_dir

        # 定义模型保存路径
        self.tfidf_path = os.path.join(model_dir, 'char_tfidf.pkl')
        self.nn_path = os.path.join(model_dir, 'char_nn.pkl')
        self.matrix_path = os.path.join(model_dir, 'char_matrix.pkl')
        self.df_path = os.path.join(model_dir, 'char_meta.pkl')  # 只存必要的元数据

        # 内存中的变量
        self.df = None
        self.vectorizer = None
        self.tfidf_matrix = None
        self.nn_model = None
        self.id_to_index = {}
        self.valid_ids = set()

    def train_and_save(self):
        """
        【离线训练模式】
        读取 CSV -> 清洗 -> 向量化 -> 建立索引 -> 保存 .pkl
        """
        print("[System] 正在构建角色推荐索引 (首次运行或文件缺失)...")
        if not os.path.exists(self.data_path):
            # 如果没有 CSV，抛出异常或创建一个空的 Dummy 数据防止报错
            print(f"[Error] 数据文件未找到: {self.data_path}")
            return

        # 1. 加载和清洗数据
        df = pd.read_csv(self.data_path)

        # 类型安全转换
        df['id'] = pd.to_numeric(df['id'], errors='coerce').fillna(0).astype(int)
        df['collects'] = pd.to_numeric(df['collects'], errors='coerce').fillna(0).astype(int)

        # 处理标签：把 nan 变空字符串，把 '|' 变成空格以便分词
        df['tags'] = df['tags'].fillna('').astype(str).str.replace('|', ' ', regex=False)

        # 重置索引确保连续
        df = df.reset_index(drop=True)
        self.df = df

        # 2. 训练 TF-IDF (特征提取)
        print(" -> Vectorizing Tags (TF-IDF)...")
        # token_pattern=r'(?u)\b\w+\b' 支持中文和英文单词
        self.vectorizer = TfidfVectorizer(token_pattern=r'(?u)\b\w+\b', min_df=2)
        self.tfidf_matrix = self.vectorizer.fit_transform(df['tags'])

        # 3. 训练 NearestNeighbors (最近邻搜索)
        print(" -> Building NN Index...")
        # metric='cosine' 计算余弦相似度
        self.nn_model = NearestNeighbors(n_neighbors=20, metric='cosine', algorithm='brute')
        self.nn_model.fit(self.tfidf_matrix)

        # 4. 保存所有模型文件到硬盘
        if not os.path.exists(self.model_dir):
            os.makedirs(self.model_dir)

        joblib.dump(self.vectorizer, self.tfidf_path)
        joblib.dump(self.nn_model, self.nn_path)
        joblib.dump(self.tfidf_matrix, self.matrix_path)

        # 保存轻量级的 DataFrame (只存 id 和 collects 用于查询和排序)
        df[['id', 'collects']].to_pickle(self.df_path)

        print("[System] 角色模型已构建并保存到 models/ 目录。")

        # 建立内存映射
        self._build_memory_index()

    def load_model(self):
        """
        【在线服务模式】
        尝试加载 .pkl 文件。如果不存在，则调用 train_and_save 现场生成。
        """
        # 检查关键文件是否存在
        required_files = [self.tfidf_path, self.nn_path, self.matrix_path, self.df_path]
        if not all(os.path.exists(f) for f in required_files):
            print("[System] 未检测到完整的角色模型缓存，开始初始化...")
            self.train_and_save()
            return

        print("[System] 正在加载角色推荐模型 (Fast Load)...")
        try:
            self.vectorizer = joblib.load(self.tfidf_path)
            self.nn_model = joblib.load(self.nn_path)
            self.tfidf_matrix = joblib.load(self.matrix_path)
            self.df = pd.read_pickle(self.df_path)

            self._build_memory_index()
            print(f"[System] 角色服务加载完毕，包含 {len(self.df)} 名角色。")
        except Exception as e:
            print(f"[Error] 加载缓存文件失败 ({e})，尝试重新训练...")
            self.train_and_save()

    def _build_memory_index(self):
        """辅助函数：建立 ID <-> DataFrame Index 的映射"""
        if self.df is not None:
            self.id_to_index = {row_id: idx for idx, row_id in enumerate(self.df['id'])}
            self.valid_ids = set(self.df['id'].values)

    def get_random_popular(self, k=10):
        """策略：从收藏数最多的前 200 名中随机采样"""
        if self.df is None:
            return []

        top_pool = self.df.nlargest(200, 'collects')

        # 如果池子太小，直接返回全部
        if len(top_pool) <= k:
            return top_pool['id'].tolist()

        # 随机采样
        return top_pool.sample(n=k)['id'].tolist()

    def recommend(self, input_ids, top_k=10):
        """
        引入随机性的推荐逻辑
        """
        if self.df is None: return []

        valid_inputs = [x for x in input_ids if x in self.valid_ids]

        # === 随机性来源 1: 如果输入为空，直接走完全随机热门 ===
        if not valid_inputs:
            return self.get_random_popular(top_k)

        try:
            # 获取输入向量
            input_indices = [self.id_to_index[x] for x in valid_inputs]
            input_vectors = self.tfidf_matrix[input_indices]

            # 计算平均向量 (用户画像)
            user_profile = np.asarray(input_vectors.mean(axis=0))

            # === 随机性来源 2: 向量扰动 (Vector Perturbation) ===
            # 给用户的兴趣向量加一点“高斯噪声”。
            # scale=0.05 是噪声强度，太大会导致推荐不准，太小没效果。
            # 这会让搜索方向发生微小的偏移。
            noise = np.random.normal(loc=0.0, scale=0.2, size=user_profile.shape)
            user_profile = user_profile + noise

            # === 随机性来源 3: 扩大召回池 + 随机采样 (Resampling) ===
            # 我们不只取 top_k，而是取 3倍 的数量，然后从中随机挑
            pool_size = (top_k + len(valid_inputs)) * 3
            distances, indices = self.nn_model.kneighbors(user_profile, n_neighbors=pool_size)

            candidates = []
            for idx in indices[0]:
                char_id = self.df.iloc[idx]['id']
                if char_id not in valid_inputs:
                    candidates.append(int(char_id))

            # 去重并保持顺序（因为 candidates 是按相似度排序的）
            # 我们只保留前 2*top_k 个作为最终候选，保证相关性不至于太差
            best_candidates = candidates[:top_k * 2]

            # 从这些最好的候选中，随机选 top_k 个
            if len(best_candidates) >= top_k:
                final_recs = random.sample(best_candidates, top_k)
            else:
                final_recs = best_candidates

            # 如果数量还不够，用热门补齐
            if len(final_recs) < top_k:
                needed = top_k - len(final_recs)
                pool = self.get_random_popular(k=needed * 5)
                for pid in pool:
                    if pid not in valid_inputs and pid not in final_recs:
                        final_recs.append(pid)
                        if len(final_recs) >= top_k:
                            break

            return final_recs

        except Exception as e:
            print(f"[Error] Recommend Failed: {e}")
            return self.get_random_popular(top_k)