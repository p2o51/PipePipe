# PipePaw — brief for feature agents

We are building **PipePaw**, a YouTube client, in the **Yoin design language** (the owner's
music app: `~/Developer/Yoin`, read-only, design doc `docs/design.md`; in the cloud: p2o51/Yoin).
It is the third Paw after PiliPaw (`~/Developer/pilipala`, p2o51/pilipala-compose) and WeiPaw.
`docs/design.md` in this repo (设计主张) is the source of truth for UI decisions. Speed > polish,
but never ship motion that is wrong: a still screen beats a broken animation. UI language is Chinese.

## Upstream (hard rule)
- `PipePipeExtractor/` (submodule) is the only upstream code the app links (composite build,
  `implementation("com.github.InfinityLoop1308.PipePipeExtractor:extractor")`). Never edit it.
- `PipePipeClient/` (submodule) is a **read-only reference** for how to call the extractor and for
  the feature list (SponsorBlock, RYD, SABR, PO tokens, login…). Port logic from it into Kotlin
  when needed (credit it in a comment). Never copy its UI, layouts, icons or animations.

## Build
- JDK 25 toolchain for the extractor (foojay provisions it), Android SDK 36.
- `./gradlew :app:compileDebugKotlin --console=plain -q` must pass before you finish.
- Do NOT touch any device / adb / emulator unless your task says so (one shared tablet).

## Ownership
- Only create/edit files inside YOUR feature package(s) listed in your task.
- Read-only for features: `core/**`, `navigation/**`, `ui/**`, other `feature/**`, gradle files.
- Need a setting? `val K = AppSettings.register("myfeature_x", default)` in your package.

## Architecture (read the files)
- `core/extractor/Yt.kt` — the only door to the extractor: `stream(url)`, `search(q, cursor)`,
  `suggestions(q)`, `channel(url)`, `channelFirstPage(info)`, `channelVideos(url, cursor)`,
  `latest(channelUrl)` (RSS), `comments(url, cursor)`. Suspend, on IO. Cursors are opaque strings.
- `core/extractor/Models.kt` — `VideoItem`, `ChannelItem`, `ListItem`; unknown numbers are < 0
  and draw as nothing.
- `core/extractor/PawDownloader.kt` — the extractor's OkHttp downloader (port of DownloaderImpl).
- `feature/player/Player.kt` — the single ExoPlayer and the current `Session`.
  `Player.play(url, preview)` from anywhere; `Player.expanded` drives the overlay.
- `feature/player/PlayerOverlay.kt` — the shell overlay (mini ⇄ full, one progress, predictive
  back class ShellOverlayDown). Mounted last in `AppNavHost`, so its back handler outranks pages.
  Pages pad their bottom with `miniPlayerInset()`; shell tabs get it inside `LocalBottomBarInset`.
- `feature/player/StreamResolver.kt` — StreamInfo → Media3 source (generated DASH per stream,
  merged video + audio; `&range=` / `&rn=` rewriting for googlevideo).
- `feature/subscriptions/Subscriptions.kt` — local subscriptions (`channels: StateFlow`).
- `navigation/Routes.kt`, `Navigator.kt` — `LocalNavigator.current.push(Route.Channel(url))`,
  `.openUrl(url)` (YouTube links open natively, others in a Custom Tab).
- Paw kit (copied from PiliPaw, keep in sync by hand): `ui/theme` (`PawTheme`, `CoverTheme`,
  `YoinMotion`, shapes, type), `ui/components` (`NetImage`, `Avatar`, `Pager`/`LoadMoreEffect`,
  `RefreshBox`, states, `FameCount`, `PawTopBar`, `ExpressivePageBackground`, seams),
  `navigation/transition` (in-window AOSP back replica), `feature/settings/SettingsKit.kt`.
- Icons: `com.gpo.yoin.symbols.YoinSymbols.*` only, from the Yoin Symbols library (sibling
  `../yoin-symbols` via `includeBuild`, else Maven Central; the gallery lists every symbol). No
  material-icons. Never draw an ImageVector in the app or copy symbol files: a missing glyph is
  added to `../yoin-symbols` first (see AGENTS.md).

## Yoin rules (the point of the project)
Same twelve rules as PiliPaw's `docs/AGENT_BRIEF.md` (M3 Expressive components first, springs
only, M3 tokens only, color blocks not strokes, typography from the theme, labels not narration,
no "·" joins, bare covers, edge-to-edge, adaptive from day one, native Android over custom,
full-bleed horizontal rows). PipePaw adds:
1. **Content stays pure.** Nothing sits on the video picture. Controls live under it.
2. **The player is an overlay, never a route.** Don't add a video page; open videos with `Player.play`.
3. **Every page states its Compact / Medium / Expanded form before it is built** (see design.md §7).

## Finish
- `./gradlew :app:compileDebugKotlin` passes.
- Commit in your worktree with `<feature>: …`; no pushes to other branches.
