# Phone checklist (Phase 16)

Everything built since Phase 9 that hasn't been confirmed on a phone. Work down the list, write **OK** or what you saw in the Result column, and send it back. Anything that fails gets fixed with a test before the item counts as done. The same checks, with step-by-step instructions and Pass/Fail/Skip buttons that save as you go, are on the "PreCut Phone Checks" page (an artifact in your claude.ai gallery); keep the two in step when checks change.

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

## 4d. Built-in templates and songs (Phase 18B)
| Check | Expected | Result |
|---|---|---|
| Projects → Templates | 12 built-in cards with storyboards; chips for each category | |
| Open Happy birthday → Use template → pick 6 photos | A birthday video with zooms, flashes, sparkles and the title in Pacifico | |
| Product spotlight → Use with a song → pick 4 clips and a song | Cuts land on the song's beats, at about the template's pace; "Shop now" stays on the last clip | |
| Star a template, use another | Favourites and Recent chips appear and show them | |
| Use with a song and a podcast | The template's own timing stays; a message says no steady beat was found | |

## 4e. Transitions, effects, filters (Phase 19A)
| Check | Expected | Result |
|---|---|---|
| The automated checks (section 1) | "catalogue export" PASS; then open `build/device_checks/out/catalogue_sheet.png` and check no frame is black or plainly wrong | |
| Transition panel | Five tabs (Basic, Slide, Wipe, Motion, Glitch); picking one plays it once | |
| Try Circle, Clock, Push up and Ink between two clips, then export | Preview and export match; the video is shorter by each overlapping transition | |
| Effects panel → Party, Art, Weather tabs | Confetti falls, Snow drifts, Sketch draws the picture in pencil | |
| Filters → Portrait, Movie, Night | Tiles show the real look on your clip | |
| Open a project saved before this update that uses effects and transitions | Looks exactly as before | |

## 4f. Fonts, text templates, sticker packs (Phase 19B)
| Check | Expected | Result |
|---|---|---|
| The automated checks (section 1) | "animated stickers play" PASS; on `catalogue_sheet.png` every `font_` and `text_` frame shows its text in its own font, and the two `stickers_` grids show every sticker | |
| Text → Style → Font | 24 fonts plus Roboto Flex, each name drawn in itself | |
| Text → Style → Templates | Five groups (Title, Social, Caption, Script, Fun); a tap restyles the text and keeps its words and place | |
| Stickers → Animated, add Fire and Party popper, play | They animate in the preview and in the export | |
| Stickers → 3D, add a few | Sharp at the default size; the timeline bar shows each one's emoji | |
| Settings → Open-source licences → Stickers | Both packs, with their licence and credit | |

## 4g. Cloud AI (Phase 20)
Use a real phone with the Play Store (an emulator can't pass Google Play's check), a debug build from this machine, and the internet on.

| Check | Expected | Result |
|---|---|---|
| Settings → Cloud AI | The switch is on; "Free minutes this month" says "Not used yet" | |
| Check cloud AI | The phone makes the test phrase, then a dialog asks before sending: what (a few seconds of sound), where (Ireland, then ElevenLabs), what's kept, Google Play's check | |
| Tap **Send** | After a few seconds: "Heard: “Hello from PreCut. This is a test of the captions.”" (or close to it); the minutes row then shows 29 of 30 minutes left and the reset date | |
| Check cloud AI again, tap **Cancel** | "Cancelled. Nothing was sent." | |
| Airplane mode, then Check cloud AI | "You're offline…" straight away, with no dialog | |
| Switch cloud AI off | The minutes row says "Off"; Check says cloud AI is off | |
| Delete my cloud data → Delete | "Deleted…"; the minutes row says "Not used yet". Check again: it works and shows full minutes | |
| Settings → Open-source licences | OkHttp and Okio, and Google Play Integrity and Play services, are listed | |
| A release build signed with the upload key, installed with `adb install` | Check cloud AI works too (the backend knows the upload certificate) | |

If Check says the phone or app couldn't be verified, send the time you tried: the function's logs (Supabase dashboard → Edge Functions → cloud → Logs) say which check failed.

## 4h. Automatic captions (Phase 21A)
Same phone and build as 4g, with the internet on. Use a 1–2 minute clip of someone speaking clearly, plus a song added with Audio → Music.

