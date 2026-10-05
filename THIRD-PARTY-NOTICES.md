# Third-party notices

Soundstage's jar carries the libraries below. There is one jar for each loader, NeoForge, Forge (Minecraft
1.20.1) and Fabric, and they carry the same libraries, but for MixinExtras, which only the Forge jar carries.
Its own classes are at the top level; each library is a separate jar nested unmodified, under
`META-INF/jarjar/` in the NeoForge and Forge jars and `META-INF/jars/` in the Fabric one,
which the loader loads as its own, so a library several mods carry is loaded once. The nested jars are listed by their Maven coordinates, which are
also how the loader tells them apart. Because these libraries are redistributed with this mod, their
licence terms apply and are reproduced here and inside the jars themselves.

Soundstage itself is under the Apache License 2.0 (see `LICENSE` and `NOTICE`). Licence texts are in
`licenses/`, and are shipped in the jar alongside this file under `META-INF/soundstage/`.

## Nested jars

| Nested jar | What it is | Licence material inside it |
|---|---|---|
| `dev.armadeus:armalib-audio-1.20.1-forge`, `dev.armadeus:armalib-audio-1.20.1-fabric`, `dev.armadeus:armalib-audio-1.21.1-neoforge`, `dev.armadeus:armalib-audio-1.21.1-fabric`, `dev.armadeus:armalib-audio-1.21.11-neoforge`, `dev.armadeus:armalib-audio-1.21.11-fabric`, `dev.armadeus:armalib-audio-26.1-neoforge`, `dev.armadeus:armalib-audio-26.1-fabric`, `dev.armadeus:armalib-audio-26.2-neoforge`, `dev.armadeus:armalib-audio-26.2-fabric`, `dev.armadeus:armalib-audio-26.3-neoforge`, `dev.armadeus:armalib-audio-26.3-fabric` (the node's own) | ArmaLib's audio mod: the sound instance Soundstage's speakers stream through; nests `dev.armadeus:armalib-lavaplayer`, ArmaLib's wrap of LavaPlayer and its dependencies, the first table below | Apache-2.0 (ArmaLib's own licence); carries `META-INF/LICENSE`. The LavaPlayer wrap inside it has one directory per wrapped library under `META-INF/armalib/licenses/`, listed in its `INDEX.txt` |
| `dev.armadeus:armalib-lavaplayer-youtube` | ArmaLib's wrap of youtube-source and nanojson, the second table below | the same, under `META-INF/armalib/licenses/` |
| `org.jcodec:jcodec` | JCodec 0.2.5, unmodified, with the JAAD it vendors | as published; the texts are also in `licenses/` here |
| `org.tukaani:xz` | XZ for Java 1.12, unmodified | as published; the text is also in `licenses/` here |
| `io.github.llamalad7:mixinextras-forge` (the Forge jar only) | MixinExtras 0.5.5, unmodified: Fabric Loader and NeoForge ship it, Forge 1.20.1 does not, and Soundstage's mixins use it | MIT, carries `LICENSE_MixinExtras`; the text is also in `licenses/` here |
| `dev.armadeus:armalib-config-1.20.1-forge`, `dev.armadeus:armalib-config-1.20.1-fabric`, `dev.armadeus:armalib-config-1.21.1-neoforge`, `dev.armadeus:armalib-config-1.21.1-fabric`, `dev.armadeus:armalib-config-1.21.11-neoforge`, `dev.armadeus:armalib-config-1.21.11-fabric`, `dev.armadeus:armalib-config-26.1-neoforge`, `dev.armadeus:armalib-config-26.1-fabric`, `dev.armadeus:armalib-config-26.2-neoforge`, `dev.armadeus:armalib-config-26.2-fabric`, `dev.armadeus:armalib-config-26.3-neoforge`, `dev.armadeus:armalib-config-26.3-fabric` (the node's own) | ArmaLib's settings mod (Soundstage's settings files and their sync), itself nesting the jars in the table below | Apache-2.0 (ArmaLib's own licence); carries `META-INF/LICENSE` |

### Inside `dev.armadeus:armalib-config-<node>`

Each is part of ArmaLib, nested as ArmaLib publishes it for the same loader, and is Apache-2.0.

| Nested jar | What it is |
|---|---|
| `dev.armadeus:armalib-ui-<node>` | ArmaLib's screen toolkit, which the config screen is drawn with; nests the platform jar |
| `dev.armadeus:armalib-platform-<node>` | ArmaLib's loader layer, which Soundstage's registries, networking and events use directly; nests the core jar |
| `dev.armadeus:armalib-core` | ArmaLib's loader-independent classes, the `Setting`/`ConfigSpec` model among them |
| `dev.armadeus:aon` | AON (0.0.6), the configuration file format library, which the settings files are written in; Apache-2.0, carrying `META-INF/LICENSE` |

ArmaLib's wraps and parts are built and published by ArmaLib (https://repo.armadeus.dev), Soundstage's
companion library, licensed under the Apache License 2.0; it carries no `NOTICE` file. AON
(https://repo.armadeus.dev), nested here through ArmaLib's config part, is likewise Apache License 2.0. Inside a wrap,
LavaPlayer's and youtube-source's own packages keep their names; the dependencies that would clash
with the game's or another mod's copies are relocated under `dev.armadeus.armalib.lavaplayer.shadow`
(and nanojson under `dev.armadeus.armalib.lavaplayer_youtube.shadow`). Rhino's service descriptor is
carried with its relocated names, and LavaPlayer's JNI declaration packages keep their original names,
because the native connector's exported symbols are compiled from them.

### Inside `dev.armadeus:armalib-lavaplayer` (nested in `dev.armadeus:armalib-audio-<node>`)

| Library | Version | Licence | Upstream |
|---|---|---|---|
| LavaPlayer / lava-common | `dev.arbjerg:lavaplayer:2.2.7`, `dev.arbjerg:lava-common:2.2.7` | Apache-2.0 | https://github.com/lavalink-devs/lavaplayer |
| LavaPlayer native bundle | `dev.arbjerg:lavaplayer-natives:2.2.7` (`connector`, `libmpg123-0`); Soundstage's own Opus codec uses `connector` too | `connector` is built from libsamplerate 0.1.9 (BSD-2-Clause), FDK-AAC 2.0.0 (Fraunhofer license), libvorbis 1.3.6 and libogg 1.3.3 (BSD-style), and Opus 1.3 (BSD-style); mpg123 1.25.10 (LGPL-2.1) is linked into `connector` on Unix and shipped as `libmpg123-0.dll` on Windows | https://github.com/lavalink-devs/lavaplayer/tree/2.2.7/natives |
| Apache HttpClient | `org.apache.httpcomponents:httpclient:4.5.14` | Apache-2.0 | https://hc.apache.org/ |
| Apache HttpCore | `org.apache.httpcomponents:httpcore:4.4.16` | Apache-2.0 | https://hc.apache.org/ |
| Apache Commons Logging | `commons-logging:commons-logging:1.2` | Apache-2.0 | https://commons.apache.org/proper/commons-logging/ |
| Apache Commons Codec | `commons-codec:commons-codec:1.11` | Apache-2.0 | https://commons.apache.org/proper/commons-codec/ |
| Jackson (databind, core, annotations) | `com.fasterxml.jackson.core:*:2.15.2` | Apache-2.0 | https://github.com/FasterXML/jackson |
| Rhino and Rhino engine | `org.mozilla:rhino:1.7.15`, `rhino-engine:1.7.15` | MPL-2.0; V8 and Sun portions retain their BSD-style notices | https://github.com/mozilla/rhino/tree/Rhino1_7_15 |
| jsoup | `org.jsoup:jsoup:1.16.1` | MIT — Copyright (c) 2009–2023 Jonathan Hedley | https://jsoup.org/ |
| JSON-java | `org.json:json:20240303` | Public domain | https://github.com/stleary/JSON-java |
| iharder Base64 | `net.iharder:base64:2.3.9` | Public domain — Robert Harder | https://github.com/omalley/base64 |

### Inside `dev.armadeus:armalib-lavaplayer-youtube`

| Library | Version | Licence | Upstream |
|---|---|---|---|
| youtube-source (`v2`, `common`) | `dev.lavalink.youtube:2be8e542d3f6f178e048dca565892684c2e40177-SNAPSHOT` | MIT — Copyright (c) 2024 devoxin | https://github.com/lavalink-devs/youtube-source |
| nanojson | `com.grack:nanojson:1.7` | Apache-2.0 | https://github.com/mmastrac/nanojson |

### Plain nested jars, and what this jar itself carries

| Library | Version | Licence | Upstream |
|---|---|---|---|
| JCodec | `org.jcodec:jcodec:0.2.5` | BSD-2-Clause (FreeBSD) — Copyright 2008-2017 JCodecProject | https://github.com/jcodec/jcodec |
| JAAD (inside the JCodec jar, `net.sourceforge.jaad`) | vendored by JCodec 0.2.5, no independent version | Public domain | https://github.com/DV8FromTheWorld/JAADec |
| XZ for Java | `org.tukaani:xz:1.12` | 0BSD — Copyright (C) The XZ for Java authors and contributors; `licenses/0BSD-xz-java.txt` | https://tukaani.org/xz/java.html |
| Noto Sans Medium (`assets/soundstage/font/noto_sans_medium.ttf`, the subtitle font, in this jar) | 2.015 | SIL OFL 1.1 — Copyright 2022 The Noto Project Authors; `licenses/OFL-1.1-noto-sans.txt` | https://github.com/notofonts/latin-greek-cyrillic |

### Relocation is a modification

ArmaLib's wraps rewrite the package names of the relocated libraries inside their class files. No
library source code is otherwise modified. The relocation rules are recorded in ArmaLib's build
(`lavaplayer/` and `lavaplayer-youtube/`).

### NOTICE files

The upstream jars carry notices for Apache HttpClient, HttpCore, Commons Logging, Commons Codec,
Jackson, and Rhino. ArmaLib's wraps carry each of them in their licence directories, and their contents
are also reproduced here in `licenses/NOTICE-*.txt`, shipped under `META-INF/soundstage/`. Rhino's
notice also records its included V8 and Sun Microsystems portions.

The native license texts used by the LavaPlayer 2.2.7 native build are included in
`licenses/BSD-2-Clause-libsamplerate.txt`, `licenses/NOTICE-fdk-aac.txt`,
`licenses/BSD-3-Clause-ogg.txt`, `licenses/BSD-3-Clause-vorbis.txt`,
`licenses/BSD-3-Clause-opus-1.3.txt`, and `licenses/LGPL-2.1-mpg123.txt`, and inside
`dev.armadeus:armalib-lavaplayer` under `META-INF/armalib/licenses/dev.arbjerg_lavaplayer-natives/`.
The component source paths in the pinned build are `natives/samplerate`, `natives/fdk-aac`,
`natives/vorbis`, and `natives/connector` in
https://github.com/lavalink-devs/lavaplayer/tree/2.2.7, which records the source versions and
download origins.

## Not bundled

These are external runtime or compile-time dependencies and are not present in the Soundstage jar.
They are listed so the resolved dependency set is clear.

| Library | Why it is not bundled |
|---|---|
| NeoForge, Minecraft; on Fabric, Fabric Loader and Fabric API | Provided by the environment the mod runs in; a Fabric player installs Fabric API, as for every Fabric mod. |
| SLF4J, Commons IO | Shipped by both client and server; left out of every nested jar and not relocated, so references resolve to the game's copies. |

## Downloaded by a server that asks, never bundled

| Program | Licence | Where it comes from |
|---|---|---|
| ffmpeg and ffprobe, BtbN's GPL shared build `n9.0.1-11-ge47273f4d9` | GPL-3.0-or-later as built (it includes libx264 and other GPL libraries); the build's own `LICENSE.txt` is kept beside it | https://github.com/BtbN/FFmpeg-Builds, release `autobuild-2026-08-31-13-27` |

Soundstage does not contain or redistribute ffmpeg. A server whose owner turns on `downloadFfmpeg`
fetches the archive from BtbN's GitHub releases itself, checks it against the SHA-256 written into
`core.ffmpeg.FfmpegBuild`, and runs it as a separate program. The corresponding source is FFmpeg's
own and BtbN's build scripts, both public at the addresses above.
