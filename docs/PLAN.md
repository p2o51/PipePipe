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
- [x] 本地 WebView JS 解码（`core/extractor/decoder`：`WebViewRuntime` + `WebViewJsDecoder`，yt-dlp 的 EJS 解码器放在 `assets/ejs`，Unlicense）。启动时在后台预热（约 3 秒）；Extractor 先问本地，失败就整个进程退回 PipePipe 的远程 API。`visionos` 拿到的是直链，平时用不上；切到 `tv_samsung` 实测 1 个 n、26 个签名 15ms 解完并正常播放。
- [ ] PO token（`LocalDomPoTokenProvider`、`SharedWebViewRuntime` 的 SABR 桥）：BotGuard 在 WebView 里跑出 integrity token，再给每个视频铸 PO token；约 850 行（`LocalDomPoTokenProvider` 576 行 + `sabr_po_token.js`），外加 `WebViewRuntime` 加回调桥。只有 `mweb`（登录）和 web 系客户端需要；未登录的 `visionos` 不需要。等登录或 `visionos` 失效再做。
- [ ] SABR（`player/datasource/Sabr*` 和 `youtube/SabrRequestCoordinator`，播放部分约 1600 行，含 UMP 协议解析和自己的 DataSource / MediaSource）：登录后的 mweb 客户端只给 SABR 流，没有直链。依赖 PO token，放在登录之后。
- [x] MediaSession + 前台服务：后台播放、通知、蓝牙按键（±10 秒），点通知回到展开的播放器；设置 › 播放 › 后台播放，关掉时离开即暂停、回来接着放。
- [x] 全屏：画面缩成圆角卡片、控件在让出的带子里（`Fullscreen.kt`，`PlayerWindow.kt` 照搬 PiliPaw：沉浸、旋转、平板软件转向）；平板竖横屏验证。
- [x] 手势：`DoubleTapSeek`（左右三分之一双击 ±10 秒，连点累加；中间双击暂停）、`HoldSpeed`（按住 2 倍，上下滑换档），展开的播放器和全屏都有；照搬 PiliPaw。
- [x] `SeekBar` / `SeekPreview`：细轨道按下变粗、长出胶囊滑块；拖动时拇指上方出 storyboard 帧和时间（`previewFrames`，按 160px 选一档，前后各预载一张），展开的播放器和全屏共用；真机核对过帧和落点一致。
- [x] 字幕（默认关；YouTube 的 srv1 文本，自动字幕也干净；画面底部一行，深色底板）、画质（按高度重新解析，接着放）和倍速（换视频保留）。一个「播放设置」面板放三组按钮，比 PiliPaw 分页轻；全屏底栏有字幕、倍速、画质三个入口。
- [x] 画中画：全屏顶栏的按钮进入；设置 › 播放 › 自动画中画（默认关，开了之后展开且在播放时回桌面自动进入，Android 12+）；小窗里只有画面。
- [x] 全屏的预测性返回：返回手势跟手，整个全屏画面缩小、变圆角，露出下面展开的播放器，控件淡出；松手退出，取消弹回。比 PiliPaw 的 `FullscreenBack`（画面飞回原位）轻。
- 冷启动带链接偶尔打开上一个视频：查明是重装 App 后 Pixel 用任务原来的链接重启任务（`killDueToPackageUpdate` → SystemUI 再发一次旧的 VIEW），平时冷启动不会；不改代码。
- [ ] 性能：覆盖层的插值从组合期挪到 layout / draw 期。

## 阶段 2.5 · 登录（owner 2026-10-21 提前）

- [x] 账号模型（`core/account`）：每个来源一个 `Profile`，未登录是完整可用的一档（本地订阅、visionos 播放），登录存 cookies、名字、handle、头像，存在应用私有的 SharedPreferences。`Source` 现在只有 YouTube；和 PiliPaw 合成一个 App 时加 Bilibili，账号面板按来源列。
- [x] 入口：首页左上角的头像圈（未登录是人形，登录后是头像）→ 账号面板（底部面板）：未登录一行说明登录能多什么 + 「登录」，不弹窗不打断；「我的」顶上一行更安静的同款。登录页是 WebView 里 Google 自己的页面，凭据只在那个页面里输入。
- [x] cookies 接进 Extractor（`ServiceList.YouTube.setTokens`，SAPISIDHASH 由 Extractor 自己算）；`account/account_menu` 取名字、@handle、头像（启动时刷新）；退出清 cookies 和 WebView 存储（owner 2026-10-21 真机退出：cookies 表清零、IndexedDB 空，再登录时 Google 从头要账号）。owner 2026-10-21 真机登录成功；登录后 visionos 播放照常。
- [ ] 登录后的播放：mweb + PO token + SABR；失败退回 visionos。实测登录后 visionos 照常能播（cookies 随播放器请求一起发），所以先不移植，等 visionos 失效或遇到只有 mweb 能播的视频（会员）再做。
- [x] 登录后的内容（`YtFeeds`，youtubei browse + Extractor 自己的 renderer 解析）：首页「订阅 / 推荐」两个流，可翻页；订阅页是账号的频道，末尾「本机」列出账号还没订的本地订阅，可「导入」；「我的」里稍后再看、赞过的视频；所有订阅按钮登录后看账号的状态（频道页用规范的 /channel/UC… 地址比对）。
- [x] debug 包里写操作空跑（`AccountWrites`）：订阅、取消订阅、导入都只记日志、弹「调试版，未发送：…」，真机上确认没有发出请求。点赞、加入播放列表还没有入口，加的时候同样走它。

## 阶段 3 · 内容

- [x] 评论（`feature/comments`）：手机是详情下「评论」一行 → 底部面板，平板双栏右栏「相关 / 评论」切换；热门 / 最新（最新取评论头里第二个排序的 continuation，再交给 Extractor 翻页）；置顶、UP 主点心标签；回复就地展开、可翻页；长评论六行折叠「展开」；评论里的「1:23」点了跳转。
- [x] 章节：详情里一排章节卡（缩略图、时间、标题，正在播的高亮），点了跳转；拖动预览时显示当前章节名。
- [x] 段落进度条（§4 B）：章节是轨道上的缝，SponsorBlock 片段是 tertiary 色块，自动跳过时那一段在条上合拢。
- [x] SponsorBlock（Extractor 自带的接口）：赞助、自我推广、互动提醒默认自动跳过，片头片尾只标出；跳过时一行「已跳过赞助」+「撤销」（回到片段开头接着放，同一视频不再跳）；设置里总开关和每类的「跳过」开关。
- [x] Return YouTube Dislike：详情里「踩」的估计数，跟名声模式走。
- [x] 历史、稍后再看、播放列表（本机，`feature/library`）：每项的 key 带来源前缀（`yt:<id>`），合并后 Bilibili 用 `bili:<bvid>` 进同一套列表；历史自动记录，每 5 秒和暂停时存进度，下次打开从原处接着放（看完的从头）；行上有进度线，长按移除，可清空；详情里「稍后再看」「播放列表」两个按钮，登录后稍后再看是账号的（debug 空跑）；「我的 › 本机」三个入口。
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
