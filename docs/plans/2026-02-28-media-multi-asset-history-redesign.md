# 多媒体解析与历史页重构 Implementation Plan

> 状态说明：本文档是未来重构实施计划，不代表当前仓库已经全部落地。

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在现有 Android 下载器中实现抖音图集/动图下载、X 多媒体保序解析、解析结果页内置预览，以及历史页三栏重构与批量任务状态管理。  

**Architecture:** 以“帖子-媒体-候选源”三层解析模型替代当前单 `formats` 模型；下载侧引入 `TaskGroup + TaskItem` 聚合；UI 以新 `ParseResultScreen` 与重构 `HistoryScreen` 承载交互。数据库升级到 v4 且采用 destructive migration，X Cookie 继续由 SharedPreferences 保留。  

**Tech Stack:** Kotlin, Jetpack Compose, Room, Coroutines/Flow, OkHttp, Android DownloadManager, Media3 ExoPlayer, JUnit4

---

### Task 1: 解析模型与接口底座

**Files:**
- Create: `app/src/main/java/com/example/videodownloader/domain/model/ParsedPost.kt`
- Create: `app/src/main/java/com/example/videodownloader/domain/model/ParsedMediaItem.kt`
- Create: `app/src/main/java/com/example/videodownloader/domain/model/DownloadCandidate.kt`
- Modify: `app/src/main/java/com/example/videodownloader/parser/ParserGateway.kt`
- Test: `app/src/test/java/com/example/videodownloader/domain/model/ParsedPostModelTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `parsed post should keep media order`() {
    val post = ParsedPost(title = "t", mediaItems = listOf(
        ParsedMediaItem(mediaId = "m1", displayOrder = 0, mediaType = MediaType.IMAGE, candidates = emptyList()),
        ParsedMediaItem(mediaId = "m2", displayOrder = 1, mediaType = MediaType.VIDEO, candidates = emptyList()),
    ))
    assertEquals(listOf("m1", "m2"), post.mediaItems.map { it.mediaId })
}
```

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ParsedPostModelTest"`  
Expected: FAIL（模型未定义）

**Step 3: Write minimal implementation**  
新增模型与枚举，`ParserGateway.parse` 返回 `ParsedPost`。

**Step 4: Run test to verify it passes**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ParsedPostModelTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/example/videodownloader/domain/model app/src/main/java/com/example/videodownloader/parser/ParserGateway.kt app/src/test/java/com/example/videodownloader/domain/model/ParsedPostModelTest.kt
git commit -m "refactor: 引入帖子级多媒体解析模型"
```

### Task 2: Room v4 与任务分层表

**Files:**
- Create: `app/src/main/java/com/example/videodownloader/data/local/TaskGroupEntity.kt`
- Create: `app/src/main/java/com/example/videodownloader/data/local/TaskItemEntity.kt`
- Create: `app/src/main/java/com/example/videodownloader/data/local/TaskGroupDao.kt`
- Create: `app/src/main/java/com/example/videodownloader/data/local/TaskItemDao.kt`
- Modify: `app/src/main/java/com/example/videodownloader/data/local/AppDatabase.kt`
- Test: `app/src/test/java/com/example/videodownloader/data/local/TaskEntityMappingTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `task group should map counters correctly`() {
    val entity = TaskGroupEntity(id = "g1", totalCount = 3, successCount = 1, failedCount = 1, downloadingCount = 1, status = "DOWNLOADING", title = "group", sourceUrl = "u", coverUrl = null, platform = "x", createdAt = 1L, updatedAt = 1L)
    assertEquals(3, entity.totalCount)
}
```

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TaskEntityMappingTest"`  
Expected: FAIL（实体缺失）

**Step 3: Write minimal implementation**  
新增实体/DAO，数据库版本升至 4，保留 destructive migration。

