#!/bin/bash

echo "=========================================="
echo "       Starting Anime RecSys Service"
echo "=========================================="

# 1. 检查模型是否存在，不存在则训练
# 注意：Linux下路径分隔符是 /
if [ ! -f "models/deepfm_weights.h5" ]; then
    echo "[Warning] Model not found! Starting training pipeline..."
    python3 train_pipeline.py
fi

# 2. 启动服务
echo "[System] Starting FastAPI Server..."
echo "[Info] Server will be available at http://localhost:8000/recommend"
echo "[Info] Keep this window OPEN while running your Spring Boot app."

# 使用 python3 启动 uvicorn
python3 -m uvicorn src.serve:app --host 0.0.0.0 --port 8000 --reload

# 模拟 Windows 的 pause (防止窗口运行完直接关闭，仅在双击运行时有效)
read -p "Press any key to exit..."