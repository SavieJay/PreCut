# Phone checklist (Phase 16)

Everything built since Phase 9 that hasn't been confirmed on a phone. Work down the list, write **OK** or what you saw in the Result column, and send it back. Anything that fails gets fixed with a test before the item counts as done.

## 0. Setup
1. On the phone: Settings → About phone → tap **Build number** 7 times, then Developer options → turn on **USB debugging**. Connect by USB and accept the prompt.
2. `adb devices` should list the phone.
3. Install the latest debug build: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`.

**If the app closes at any point:** open it again, go to Settings → **Crash reports** → **Share**, and send me the report. For crashes inside the phone's video codecs, which the app can't record, run `adb logcat -d -b crash > crash.txt` and send that file.

## 1. Automated checks (about 10 minutes)
Run `tools/device_checks.sh` with the phone connected. It exports test projects and reverses test clips on the phone, then measures the results.

| Check | Expected | Result |
|---|---|---|
| The script's summary | every line PASS (reverse order, reverse rotation, text timing, rotated text, audio gaps, fades, high speed, proxy keyframes, picture timing across a cut) | |
| "picture timing, no frame-rate pass (reference)" vs "picture timing across a cut" | both PASS. If only the reference passes, the frame-rate pass repeats a frame at cuts, so tell me. | |

## 2. The two crash fixes (2026-10-20)
| Check | Expected | Result |
|---|---|---|
| Reverse the clip that crashed before | Finishes. The bar moves past 40% and the clip plays backwards. | |
| Reverse a portrait (upright) phone video | The result is upright, not sideways | |
| Full screen while paused | Opens showing the current frame | |
| Full screen while playing; pinch to zoom; rotate the phone; scrub | Picture fills correctly, zoom follows the fingers, rotation re-fits | |
| Close full screen | The editor preview returns and plays | |

## 3. Overlays, effects, blend modes (Phase 14)
| Check | Expected | Result |
|---|---|---|
| Overlay a bright clip on a video → Blend → Multiply, Screen, Difference | The preview changes at once; the export matches | |
| Select the overlay → Effects → Glitch or Shake | The effect stays inside the overlay; it shows in the export | |
| Overlay with In: Slide and Motion blur 100%, then export | A trail behind the moving overlay, no dark fringe | |
| Dissolve between two main clips, the second with an effect | The effect shows during the dissolve | |
| Stickers → My images → import an animated WebP | It plays in the preview and the export | |
| Export a GIF | Its length matches the project | |

## 4. Earlier items never confirmed on a phone (Phases 9–12)
| Check | Expected | Result |
|---|---|---|
| Play a project with cuts and a moving clip | Smooth playback, no stalls after dragging the timeline | |
| While paused, open Mask or Chroma (a tall panel), then close it | The preview picture stays in place | |
| Motion blur at 100% on a fast clip, while playing | Blur visible while playing (a paused frame stays sharp) | |
| Overlay video + keyframes (move between 2 keyframes) | The overlay moves smoothly; the export matches | |
| Text with In/Out animations, a curved text, a GIF sticker | Each looks the same in preview and export | |
| Two effect layers and a clip effect | Preview and export match | |
| Export 1080p while switching to another app | Notification progress continues; the video is saved; Open and Share work | |
| HDR video (if your phone records HDR) exported with HDR on | Plays as HDR in the gallery (or note that the option is disabled) | |

## 4b. Beats and AutoCut (Phase 17)
| Check | Expected | Result |
|---|---|---|
| Add a song → Beats → Every beat → Detect | Yellow beat dots appear on the beat of the song within a few seconds | |
| Same with Every 2nd and Every 4th | Fewer dots, on the strong beats (bar starts in most songs) | |
| Beats → Cut clips to the beat, then play | Each clip changes on a beat; Undo restores all clips in one step | |
| Projects → AutoCut → Every 2nd → pick 5 clips and a song | A new project opens with the song and clips cut to its beats; everything is editable | |
| AutoCut with a podcast or speech-only audio | Opens with clips uncut, and says no steady beat was found | |
| The automated checks (section 1) | "beat detection" and "AutoCut cuts land on beats" PASS | |

## 4c. Templates (Phase 18A)
| Check | Expected | Result |
|---|---|---|
| Editor → ⋮ → Save as template; name it, pick a category, leave all clips Replaceable → Save | "Saved to Projects → Templates"; the sheet closes | |
| Projects → Templates | The card shows the thumbnail; within a minute (preview rendering) its page plays the preview on a loop | |
| Template → Use template → pick as many clips as it has | A new project opens with your clips in the slots, keeping the template's looks, animations, transitions, texts and timing | |
| Use template with fewer clips, and with a video shorter than a slot | Clips repeat; the short video plays slower to fill its slot (or a message says the timing shifts) | |
| Delete the template, then open the project made from it | The project still has everything (stickers, music and other fixed media included) | |
| The automated checks (section 1) | "template slots start on time" PASS | |

## 5. Release build (Phase 15)
1. `./gradlew assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk`. If Android refuses because the debug build is installed, uninstall it first. That deletes the debug build's projects.
2. Then:

| Check | Expected | Result |
|---|---|---|
| Create a project, add clips, text with a custom font, an effect, an overlay with a blend mode | Works as in the debug build | |
| Close the app completely and reopen the project | Everything is still there | |
| Export a video, a GIF, audio only and a frame | All four saved, Open and Share work | |
| Settings → Open-source licences → tap an entry | The full licence text shows | |
| Full screen and Reverse | No crash | |

## 6. Store screenshots
Take the six screenshots listed in `docs/store/README.md` while you're at it.
