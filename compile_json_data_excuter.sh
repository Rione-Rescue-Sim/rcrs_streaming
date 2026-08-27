#!/bin/bash
### extract.sh (Ubuntu / Git Bash 両対応版)

### rcrs-serverのパス（環境に合わせて書き換える）
RCRS_SERVER_DIR="../rcrs-server"

### 1. 実行環境（OS）を自動判定して区切り文字を切り替える
# $OSTYPE が "msys"（Git Bashの識別子）を含む場合はセミコロン、それ以外はコロンにします
if [[ "$OSTYPE" == "msys" ]]; then
    echo "Detected Environment: Git Bash (Windows)"
    SEP=";"
else
    echo "Detected Environment: Linux / Ubuntu"
    SEP=":"
fi

### 2. 判定した区切り文字（$SEP）を使ってクラスパスを作成
CP="${RCRS_SERVER_DIR}/jars/*${SEP}${RCRS_SERVER_DIR}/lib/*"

### 3. コンパイル
# Windowsでの日本語文字化け・エラーを防ぐため -encoding UTF-8 を付与しています
echo "Compiling JsonDataExtractor.java..."
javac -encoding UTF-8 -cp "$CP" JsonDataExtractor.java

if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

### 4. 実行
echo "Running JsonDataExtractor..."
java -Xmx4g -Dlog4j.log.dir="." -cp ".${SEP}${CP}" JsonDataExtractor "$1" "$2"