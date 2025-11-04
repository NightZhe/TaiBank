# RemoteClientApp

這是一個 Android 遠端客戶端應用程式，可以透過 WebSocket 與伺服器通訊並執行遠端指令。

## 功能特點

- 透過 WebSocket 與伺服器建立長連線
- 啟動時自動回傳裝置資訊（Device ID、IP、型號、電量）
- 接收並執行伺服器指令：
  - `launch`: 啟動指定的應用程式
  - `toast`: 顯示訊息提示
- 使用 Foreground Service 維持背景連線

## 編譯 APK

### 方法 1: 使用 Android Studio
1. 用 Android Studio 開啟此專案
2. 修改 `WebSocketService.kt` 中的 `SERVER_WS_URL` 為你的伺服器位址
3. 點擊 Build > Build Bundle(s) / APK(s) > Build APK(s)
4. APK 會在 `app/build/outputs/apk/debug/` 目錄中

### 方法 2: 使用命令列
1. 確保已安裝 Android SDK
2. 修改 `local.properties` 中的 `sdk.dir` 路徑
3. 修改 `WebSocketService.kt` 中的 `SERVER_WS_URL`
4. 執行以下命令：

```bash
chmod +x gradlew
./gradlew assembleDebug
```

5. APK 會在 `app/build/outputs/apk/debug/app-debug.apk`

## 安裝與使用

1. 將 APK 傳送到 Android 裝置
2. 開啟「未知來源」的安裝權限
3. 安裝並開啟應用程式
4. 點擊「Start Service」開始連線

## 伺服器指令格式

### Launch 指令
```json
{
  "action": "launch",
  "package": "com.example.app"
}
```

### Toast 指令
```json
{
  "action": "toast",
  "text": "Hello from server"
}
```

## 注意事項

- 需要修改 `SERVER_WS_URL` 為實際的伺服器位址
- 確保裝置與伺服器網路可連通
- 需要授予網路權限和通知權限
