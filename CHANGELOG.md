# Changelog

Newest first. Each section is pasted into Modrinth and CurseForge as that version's changelog.

## 1.0.1

- Server owners updating from 1.0.0: your config keeps 1.0.0's lower video quality limit. To get the new, sharper default, delete the `maxVideoHeight` line from `config/soundstage.aon`.
- Added the Remote: open your A/V receiver's screen from up to 96 blocks away in the same dimension. Craft it from a stone button, redstone and a copper ingot, then sneak and use it on the receiver to link it. It matches the receiver's finish, lights up with it, and respects the receiver's lock. Put it alone in a crafting grid to unlink it.
- Films and videos that have more than one language, including YouTube's dubbed videos, can switch language from the receiver's Playing tab. The receiver remembers your choice for what plays next. Director's commentary and audio description can be picked too.
- Displays can show pictures (PNG or JPEG) from the media folder, or from a web link on servers that allow direct links, and videos without sound now play too, for signs and promotional videos. A picture alone in the queue stays up until it's skipped; with something queued after it, it moves on after 10 seconds, so a looping queue of pictures makes a slideshow. Pictures and videos without sound need ffmpeg on the server. Server owners can change the time with `imageDisplaySeconds`.
- Video is sharper: the bigger the display, the sharper the picture, up to full HD. Sharper video uses more of each watching player's internet connection, which is worth knowing if you host from home.
- Servers now show video on as many receivers as they can handle, instead of a fixed four. A receiver waiting its turn plays the sound and shows what's playing until its picture can start. Server owners can set a fixed limit with `maxPicturesEncoding`.
- If the server can't keep up with a video, it lowers the picture quality first, so the sound keeps playing. If that's still too much, the sound plays on and the screen shows what's playing instead.
- Players who aren't looking at a display no longer download its video, and turning to the screen still shows the picture straight away.
- Linking a display while a video plays now shows the picture within a second or two, instead of waiting for the next track.
- Receivers with no display linked no longer prepare video, so they cost the server only the sound.
- Video plays more smoothly on slower computers, and skips frames to stay in sync with the sound instead of falling behind. It also eases off if playing it would slow your game down.
- Twitch streams broadcast at a demanding quality now play smoothly on displays, and some Twitch streams that showed no picture now do.
- On servers that allow direct links, more video files play: some files whose sound couldn't be read before now play with sound and picture.
- Large displays no longer shimmer when seen from a distance.
- Fixed films starting on the director's commentary when it's listed first.
- Fixed screens at the edge of your view sometimes showing the now-playing card instead of their part of the video.
- Fixed a speaker picked with the middle mouse button showing its grille in your hand and inventory when it had been removed.
- Fixed speakers, subwoofers, party boxes and receivers being invisible on Minecraft 26.2 and 26.3 when held by another player, dropped on the ground or placed in an item frame.
- Fixed a way a modified client could make the server work hard by switching subtitles over and over.

## 1.0.0

The first release of Soundstage!

- Build a real sound system: speakers, satellites, a center speaker, a subwoofer and the Party Box, all driven by an A/V receiver. Start with a stereo pair or go all the way to 5.1 surround.
- Every speaker linked to a receiver plays in sync with the others.
- Play music and videos from YouTube, SoundCloud, Bandcamp, Vimeo and Twitch, or from your server's media folder, with a queue, loop and shuffle.
- Put screens side by side to build a display up to 16 by 16 blocks. Videos, streams and films play on it in time with the sound.
- Films and videos can show subtitles. Pick a track or turn them off from the display's menu or the receiver's Displays tab, and each track remembers your choice. They start off unless the film marks a default track or has one in the language of whoever last used the receiver, and YouTube's auto-generated captions only show if you pick them.
- Turn the receiver up to 200% without it distorting, switch on Levelling to even out quiet dialogue and loud scenes in films, and lock it so only you (and server operators) can change it.
- Share your computer's audio with everyone near a receiver.
- Works with shader packs (Iris, or Oculus on Forge) and Sodium: speaker cones still move, screens keep their true colours, and the receiver's tubes glow.
- Available for Minecraft 1.20.1 (Forge and Fabric) and for 1.21.1, 1.21.11, 26.1, 26.2 and 26.3 (NeoForge and Fabric). On Fabric you'll also need Fabric API; nothing else is required.
- NeoForge for 26.3 is still in beta, so the 26.3 NeoForge version is built for its latest beta.
- On 1.21.1, Minecraft's Directional Audio setting now actually works. Without this fix, headphones always got directional audio, even with the setting off. This affects every sound in the game.
- Fixes a Sodium issue where a block placed while a shader pack was reloading, or while its area was still loading, could stay invisible. This helps every block, not just Soundstage's.
