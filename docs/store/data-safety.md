# Play Console answers — PreCut 1.0.0

Answers for the forms under Play Console → Policy → App content, based on what the app actually does: there is no INTERNET permission in the merged manifest, and no analytics, ads or accounts. Media3 adds `ACCESS_NETWORK_STATE` (it can read whether a network is connected, but can't use it) and `WAKE_LOCK` (keeps playback and exports running); neither sends anything.

## Data safety
| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not shown once you answer No above |
| Do you provide a way for users to request that their data is deleted? | Not shown once you answer No above |

Why "No" is right: Play counts data as *collected* only when it leaves the device. PreCut processes photos, videos, audio recordings and files **on the device only** (Play's "processed ephemerally / on device" exemption). Text to speech uses the phone's installed engine, which isn't PreCut collecting the text.

## Privacy policy
The URL of the hosted `privacy-policy.md`. Play requires one because the app requests the microphone.

## Ads
**No**, the app doesn't contain ads.

## App access
**All functionality is available without special access.** There's no login.

## Content rating (IARC questionnaire)
- Category: **Utility, Productivity, Communication or Other**
- Violence, sexuality, language, controlled substances, gambling: **No**
- Users can interact or exchange content with each other in the app: **No** (exports are shared through Android's share sheet, outside the app)
- Shares the user's location: **No**
- Allows purchases of digital goods: **No**
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
