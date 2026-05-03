# Video Downloader（安卓本地下载）

> 文档版本：v1.3.2
> 最后更新：2026-04-26

一个仅用于个人本地使用的安卓视频下载工具，当前重点支持抖音、X，以及 `kstore.vip/*.html#...`（如 `bluegay/xgaymv`）这类 hash 包装链接的解析与下载。

## 当前实现
- 支持抖音分享文案提取链接并解析。
- 支持 X 普通视频与部分 m3u8/直播回放链接解析。
- 支持 `kstore.vip/*.html#...` 变体 Base64 hash 解码，提取真实 `mp4/m3u8` 地址。
- 支持普通 m3u8 与 `AES-128` 加密 m3u8（`#EXT-X-KEY`）下载与合并。
- 支持 X 链接解析提速（状态链接并行分支抢占 + 镜像并行探测）。
- 支持下载队列、失败重试、历史管理、已完成管理。
- 支持下载前后双阶段校验，自动拦截空文件、网页内容和异常文件。
- 支持同名文件自动重命名：`标题.mp4`、`标题(1).mp4`、`标题(2).mp4`。
- 历史中心统一为 `HistoryScreen`，包含“下载 / 已完成 / 解析记录”三栏。

## 现状说明
- 当前解析模型仍是 `ParsedVideoInfo + List<VideoFormat>`。
- 当前下载模型仍是单 `DownloadTask`；“批量下载”和“失败组重试”基于 UI 分组实现，不是真正的 `TaskGroup + TaskItem`。
- `docs/plans/` 下的文件是后续重构方案，不代表当前已落地实现。

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

# 运行单元测试
.\gradlew.bat :app:testDebugUnitTest
```

## 文档导航
- `docs/需求说明.md`：当前基线需求与验收范围
- `docs/技术设计文档.md`：当前架构与关键流程
- `docs/当前架构现状.md`：当前真实实现边界与未落地项
- `docs/启动说明.md`：环境与运行步骤
- `docs/失败场景回归测试清单.md`：回归测试项
- `docs/项目维护手册.md`：维护与排障指引
- `docs/bluegay链接解析调研.md`：kstore hash 链接专项调研
- `docs/plans/`：未来重构方案与设计稿

## 变更记录
- 2026-02-10 v1.0.0：建立文档版本头与统一维护入口
- 2026-02-10 v1.1.0：对齐推荐排序、m3u8 预览、同名重命名、历史页管理交互
- 2026-02-14 v1.2.0：补充 m3u8 下载提速实现（并发窗口、退避重试、连接池与缓冲优化）
- 2026-02-14 v1.3.0：补充 kstore hash 链接解析、AES-128 加密 m3u8 下载、队列进度条与已完成页管理增强
- 2026-02-14 v1.3.1：补充 X 解析提速与分支耗时日志能力
- 2026-04-26 v1.3.2：收口历史中心结构，补充当前架构现状说明并明确 `docs/plans` 为未来方案
