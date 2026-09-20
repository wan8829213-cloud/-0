# 医美回复键盘（Android 输入法）

在微信里点输入框 → 切到本输入法 → 「读取剪贴板并生成」→ 点候选回复，直接输入到输入框。
另有全拼中文打字、中/英切换、数字符号页。话术库、合规过滤、AI 接口（reply-api.js）与小程序/iOS 版一致。

## 构建
1. 用 Android Studio（Hedgehog 及以上）打开本目录，等待 Gradle 同步（首次会下载 Gradle 8.4 与 Android Gradle Plugin 8.2.2；国内网络可在 settings.gradle.kts 里加阿里云镜像）。
2. 手机开启开发者模式和 USB 调试，点 Run 安装；或 Build → Build APK(s) 得到 APK 发给同事安装。
3. 改 `ReplyApi.kt` 的 `DEFAULT_BASE` 为你的 HTTPS 域名（也可在 App 设置页里改）。
4. 打开 App：点「启用输入法」勾选「医美回复」，再点「切换输入法」选它。
5. `applicationId` 是 `com.example.yimei`，正式发布前请改成自己的包名。

## 说明
- 不需要「完全访问」之类的额外权限；只申请了联网（INTERNET）。输入法是系统默认输入法期间可以读取剪贴板（Android 10+ 的限制不影响输入法）。
- 拼音显示在输入框内（组合文字），候选栏点选上屏；空格上屏首选；ü 用 v 输入（nv、lv）。
- 词库 `app/src/main/assets/dict.txt`（约 1400 字 + 400 词，医美/沟通用语为主），格式 `c 拼音 汉字…` / `w 词语 拼音`，直接加行即可；话术在 `assets/script_data.json`。
- 暂无：英文大写、联想、词频学习、九宫格、语音。
- 代码未在 Android Studio 里实际编译验证（沙箱没有 Android SDK），首次构建若有编译提示请贴给我修。

## 没有 Android Studio 也能出 APK（GitHub 云端构建）
1. 在 GitHub 新建一个私有仓库，把本目录全部文件上传（注意包含 .github 文件夹）。
2. 仓库 → Actions → Build APK → Run workflow，等 5~10 分钟。
3. 完成后在该次运行页面底部 Artifacts 下载 yimei-keyboard-apk，解压得到 app-debug.apk，传到手机安装（需允许「安装未知来源应用」）。
