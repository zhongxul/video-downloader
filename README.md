# Video Downloader（安卓本地下载）

> 文档版本：v1.3.1  
> 最后更新：2026-02-14

一个仅用于个人本地使用的安卓视频下载工具，当前重点支持抖音、X，以及 `kstore.vip/*.html#...`（如 `bluegay/xgaymv`）这类 hash 包装链接的解析与下载。

## 当前实现
- 支持抖音分享文案提取链接并解析。
- 支持 X 普通视频与部分 m3u8/直播回放链接解析（受站点策略与网络环境影响）。
- 支持 `kstore.vip/*.html#...` 变体 Base64 hash 解码，提取真实 `mp4/m3u8` 地址。
- 支持普通 m3u8 与 `AES-128` 加密 m3u8（`#EXT-X-KEY`）下载与合并。
- 支持 X 链接解析提速（状态链接并行分支抢占 + 镜像并行探测）。
- 支持下载队列、失败重试、历史管理、已完成管理。
- 支持下载前后双阶段校验，自动拦截空文件/网页内容/异常文件。
- 支持同名文件自动重命名：`标题.mp4`、`标题(1).mp4`、`标题(2).mp4`。
- 下载队列卡片显示进度条，已完成页支持“全选/取消全选”和长按进入管理选中。

## 快速开始
### Android Studio
1. 打开项目目录：`D:\Video Downloader`
2. 等待 Gradle Sync 完成
3. 运行 `app` 到真机（Android 10+）

### 终端构建
```powershell
.\环境一键配置.ps1
.\gradlew.bat :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 常用命令
```powershell
# 构建 Debug APK
.\gradlew.bat :app:assembleDebug

# 安装到已连接设备
.\gradlew.bat :app:installDebug

# 如本机 PATH 未配置 adb，可直接安装
D:\Android\Sdk\platform-tools\adb.exe install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## 关键行为说明
### kstore hash 链接
- 链接形态：`https://...kstore.vip/*.html#<payload>`
- `payload` 按 `- -> +`、`_ -> /`、`. -> =` 转换后 Base64 解码为 JSON。
- JSON 内含 `title/url`，`url` 通常为带时效参数 `auth_key` 的 m3u8。

### m3u8
- 普通 m3u8：分片并发下载，按顺序写入合并。
- 加密 m3u8：支持 `METHOD=AES-128`（拉取 key 后逐片解密再合并）。
- 目前不支持：`SAMPLE-AES` 等非 `AES-128` 加密方式。

### X 解析速度
- 对 `status` 链接采用并行解析分支，减少串行等待。
- 对 FX 镜像采用并行探测，谁先命中用谁。
- 新增分支耗时日志，便于区分网络问题与平台侧返回问题。

## 文档导航
- `docs/需求说明.md`：范围、目标与验收基线
- `docs/技术设计文档.md`：架构与核心流程设计
- `docs/启动说明.md`：环境与运行步骤
- `docs/失败场景回归测试清单.md`：回归测试项
- `docs/项目维护手册.md`：维护与排障指引
- `docs/bluegay链接解析调研.md`：kstore hash 链接专项调研

## 变更记录
- 2026-02-10 v1.0.0：建立文档版本头与统一维护入口
- 2026-02-10 v1.1.0：对齐推荐排序、m3u8 预览、同名重命名、历史页管理交互
- 2026-02-14 v1.2.0：补充 m3u8 下载提速实现（并发窗口、退避重试、连接池与缓冲优化）
- 2026-02-14 v1.3.0：补充 kstore hash 链接解析、AES-128 加密 m3u8 下载、队列进度条与已完成页管理增强
- 2026-02-14 v1.3.1：补充 X 解析提速与分支耗时日志能力
