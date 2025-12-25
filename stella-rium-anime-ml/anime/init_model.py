import os
from src.character_recommender import CharacterRecommender


def manual_init():
    csv_path = 'data/character_translated.csv'

    print("=== 开始手动初始化角色模型 ===")

    # 1. 检查数据文件是否存在
    if not os.path.exists(csv_path):
        print(f"\n❌ [致命错误] 找不到数据文件：{os.path.abspath(csv_path)}")
        print("请检查：")
        print("1. 你的 csv 文件名是不是叫 character_translated.csv？")
        print("2. 它是不是放在了 data 文件夹里？")
        return

    print(f"✅ 成功找到 CSV 文件，准备处理...")

    # 2. 尝试生成模型
    try:
        # 强制指定路径
        rec = CharacterRecommender(data_path=csv_path, model_dir='models/')
        rec.train_and_save()  # 这一步会生成 .pkl 文件

        print("\n🎉 模型生成成功！")
        print("现在请去 models/ 文件夹看看，应该有以下文件了：")
        print(" - char_tfidf.pkl")
        print(" - char_nn.pkl")
        print(" - char_matrix.pkl")
        print(" - char_meta.pkl")
        print("\n👉 现在你可以重新运行 serve.py 了！")

    except Exception as e:
        print(f"\n❌ [生成失败] 报错信息如下：")
        print(e)
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    manual_init()