| Check | Expected | Result |
|---|---|---|
| Main toolbar | **Captions** sits right after Text | |
| Tap Captions | The sheet shows Language (Auto first), Style (Highlight picked), the note that music is left out, and "Needs 2 min · N min left" | |
| Pick English, then Generate | "Preparing the sound…" counts up, then the consent dialog shows the length and size | |
| Tap **Send** | "Making captions…", then "N captions added" with Undo. Lines of a few words appear on the text track, timed to the speech | |
| Play the preview | Each line shows while it's spoken; with Highlight, the word being spoken turns yellow | |
| Export the video | The captions and the moving highlight look the same as in the preview | |
| The song's lyrics | No captions come from the music | |
| Undo | Every caption goes in one step; Redo brings them back | |
| Captions (the panel opens now) → Generate again → Box → Generate captions → Send | The sheet says it replaces the captions already there; afterwards there's one set, in the Box style | |
| Tap a caption, edit its words or move it | It behaves like any text | |
| Mute the clip (no voiceover), open Captions | It says there's no sound to caption; Generate is off | |
| Airplane mode, open Captions | The offline message; Generate is off | |
| Generate, then **Cancel** in the consent dialog | Nothing is sent; the minutes don't change | |
| A video over 10 minutes (optional) | The dialog says it's sent in parts; "Sending part 1 of 2…"; **Stop** during part 1 keeps part 1's captions and says you stopped | |
| Yoruba, Igbo or Hausa speech (optional) | Captions in that language; note how good they are | |

## 4i. Editing captions (Phase 21B)
The project from 4h, with its captions.

| Check | Expected | Result |
|---|---|---|
| Tap Captions | The Captions panel opens (not the sheet): style chips, every line with its start time, Generate again. While playing, the line being spoken is marked and kept in view | |
| Tap a line | The playhead goes to it and it opens: its words, Start and End with − and +, Split, Merge, Hear again, Delete | |
| Fix a word | The preview shows the new words; with Highlight, the highlight still moves word by word | |
| Start −, End + a few times | The times change by 0.1 s; they stop at the lines before and after. One Undo puts back a quick run of taps | |
| Split with the playhead inside the line | Two lines, cut between words; each shows only its own words | |
| Merge | The line and the next become one | |
| Hear again | The consent dialog shows a few seconds; then "Caption heard again" with Undo; the line's words are redone, its times kept | |
| Delete | The line goes; deleting the last caption closes the panel | |
| A style chip (e.g. Pop) | Every caption changes; titles you added yourself don't | |
| Tap a caption → Text → Style | "Apply to: All captions" is on: a font or colour change goes to every caption. With "This caption", only that one changes | |
| Header: Save subtitles | The file picker suggests "<project>.srt"; the saved file opens in a text app with numbered lines and times like 00:00:03,200 --> 00:00:05,800 | |
| Header: Share subtitles | The share sheet offers the .srt file (e.g. to Drive or WhatsApp) | |
| Load the .srt next to the exported video in a player (VLC, or YouTube Studio) | The subtitles line up with the speech | |

## 4j. Remove background (Phase 22A)
Works on the phone, offline. Use a video of one person filmed fairly close (head and shoulders to full body), a portrait (upright) video of a person, a photo of a person, and a clip with no people (a landscape).

| Check | Expected | Result |
|---|---|---|
| Select the person video → Remove BG → switch on | "Removing background… N%" with time left and Cancel; then "Background removed" with Undo. The person stays, the rest shows the canvas (black by default) | |
| Play it | The outline follows the person without flickering or lagging behind; hair and hands may be rougher | |
| Canvas → Blur, then a colour, then a picture | The person stays in front of each background | |
| Softness and Edge sliders (release each) | Softness blurs the outline's edge; Edge right keeps more around the person, left trims closer | |
| Show original on, then off | The whole clip, then the cut-out clip again | |
| Export at 1080p and play from the gallery | Same cut-out as the preview, at the same moments | |
| The portrait video | The outline sits on the person (not turned sideways or mirrored) | |
| The photo, as a main clip and as an overlay | The person stays; as an overlay, the video below shows around them | |
| An overlay of the person over another video, moving with keyframes | The cut-out person moves with the overlay; the export matches | |
| Cancel half way, then switch on again | Nothing changes after Cancel; the second run starts over and finishes | |
| Switch off, then on again | Instant the second time (the outline is reused) | |
| The clip with no people | "No person found in this clip" and the switch stays off | |
| Split the cut-out clip, Duplicate it, Speed 2x, Reverse it | Every piece stays cut out, at the right moments | |
| Trim the cut-out clip longer than it was | The panel says part of the clip isn't cut out, with Update; Update fixes it | |
| Undo, Redo; close and reopen the project | Each comes back as it was | |
| Save as template with the clip as a slot, then use it | The filled slot starts without the cut-out (switch it on again) | |
| Projects → gear → Open-source licences | LiteRT and the MediaPipe Selfie Segmentation model are listed | |