**Step 4: Run test to verify it passes**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TaskEntityMappingTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/example/videodownloader/data/local
git commit -m "feat: 新增TaskGroup与TaskItem本地表结构"
```

### Task 3: 仓储与聚合状态计算

**Files:**
- Create: `app/src/main/java/com/example/videodownloader/data/repository/TaskGroupRepository.kt`
- Create: `app/src/main/java/com/example/videodownloader/data/repository/TaskGroupRepositoryImpl.kt`
- Create: `app/src/main/java/com/example/videodownloader/data/repository/TaskItemRepository.kt`
- Create: `app/src/main/java/com/example/videodownloader/data/repository/TaskItemRepositoryImpl.kt`
- Create: `app/src/main/java/com/example/videodownloader/domain/usecase/AggregateTaskGroupStatusUseCase.kt`
- Test: `app/src/test/java/com/example/videodownloader/domain/usecase/AggregateTaskGroupStatusUseCaseTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `should return partial success when success and failed coexist`() {
    val status = AggregateTaskGroupStatusUseCase().invoke(
        success = 1, failed = 1, downloading = 0, queued = 0, total = 2
    )
    assertEquals(TaskGroupStatus.PARTIAL_SUCCESS, status)
}
```

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*AggregateTaskGroupStatusUseCaseTest"`  
Expected: FAIL（UseCase未实现）

**Step 3: Write minimal implementation**  
实现聚合规则：`DOWNLOADING > PARTIAL_SUCCESS > SUCCESS > FAILED`。

**Step 4: Run test to verify it passes**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*AggregateTaskGroupStatusUseCaseTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/example/videodownloader/data/repository app/src/main/java/com/example/videodownloader/domain/usecase/AggregateTaskGroupStatusUseCase.kt app/src/test/java/com/example/videodownloader/domain/usecase/AggregateTaskGroupStatusUseCaseTest.kt
git commit -m "feat: 实现批量任务聚合状态计算与仓储"
```

### Task 4: Douyin 图集/动图解析

**Files:**
- Modify: `app/src/main/java/com/example/videodownloader/parser/WebParserGateway.kt`
- Test: `app/src/test/java/com/example/videodownloader/parser/WebParserGatewayDouyinAlbumTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `douyin album should output ordered image items`() {
    val post = parser.parse("https://www.douyin.com/mock-album")
    assertTrue(post.mediaItems.size > 1)
    assertEquals(MediaType.IMAGE, post.mediaItems.first().mediaType)
}
```

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*WebParserGatewayDouyinAlbumTest"`  
Expected: FAIL

**Step 3: Write minimal implementation**  
补充图集结构提取，动图候选按 `gif/webp -> mp4` 优先级输出。

**Step 4: Run test to verify it passes**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*WebParserGatewayDouyinAlbumTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/example/videodownloader/parser/WebParserGateway.kt app/src/test/java/com/example/videodownloader/parser/WebParserGatewayDouyinAlbumTest.kt
git commit -m "feat: 支持抖音图集与动图候选解析"
```

### Task 5: X 多媒体保序解析

**Files:**
- Modify: `app/src/main/java/com/example/videodownloader/parser/WebParserGateway.kt`
- Modify: `app/src/main/java/com/example/videodownloader/parser/YtDlpParserGateway.kt`
- Test: `app/src/test/java/com/example/videodownloader/parser/WebParserGatewayXMultiMediaTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `x tweet with mixed media should keep original order`() {
    val post = parser.parse("https://x.com/mock/status/1")
    assertEquals(listOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.IMAGE), post.mediaItems.map { it.mediaType })
}
```

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*WebParserGatewayXMultiMediaTest"`  
Expected: FAIL

**Step 3: Write minimal implementation**  
按推文媒体数组顺序构造 `mediaItems`；每媒体项标记推荐候选。

**Step 4: Run test to verify it passes**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*WebParserGatewayXMultiMediaTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/example/videodownloader/parser/WebParserGateway.kt app/src/main/java/com/example/videodownloader/parser/YtDlpParserGateway.kt app/src/test/java/com/example/videodownloader/parser/WebParserGatewayXMultiMediaTest.kt
git commit -m "feat: 支持X多媒体按原文顺序解析"
```

### Task 6: 解析结果页与导航切换

**Files:**
- Create: `app/src/main/java/com/example/videodownloader/ui/screen/result/ParseResultScreen.kt`
- Create: `app/src/main/java/com/example/videodownloader/ui/screen/result/ParseResultViewModel.kt`
- Modify: `app/src/main/java/com/example/videodownloader/ui/AppNavHost.kt`
- Modify: `app/src/main/java/com/example/videodownloader/ui/screen/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/example/videodownloader/ui/screen/home/HomeScreen.kt`
- Test: `app/src/test/java/com/example/videodownloader/ui/screen/result/ParseResultViewModelTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `download all should create one group and many items`() = runTest {
    viewModel.onDownloadAllClick()
    assertEquals(1, fakeGroupRepo.created.size)
    assertTrue(fakeItemRepo.created.size > 1)
}
```

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ParseResultViewModelTest"`  
Expected: FAIL

