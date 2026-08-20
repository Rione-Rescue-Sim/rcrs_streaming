#!/bin/bash
# extract.sh

# rcrs-serverのパス（環境に合わせて書き換える）
RCRS_SERVER_DIR="../rcrs-server"
# 必要なライブラリへのクラスパスを通す
CP="$RCRS_SERVER_DIR/jars/*:$RCRS_SERVER_DIR/lib/*"

# コンパイル
javac -cp "$CP" JsonDataExtractor.java

# 実行
java -Xmx4g -Dlog4j.log.dir="." -cp ".:$CP" JsonDataExtractor "$1" "$2"