## 4k. Auto reframe and Reduce noise (Phase 22B)
Both work on the phone, offline. For Reframe use a landscape video of a person walking across, in a project set to 9:16 (Ratio). For Reduce noise use a voice recording with a steady background noise (a fan, traffic hum, wind), and the same recorded as a voiceover (Audio → Record).

| Check | Expected | Result |
|---|---|---|
| Select the landscape clip → Reframe → Normal → Reframe this clip | "Reframing… N%" with Cancel, then "Clip reframed" with Undo; the picture fills the 9:16 frame | |
| Play it | The frame follows the person smoothly, without jitter; it holds still while they stand still | |
| Slow, then Fast (Reframe this clip again after Undo) | Slow pans more gently and lags more; Fast keeps the person closer to the middle | |
| Show original framing on, then off | The whole landscape picture unmoved, then the reframed clip | |
| Look at the keyframes on the clip; move one on the preview | Diamonds on the clip; moving one changes the path there, like any keyframe | |
| A clip with an opacity or volume keyframe, then Reframe | The fade or volume change is still there | |
| A clip with Remove BG on, then Reframe | Works, and quicker (it reads the cut-out outline) | |
| Apply to all with several clips | One progress dialog for all; "N clips reframed"; one Undo puts them all back | |
| A clip with no people | "No person found in this clip"; nothing changes | |
| Ratio 16:9 with a 16:9 clip, then Reframe | "This clip already fits the frame. Pick another ratio first." | |
| Export at 1080p | The export follows the person exactly as the preview did | |
| Select the noisy clip → Volume → Reduce noise on | "Measuring the noise…" briefly, then the switch is on; the background noise is much quieter and the voice stays clear | |
| Strength up and down (release each), then off | More, then less noise taken out; off plays the original sound | |
| The voiceover → Volume → Reduce noise | The same on an audio clip | |
| Export, and Export → Audio only | Both sound like the preview, in sync with the picture (lips match) | |
| Extract audio from the cleaned clip | The extracted audio is cleaned too | |
| Reduce noise on a clip with no sound (a screen recording without audio) | "Not enough sound in this clip to measure its noise"; the switch stays off | |
| Undo, Redo; close and reopen the project | Each comes back as it was | |

## 4l. AI edit (Phase 22C)
Needs the Anthropic key on the server (`ANTHROPIC_API_KEY` in `supabase/.env.secrets`, pushed, and the `edit` migration applied) and cloud AI on. Use a project with three or four clips, a text and music with beats marked (Beats → Auto).

| Check | Expected | Result |
|---|---|---|
| Before the key is on the server: AI edit → type a request → Agree and send | "AI edits aren't switched on yet…"; nothing changes | |
| AI edit (first button on the toolbar) | The sheet: request box, idea chips, the note on what's sent, "N AI edit requests left this month", Agree and send | |
| Tap an idea chip, then Agree and send | "Claude is editing… Step 1 · 0 changes so far", then the review in place of the timeline, with Claude's sentence and a list of changes | |
| Play the preview during the review | It plays the changes; the real project is untouched (Discard shows it as it was) | |
| Switch one change off | The preview drops it at once; Apply's count goes down | |
| Apply | "N AI changes applied" with Undo; the timeline shows them; one Undo removes them all | |
| "Make it about 15 seconds" on a 40 s project | Clips trimmed or removed so the total is near 15 s | |
| "Cut the clips to the music" | Clips end on beats | |
| Refine → "keep the first clip longer" | Claude changes it; the list grows; earlier switched-off changes are gone | |
| Stop while it works | The review shows the changes made so far, with "Stopped." | |
| A request it can't do ("add a dog") | The sheet stays open with Claude saying it can't | |
| Airplane mode, then Send | "You're offline…"; the sheet keeps the request | |
| Back during the review | The review closes; nothing was applied | |
| Settings → Cloud AI off, then AI edit → Send | "Cloud AI is off in Settings." | |

