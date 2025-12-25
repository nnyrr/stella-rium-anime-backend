# train_pipeline.py
import joblib
from src.preprocess import load_data, process_features
from src.recall import ItemCF
from src.ranking import train_ranking_model


def run_pipeline():
    print("====== 1. 加载数据 ======")
    df, _ = load_data('data/')

    print("====== 2. 处理特征 ======")
    # train_df, feature_columns = process_features(df, train_mode=True)
    # joblib.dump(feature_columns, 'models/feature_columns.pkl')

    print("====== 3. 训练 DeepFM ======")
    # 这里的 target_col 对应 preprocess.py 里的 TARGET ('user_rating')
    # train_ranking_model(train_df, feature_columns, target_col='user_rating')

    print("====== 4. 训练 ItemCF (可选) ======")
    # 如果之前训练过且不想覆盖，可以注释掉
    item_cf = ItemCF()
    item_cf.train(df)  # 注意 ItemCF 需要的是原始 df 的 user_id/anime_id

    print("训练完成！")


if __name__ == "__main__":
    run_pipeline()