# PreCut privacy policy

**Effective:** 13 November 2026 (from the PreCut version with accounts and cloud projects)

PreCut is a video editor for Android. This policy explains what the app does with your information. In short: you sign in with Google or your email, and PreCut saves your projects to your account, with their videos, photos and other files, so they're on every phone you sign in to. Cloud AI features send only what they need, and only after you agree: the sound, to turn speech into words, or a written description of your project and your request, for AI edit.

## Your account
PreCut needs an account. You sign in with **Google** or with your **email address** and a 6-digit code PreCut emails you; there's no password.

- **What PreCut keeps:** your email address and an account ID. If you sign in with Google, also the name and profile picture address your Google account shares with apps; PreCut doesn't show or use them. Google sign-in runs under Google's terms; PreCut receives only that Google confirmed it's you.
- **Where:** Supabase Auth, part of PreCut's server, in Ireland (EU). The sign-in code is sent by an email delivery service, which handles your address only to send it.
- **On your phone:** the signed-in session, encrypted with a key kept in Android's secure key store. It isn't included in phone backups.
- **PreCut Pro** is linked to your account, so it works on every phone you sign in to.

## Your projects in the cloud
While you're signed in, PreCut saves each project to your account as you edit it, and brings your projects to any phone you sign in to.

- **What is saved:** the project itself (its clips, order, edits, effects, texts and captions, names) and **every file it uses**: videos, photos, music and sounds, voiceovers, stickers and drawings, fonts and colour looks you imported.
- **Where:** PreCut's server and file storage on Supabase, in Ireland (EU), encrypted in transit (HTTPS) and at rest. Only your account can read your files; other phones get them through links that work for an hour.
- **When:** projects sync shortly after you edit. Videos and photos upload on Wi-Fi unless you allow mobile data in **Settings → Account**.
- **How long:** until you delete the project (a deleted project's record is kept for 90 days so your other phones remove it too; files no project uses are deleted 30 days later) or delete your account (everything at once).
- **Storage:** each account has a storage limit, shown in Settings → Account. When it's full, new files stay on your phone.
- PreCut doesn't look at, analyse or share your projects or files. They're kept only to bring them back to you.

## Cloud AI (optional, and asked every time)
Some features can use cloud AI: Settings → Cloud AI → Check cloud AI, automatic captions, and AI edit. Before anything is sent, PreCut shows what it is about to send and asks you. If you say no, nothing is sent. You can switch cloud AI off in Settings → Cloud AI.

When you agree:

- **What is sent for captions:** only the sound you're making captions for (for Check cloud AI, a test phrase your phone speaks). Never the video or photos.
- **Where it goes:** PreCut's server, which runs on Supabase in Ireland (EU). It passes the sound to ElevenLabs, a speech-to-text service, and sends the words and their timings back to your phone.
- **What PreCut's server keeps:** no audio and no words. It keeps a random ID that the app made for this installation (not linked to your name, email or phone number) and how many minutes and AI edit requests it used this month, so each phone gets its free share. Both are deleted after 13 months without use, or when you tap **Settings → Cloud AI → Delete my cloud data**. The server's logs record each request's time, type, size and outcome with a shortened form of that ID, never the sound, the words, your requests or Claude's answers; like any web service, the hosting provider's logs also record your IP address. Logs are kept briefly to run and protect the service.
- **ElevenLabs** turns the sound into words for PreCut, under its own privacy policy and data terms (https://elevenlabs.io/privacy-policy).
- **AI edit:** what you ask for, and a written description of the project you're editing: how many clips there are, their lengths, order and settings (filters, speed, transitions), the words of your texts and captions, the names of your music and sound files, and what your phone worked out from the media itself: how loud each second is, where the picture changes, and a few words for what an on-phone image model sees in each shot (for example "seashore"). Never the video, photos or sound. PreCut's server passes them to **Claude**, an AI by **Anthropic**, which answers with changes for your phone to make; you review every change before it's applied. PreCut's server doesn't store the description, your request or Claude's answers; it keeps only how many AI edit requests this phone used this month and their size and cost, with the random ID below. Anthropic processes them for PreCut under its commercial terms and privacy policy (https://www.anthropic.com/legal/privacy), which don't allow it to train its models on them.
- **Translate captions:** the words of your captions and the language you chose, to PreCut's server and Claude, as for AI edit; never the video or sound. A dub is spoken on your phone by its own text-to-speech voice.
- **Google Play Integrity:** before sending, PreCut asks Google Play to confirm that it's the genuine PreCut on a genuine Android phone, so no one else can use PreCut's minutes. Google Play makes that check under Google's terms, with information about the app and the device; PreCut receives only the result.

Cloud AI needs the internet. Without a connection, PreCut tells you and sends nothing.

## PreCut Pro (optional subscription)
PreCut Pro is sold through Google Play, which handles the payment under Google's terms; PreCut never sees your card or Google account details. To confirm a purchase, the app sends PreCut's server the purchase token Google Play gives it. The server checks it with Google and keeps the token, the plan, its state (active, cancelled, payment problem) and end date, linked to the random ID of each phone you use it on, so Pro works there. Google Play tells the server when the subscription renews, is cancelled or refunded. This is deleted after 13 months without use, or when you tap Settings → Cloud AI → Delete my cloud data (and no other phone of yours uses the purchase); deleting it doesn't cancel the subscription (do that in Google Play).

## Template links (optional)
When you share a template as a link, PreCut's server keeps the template (its layout, texts, effects and timing; never your photos, videos or music) under a short code, so anyone with the link can open it in PreCut or on PreCut's website. It's kept until you tap Settings → Cloud AI → Delete my cloud data.

## Updates, ratings and installs
- Now and then PreCut asks its server whether a newer version is out, so it can tell you. The request carries nothing about you; the server sees it like any web request.
- After a few good exports, PreCut may let Google Play show its rating card. What you write there goes to Google Play under Google's terms. PreCut only remembers, on your phone, when it last let the card show.
- If you install PreCut from Google Play through a shared template's page, PreCut reads Google Play's install referrer once, on your phone, to open that template on its first start. Nothing is sent.

## What stays on your phone
To edit, PreCut works with files on your device:

- **Photos and videos you pick.** You choose them with Android's picker, and PreCut can read only those. If the picker can't keep access to a file, PreCut saves a copy in its own storage so your project still works later.
- **Your gallery, for B-roll (optional).** If you open B-roll, PreCut asks to read your photos and videos (on Android 14 and newer you can allow only some). It looks at the newest few hundred on your phone with an on-device image model to find them by what's in them, and keeps that list of labels in its own storage. Nothing from your gallery is uploaded. You can take the access back in Android's settings at any time.
- **AI on your phone.** Background removal, auto reframe, noise reduction, follow person, finding moments for shorts and B-roll labels run on your phone with models built into the app. Nothing is sent for them.
- **Microphone.** PreCut asks for the microphone only when you record a voiceover. The recording is saved in the app's storage as part of your project.
- **Your projects.** PreCut saves drafts, covers, preview copies and other working files in its own storage on your phone, and a copy of each project and its files in your account (above). So do your brand kit (a logo or fonts you add are copied in) and your answers to the welcome questions, which only change what it suggests first.
- **Exports.** Videos, GIFs, audio and photos you export are saved to your phone's gallery or music folders, where other apps can see them like any other media.
- **Notifications.** PreCut shows a notification while an export runs, so you can follow or cancel it.
- **Crash reports.** If PreCut closes unexpectedly, it saves a short report (the error, your phone model, Android and app versions, and PreCut's own recent log lines) in its own storage. It stays there unless you share it yourself from Settings → Crash reports.

PreCut has no ads and no analytics. When you share an export, Android's share sheet sends it to the app you choose. What happens next is up to that app.

## Text to speech
If you use text to speech, PreCut passes your text to the text-to-speech engine installed on your phone. That engine's own privacy policy applies to the text.

## Deleting your data
- **Settings → Account → Delete account** deletes your account and, at once, every project and file saved to it, your sign-in details and the link to your Pro purchases. You can also ask on PreCut's website (`/delete-data`) or by email (below), and we'll delete it within 30 days. Deleting the account doesn't cancel a Pro subscription (do that in Google Play).
- Deleting a project removes it from your account and your phones; its files go 30 days later if no other project uses them. Settings → Clear preview cache frees working copies on the phone.
- **Settings → Cloud AI → Delete my cloud data** deletes the random ID and usage PreCut's server keeps for this phone, its Pro purchase records (unless another phone of yours uses them) and the templates you shared as links.
- Uninstalling PreCut removes everything it stored on your phone, except media you exported to your gallery. What PreCut's server keeps is then deleted after 13 months without use.

## Children
PreCut isn't directed at children under 13, and accounts are for people aged 13 and over. Cloud AI sends only what the person using the app chooses to send.

## Changes
If this policy changes, the new version will be posted at this address with a new effective date.

## Contact
Questions about this policy: savyj03@gmail.com. The published copy is on PreCut's website at `/privacy` (Settings → About → Privacy policy), which shows the policy for the version on the download page.