## 4m. Forced updates (Phase 23)
The server side is live since 2 November (migrations and function deployed). The installed version code is the number in brackets in Settings → About (Version 1.0.0 (N)). Change the row in Supabase → Table Editor → `app_config`.

| Check | Expected | Result |
|---|---|---|
| `latest_version_code` above the installed one; leave the app and come back after 15 minutes (or force-stop and reopen) | Home shows "A new version of PreCut is ready." with Update and × | |
| × on the banner, then force-stop and reopen | No banner; raising `latest_version_code` again brings it back | |
| Update on the banner (debug build, not from Play) | The website's `/download` page opens (or `download_url`) | |
| `min_supported_version_code` above the installed one, `latest_version_name` set (e.g. 1.2.0); reopen the app | "Update required" covers everything: the logo, a phone with a rising arrow, "You have 1.1.0 · Latest 1.2.0", Update now; Back and taps do nothing | |
| Same with `latest_version_name` empty | "You have 1.1.0" alone | |
| With a project open in the editor when it appears | Reopening after lowering the minimum shows the last edits saved | |
| Start an export, then raise the minimum and come back to the app | The export finishes; the update screen appears after it | |
| Airplane mode, force-stop, reopen (after the minimum was raised once) | Still blocked (the last answer is kept) | |
| Clear the app's data, airplane mode, open | Opens normally (nothing kept, never blocks) | |
| Lower the minimum again, come back after 15 minutes | The app is usable again | |
| From Play internal app sharing (docs/RELEASING.md): install the lower build, raise the minimum, Update now | Play's full-screen update runs and the app restarts on the new version | |
| Internal app sharing, `latest_version_code` raised: the banner's Update | Play asks; then the banner shows "Downloading the update… N%" with a bar, then "The update is downloaded." with Restart, which installs it | |
| While that download runs, raise the minimum too | The blocking screen shows the bar and percentage instead of Update now, then Restart to update | |
| After that update, with `whats_new` set | "What's new in 1.1.0" (the new version) with a row and icon per bullet and Got it, once | |
| Open a draft saved by an older PreCut build | It opens as before | |

## 4n. Onboarding (Phase 24)
Clear the app's data first (Settings → Apps → PreCut → Storage → Clear storage), or use Settings → Replay intro for the pages alone.

| Check | Expected | Result |
|---|---|---|
| Open PreCut | Welcome (design O-01): a tilted wall of clips with a white playhead through it, the logo, "Edit like a pro. In minutes.", Get started, I already have projects, Skip; at the top a track of clips, the first part-filled with a playhead | |
| Get started → What will you create? | Each card shows its shape (9:16, 16:9, 1:1); picking Social and Travel says "New projects will start in 9:16…" under the grid; Continue is off until one is picked | |
| Your editing experience | Radio cards; the picked one opens: New shows the Simple toolbar (AI edit to Filters, then More), the others the full toolbar running off the edge | |
| Get started → pick YouTube videos → Continue → New to editing → Continue | Android 13+: the notifications page; Allow asks the system; then Projects | |
| New project | Starts in 16:9 | |
| Templates tab | Vlog templates first | |
| Open a project | Three tips in turn: timeline, tools, Export, each with an accent ring and a light bubble pointing at it; Next or a tap anywhere moves on; the toolbar shows six tools and More | |
| More, then Less | The full row, then the six again | |
| Settings → Simple mode off | The full toolbar in the editor | |
| Settings → Replay intro → Skip | Back in Settings; the earlier answers were kept | |
| Clear data again, then Skip on Welcome | Projects with the usual defaults (9:16, full toolbar); onboarding doesn't come back after a restart | |
| Not now on the notifications page, then export | The export still asks for notifications | |
| Install 1.1.0 over 1.0.0 with projects | No onboarding and no editor tips; Projects opens as before | |
| Android 12 or older | No notifications page | |

