#!/bin/bash
### extract.sh (Ubuntu / Git Bash / Cygwin 対応版)

# Git Bash環境下でのパス自動変換バグを防止
export MSYS_NO_PATHCONV=1

### rcrs-serverのパス（環境に合わせて書き換える）
RCRS_SERVER_DIR="../rcrs-server"

### 1. 実行環境（OS）判定
# Git Bash は msys、conda 環境などでは cygwin になることがある
if [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* || "$OSTYPE" == win32* ]]; then
    echo "Detected Environment: Windows (Git Bash / Cygwin)"
    SEP=";"
else
    echo "Detected Environment: Linux / Ubuntu"
    SEP=":"
fi

### 2. クラスパスの作成
CP="${RCRS_SERVER_DIR}/jars/*${SEP}${RCRS_SERVER_DIR}/lib/*"

echo "Checking classpath..."
echo "CP: $CP"

### 3. コンパイル
echo "Compiling JsonDataExtractor.java..."
javac -encoding UTF-8 -cp "$CP" JsonDataExtractor.java

if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

echo "Compilation successful!"

### 4. 実行
if [ $# -lt 2 ]; then
    echo "Usage: $0 <input.log> <output.jsonl>"
    exit 1
fi

echo "Running JsonDataExtractor..."
java -Xmx4g -Dlog4j.log.dir="." -cp ".${SEP}${CP}" JsonDataExtractor "$1" "$2"