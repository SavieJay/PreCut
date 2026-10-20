# Store materials — PreCut 1.0.0

| File | What it's for |
|---|---|
| `listing.md` | Store listing text, release notes, category |
| `privacy-policy.md` | Privacy policy. Host it on any public URL (GitHub Pages, Google Sites) and fill in the contact email |
| `data-safety.md` | Answers for Data safety, content rating, target audience and foreground service declarations |
| `icon-512.png` | Play icon, 512 × 512 |
| `feature-graphic.png` | Feature graphic, 1024 × 500 |

Remake the two images with `python3 -I tools/store_assets.py` if the logo changes.

## Screenshots (2–8 phone screenshots, needed before publishing)
Take them on a phone with a real project, in this order:

1. The editor with a few clips, text and music on the timeline
2. The Effects panel on a clip
3. Text styling (a styled, curved or glowing text on the preview)
4. An overlay with a blend mode
5. Keyframes or the speed curve
6. The export settings sheet

With the phone connected: `adb exec-out screencap -p > shot1.png`. Use the status bar's demo mode for a clean clock and battery:
`adb shell settings put global sysui_demo_allowed 1 && adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930`

## Uploading
1. Build the bundle: `./gradlew bundleRelease`. It comes out at `app/build/outputs/bundle/release/app-release.aab`, signed with the upload key from `keystore.properties`.
2. In Play Console, create the app, turn on **Play App Signing** (Google keeps the app signing key; your key is only the upload key), and upload the `.aab` to Internal testing first.
3. Back up `keystore.properties` and `keystore/precut-upload.jks` together, somewhere outside this computer. If they're lost, Play support can reset the upload key, but it takes time.
4. Each new upload needs a higher `versionCode`. It's the number of commits, so commit before building a new release.
