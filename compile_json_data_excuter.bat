@echo off
rem rcrs-serverのパス（環境に合わせて書き換えてください）
set RCRS_SERVER_DIR=..\rcrs-server

rem 必要なライブラリへのクラスパスを通す（Windowsなので ";" で区切ります）
set CP="%RCRS_SERVER_DIR%\jars\*;%RCRS_SERVER_DIR%\lib\*"

echo コンパイル中...
rem ソースコードがUTF-8で保存されている場合は -encoding UTF-8 を指定します
javac -encoding UTF-8 -cp %CP% JsonDataExtractor.java

if %ERRORLEVEL% neq 0 (
    echo コンパイルに失敗しました。
    pause
    exit /b %ERRORLEVEL%
)

echo 実行中...
java -Xmx4g -Dlog4j.log.dir="." -cp ".;%CP%" JsonDataExtractor %1 %2