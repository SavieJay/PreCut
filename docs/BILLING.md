# PreCut Pro: Google Play subscriptions (Phase 25)

PreCut sells one thing: the **PreCut Pro** subscription, through Google Play Billing only (Play policy for digital features in a Play app). Purchases are verified by PreCut's own `cloud` function with the Google Play Developer API; the app never decides Pro on its own (docs/CLOUD_API.md: `POST /purchase`, `POST /rtdn`, the `pro` object in `Status`).

## Play Console setup (your steps)
1. **Subscription:** Monetize → Products → Subscriptions → Create subscription, product ID **`precut_pro`** (the app and `PRO_PRODUCT_IDS` use this id), name "PreCut Pro".
2. **Base plans** on it, both auto-renewing:
   - **`yearly`**: billing period 1 year.
   - **`monthly`**: billing period 1 month.
   Set the prices (Play converts to every country; check Nigeria's naira price by hand).
   Grace period and account hold: keep Play's defaults (grace period on; the app shows "Google Play couldn't take your payment" during it and "Pro is on hold" after).
3. **Free trial offer** on the yearly base plan (and the monthly one if you like): offer ID `free-trial`, eligibility "New customer acquisition: never had this subscription", phase "Free trial, 7 days". Play only shows the offer to people it applies to; the paywall then says "Start 7-day free trial".
4. Activate the base plans and the offer.
5. **License testers:** Setup → License testing → add the Google accounts that test purchases (they're charged nothing; test subscriptions renew every few minutes).

## Server setup (your steps, then Claude can deploy)
1. **Service account access:** Play Console → Users and permissions → Invite new users → the service account's email (the one in `GOOGLE_SERVICE_ACCOUNT_JSON`) → App permissions → PreCut → tick **View financial data, orders and cancellation survey responses** and **Manage orders and subscriptions**. It can take up to a day before Google accepts the account.
2. **Google Play Android Developer API:** enable it in the Google Cloud project of that service account (APIs & Services → Library).
3. **Real-time developer notifications:**
   - In Google Cloud → Pub/Sub, create a topic, e.g. `precut-rtdn`, and on it grant **Pub/Sub Publisher** to `google-play-developer-notifications@system.gserviceaccount.com`.
   - Create a **push** subscription on the topic with endpoint `https://auekkmbwfpqofjqugjsr.supabase.co/functions/v1/cloud/rtdn?secret=<RTDN_SECRET>` (a long random value; put the same value in `supabase/.env.secrets` as `RTDN_SECRET`). Leave "Enable authentication" off: the secret is the check, and every notification is looked up with Google anyway.
   - Play Console → Monetize → Monetization setup → Real-time developer notifications → topic name `projects/<project>/topics/precut-rtdn` → Send test notification (the function logs `rtdn ignored` for the test).
4. Deploy: `npx supabase db push --linked` (the `pro` migration), `python3 -I tools/cloud_secrets.py --push`, `npx supabase functions deploy cloud --use-api --no-verify-jwt`.

## How it works
- **Buying:** the paywall shows the yearly and monthly plans with Play's prices (`queryProductDetails`), and Play's own screen takes the payment. The purchase token goes to `POST /purchase` (with Play Integrity, bound to the token); the server asks Google (`purchases.subscriptionsv2.get`), keeps the state and end date, links the purchase to this install, acknowledges it (Google refunds unacknowledged purchases after three days), and answers with the install's plan and quotas.
- **Renewals, cancellations, payment problems, refunds:** Google sends a notification to `/rtdn`; the server looks the purchase up again and saves what Google says. Notifications for purchases no app has sent are ignored.
- **Restore purchases** (paywall and Settings): sends every PreCut Pro purchase this Google account owns; the server links the valid ones to this install. The app also does this quietly on start when Play knows a purchase the server hasn't confirmed (a purchase cut short, a reinstall).
- **Offline:** the last answer is kept; Pro holds until 3 days past the paid period's end and while the answer is under 30 days old.
- **What Pro changes now:** the monthly quotas (`cloud_config`: `pro_seconds_per_month` 300 minutes of captions, `pro_edit_runs_per_month` 100 AI edit requests, against 30 minutes and 10 free). Phases 26–27 add the Pro features, each with a small free taste.
- **Gating rules:** Pro tools carry a PRO badge and open the paywall when tapped by a free user with no free uses left; the paywall never opens by itself, during an export, or in the middle of an edit.

## Testing
- JVM: `ProRulesTest` (offline expiry, notices, offers, gating), `CloudClientTest` (the purchase call), `OnboardingViewModelTest` (the trial page). Deno: `purchase_test.ts` (verification, acknowledgement, notifications, the Google API mapping) and the `pro` SQL tests.
- On a phone with a license tester account: `docs/DEVICE_CHECKLIST.md` 4o.
