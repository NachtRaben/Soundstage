# Soundstage

Soundstage adds speakers, an A/V receiver and screens to Minecraft. You link speakers to a receiver,
give it something to play, and everyone standing near those speakers hears it, in time, from where
the speakers actually are. Link a wall of screens as well and the receiver shows the picture too.

It runs on Minecraft 1.20.1 (Forge and Fabric) and on 1.21.1, 1.21.11, 26.1, 26.2 and 26.3 (NeoForge and
Fabric). NeoForge doesn't have a stable 26.3 build yet, so that version is built on the NeoForge beta. On
Fabric it needs Fabric API, as most Fabric mods do; everything else it uses is bundled. Shader packs (Iris,
or Oculus on Forge), Sodium and Embeddium all work with it, and none of them is required. Install it on the
server and on every player's game.

## Getting started

1. Craft an **A/V Receiver**, a few speakers and a **Speaker Linker**. A recipe viewer such as JEI or
   EMI shows the recipes.
2. Place the speakers where you want the sound to come from.
3. Take the Speaker Linker, click the receiver, then click each speaker. Sneak and click a speaker
   with the linker to unlink it again.
4. Open the receiver, paste a link or a search such as `ytsearch:your song`, and press play.

A **Party Box** is a receiver and speakers in one block, for when you just want music somewhere.

## Speakers

There are five: the Speaker, the Satellite Speaker, the Center Speaker, the Subwoofer and the Party
Box. Each speaker has its own settings screen (use it with an empty hand):

- where it aims, and how far it can be heard, from 8 to 128 blocks;
- its own volume, as a trim on top of the receiver's volume;
- a delay of up to 500 ms, for lining up speakers that are far apart.

Speakers under one receiver stay in step with each other, so a large build doesn't turn into an
echo. The receiver's Speakers tab sets which channel each one plays: mono, left, right, centre, the
surround pair or the subwoofer's LFE. That is enough for stereo or a full 5.1 room. Sneaking and
right-clicking a speaker with an empty hand also steps through its channels.

Every speaker plays from where it actually stands. With headphones on, turn on **Directional Audio**
(Options, then Music & Sounds) and the left speaker is on your left and the surrounds are behind you.

You can dress a cabinet in your own materials. Right-click a speaker or the receiver with a log,
planks, terracotta, concrete or glass to change its casing, or a speaker with wool to change its
grille. Shears take the grille off. A glass casing shows the driver moving inside. Broken and
picked speakers keep their finish and their settings.

The drivers move with the music being played, not a canned animation, and the receiver's meters,
knob, keys and glowing valves show what it is actually doing. Parrots and allays dance to a playing
speaker, and sculk sensors hear it the way they hear a jukebox.

## The receiver

The receiver's screen has five tabs:

- **Playing**: what's on, the controls, volume, loop and shuffle.
- **Queue**: reorder, remove, or play from any point. Shuffle never loses the order you made.
- **Displays**: the screens linked to it, and the Video switch.
- **Speakers**: the linked speakers and the channel each one plays.
- **Settings**: levelling, the lock, and sharing your own audio.

Volume goes up to 200%. Films are mixed much quieter than music, and past 100% a limiter turns the
loudest moments down rather than letting them distort. New receivers start at 50%.

**Levelling** brings quiet dialogue up and holds loud scenes down. It's meant for films and changes
music too, which is why it's a switch and starts off.

**Video**, on the Displays tab, turns a receiver's picture off when you only want the sound of
something; its screens then show a now-playing card instead.

**Lock** stops anyone but you (and server operators) from changing a receiver, its speakers or its
screens. Whoever places a receiver or party box owns it.

## What it can play

- **YouTube** links, or a search such as `ytsearch:Me at the zoo`; `ytmsearch:` searches YouTube Music.
- **SoundCloud** links, or a search with `scsearch:`.
- **Bandcamp** links.
- **Vimeo** links.
- **Twitch** channels, live, including channels using Twitch's enhanced broadcasting.
- **Files from the server's media folder**, with `media:<file name>`, or the **Browse** button.
- **Direct links** to audio and video files, if the server allows them (off by default).
- **Your own computer's audio**, through **Share** (see below).

## Screens

Place screen blocks side by side on a wall, all facing the same way, and they work as one display, up
to 16×16 blocks. Link a display to a receiver with the Speaker Linker, the same way as a speaker: click
the receiver, then any screen of the display.

