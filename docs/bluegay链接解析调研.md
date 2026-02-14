# kstore hash 链接解析与下载调研（MCP 实测）

更新日期：2026-02-14

## 1. 目标
- 分析 `kstore.vip/*.html#...` 这类链接点击“开始下载”后的真实下载机制。
- 给出项目侧可落地的解析与下载方案，并记录实施状态。

## 2. 链接形态与解码规则
- 页面形态：`https://<host>/<page>.html#<payload>`
- 样例页面：`bluegay.html`、`xgaymv.html`
- `#` 后 payload 解码规则：
  - `- -> +`
  - `_ -> /`
  - `. -> =`
  - Base64 解码后按 UTF-8 解析 JSON

解码后 JSON 结构：
```json
{
  "title": "视频标题",
  "url": "https://...m3u8?auth_key=...&via_m=..."
}
```

## 3. MCP 抓包结论
- 点击“开始下载”后，前端流程是：
1. 请求 hash 解码得到的 `m3u8`。
2. 读取 `#EXT-X-KEY`，请求 `crypt.key`。
3. 并发请求大量 `.ts` 分片。
4. 浏览器端执行 AES-128 解密并合并输出文件。

关键点：
- `auth_key` 是时效参数，过期后下载失败。
- 链接通常是加密 m3u8，不是单纯直链 mp4。

## 4. 项目侧实施结果
### 4.1 解析层
- 已支持 `kstore.vip/*.html#...` 通用识别（不再只限 `bluegay`）。
- 支持尾部多余 `#` 的容错处理。
- 产出格式为 `mp4/m3u8` 可下载选项。

实现文件：
- `app/src/main/java/com/example/videodownloader/parser/WebParserGateway.kt`

### 4.2 下载层
- 已支持 `#EXT-X-KEY` 且 `METHOD=AES-128` 的下载链路：
  - 解析 `METHOD/URI/IV`
  - 下载 key
  - 分片下载后逐片解密
  - 顺序合并写入

实现文件：
- `app/src/main/java/com/example/videodownloader/download/AndroidDownloadGateway.kt`

## 5. 当前限制
- 仅支持 `METHOD=AES-128`。
- `SAMPLE-AES` 等其他加密方法返回“不支持”错误。
- `auth_key` 过期需重新获取链接。

## 6. 结论
- `bluegay/xgaymv` 这类 kstore hash 链接在当前版本已具备“可解析 + 可下载”能力（满足 AES-128 场景）。
