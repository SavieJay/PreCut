# Accounts and cloud projects: setup and costs (Phase 30)

PreCut needs an account from the first launch: Google (one tap) or a 6-digit code sent by email.
Every project and its files sync to the account. The server side is Supabase Auth (accounts),
the `cloud` function (projects, quotas; `docs/CLOUD_API.md`) and Supabase Storage (the private
`media` bucket). The routes stay off (`not_configured`) until the steps below are done; nothing
else in the app depends on them.

## 1. Supabase plan and Storage
1. **Upgrade the project to the Pro plan** (Dashboard → Organization → Billing). The free plan
   caps every uploaded file at 50 MB and all storage at 1 GB, which videos outgrow at once.
2. Storage → Settings: **global file size limit 4 GB** (the `media` bucket asks for 4 GB; a lower
   global limit wins). The bucket itself is created by the migration.

## 2. Email codes
Supabase's own mailer sends only a couple of emails an hour, for testing.
1. A sender for your domain, for example [Resend](https://resend.com) (free tier: 3,000 emails a
   month): add and verify the domain, create an API key.
2. Authentication → Emails → SMTP settings: on; host `smtp.resend.com`, port `465`, user `resend`,
   password = the API key, sender `PreCut <noreply@your-domain>`.
3. Authentication → Emails → Templates: in **Magic link** and **Confirm signup**, show the code:
   ```html
   <h2>Your PreCut code</h2>
   <p>Enter this code in PreCut: <strong style="font-size:24px;letter-spacing:4px">{{ .Token }}</strong></p>
   <p>It works for 10 minutes. If you didn't ask for it, ignore this email.</p>
   ```
   Subject: `Your PreCut code: {{ .Token }}`.
4. Authentication → Sign In / Providers → Email: email OTP expiry 600 seconds, length 6.
   Authentication → Rate limits: emails sent per hour, for example 100.

## 3. Google sign-in
1. [Google Cloud Console](https://console.cloud.google.com) → the project Play Integrity uses →
   APIs & Services → **OAuth consent screen**: External, app name PreCut, support email, the
   website's privacy page, scopes `openid`, `email`, `profile`. Publish it (no verification is
   needed for these scopes).
2. Credentials → Create OAuth client ID → **Web application**, name "PreCut sign-in". Keep its
   client ID and secret. No redirect is needed (the app sends an ID token).
3. Credentials → Create OAuth client ID → **Android**, package `com.savie.precut`, SHA-1 of the
   **upload key** (`keytool -list -v -keystore keystore/precut-upload.jks` → SHA1). Once on Play, a
   second Android client with the SHA-1 of **Play's app signing key** (Play Console → Setup → App
   signing). Debug builds need a third with the debug key's SHA-1 (`./gradlew signingReport`).
4. Supabase → Authentication → Sign In / Providers → **Google**: on; Client IDs = the web client ID,
   then the Android client IDs, comma-separated; Client secret = the web client's secret.

## 4. The app's keys (`cloud.properties`, gitignored)
Add two lines, then copy the whole file into the GitHub secret `PRECUT_CLOUD_PROPERTIES` again:
```
supabasePublishableKey=sb_publishable_...   (Settings → API Keys; public by design)
googleWebClientId=....apps.googleusercontent.com   (the web client from step 3.2)
```
The Supabase address comes from the existing `cloudUrl`.

## 5. Deploy
```
npx supabase db push --remote --yes
npx supabase functions deploy cloud --use-api --no-verify-jwt --yes
```
The daily clean-up (`.github/workflows/cloud-cleanup.yml`) uses the existing
`PRECUT_RELEASE_SECRET`.

## Costs (Supabase Pro, November 2026 prices)
- $25 a month for the plan, including 100 GB of storage and 250 GB of downloads.
- Above that: storage about $0.021 per GB a month, downloads about $0.09 per GB.
- Limits per account (`cloud_config`, change any time): 2 GB free, 50 GB Pro, 4 GB a file.
  Example: 1,000 free accounts using 1 GB each is 1 TB, about $19 a month above the plan.
- Files no project has used for 30 days are deleted by the daily clean-up; deleting an account
  deletes its files at once.
- Email: Resend's free tier, then from $20 a month for 50,000 emails.
