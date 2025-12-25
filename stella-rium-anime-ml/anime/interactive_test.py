import pandas as pd
import os
import random
from src.preprocess import load_data
from src.recall import ItemCF


def interactive_mode():
    print("====== 1. 初始化系统 ======")
    print("正在加载元数据 (为了显示动漫名字)...")
    # 我们只需要 anime_details 来查名字，不需要全量训练数据
    # 为了方便，还是调用 load_data，但我们只取 anime 部分
    _, anime_meta = load_data('data/')

    # 建立 ID -> Title 的字典，方便人类阅读
    id2title = anime_meta.set_index('anime_id')['title'].to_dict()
    title2id = {v: k for k, v in id2title.items()}  # 反向查找用

    print("正在加载 Recall 模型 (不进行训练)...")
    item_cf = ItemCF()

    # 【关键】这里只 Load，不 Train
    # 如果你还没有 models/item_cf_sim.pkl，请先运行一次 train_pipeline.py 或 debug_recall.py
    if os.path.exists('models/item_cf_sim.pkl'):
        item_cf.load_model()
        print("模型加载成功！")
    else:
        print("错误：未找到模型文件 models/item_cf_sim.pkl")
        print("请先运行 python debug_recall.py 或 train_pipeline.py 进行一次训练。")
        return

    print("\n====== 2. 进入交互测试模式 ======")
    print("输入 'q' 退出，输入 'r' 随机生成10个历史记录")
    print("或者直接输入动漫 ID (用逗号分隔)，例如: 1, 15, 203")

    while True:
        print("\n------------------------------------------------")
        user_input = input("请输入已看过的动漫 ID (或 r/q): ").strip()

        if user_input.lower() == 'q':
            break

        history_ids = []

        # === 逻辑 A: 随机生成 10 个 ID 模拟用户 ===
        if user_input.lower() == 'r':
            # 从所有动漫里随机挑 10 个作为假的历史
            all_ids = list(id2title.keys())
            history_ids = random.sample(all_ids, 10)
            print(f"已随机生成 10 部历史记录: {history_ids}")

        # === 逻辑 B: 手动输入 ID ===
        else:
            try:
                # 处理输入，分割逗号
                str_ids = user_input.split(',')
                for sid in str_ids:
                    if sid.strip():
                        history_ids.append(int(sid.strip()))
            except ValueError:
                print("输入格式错误，请输入数字 ID，用逗号分隔。")
                continue

        # === 打印用户历史 (带名字) ===
        print(f"\n【用户历史 ({len(history_ids)} 部)】:")
        for aid in history_ids:
            title = id2title.get(aid, "未知标题")
            print(f"  [{aid}] {title}")

        # === 核心：调用推荐 ===
        # 这一步是瞬间完成的
        recommend_ids = item_cf.recommend(history_ids, top_k=10)

        # === 打印推荐结果 ===
        print(f"\n【推荐结果 (Top 10)】:")
        if not recommend_ids:
            print("  无推荐结果 (可能是历史记录太冷门或模型未覆盖)")
        else:
            for i, aid in enumerate(recommend_ids):
                title = id2title.get(aid, "未知标题")
                print(f"  {i + 1}. [{aid}] {title}")


if __name__ == "__main__":
    interactive_mode()