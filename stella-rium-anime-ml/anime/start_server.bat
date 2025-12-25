@echo off
echo ==========================================
echo       Starting Anime RecSys Service
echo ==========================================

:: 1. 检查模型是否存在，不存在则训练
if not exist "models\deepfm_weights.h5" (
    echo [Warning] Model not found! Starting training pipeline...
    python train_pipeline.py
)

:: 2. 启动服务
echo [System] Starting FastAPI Server...
echo [Info] Server will be available at http://localhost:8000/recommend
echo [Info] Keep this window OPEN while running your Spring Boot app.

python -m uvicorn src.serve:app --host 0.0.0.0 --port 8000 --reload

pause