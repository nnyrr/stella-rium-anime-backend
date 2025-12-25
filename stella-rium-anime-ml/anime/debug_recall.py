import pandas as pd
import random
from src.preprocess import load_data
from src.recall import ItemCF


def test_workflow():
    print("====== 1. 测试 Preprocess (数据加载) ======")
    # df 包含了 user_id, anime_id 以及合并后的信息
    df, anime_meta = load_data('data/')
    print(f"数据加载成功！")
    print(f"总记录数: {len(df)}")
    print(f"用户总数: {df['user_id'].nunique()}")
    print(f"动漫总数: {df['anime_id'].nunique()}")

    print("\n====== 2. 测试 Recall (Swing 模型训练) ======")
    # 实例化召回模型
    item_cf = ItemCF()

    # 训练模型 (这一步会跑 Swing 算法，计算进度条)
    # 注意：Swing只需要 user_id 和 anime_id 列
    item_cf.train(df)

    print("\n====== 3. 现场预测测试 ======")
    # 为了验证效果，我们找一个观看历史比较丰富的用户（比如看过 10~50 部番的用户）
    # 统计每个用户的观看数量
    user_counts = df['user_id'].value_counts()
    # 筛选出看过 10 到 50 部番的用户作为测试对象
    valid_users = user_counts[(user_counts >= 10) & (user_counts <= 50)].index.tolist()

    if not valid_users:
        print("警告：没有找到观看记录在 10-50 之间的用户，随机抽取一个用户。")
        test_user_id = df['user_id'].iloc[0]
    else:
        test_user_id = random.choice(valid_users)

    print(f"测试用户 ID: {test_user_id}")

    # 获取该用户的历史观看列表 (ID列表)
    history_ids = df[df['user_id'] == test_user_id]['anime_id'].unique().tolist()

    # 调用 recommend 接口
    print("正在计算推荐结果...")
    recommend_ids = item_cf.recommend(history_ids, top_k=10)

    print("\n------------------------------------------------")
    print(f"【用户历史】(最近 10 部):")
    # 将 ID 转换为 标题 打印出来，方便人眼观察
    # 建立一个 ID -> Title 的映射字典
    id2title = anime_meta.set_index('anime_id')['title'].to_dict()

    for aid in history_ids[:10]:
        print(f" - {id2title.get(aid, '未知标题')} (ID: {aid})")

    print(f"\n【Swing 召回推荐结果】:")
    if not recommend_ids:
        print("没有产生推荐结果 (可能是数据过少或该用户历史太偏)")
    else:
        for i, aid in enumerate(recommend_ids):
            print(f" {i + 1}. {id2title.get(aid, '未知标题')} (ID: {aid})")
    print("------------------------------------------------")


if __name__ == "__main__":
    test_workflow()