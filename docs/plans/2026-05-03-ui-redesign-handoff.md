# UI 重构交付文档

**日期**: 2026-05-03  
**范围**: `ui/redesign/` 目录下 28 个新文件，零修改现有代码

## 产出概览

| 层级 | 文件 | 说明 |
|------|------|------|
| 设计系统 | `theme/AppDesignSystem.kt` | 颜色、字体、间距、圆角 token，通过 `AppTheme` CompositionLocal 提供 |
| 公共组件 | `component/SharedComponents.kt` | BottomNav、PrimaryButton、SecondaryButton、Card、Chip、StatCard |
| 导航 | `navigation/RedesignNavHost.kt` | 3 个底部 Tab + 2 个二级路由，Tab 切换保留状态 |
| 下载页 | `download/` (5 文件) | 首页：链接输入、今日统计、最近解析、队列摘要 |
| 资源确认页 | `parse_result/` (5 文件) | 缩略图横滑、大图预览、版本信息、下载当前/全部 |
| 资源库 | `library/` (5 文件) | 三段式 Tab（进行中/已完成/解析记录）、进度条卡片 |
| 详情页 | `detail/` (5 文件) | 状态标签、媒体预览、任务元信息、操作建议 |
| 我的 | `profile/` (5 文件) | 个人卡片、命名规则/网络策略统计、常用设置、帮助诊断 |

## 每页文件结构

```
*UiState.kt    — 不可变状态数据类
*Action.kt     — 用户操作密封接口
*UiEvent.kt    — 一次性事件（导航、Toast）
*ViewModel.kt  — StateFlow + Channel + onAction 骨架
*Screen.kt     — Compose UI + @Preview
```

## 构建状态

- `compileDebugKotlin` 通过，0 error / 0 warning
- 未修改任何现有文件，新旧 UI 完全隔离

## 待接入

ViewModel 中标记了 `// Codex:` 的位置需要接入 domain 层：

- `DownloadViewModel` — 剪贴板粘贴、调用 `ParseLinkUseCase`、跳转解析结果
- `ParseResultViewModel` — 加载解析记录、调用 `CreateDownloadTaskUseCase`
- `LibraryViewModel` — 订阅 `SyncDownloadStatusUseCase`、分组展示
- `DetailViewModel` — 加载任务详情、暂停/恢复/重试/删除/打开文件
- `ProfileViewModel` — 读写设置项、导出日志、反馈

## 激活方式

在 `MainActivity` 中将 `AppNavHost()` 替换为 `RedesignNavHost()` 即可切换到新 UI。