YouTube videos, Twitch streams, films from the media folder and linked video files show their picture
while a display is linked; YouTube live streams are the exception and play sound only. Songs, and
anything played with Video switched off, get a now-playing card instead, with the artwork, a spectrum
and the time.

Right-click a screen with an empty hand for its menu. There you can turn subtitles on or off and pick
their size (that's just for you), choose which subtitle track plays, choose **Fit** or **Fill** for the
whole display, or unlink it. The receiver's Displays tab has the same choices.

A few details:

- Films show their own text subtitles, and YouTube videos show their captions. Subtitles start off
  unless the film marks one track as its default or has one in the game language of whoever last used
  the receiver. Otherwise, pick a track (or Off) from the dropdown; each track remembers your choice.
  YouTube's auto-generated captions only show if you pick them.
- HDR films are tone-mapped so they don't look grey and washed out.
- A film's 5.1 track plays through a 5.1 room: link a Center Speaker or a pair of surround speakers
  and each channel goes to its own speaker. Without them you get the film's stereo mix.
- The picture is made to suit the display, 120 lines per block of height, up to the server's limit
  (480 lines by default). Every player decodes the picture on their own computer, so taller costs
  everyone more.
- A screen nobody is looking at doesn't decode anything. The sound carries on regardless.

Pictures need ffmpeg on the server, except for Twitch, which is passed through as it comes. Without
ffmpeg, YouTube and most files still play, sound only.

## Sharing your own audio

A receiver or party box can play what your own computer is playing: a music app, a local file,
anything without a link. Open **Share** from the Settings tab, pick an input device and start.
Everyone near the receiver hears it through its speakers, and whatever the receiver was playing before
carries on from where it was when you stop.

The game can only read an input device, so the app has to be sent into one:

- **Linux (PipeWire or PulseAudio):** press **Create output** on the share screen. It adds a virtual
  output called Soundstage and picks it. Move the app to Soundstage in your sound settings and start
  sharing. The output is removed when the game closes. If an effects app such as EasyEffects grabs
  every stream, exclude the app there, or it may be moved back to your speakers.
- **Windows:** install a virtual cable such as VB-CABLE, set the app's output to *CABLE Input*
  (Settings → Sound → Volume mixer), and pick *CABLE Output* in Soundstage.
- **macOS:** see [Platforms](#platforms).

The share screen shows the device's level before you share anything, so you can see the app is
reaching it. A share ends when you stop it, when you leave, or after ten seconds of nothing arriving.

## For server owners

Settings live in `config/soundstage.aon` (each player's own display settings in
`config/soundstage-client.aon`). In singleplayer that's the game's own config folder.
Most of them can be changed while the server runs; the list of enabled sources is read at start-up.

The defaults are meant to be safe for a server with players you don't know. The following are things
you accept by turning them on (or by leaving them on), so read these before changing them:

- `enableHttp` (off): lets players play any direct http(s) link. The server fetches whatever URL a
  player types, so it can be used to poke at addresses only your server can reach. Even when it's
  on, private and local addresses are refused unless `allowPrivateNetworkHosts` is also on.
- `allowPrivateNetworkHosts` (off): lets operators, and only operators, play links on your own
  network (a NAS, another PC). Everyone else is still refused, through redirects and playlists too.
- `enableMediaFolder` (on): lets players play files you put in the `soundstage-media` folder in the
  server's directory. Anyone on the server can play anything in that folder and see its file names,
  so only put things there you're happy for all of them to use. Players can only name a file
  directly in that folder: no paths, no subfolders, no hidden files.
- `enableLiveInput` (on): lets players share audio from their own computers through receivers.
- `downloadFfmpeg` (off): lets a server with no ffmpeg download one (see below).
- YouTube sign-in: `/soundstage oauth` (operators only) links the server to a Google account when
  YouTube starts asking servers to sign in. Use a spare account, not your own. The token is saved in
  the config file, so keep that file private.

Other useful settings: per-source switches (`enableYoutube`, `enableSoundCloud` and so on), URL allow
and deny lists, queue and playlist limits, `maxPlayingReceivers` (how many receivers may play at
once, 16 by default), `enableVideo` and `maxVideoHeight`.

### ffmpeg

Pictures for YouTube, films and video links, HDR tone mapping, subtitles and 5.1 all need ffmpeg on
the server. Soundstage looks for `ffmpeg` on the PATH, or wherever `ffmpegPath` points.

If the server has none, you can set `downloadFfmpeg = true`. Soundstage then downloads a build of
ffmpeg from [BtbN's FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds) on GitHub (about 60 MB for
Linux, 76 MB for Windows, 180 MB unpacked) into a `soundstage-ffmpeg` folder, checks it against the
SHA-256 hash built into this version of Soundstage, runs a short test with it, and only then uses it.
A download that doesn't match is thrown away. It is only used while `ffmpegPath` is left as `ffmpeg`
and nothing runs from the PATH, so an ffmpeg you installed yourself always wins. There are builds for
Linux (x64 and ARM64, glibc) and Windows (x64 and ARM64), not macOS. ffmpeg is GPL software made by
other people; Soundstage doesn't include it, and only runs it as a separate program.

## Please read

**What gets played.** Soundstage plays what people give it. It doesn't host, supply or check any of
it, and we aren't responsible for what anyone plays through it. That's down to the person playing it
and the server that lets them.

**Streaming services.** Soundstage fetches audio and video from the services you leave enabled, over
your server's connection and under your server's address. Whether that's allowed depends on those
services' terms and on where you are, and that part is up to you. Services also block or rate-limit
servers that fetch a lot, which is what `maxPlayingReceivers` and the queue limits are there to help
with. YouTube in particular may start asking the server to sign in. Soundstage isn't affiliated with
or endorsed by any of these services.

**The media folder** is a folder of your own files that every player on the server can play. Only you
decide what goes in it, so only put files there that you're allowed to share with your players.

**Direct links and your network.** `enableHttp` lets players have your server fetch any public web
address they give it, and `allowPrivateNetworkHosts` lets operators reach machines on your own network. Both are off unless
you turn them on, and what they reach once you do is your responsibility.

**ffmpeg** is a separate program with its own licence. Soundstage only downloads it when you turn that
on, only from the release it was built against, and only keeps it if the hash matches.

Soundstage comes with no warranty; see the licence.

## Platforms

Soundstage runs on Windows, Linux and macOS (Apple Silicon too). On macOS:

- There's no ffmpeg download. Install ffmpeg yourself (for example with Homebrew) if you want pictures.
- Sharing your own audio needs a virtual device such as BlackHole, and macOS may not let the game read
  any input at all without microphone permission, which the Minecraft launcher may not ask for.

## Known limitations

- Subtitles that are pictures (the PGS and DVD subtitles on many Blu-ray rips) aren't shown; only
  text subtitles are.
- A film started or skipped far in waits a little for its subtitles there, since they're read from
  the start of the file.
- Dolby Vision profile 5 films play with their colours off. Other Dolby Vision profiles are fine.
- Two players watching one screen can be a few milliseconds apart from each other. Each sees the
  picture in step with what they hear.
- Files with no sound at all are refused, because the sound is what times the picture.

## Troubleshooting

**Videos play without a picture.** The server has no ffmpeg. Install it, point `ffmpegPath` at it, or
turn on `downloadFfmpeg`. The server log says which it found.

**YouTube says to sign in to confirm you're not a bot.** YouTube is blocking the server's address.
An operator can run `/soundstage oauth` and follow the link with a spare Google account. In
singleplayer the prompt opens by itself.

**Every YouTube, SoundCloud, Bandcamp, Vimeo or Twitch load fails, and the log says a host "resolved
only to non-public addresses".** Something on the server's network is answering those names with a
private address, usually a fake-IP DNS proxy (Clash, mihomo, sing-box or Surge in TUN mode, which
answer from 198.18.0.0/15). Give the server a real DNS resolver, or keep the Java process out of the
proxy's fake-IP mode. There's no setting to allow it.

## Building

You need JDK 25 to run Gradle; each Minecraft version compiles for its own Java (17, 21 or 25), which Gradle
finds or downloads.

```sh
./gradlew build
```

Each Minecraft version and loader is its own jar,
`versions/<version>-<loader>/build/libs/soundstage-<version>-<loader>-<mod version>.jar`.

Issues and pull requests are welcome.

## Licence

Apache 2.0 (see [LICENSE](LICENSE)). Put it in your modpack, fork it, change it however you like. Just
keep the [NOTICE](NOTICE) file with it so I get the credit.

The libraries Soundstage bundles are other people's work; [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)
lists them and their licences, and the full texts are in `licenses/`.