**Step 3: Write minimal implementation**  
完成解析后跳转结果页；单项直显、多项 Pager；接入“下载当前/下载全部”。

**Step 4: Run test to verify it passes**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ParseResultViewModelTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/example/videodownloader/ui/screen/result app/src/main/java/com/example/videodownloader/ui/AppNavHost.kt app/src/main/java/com/example/videodownloader/ui/screen/home/HomeViewModel.kt app/src/main/java/com/example/videodownloader/ui/screen/home/HomeScreen.kt app/src/test/java/com/example/videodownloader/ui/screen/result/ParseResultViewModelTest.kt
git commit -m "feat: 新增解析结果页与单项/批量下载入口"
```

### Task 7: 内置媒体预览（图片放大 + 视频播放）

**Files:**
- Create: `app/src/main/java/com/example/videodownloader/ui/screen/result/MediaPreviewComponents.kt`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/example/videodownloader/ui/screen/result/MediaPreviewDecisionTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `gif candidate should be preferred over mp4`() {
    val selected = pickDefaultCandidate(listOf(
        DownloadCandidate("1", "u1", "mp4", false),
        DownloadCandidate("2", "u2", "gif", true),
    ))
    assertEquals("gif", selected.ext)
}
```

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*MediaPreviewDecisionTest"`  
Expected: FAIL

**Step 3: Write minimal implementation**  
引入 Media3 Compose 依赖；实现图片全屏缩放、视频内置播放器、候选优先策略。

**Step 4: Run test to verify it passes**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*MediaPreviewDecisionTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/build.gradle.kts app/src/main/java/com/example/videodownloader/ui/screen/result/MediaPreviewComponents.kt app/src/test/java/com/example/videodownloader/ui/screen/result/MediaPreviewDecisionTest.kt
git commit -m "feat: 支持解析结果页内置图片与视频预览"
```

### Task 8: 下载看板 ViewModel（下载中/失败双分段）

**Files:**
- Create: `app/src/main/java/com/example/videodownloader/ui/screen/history/DownloadBoardViewModel.kt`
- Create: `app/src/main/java/com/example/videodownloader/domain/usecase/ObserveDownloadBoardUseCase.kt`
- Test: `app/src/test/java/com/example/videodownloader/ui/screen/history/DownloadBoardViewModelTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `same group should appear in downloading and failed segments`() = runTest {
    val board = viewModel.uiState.value
    assertTrue(board.downloadingGroups.any { it.groupId == "g1" })
    assertTrue(board.failedGroups.any { it.groupId == "g1" })
}
```

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*DownloadBoardViewModelTest"`  
Expected: FAIL

**Step 3: Write minimal implementation**  
实现组级双投影分段，下载中/失败各自只含对应子项。

**Step 4: Run test to verify it passes**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*DownloadBoardViewModelTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/example/videodownloader/ui/screen/history/DownloadBoardViewModel.kt app/src/main/java/com/example/videodownloader/domain/usecase/ObserveDownloadBoardUseCase.kt app/src/test/java/com/example/videodownloader/ui/screen/history/DownloadBoardViewModelTest.kt
git commit -m "feat: 实现下载看板双分段聚合视图"
```

### Task 9: 历史页 UI 全面重构（三栏 + 二级分段）

**Files:**
- Modify: `app/src/main/java/com/example/videodownloader/ui/screen/history/HistoryScreen.kt`
- Modify: `app/src/main/java/com/example/videodownloader/ui/AppNavHost.kt`
- Delete: `app/src/main/java/com/example/videodownloader/ui/screen/history/CompletedScreen.kt`
- Delete: `app/src/main/java/com/example/videodownloader/ui/screen/history/CompletedViewModel.kt`
- Test: `app/src/test/java/com/example/videodownloader/ui/screen/history/HistoryInteractionRulesTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `group card click should toggle expand only`() {
    val result = reducer.onGroupCardClick(expanded = false)
    assertTrue(result.expanded)
    assertFalse(result.navigateToDetail)
}
```

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*HistoryInteractionRulesTest"`  
Expected: FAIL

**Step 3: Write minimal implementation**  
重构为 `下载/已完成/解析记录` 三栏，下载下 `下载中/下载失败` 分段，并落实点击规则。

