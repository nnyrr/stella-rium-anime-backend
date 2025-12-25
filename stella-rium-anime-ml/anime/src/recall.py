import pandas as pd
import numpy as np
import joblib
import os
from collections import defaultdict
from itertools import combinations
from tqdm import tqdm


class ItemCF:
    def __init__(self):
        self.sim_matrix = None
        self.item_counts = {}  # 用于存储每部番的观看人数 (用于降权)
        self.hot_items = []  # 用于存储热门榜单 (用于兜底)
        self.top_k = 200  # 默认推荐数量

    def train(self, df):
        """
        基于 Swing 算法构建物品相似度矩阵
        df: 必须包含 user_id, anime_id
        """
        print("正在初始化 Swing 算法数据结构...")

        # === 1. 统计热度数据 (用于后续降权和兜底) ===
        # 统计每部动漫的观看人数
        # 假设 df 中每一行就是一个“观看行为”
        counts_series = df['anime_id'].value_counts()
        self.item_counts = counts_series.to_dict()

        # 生成热门榜单 (取前 100 名作为兜底)
        self.hot_items = counts_series.head(100).index.tolist()
        print(f"热门榜单已生成，Top 5: {self.hot_items[:5]}")

        # === 2. 构建索引 ===
        # 构建 user -> [items] 字典
        user_items = df.groupby('user_id')['anime_id'].apply(set).to_dict()
        # 构建 item -> [users] 字典
        item_users = df.groupby('anime_id')['user_id'].apply(list).to_dict()

        # === 3. Swing 参数设置 ===
        alpha = 1.0
        top_n_sim = 100
        # 【参数调整】500 是一个折中值。
        # 1000 太慢且容易过拟合热门；100 太稀疏容易丢失关联。500 比较平衡。
        max_user_per_item = 1000

        # === 4. 开始计算相似度 ===
        # 结构：sim_scores[item_i][item_j] = score
        sim_scores = defaultdict(lambda: defaultdict(float))

        print(f"开始 Swing 计算 (共 {len(item_users)} 部动漫)...")

        for item_i, users_i in tqdm(item_users.items(), desc="Swing Training"):
            # 如果看过的人太多，进行随机截断
            if len(users_i) > max_user_per_item:
                users_i = np.random.choice(users_i, max_user_per_item, replace=False)

            # Swing 核心逻辑：双重循环遍历共同用户
            for u, v in combinations(users_i, 2):
                interaction_size = len(user_items[u] & user_items[v])
                weight = 1.0 / (interaction_size + alpha)

                common_items = user_items[u] & user_items[v]
                for item_j in common_items:
                    if item_i == item_j:
                        continue
                    sim_scores[item_i][item_j] += weight
                    sim_scores[item_j][item_i] += weight

        # === 5. 矩阵归一化与保存 ===
        print("计算完成，正在转换并保存矩阵...")

        final_sim_dict = {}
        for item, related_items in sim_scores.items():
            # 排序并截取 Top N
            sorted_items = sorted(related_items.items(), key=lambda x: x[1], reverse=True)[:top_n_sim]
            # 归一化
            if sorted_items:
                max_score = sorted_items[0][1]
                final_sim_dict[item] = {k: v / max_score for k, v in sorted_items}
            else:
                final_sim_dict[item] = {}

        self.sim_matrix = final_sim_dict

        # 保存所有必要数据
        os.makedirs('models', exist_ok=True)
        save_data = {
            'sim': self.sim_matrix,
            'counts': self.item_counts,  # 新增：保存观看次数
            'hot': self.hot_items  # 新增：保存热门榜单
        }
        joblib.dump(save_data, 'models/item_cf_sim.pkl')
        print("Swing 模型、热度统计、热门榜单 已全部保存。")

    def load_model(self):
        path = 'models/item_cf_sim.pkl'
        if os.path.exists(path):
            data = joblib.load(path)
            # 兼容逻辑：防止读取旧版模型报错
            if isinstance(data, dict) and 'sim' not in data:
                self.sim_matrix = data
                self.item_counts = {}
                self.hot_items = []
            else:
                self.sim_matrix = data['sim']
                self.item_counts = data.get('counts', {})
                self.hot_items = data.get('hot', [])
        else:
            raise FileNotFoundError(f"{path} not found. Please train first.")

    def recommend(self, user_history_ids, top_k=200):
        """
        user_history_ids: 用户看过的 anime_id 列表
        """
        if self.sim_matrix is None:
            self.load_model()

        # === 策略 1: 冷启动 (无历史记录) ===
        if not user_history_ids:
            # 直接返回热门榜单
            return self.hot_items[:top_k]

        candidates = {}

        # === 策略 2: 基于 ItemCF 召回 ===
        for watched_id in user_history_ids:
            similar_items = self.sim_matrix.get(watched_id, {})

            for sim_id, score in similar_items.items():
                if sim_id in user_history_ids:
                    continue
                candidates[sim_id] = candidates.get(sim_id, 0) + score

        # 如果算不出任何相似结果 (历史记录太冷门)，也用热门兜底
        if not candidates:
            return self.hot_items[:top_k]

        # === 策略 3: 热门降权 (Harry Potter Effect 修正) ===
        # 惩罚因子：越大，对热门番的打压越重。建议 0.4 ~ 0.6
        penalty_factor = 2.0

        final_scores = []
        for aid, raw_score in candidates.items():
            # 获取该番剧的观看人数 (如果字典里没有，默认给1)
            count = self.item_counts.get(aid, 1)

            # 降权公式： 分数 / log(观看人数 + 2)
            # log(count + 2) 确保分母 > 0 且平滑增长
            penalty = np.log(count + 2) if count > 0 else 1.0

            # 应用惩罚
            weighted_score = raw_score / (penalty ** penalty_factor)
            final_scores.append((aid, weighted_score))

        # 排序返回
        sorted_candidates = sorted(final_scores, key=lambda x: x[1], reverse=True)
        rec_list = [x[0] for x in sorted_candidates[:top_k]]

        # 【调试信息】看看算法到底算出了几个
        algo_count = len(rec_list)

        # === 策略 4: 数量补全 (修改版) ===
        if len(rec_list) < top_k:
            needed = top_k - len(rec_list)

            # 备选池：从热门榜 Top 100 中剔除已推荐的和用户看过的
            pool = [x for x in self.hot_items if x not in rec_list and x not in user_history_ids]

            # 【核心修改】随机抽取，而不是按顺序取
            # 如果备选池够大，就随机抽；不够就全拿
            import random
            if len(pool) > needed:
                fill_items = random.sample(pool, needed)
            else:
                fill_items = pool

            rec_list.extend(fill_items)

            print(f"--- [DEBUG] 算法召回 {algo_count} 个，热门补全 {len(fill_items)} 个 ---")
        else:
            print(f"--- [DEBUG] 算法召回充足 ({algo_count} 个)，无需补全 ---")

        return rec_list