import pandas as pd
import numpy as np
import os
import random
import joblib
from src.recall import ItemCF
from src.ranking import load_ranking_model, predict_ranking, create_ranking_input
# 【新增】导入重排模块
from src.rerank import mmr_rerank


def load_anime_metadata(data_path):
    print("正在加载动漫元数据...")
    anime = pd.read_csv(os.path.join(data_path, 'anime_details.csv'))
    stats = pd.read_csv(os.path.join(data_path, 'anime_rating_stats.csv'))

    # 去重
    anime = anime.drop_duplicates(subset=['anime_id'])
    stats = stats.drop_duplicates(subset=['anime_id'])

    # 清洗
    anime['anime_id'] = pd.to_numeric(anime['anime_id'], errors='coerce').fillna(0).astype(int)
    stats['anime_id'] = pd.to_numeric(stats['anime_id'], errors='coerce').fillna(0).astype(int)

    if 'episodes' in anime.columns:
        anime['episodes'] = pd.to_numeric(anime['episodes'], errors='coerce').fillna(12)
    if 'date' in anime.columns:
        anime['date'] = pd.to_datetime(anime['date'], errors='coerce')
        anime['anime_year'] = anime['date'].dt.year.fillna(2020).astype(int)
    else:
        anime['anime_year'] = 2020

    if 'completion_rate' in stats.columns:
        stats['completion_rate'] = stats['completion_rate'].astype(str).str.replace('%', '', regex=False)
        stats['completion_rate'] = pd.to_numeric(stats['completion_rate'], errors='coerce').fillna(0)

    if 'seen_count' in stats.columns:
        stats['seen_count'] = stats['seen_count'].astype(str).str.replace(',', '', regex=False)
        stats['seen_count'] = pd.to_numeric(stats['seen_count'], errors='coerce').fillna(0)

    anime = anime.rename(columns={'rating': 'anime_avg_rating'})

    # 这里的 merge 要保留 'tags'，因为 MMR 需要用
    cols_to_use = ['anime_id', 'title', 'tags', 'anime_avg_rating', 'episodes', 'anime_year']
    cols_to_use = [c for c in cols_to_use if c in anime.columns]

    anime_meta = pd.merge(anime[cols_to_use], stats[['anime_id', 'completion_rate', 'seen_count']], on='anime_id',
                          how='left')

    return anime_meta


def debug_ranking_mode():
    print("====== 1. 初始化系统 (游客模式) ======")
    data_path = 'data/'
    anime_meta_df = load_anime_metadata(data_path)

    # 空用户表
    user_meta_df = pd.DataFrame(columns=['user_id', 'user_avg_rating', 'user_collections'])

    id2title = anime_meta_df.set_index('anime_id')['title'].to_dict()

    if not os.path.exists('models/encoders.pkl'):
        print("错误：请先运行 train_pipeline.py")
        return

    encoders = joblib.load('models/encoders.pkl')
    feature_columns = joblib.load('models/feature_columns.pkl')

    print("加载 Recall 模型...")
    item_cf = ItemCF()
    item_cf.load_model()

    print("加载 Ranking 模型...")
    ranking_model = load_ranking_model(feature_columns)

    print("\n====== 2. 进入交互调试模式 ======")
    while True:
        print("\n" + "-" * 80)
        history_input = input("请输入已看过的动漫 ID (r/q): ").strip()
        if history_input.lower() == 'q': break

        history_ids = []
        if history_input.lower() == 'r':
            all_ids = list(id2title.keys())
            history_ids = random.sample(all_ids, 10)
        else:
            try:
                history_ids = [int(x.strip()) for x in history_input.split(',') if x.strip()]
            except:
                continue

        print(f"\n【游客历史】:")
        for aid in history_ids:
            print(f"  * {id2title.get(aid, '未知')} ({aid})")

        # === 1. Recall ===
        print(f"\n>>> 1. ItemCF 召回...")
        recall_ids = item_cf.recommend(history_ids, top_k=200)  # 召回 200 个
        if len(recall_ids) < 200:
            pool = [x for x in item_cf.hot_items if x not in recall_ids and x not in history_ids]
            recall_ids.extend(pool[:(200 - len(recall_ids))])

        # === 2. Ranking ===
        print(f">>> 2. DeepFM 精排...")
        input_df = create_ranking_input(-1, recall_ids, anime_meta_df, user_meta_df, encoders)
        scores = predict_ranking(ranking_model, input_df, feature_columns)

        # 结果打包: [(id, score), ...]
        ranked_results = list(zip(recall_ids, scores))
        # 按分数降序
        ranked_results.sort(key=lambda x: x[1], reverse=True)

        # === 3. Rerank (MMR) ===
        print(f">>> 3. MMR 重排 (Lambda=0.6)...")
        # MMR 需要传入排序后的 [(id, score)] 列表
        # 取 Top 30 给 MMR 去筛选 Top 10，给 MMR 留点余地
        rerank_input = ranked_results[:100]
        final_ids = mmr_rerank(rerank_input, anime_meta_df, top_n=10, lambda_param=0.6)

        # === 展示对比 ===
        print(f"\n{'=' * 35} 最终推荐流程对比 {'=' * 35}")
        print(f"{'Ranking (Top 10)':<40} | {'Rerank (MMR Top 10)':<40}")
        print("-" * 85)

        for i in range(10):
            # Ranking 结果
            rank_str = "---"
            if i < len(ranked_results):
                rid = ranked_results[i][0]
                rtitle = id2title.get(rid, "未知")[:15]
                rank_str = f"{rtitle} ({ranked_results[i][1]:.2f})"

            # Rerank 结果
            rerank_str = "---"
            if i < len(final_ids):
                rrid = final_ids[i]
                rrtitle = id2title.get(rrid, "未知")[:15]
                # 找回分数用于展示
                score = next((x[1] for x in ranked_results if x[0] == rrid), 0)
                rerank_str = f"{rrtitle} ({score:.2f})"

            print(f"{rank_str:<40} | {rerank_str:<40}")


if __name__ == "__main__":
    debug_ranking_mode()