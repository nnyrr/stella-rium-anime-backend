import numpy as np


def calculate_jaccard_similarity(tag_set_a, tag_set_b):
    """
    计算两个集合的 Jaccard 相似度
    Jaccard = (A ∩ B) / (A ∪ B)
    """
    if not tag_set_a or not tag_set_b:
        return 0.0
    intersection = len(tag_set_a.intersection(tag_set_b))
    union = len(tag_set_a.union(tag_set_b))
    return intersection / union if union > 0 else 0.0


def mmr_rerank(ranked_items, anime_meta_df, top_n=10, lambda_param=0.6):
    """
    MMR 重排算法

    :param ranked_items: list of (anime_id, score) 来自 DeepFM 的排序结果
    :param anime_meta_df: 包含 'anime_id' 和 'tags' 列的元数据
    :param top_n: 最终推荐数量
    :param lambda_param: 平衡系数 (0~1)
           - 0.5: 平衡相关性和多样性
           - 0.8: 偏向相关性 (分数高的优先)
           - 0.2: 偏向多样性 (长得不一样的优先)
    :return: 重排后的 anime_id 列表
    """
    # 1. 准备数据
    # 将 tags 字符串转换为 set，方便计算相似度
    # 假设 tags 是 "Action|Comedy" 格式
    item_tags_dict = {}

    # 预处理：把所有候选集的 tags 找出来存字典，避免循环里反复查 DataFrame
    candidate_ids = [x[0] for x in ranked_items]

    # 筛选出候选集的 meta
    # 注意：这里假设 anime_meta_df 已经去重且 index 方便查找
    # 为了速度，转为 dict: {anime_id: {'Action', 'Comedy'}}
    subset = anime_meta_df[anime_meta_df['anime_id'].isin(candidate_ids)]

    for _, row in subset.iterrows():
        tags_str = str(row['tags'])
        if pd.isna(row['tags']) or tags_str == 'nan':
            item_tags_dict[row['anime_id']] = set()
        else:
            # 兼容逗号或竖线分隔
            sep = '|' if '|' in tags_str else ','
            item_tags_dict[row['anime_id']] = set(t.strip() for t in tags_str.split(sep))

    # 2. MMR 迭代
    selected_items = []  # 最终推荐列表 S
    candidate_dict = {item[0]: item[1] for item in ranked_items}  # 候选列表 R

    # 循环直到选够 top_n 或者没得选了
    while len(selected_items) < top_n and len(candidate_dict) > 0:
        best_item = None
        max_mmr_score = -np.inf

        # 遍历所有剩余候选者
        for item_id, relevance_score in candidate_dict.items():

            # A. 计算与已选集合的最大相似度 (Diversity Penalty)
            max_sim_with_selected = 0.0
            if selected_items:
                current_tags = item_tags_dict.get(item_id, set())
                for selected_id in selected_items:
                    sel_tags = item_tags_dict.get(selected_id, set())
                    sim = calculate_jaccard_similarity(current_tags, sel_tags)
                    if sim > max_sim_with_selected:
                        max_sim_with_selected = sim

            # B. MMR 公式
            # Score = λ * Relevance - (1 - λ) * MaxSimilarity
            # 注意：relevance_score 是 1-10 分，similarity 是 0-1
            # 为了量纲统一，建议把 relevance_score 归一化到 0-1，或者把 similarity 放大
            # 这里我们把 similarity 放大 10 倍 (假设分差在 1-2 分之间很重要)

            # 归一化 Relevance (0~1)
            # 假设 DeepFM 输出最大约 10 分
            norm_rel = relevance_score / 10.0

            mmr_score = lambda_param * norm_rel - (1 - lambda_param) * max_sim_with_selected

            if mmr_score > max_mmr_score:
                max_mmr_score = mmr_score
                best_item = item_id

        # 选中最佳 item
        selected_items.append(best_item)
        del candidate_dict[best_item]

    return selected_items


# 必须引入 pandas，虽然上面函数里只用了 iterrows，但 type hint 可能会用到
import pandas as pd