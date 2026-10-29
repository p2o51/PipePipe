# PipePaw

A YouTube client in the Paw family (PiliPaw, WeiPaw): Yoin-quality thinking and design brought
to another app. Personal project, never published to any store.

- **Upstream**: [PipePipe](https://github.com/InfinityLoop1308/PipePipe) by InfinityLoop1308, GPL-3.0.
  Only its extractor is linked (`PipePipeExtractor`, a composite build of the submodule).
  `PipePipeClient` is a read-only reference for APIs and features; its UI is not used.
  PipePipe's own README is kept as [README.upstream.md](README.upstream.md).
- **UI**: Jetpack Compose, rewritten by Yoin's rules (p2o51/Yoin), with the Paw kit copied
  from PiliPaw (theme, seam dissolve, page transitions, FameCount).
- **Icons**: [Yoin Symbols](https://github.com/p2o51/yoin-symbols) (MIT), every icon. Gradle builds it
  from source from a checkout at `../yoin-symbols` and stops without it.
- **Docs**: [设计主张](docs/design.md) · [AGENT_BRIEF](docs/AGENT_BRIEF.md) · [计划](docs/PLAN.md)

## Build

```sh
git submodule update --init PipePipeExtractor PipePipeClient
git clone https://github.com/p2o51/yoin-symbols ../yoin-symbols   # icons; `git -C ../yoin-symbols pull` for updates
./gradlew :app:assembleDebug
```

Needs a JDK 25 toolchain for the extractor (Gradle's foojay resolver provisions one when the
machine has none) and Android SDK 36.

## License

GPL-3.0, as upstream (see [LICENSE](LICENSE)).