**Step 4: Run test to verify it passes**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*HistoryInteractionRulesTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/example/videodownloader/ui/screen/history/HistoryScreen.kt app/src/main/java/com/example/videodownloader/ui/AppNavHost.kt app/src/test/java/com/example/videodownloader/ui/screen/history/HistoryInteractionRulesTest.kt
git rm app/src/main/java/com/example/videodownloader/ui/screen/history/CompletedScreen.kt app/src/main/java/com/example/videodownloader/ui/screen/history/CompletedViewModel.kt
git commit -m "refactor: 重构历史页为三栏与下载二级分段结构"
```

### Task 10: 批量失败一键重试

**Files:**
- Create: `app/src/main/java/com/example/videodownloader/domain/usecase/RetryFailedItemsInGroupUseCase.kt`
- Modify: `app/src/main/java/com/example/videodownloader/ui/screen/history/DownloadBoardViewModel.kt`
- Test: `app/src/test/java/com/example/videodownloader/domain/usecase/RetryFailedItemsInGroupUseCaseTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `retry group should clone only failed items`() = runTest {
    val retried = useCase("g1")
    assertEquals(listOf("failed-1", "failed-2"), retried.map { it.retryFromItemId })
}
```

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*RetryFailedItemsInGroupUseCaseTest"`  
Expected: FAIL

**Step 3: Write minimal implementation**  
实现仅失败子项重试，成功子项不重复入队。

**Step 4: Run test to verify it passes**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*RetryFailedItemsInGroupUseCaseTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/example/videodownloader/domain/usecase/RetryFailedItemsInGroupUseCase.kt app/src/main/java/com/example/videodownloader/ui/screen/history/DownloadBoardViewModel.kt app/src/test/java/com/example/videodownloader/domain/usecase/RetryFailedItemsInGroupUseCaseTest.kt
git commit -m "feat: 支持批量任务失败子项一键重试"
```

### Task 11: 状态同步链路升级

**Files:**
- Modify: `app/src/main/java/com/example/videodownloader/domain/usecase/SyncDownloadStatusUseCase.kt`
- Modify: `app/src/main/java/com/example/videodownloader/di/AppContainer.kt`
- Test: `app/src/test/java/com/example/videodownloader/domain/usecase/SyncDownloadStatusUseCaseTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `sync should update item first then aggregate group`() = runTest {
    syncUseCase()
    assertEquals("DOWNLOADING", fakeItemRepo.lastUpdatedStatus)
    assertEquals("PARTIAL_SUCCESS", fakeGroupRepo.lastAggregatedStatus)
}
```

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*SyncDownloadStatusUseCaseTest"`  
Expected: FAIL

**Step 3: Write minimal implementation**  
按“子任务同步 -> 主任务聚合”顺序重写同步逻辑。

**Step 4: Run test to verify it passes**  
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*SyncDownloadStatusUseCaseTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/example/videodownloader/domain/usecase/SyncDownloadStatusUseCase.kt app/src/main/java/com/example/videodownloader/di/AppContainer.kt app/src/test/java/com/example/videodownloader/domain/usecase/SyncDownloadStatusUseCaseTest.kt
git commit -m "refactor: 调整下载状态同步为子任务优先聚合"
```

### Task 12: 文档与回归清单更新

**Files:**
- Modify: `README.md`
- Modify: `docs/需求说明.md`
- Modify: `docs/技术设计文档.md`
- Modify: `docs/失败场景回归测试清单.md`

**Step 1: Write the failing test**  
无自动化测试，先补充人工回归矩阵与验收脚本清单。

**Step 2: Run test to verify it fails**  
Run: `.\gradlew.bat :app:assembleDebug`  
Expected: 若文档与实现不一致，按回归脚本会发现不通过项。

**Step 3: Write minimal implementation**  
对齐文档描述与实际交互、模型、限制与命令。

**Step 4: Run test to verify it passes**  
Run:  
- `.\gradlew.bat :app:testDebugUnitTest`  
- `.\gradlew.bat :app:assembleDebug`  
Expected: 全部通过

**Step 5: Commit**
```bash
git add README.md docs/需求说明.md docs/技术设计文档.md docs/失败场景回归测试清单.md
git commit -m "docs: 更新多媒体解析与历史页重构文档及回归清单"
```

## 全量验证命令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

期望：

1. 单元测试通过。
2. Debug 构建成功。
3. 核心人工回归场景通过（X 多媒体顺序、Douyin 图集、批量重试、历史页交互）。
