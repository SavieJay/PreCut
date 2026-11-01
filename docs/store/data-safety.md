# Play Console answers — PreCut (with cloud AI, Phases 20–22C)

Answers for the forms under Play Console → Policy → App content, based on what the app actually does. Since Phase 20 the app has the INTERNET permission for its optional cloud AI: it sends nothing until the user agrees (a dialog before every upload for captions; the AI edit sheet's note, agreed on the first Send and shown every time), and then only the sound, or for AI edit a text description of the project and the request (docs/CLOUD_API.md). There are still no analytics, ads or accounts. Media3 adds `WAKE_LOCK` (keeps playback and exports running); `ACCESS_NETWORK_STATE` lets the app say "you're offline" before trying.

## Data safety
| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS only; cleartext traffic is switched off in the manifest) |
| Do you provide a way for users to request that their data is deleted? | **Yes**: in the app, Settings → Cloud AI → Delete my cloud data (and the privacy policy's contact address) |

Data types to tick, each **collected** and **not shared** (Supabase, which hosts PreCut's server, ElevenLabs, which turns speech into words, and Anthropic, whose Claude suggests AI edits, process data on PreCut's behalf, which Play counts as service providers rather than sharing):

| Data type | Processed ephemerally? | Required or optional | Purposes |
|---|---|---|---|
| **Audio → Voice or sound recordings** | **No**. PreCut's server keeps nothing, but ElevenLabs may keep audio for a time under its own terms (answer Yes only on an ElevenLabs plan with zero retention, `ELEVENLABS_ZERO_RETENTION=true`) | **Optional** (users can decline each upload or switch cloud AI off) | **App functionality** |
| **App activity → Other user-generated content** (AI edit: the request and the project's description, including the user's text overlays and music file names) | **No**, as for audio: PreCut's server passes it on and keeps none of it, but Anthropic may keep API inputs for a limited time under its own terms (check them before release) | **Optional** (only when the user sends an AI edit request) | **App functionality** |
| **Financial info → Purchase history** (PreCut Pro: the Google Play purchase token, plan, state and end date) | No (kept while the subscription is used, up to 13 months unused) | **Optional** (only for people who subscribe or restore) | **App functionality** |
| **Device or other IDs** (the random installation ID the app makes on first cloud use) | No (kept with the minutes used, up to 13 months unused) | **Optional** | **App functionality**; **Fraud prevention, security, and compliance** |

Notes:
- Photos, videos and voiceovers stay on the device; only the sound the user chooses is sent, and for AI edit a text description of the project (no media). Text to speech uses the phone's own engine.
- IP addresses appear in the hosting provider's logs but aren't used to work out location, so they aren't a separate data type here.
- The Play Integrity API sends Google a request hash, the app's package, version and signing certificate, its licence status and a device attestation, used only to answer the check (Google's terms: https://developer.android.com/google/play/integrity/terms#data-safety). PreCut receives only the verdict. Google leaves the form answers to the developer; the "Device or other IDs" entry above, for fraud prevention and security, is the place it belongs if you count it.
- Before release, check these answers against `privacy-policy.md` and the app's Settings → Cloud AI → What is sent, which say the same.

## Privacy policy
The URL of the hosted `privacy-policy.md`. Play requires one because the app requests the microphone and, with cloud AI, sends audio.

## Ads
**No**, the app doesn't contain ads.

## App access
**All functionality is available without special access.** There's no login.

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
