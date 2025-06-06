#!/bin/bash

# ================================================
# 配置区
# ================================================
# 获取脚本所在目录作为工作空间
WORKSPACE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
echo "[INFO] Workspace: $WORKSPACE"

# 切换到工作空间目录
cd "$WORKSPACE" || {
  echo "[ERROR] Failed to enter workspace"
  exit 1
}

# ================================================
# 参数解析
# ================================================
if [ $# -lt 2 ]; then
  echo "Usage: $0 <language> <output_dir>"
  echo "Example: $0 --java ./generated"
  exit 1
fi

LANGUAGE=$1
OUTPUT_DIR=$2

# 转换输出路径为绝对路径
FULL_OUTPUT_DIR=$(mkdir -p "$OUTPUT_DIR" && cd "$OUTPUT_DIR" && pwd)
echo "[INFO] Output directory: $FULL_OUTPUT_DIR"

# ================================================
# 检查依赖
# ================================================
if ! command -v flatc &> /dev/null; then
  echo "[ERROR] flatc compiler not found. Install FlatBuffers first."
  exit 1
fi

# ================================================
# 处理所有 .fbs 文件
# ================================================
for FBS_FILE in "$WORKSPACE"/*.fbs; do
  if [ -f "$FBS_FILE" ]; then
    echo "[INFO] Compiling $FBS_FILE"

    flatc "$LANGUAGE" -o "$FULL_OUTPUT_DIR" "$FBS_FILE"

    if [ $? -ne 0 ]; then
      echo "[ERROR] Failed to compile $FBS_FILE"
      exit 1
    fi
  fi
done

echo "[SUCCESS] All files generated to $FULL_OUTPUT_DIR"