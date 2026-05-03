# UI 重构交付文档

**日期**: 2026-05-03  
**范围**: `ui/redesign/` 新 UI、`RedesignNavHost` 导航接入、资源确认/资源库/详情/我的页面真实数据接入

> 当前状态：本文最初用于 Claude UI 交接。2026-05-03 后续已由 Codex 接入真实解析、下载、资源库与设置逻辑，因此本文不再表示“待接入清单”，而是作为设计与代码交接记录保留。

## 产出概览

| 层级 | 文件 | 说明 |
|------|------|------|
| 设计系统 | `theme/AppDesignSystem.kt` | 颜色、字体、间距、圆角 token，通过 `AppTheme` CompositionLocal 提供 |
| 公共组件 | `component/SharedComponents.kt` | BottomNav、PrimaryButton、SecondaryButton、Card、Chip、StatCard |
| 导航 | `navigation/RedesignNavHost.kt` | 3 个底部 Tab + 2 个二级路由，Tab 切换保留状态 |
| 下载页 | `download/` (5 文件) | 首页：链接输入、今日统计、最近解析、队列摘要 |
| 资源确认页 | `parse_result/` (5 文件) | 真实解析结果、缩略图横滑、大图预览、下载当前/全部；视频只在此页预览区点击后原地播放 |
| 资源库 | `library/` (5 文件) | 三段式 Tab（进行中/已完成/解析记录）、空状态、已完成/解析记录长按管理、同图集聚合 |
| 详情页 | `detail/` (5 文件) | 状态标签、静态媒体预览、任务元信息、删除/重试/暂停/恢复/打开内容 |
| 我的 | `profile/` (5 文件) | 个人卡片、常用设置、X Cookie 设置入口 |

## 每页文件结构

```
*UiState.kt    — 不可变状态数据类
*Action.kt     — 用户操作密封接口
*UiEvent.kt    — 一次性事件（导航、Toast）
*ViewModel.kt  — StateFlow + Channel + onAction 骨架
*Screen.kt     — Compose UI + @Preview
```

## 当前实现状态

- `MainActivity` 已切换到 `RedesignNavHost(container = app.container)`
- 下载页已接入剪贴板、`ParseLinkUseCase`、`ParseResultStore`
- 资源确认页已接入 `CreateDownloadTaskUseCase`
- 资源库已接入真实 `DownloadTask` / `ParseRecord` 订阅与状态同步
- 详情页已接入删除、重试、暂停、恢复、打开本地媒体
- 我的页已接入 X Cookie 设置旧页面路由

## 已知边界

- 解析记录只持久化摘要，不能直接恢复完整资源确认页。
- 图集聚合基于 `parseRecordId` 的 UI 分组，不是正式 `TaskGroup + TaskItem`。
- 已完成列表和详情页不提供原地播放，只展示静态预览；打开内容走系统 viewer/player。

## 激活方式

已激活。入口位于 `MainActivity.kt`。
