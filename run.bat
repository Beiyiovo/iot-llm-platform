@echo off
chcp 65001 >nul
cd /d "%~dp0"

if not exist "target\iot-llm-platform-1.0.0.jar" (
    echo [1/2] 首次运行，正在打包...
    call mvn -DskipTests package
    if errorlevel 1 (
        echo.
        echo 打包失败，请检查 JDK 21 和 Maven 是否已配置。
        pause
        exit /b 1
    )
)

echo.
echo [2/2] 启动中，稍后访问 http://localhost:8080
echo       账号 admin / 123456
echo       按 Ctrl+C 停止
echo.
java -Dfile.encoding=UTF-8 -jar target\iot-llm-platform-1.0.0.jar --iot.simulator.enabled=true
pause