## 4o. PreCut Pro (Phase 25)
Needs the Play Console setup and server steps in `docs/BILLING.md`, a build installed from Play (internal testing), and a license tester account on the phone.

| Check | Expected | Result |
|---|---|---|
| Settings → PreCut Pro → Get PreCut Pro | The paywall: benefits, Yearly (Best value or Save N%) and Monthly with prices in your currency, "Start 7-day free trial" | |
| Start the trial, pay in Play's sheet (test card) | The paywall closes; Settings shows "PreCut Pro, yearly" and "Free trial until …"; caption minutes now 300 | |
| Settings → Cloud AI off, then back to Settings → PreCut Pro | Still Pro (Pro doesn't need cloud AI on) | |
| Uninstall, reinstall, Settings → PreCut Pro → Restore purchases | "Pro restored on this phone." | |
| Cancel in Google Play (Manage in Google Play) | After a moment, Settings: "Your subscription won't renew…" and still Pro until the date | |
| Wait for the test subscription to end (test renewals take minutes) | Pro ends; quotas back to the free ones | |
| Payment declined test card | "Google Play couldn't take your payment…" (grace), later "Pro is on hold…" and not Pro | |
| Airplane mode with Pro | Pro stays | |
| Onboarding's last page (Settings → Replay intro), installed from Play with a trial offer | "Try PreCut Pro free for N days" with Play's N, Continue free, Skip; Start free trial opens the paywall over Projects | |
| The same on the website's APK, offline, or once the trial was used | No Pro page: onboarding ends after the experience (or notifications) page | |
| A phone without the Play Store | The paywall says Pro can't be bought right now | |

## 4p. Follow person (Phase 26A)
| Check | Expected | Result |
|---|---|---|
| A text over a clip of someone walking across → Text panel → Follow | "Following the person…", then "Now follows the person" with Undo | |
| Play | The text moves with the person, keeping its distance; no shaking | |
| A sticker → Follow | The same for the sticker | |
| Follow over a clip with nobody in it | "No person found in the clip under it" | |
| Undo | The text stays still again | |

## 4q. Pro features (Phase 26B)
On a free account each Pro tool shows PRO; use it until its free uses run out, then it opens the paywall. A license-tester Pro account removes the limits.

| Check | Expected | Result |
|---|---|---|
| A talking clip with captions → Transcript | The words; fillers tinted; Remove filler words (n), Remove silences (n) | |
| Strike two words, Preview, then Apply cuts | The preview skips them; Apply: "… cut from the video" with Undo; captions stay in time with the voice | |
| Transcript on a project without captions | "Make captions first" with Make captions | |
| Hook on a project with a slow, untitled start | 2–4 tips; each Apply changes the video (title, trim, zoom, quiet start, beats) | |
| Settings → Brand kit: logo, two colours, fonts, intro, outro; then Brand in a project | Intro first, outro last, logo top right, texts in the colour and font; Undo removes it all | |
| Apply to new projects on, then a new project (Pro) | Opens already branded | |
| Formats on a landscape interview | Three frames (9:16, 1:1, 16:9), each keeping the person in view; Export 3 videos saves three files | |
| Captions → Translate → French → Translate | Every line in French, in time; one Undo restores | |
| Translate with Dub on, with a French voice installed | Each line spoken over the video; clip sound muted | |
| Dub in a language with no phone voice | "…no voice for that language…"; the translation is kept | |
| Shorts on a 5-minute talk with captions | Up to five shorts in Projects, upright, captioned, reframed; Open and Export work | |
| Shorts on a 30-second clip | "Shorts need a video of at least a minute." | |
| B-roll the first time | The explanation and Allow access; Android 14: "Select photos" works too | |
| Search "dog", "beach", "food" | Matching photos and videos from the gallery; tap one: it covers the frame at the playhead | |
| Deny access | "B-roll needs access to your gallery." | |

## 4r. AI Edit (Phase 27)
Needs the Anthropic key on the server (as 4l). Use four or five clips with someone talking, music with beats marked, and captions on one project.

| Check | Expected | Result |
|---|---|---|
| Home → AI Edit (top bar) → pick clips | A new project opens with the AI edit sheet; the four idea chips first | |
| "30 s TikTok, upbeat" → Send | "Understanding your clips… n%", then lines like "Picking the best moments…", "Cutting to the beat…", the step and changes so far | |
| The review | Changes grouped under Step 1, Step 2…; the preview plays the result; Apply all | |
| Switch two off | Apply 5 (or so); applying keeps the rest; one Undo removes them | |
| "Remove pauses and add captions" | Cuts of the pauses; "Add captions (made after you apply)"; after Apply the captions sheet opens with the style | |
| Refine → "make it shorter" | More changes under the next step | |
| A free account after its free requests | The paywall opens from Send | |
| Airplane mode, then Send | Offline message; nothing changed | |

## 4s. Growth and sharing (Phase 28)
Template links need the `shared_templates` migration and the function deployed; links open the app only after the website serves `assetlinks.json` for `webHost`.

| Check | Expected | Result |
|---|---|---|
| A template made from your own clips (slots only) → Share | The share sheet with `https://<webHost>/t/<id>` | |
| A template with a sticker of your own photo → Share | "…uses your own photos or stickers… can't be shared…" | |
| Open the link on another phone with PreCut (from a chat app) | PreCut opens the template page; it's in Templates; no music | |
| The link with the app not installed | The website's template page with the Play button | |
| Export settings → "Made with PreCut" end card on, export | The video ends with 2 s of the logo and words; off: no card | |
| Export twice successfully | After the second, Play's rating card may show (Play decides) | |
| A failed export | No rating card | |
| Settings → About → Privacy policy, Terms of use, Help and support | Each opens the website's page in the browser | |

## 4t. Afterglow restyle (Phase 29)
Compare each screen side by side with its frame on the canvas's "Redesign 2026" page (RD-…). Check on the Android 10 phone and, if you can, an Android 12+ phone.
1. **Icon and splash (RD-A01, A02):** the launcher icon shows the logo smaller on near-black with a soft pink glow; on Android 12+ the splash shows the same. Long-press the icon on Android 13+ with themed icons on: the single-colour version still appears.
2. **Colours everywhere:** every main button is a pink pill with a **dark** label; there's only one pink action per screen. Delete confirmations use a red pill. Lime appears only on AI things (captions, AI Edit, remove background, reframe…), never on effects or normal buttons.
3. **App and Projects (RD-H01–H09):** pink-glow background; glass project cards; the card menu, rename and delete dialogs; importing; Settings in grouped glass cards.
4. **Onboarding (RD-O01–O06):** Settings → Replay intro; each page and the three editor tips.
5. **Editor (RD-E01–E10):** the top bar (back, title with "Saved · ratio · length", undo/redo, small pink Export); the preview's rounded corners; the white round play button; the floating glass dock above the bottom edge; track icons in the left gutter; a selected clip has a pink outline with pink trim handles; the playhead's white time bubble.
6. **Tool panels (RD-T…, X…, U…, P…, S…):** every tool opens as a floating glass panel with an 18 sp title and a round check (Done) at the top right; chips are 40 dp pills (pink when picked); tiles show a pink ring and a check when picked; sliders have a 24 dp white knob, and a white value bubble while dragging.
7. **Timeline colours:** video pink, music aqua, voiceover light blue, text amber, overlay periwinkle, stickers orange, effects lavender, looks pink-lilac, all with dark labels you can read.
8. **Export (RD-EX01–EX05b):** settings in segmented groups with one pink Export; the exporting, done and failed screens.
9. **Nothing glassy over the video:** chips and dialogs drawn over the preview are solid, not see-through.
10. **Small screen (RD-E03b, T06c):** on a 360 dp wide phone (or Display size set to largest), nothing is cut off in the editor or the Filters panel.
11. **Reduce motion:** Settings → Accessibility → Remove animations on: buttons no longer shrink when pressed, and panels appear without sliding.

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
| Settings → Cloud AI → Check cloud AI → Send | Works as in 4g | |
| Generate captions for a short speech clip | Captions are added, as in 4h | |
| Remove BG on a clip with a person, then export | Works as in 4j (the release build shrinks code, so this checks LiteRT survived) | |
| Reframe a clip and Reduce noise on another, then export | Works as in 4k | |

## 6. Store screenshots
Take the six screenshots listed in `docs/store/README.md` while you're at it.
