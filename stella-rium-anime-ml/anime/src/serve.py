import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List
import pandas as pd
import numpy as np
import random
import joblib
import os
import contextlib

# === 引入自定义模块 ===
from src.recall import ItemCF
from src.ranking import load_ranking_model, predict_ranking, create_ranking_input
from src.rerank import mmr_rerank

# 【新增】引入角色推荐模块
from src.character_recommender import CharacterRecommender


# === 全局变量 (内存驻留) ===
class GlobalState:
    item_cf = None
    ranking_model = None
    feature_columns = None
    encoders = None
    anime_meta_df = None
    user_meta_df = None
    # 【新增】角色推荐器
    character_recommender = None


state = GlobalState()


# === 辅助函数：加载数据 ===
def load_serving_data():
    print("[System] 正在加载动漫元数据...")
    data_path = 'data/'

    # 1. 读取 CSV
    anime = pd.read_csv(os.path.join(data_path, 'anime_details.csv'))
    stats = pd.read_csv(os.path.join(data_path, 'anime_rating_stats.csv'))
    users = pd.read_csv(os.path.join(data_path, 'user_details.csv'))

    # 2. 数据清洗 & 去重
    anime = anime.drop_duplicates(subset=['anime_id'])
    stats = stats.drop_duplicates(subset=['anime_id'])

    # 安全转换函数
    def safe_to_int(series, default_val=0):
        s = pd.to_numeric(series, errors='coerce')
        s = s.fillna(default_val)
        s = s.replace([np.inf, -np.inf], default_val)
        return s.astype(float).astype(int)

    anime['anime_id'] = safe_to_int(anime['anime_id'])
    stats['anime_id'] = safe_to_int(stats['anime_id'])
    users['user_id'] = safe_to_int(users['user_id'])

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
    users = users.rename(columns={'avg_rating': 'user_avg_rating', 'collections': 'user_collections'})

    cols_to_use = ['anime_id', 'title', 'tags', 'anime_avg_rating', 'episodes', 'anime_year']
    cols_to_use = [c for c in cols_to_use if c in anime.columns]

    anime_meta = pd.merge(anime[cols_to_use], stats[['anime_id', 'completion_rate', 'seen_count']], on='anime_id',
                          how='left')

    return anime_meta, users


# === 生命周期管理 ===
@contextlib.asynccontextmanager
async def lifespan(app: FastAPI):
    # 1. 加载动漫数据
    try:
        state.anime_meta_df, state.user_meta_df = load_serving_data()
    except Exception as e:
        print(f"[Error] 动漫数据加载失败: {e}")
        # 这里可以选择 raise 也可以 pass，取决于是否允许部分服务运行
        # raise e

    # 2. 加载 Ranking 辅助文件
    try:
        print("[System] 正在加载 Encoders...")
        state.encoders = joblib.load('models/encoders.pkl')
        state.feature_columns = joblib.load('models/feature_columns.pkl')
    except Exception:
        print(f"[Warning] Encoders 缺失，跳过加载")

    # 3. 加载 ItemCF
    try:
        print("[System] 正在加载 ItemCF 模型...")
        state.item_cf = ItemCF()
        state.item_cf.load_model()
    except Exception:
        print(f"[Warning] ItemCF 模型缺失，跳过加载")

    # 4. 加载 Ranking 模型
    try:
        print("[System] 正在加载 DeepFM/DCN 模型...")
        state.ranking_model = load_ranking_model(state.feature_columns)
    except Exception:
        print(f"[Warning] Ranking 模型缺失，跳过加载")

    # === 5. 【新增】加载 Character 模型 ===
    try:
        # 这里实例化并调用 load_model()
        # 它会自动处理训练、保存和读取
        state.character_recommender = CharacterRecommender()
        state.character_recommender.load_model()
    except Exception as e:
        print(f"[Error] Character Recommender 加载失败: {e}")

    print("\n✅ 推荐系统服务已就绪！Listening on port 8000...")
    yield
    print("System shutting down...")


app = FastAPI(lifespan=lifespan)


# === 请求模型 ===
class RecommendRequest(BaseModel):
    user_id: int
    history_anime_ids: List[int]
    top_k: int = 10


class RecommendResponse(BaseModel):
    recommendations: List[int]
    debug_info: str = "success"


# 【新增】角色推荐请求体
class CharacterRecommendRequest(BaseModel):
    collected_character_ids: List[int]  # 收藏过的角色ID数组
    top_k: int = 10


# === 接口 1: 动漫推荐 ===
@app.post("/recommend/anime", response_model=RecommendResponse)
async def recommend_anime_endpoint(req: RecommendRequest):
    try:
        if not state.item_cf or not state.ranking_model:
            return RecommendResponse(recommendations=[], debug_info="Models not loaded")

        # 1. 召回
        recall_size = 400
        candidates = state.item_cf.recommend(req.history_anime_ids, top_k=recall_size)

        if len(candidates) < recall_size:
            pool = [x for x in state.item_cf.hot_items if x not in candidates and x not in req.history_anime_ids]
            random.shuffle(pool)
            needed = recall_size - len(candidates)
            candidates.extend(pool[:needed])

        if not candidates:
            return RecommendResponse(recommendations=[], debug_info="No candidates found")

        # 2. 排序
        input_df = create_ranking_input(
            user_id=req.user_id,
            candidate_ids=candidates,
            anime_meta_df=state.anime_meta_df,
            user_meta_df=state.user_meta_df,
            encoders=state.encoders
        )

        scores = predict_ranking(state.ranking_model, input_df, state.feature_columns)

        # 注入噪声
        noise = np.random.normal(loc=0.0, scale=20.0, size=len(scores))
        final_scores = scores + noise

        ranked_items = list(zip(candidates, final_scores))
        ranked_items.sort(key=lambda x: x[1], reverse=True)

        # 3. 重排 (MMR)
        rerank_input = ranked_items[:40]
        final_ids = mmr_rerank(
            ranked_items=rerank_input,
            anime_meta_df=state.anime_meta_df,
            top_n=req.top_k,
            lambda_param=0.6
        )

        # 探索性推荐
        if len(final_ids) > 2 and random.random() < 0.4:
            surprise_pool = [x for x in state.item_cf.hot_items if
                             x not in final_ids and x not in req.history_anime_ids]
            if surprise_pool:
                surprise_item = random.choice(surprise_pool)
                final_ids[-1] = surprise_item

        return RecommendResponse(recommendations=final_ids)

    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


# === 接口 2: 【新增】角色推荐 ===
@app.post("/recommend/character", response_model=RecommendResponse)
async def recommend_character_endpoint(req: CharacterRecommendRequest):
    """
    输入：收藏的角色ID列表
    输出：推荐的角色ID列表 (基于标签TF-IDF相似度)
    """
    try:
        # 检查模块是否就绪
        if state.character_recommender is None:
            raise HTTPException(status_code=503, detail="Character service not initialized")

        # 调用推荐逻辑
        recs = state.character_recommender.recommend(
            input_ids=req.collected_character_ids,
            top_k=req.top_k
        )

        return RecommendResponse(recommendations=recs, debug_info="character_content_based")

    except Exception as e:
        print(f"Error in character recommend: {e}")
        # 发生未知错误时，返回 500
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)