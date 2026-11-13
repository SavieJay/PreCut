# Play Console answers — PreCut (with accounts and cloud projects, Phase 30)

Answers for the forms under Play Console → Policy → App content, based on what the app actually does. Since Phase 20 the app has the INTERNET permission for its optional cloud AI: it sends nothing until the user agrees (a dialog before every upload for captions; the AI edit sheet's note, agreed on the first Send and shown every time), and then only the sound, or for AI edit a text description of the project and the request (docs/CLOUD_API.md). Since Phase 30 an account is required (Google or an emailed code), and every project syncs to it with all its files (docs/ACCOUNTS.md). There are still no analytics or ads. Media3 adds `WAKE_LOCK` (keeps playback and exports running); `ACCESS_NETWORK_STATE` lets the app say "you're offline" before trying.

## Data safety
| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS only; cleartext traffic is switched off in the manifest) |
| Do you provide a way for users to request that their data is deleted? | **Yes**: in the app, Settings → Account → Delete account (everything) and Settings → Cloud AI → Delete my cloud data; and on the web |
| Does your app allow users to create an account? | **Yes**, with OAuth (Google) and with an email address and one-time code |
| Delete account URL (required with accounts) | `https://precut.netlify.app/delete-data` (the website's account deletion page) |
| Can users request that some data be deleted without deleting the account? | **Yes**: deleting a project deletes it and (30 days later) its files; Delete my cloud data |

Data types to tick, each **collected** and **not shared** (Supabase, which hosts PreCut's server, ElevenLabs, which turns speech into words, and Anthropic, whose Claude suggests AI edits, process data on PreCut's behalf, which Play counts as service providers rather than sharing):

| Data type | Processed ephemerally? | Required or optional | Purposes |
|---|---|---|---|
| **Personal info → Email address** (the account's) | No | **Required** (every account) | **App functionality**; **Account management** |
| **Personal info → Name** (only with Google sign-in: the name Google shares; not shown or used) | No | **Optional** (only with Google) | **Account management** |
| **Personal info → User IDs** (the account ID) | No | **Required** | **App functionality**; **Account management** |
| **Photos and videos → Photos** and **→ Videos** (every project's media, synced to the account) | No (kept until the project or account is deleted) | **Required** (projects sync while signed in) | **App functionality** |
| **Audio → Music files** and **→ Other audio files** (music and sounds in projects) | No | **Required** | **App functionality** |
| **Files and docs** (imported fonts and colour looks in projects) | No | **Required** | **App functionality** |
| **App activity → Other user-generated content** (the projects themselves: edits, texts, captions, names) | No | **Required** | **App functionality** |
| **Audio → Voice or sound recordings** (cloud captions' sound; also voiceovers, synced with projects) | **No**. PreCut's server keeps nothing, but ElevenLabs may keep audio for a time under its own terms (answer Yes only on an ElevenLabs plan with zero retention, `ELEVENLABS_ZERO_RETENTION=true`) | **Optional** (users can decline each upload or switch cloud AI off) | **App functionality** |
| **App activity → Other user-generated content** (AI edit: the request and the project's description, including the user's text overlays and music file names) | **No**, as for audio: PreCut's server passes it on and keeps none of it, but Anthropic may keep API inputs for a limited time under its own terms (check them before release) | **Optional** (only when the user sends an AI edit request) | **App functionality** |
| **Financial info → Purchase history** (PreCut Pro: the Google Play purchase token, plan, state and end date) | No (kept while the subscription is used, up to 13 months unused) | **Optional** (only for people who subscribe or restore) | **App functionality** |
| **App activity → Other user-generated content** (template links: a template the user chooses to share; no media) | No (kept until the user deletes their cloud data) | **Optional** | **App functionality** (shown to people who open the link, at the user's direction) |
| **Device or other IDs** (the random installation ID the app makes on first cloud use) | No (kept with the minutes used, up to 13 months unused) | **Optional** | **App functionality**; **Fraud prevention, security, and compliance** |

Notes:
- Since Phase 30, projects and all their media sync to the user's account (Supabase in Ireland; encrypted in transit and at rest; private to the account). Cloud AI still sends only the sound the user chooses, and for AI edit a text description of the project (no media). Text to speech uses the phone's own engine.
- The email delivery service that sends sign-in codes is a service provider (it handles the address only to send the code).
- IP addresses appear in the hosting provider's logs but aren't used to work out location, so they aren't a separate data type here.
- The Play Integrity API sends Google a request hash, the app's package, version and signing certificate, its licence status and a device attestation, used only to answer the check (Google's terms: https://developer.android.com/google/play/integrity/terms#data-safety). PreCut receives only the verdict. Google leaves the form answers to the developer; the "Device or other IDs" entry above, for fraud prevention and security, is the place it belongs if you count it.
- Before release, check these answers against `privacy-policy.md` and the app's Settings → Cloud AI → What is sent, which say the same.

## Privacy policy
The URL of the hosted `privacy-policy.md`. Play requires one because the app requests the microphone and, with cloud AI, sends audio.

## Photo and video permissions (Phase 26B Smart B-roll)
The app declares `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` (and `READ_MEDIA_VISUAL_USER_SELECTED`; `READ_EXTERNAL_STORAGE` up to Android 12). Play requires a declaration (Policy → App content → Photo and video permissions), and may refuse apps whose use the Photo Picker could cover. Proposed text:

> PreCut's Smart B-roll lets a creator search their own gallery by what's in the pictures ("beach", "dog", "food") while editing, and drop a match over the video as B-roll. Searching by content needs to look at many photos and videos at once, which the Photo Picker can't provide; the picker only returns items the user already chose. The permission is asked only when the user opens B-roll; the photos and videos are classified on the phone (an on-device image model) and never uploaded; on Android 14 and newer the user can grant access to selected photos only. Every other way of adding media in PreCut uses the Photo Picker.

If Play refuses the declaration, the fallback is to remove the permissions and let B-roll search only media the user picks with the Photo Picker or already used in projects (the merge review's second option).

Data safety: the gallery's photos and videos are processed only on the phone and never collected, so nothing new is ticked above.

## Ads
**No**, the app doesn't contain ads.

## App access
**All or some functionality is restricted** (sign-in is required). Instructions for reviewers: "Tap Continue with Google and sign in with any Google account; or enter any email address you can read and type the 6-digit code it receives. Everything in the app is then available; no paid features are needed for review." Google sign-in needs no credentials from us; if Play asks for them anyway, make a review account and give its email inbox's login.

## Content rating (IARC questionnaire)
- Category: **Utility, Productivity, Communication or Other**
- Violence, sexuality, language, controlled substances, gambling: **No**
- Users can interact or exchange content with each other in the app: **No** (exports are shared through Android's share sheet, outside the app)
- Shares the user's location: **No**
- Allows purchases of digital goods: **Yes** (the PreCut Pro subscription, through Google Play Billing)
- Expected result: rated for everyone (PEGI 3 / ESRB Everyone)

## Target audience and content
- Recommended target age: **13 and over**. Including under-13s brings in the Families policy, which PreCut doesn't need.
- Appeals to children: **No**

## Foreground service permissions
PreCut declares two foreground service types for the same task, a user-started export that keeps running in the background with a progress notification and a Cancel button:

| Type | Used on | Declaration text |
|---|---|---|
| `mediaProcessing` | Android 15 and newer | Encodes the video the user chose to export (transcoding and saving it to the gallery). The user starts it, sees its progress in a notification, and can cancel it. |
| `dataSync` | Android 10 to 14 | The same export task, on Android versions that have no `mediaProcessing` type. |

Play asks for a short video showing the feature: record starting an export, leaving the app, and the notification's progress and Cancel.

## Government app, financial features, health
**No** to each.
