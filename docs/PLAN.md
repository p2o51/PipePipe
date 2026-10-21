# PipePaw 实施计划

## 阶段 0 · 骨架（这个分支）

- [x] 仓库：根目录是 PipePaw 的 Gradle 工程；`includeBuild("PipePipeExtractor")`；PipePipeClient 只读参考；上游 README 留作 `README.upstream.md`。
- [x] Paw 套件从 PiliPaw（GitHub main，0.4.20）拷入，包名改成 `com.gpo.pipepaw`。
- [x] Extractor 接入：`PawDownloader`（DownloaderImpl 的 Kotlin 版）、`Yt` 门面、未登录用 `visionos` 客户端。
- [x] 壳：首页（订阅的最新上传）、订阅、我的（设置），浮动底栏，seam 溶解。
- [x] 搜索（联想词）→ 结果（视频、频道）→ 频道页（封面取色、订阅、上传网格）。
- [x] 播放器覆盖层：迷你条 ⇄ 全屏一条进度，拖动、预测性返回；控件在画面下方；详情、简介、相关视频；平板双栏。
- [x] 播放：每条流生成单流 DASH 清单，视频 + 音频合并；googlevideo 的 `&range=` / `&rn=`。

## 阶段 1 · 在 Mac 上跑通

- [x] 编译（AGP 9.2.1；Extractor 用 JDK 25 toolchain，app 本身 Java 17）。app 留在 17，Gradle 的变体匹配没有拒绝。根 settings 里的 foojay 管不到 includeBuild，所以机器上要有 JDK 25：在 `~/.gradle/gradle.properties` 里设 `org.gradle.java.installations.paths=<JDK 25 home>`。
- [x] 真机：搜索 → 播放 → 收起 → 进频道 → 订阅 → 首页，平板竖横屏各走一遍（录屏见 artifact）。
- [x] 确认未登录播放能拿到地址（签名和 n 参数现在走 PipePipe 的远程解码 API，`api.pipepipe.dev`）。
- [x] 把 Paw 套件对齐到 Mac 上 PiliPaw 0.4.31：主题、seam、SettingsKit 和大部分组件本来就一致；补了返回手势按窗口宽度计行程、底栏长按（长按首页开搜索）、CrashLog（我的 › 关于 › 上次崩溃）。没搬的都是 B 站功能：卡片预览、单列首页、底栏分区隐藏、返回时画面飞回卡片（PipePaw 的播放器是覆盖层，不经过页面返回）。
- [ ] 发布：要不要沿用 PiliPaw 的规矩（Notion 更新日志 + Drive 上 debug 签名的 release 包）？

## 阶段 2 · 播放内核（从 PipePipeClient 移植成 Kotlin + Media3）

- [x] `YoutubeHttpDataSource` 的 Kotlin 版（`YoutubeDataSource`，OkHttp + ResolvingDataSource）：`&range=` / `&rn=`、按客户端换 UA（Android / iOS）、移动端用 POST、WEB / TV 内嵌带 Origin / Referer。403 等 HTTP 和网络错误时重新解析、从原位置接着放（每个视频两次），和 PipePipe 的 PlayerErrorHandler 一样；用强制失败的请求在真机上验证过。
- [ ] 本地 WebView JS 解码（`WebViewJavaScriptDecoder`），不再依赖远程解码 API。
- [ ] PO token（`LocalDomPoTokenProvider`、`SharedWebViewRuntime`）。
- [ ] SABR（`player/datasource/Sabr*`，约 2000 行）：登录后的 mweb 客户端需要。
- [x] MediaSession + 前台服务：后台播放、通知、蓝牙按键（±10 秒），点通知回到展开的播放器；设置 › 播放 › 后台播放，关掉时离开即暂停、回来接着放。
- [ ] 全屏和画中画：搬 PiliPaw 的 `FullscreenBack`、`DoubleTapSeek`、`HoldSpeed`、`SeekBar` / `SeekPreview`（YouTube 的 storyboard = `previewFrames`）、`Subtitles`、画质和倍速面板。
- [ ] 性能：覆盖层的插值从组合期挪到 layout / draw 期。

## 阶段 3 · 内容

- [ ] 评论（底部面板 / 平板右栏）、章节、SponsorBlock、ReturnYouTubeDislike。
- [ ] 段落进度条（设计主张 §4 B）。
- [ ] 历史、稍后再看、播放列表（本机）。
- [ ] 自建 Feed、静音规则、搜索筛选。
- [ ] 听 YouTube（设计主张 §4 C）。

## 阶段 4 · 可选

- [ ] 登录（WebView cookie → mweb + SABR）；debug 包写操作空跑。
- [ ] 品牌：图标重画、启动动画、Glance 小部件、桌面快捷方式。

## 讨论稿里的问题，现在怎么处理

1. 走 A 路线吗：**是**（只用 Extractor，客户端当参考）。
2. 播放器形态：**按推荐做成壳里的覆盖层**，如果你更想要 PiliPaw 的卡片放大成页面，改的是 `feature/player` 一处。
3. 朋友最夸的点：还没答，不阻塞。
4. Paw 套件抽库：**先拷贝**，第三个 Paw 跑起来后再看要不要抽。
5. Skill 用在哪：不影响这里。

## 风险

- YouTube 对未登录客户端的限制变化很快；`visionos` 失效时要能切 `tv_samsung` / `mweb`，PipePipe 每两周的更新是主要来源，升级只要更新子模块。
- 远程解码 API 是第三方服务（PipePipe 作者的），阶段 2 要换成本地解码。
