# Design prompt: PreCut new screens (Phases 23–27)

Paste everything inside the code block into your design tool. If it works better in batches,
paste "Context" + "Design system" + "Rules" once, then add one screen group at a time.
Tip: also attach 2–3 screenshots of current PreCut screens (Home, Editor) so the new designs match exactly.

```
CONTEXT
Design new screens for PreCut, an existing Android video editor for creators. The app already has a polished dark design; these new screens must look like they belong to it. The feel is premium, calm and creator-focused, as polished as the leading mobile video editors, but simpler: every screen has one clear primary action, short copy, and generous spacing. Android, 412 x 915 dp frames, status bar and gesture navigation bar visible, portrait.

DESIGN SYSTEM (match exactly)
- Dark only. Background #0E0E10, surface #17171A, elevated surface / sheets #222226, raised #2E2E33, outlines #3A3A40
- Text #F2F2F5, secondary #9A9AA3, faint #6B6B74
- Accent #FF4D6D (selected states, highlights), accent fill for primary buttons #D92B50 with white text, accent soft background #5C1426
- Success #38D9A9, warning #FFD43B
- "Pro" badge: small pill, accent soft background, accent text, 10sp bold "PRO"
- Typography: Roboto Flex (headlines 24–32sp bold, titles 18sp semibold, body 14–15sp), Roboto Mono for timecodes and counters
- Icons: Material Symbols Rounded, 24dp
- Radii: 12dp cards, 20dp top corners on bottom sheets, pill-shaped primary buttons, 48dp minimum touch targets, 16dp side margins, 8dp grid

GROUP A — ONBOARDING (first launch only, every screen has "Skip" top-right and a progress indicator of 5 dots)
A1. Welcome: full-bleed looping video collage of vibrant creator clips with a dark gradient at the bottom; PreCut logo; headline "Edit like a pro. In minutes."; subline "Free pro-level tools, no watermark."; primary button "Get started"; small text link "I already have projects".
A2. What will you create?: title, subtitle "Pick all that apply"; 2x2 grid of large selectable cards with an illustration and label: "Short videos for social", "YouTube videos", "Travel & family memories", "Business & brand"; selected cards get an accent border and check; primary "Continue" (disabled until one is picked).
A3. Your editing experience: three stacked option cards with icon, title and one-line description: "New to editing — keep it simple", "Some experience", "Pro — show me everything"; note under them: "You can change this any time in Settings."
A4. Notifications priming: bell illustration with a mini export-progress notification mock; title "Know when your video is ready"; text "We'll only notify you about exports and important updates."; primary "Allow notifications", text button "Not now".
A5. Pro trial offer (soft, never pushy): close (X) top-left AND a clear "Continue free" text button at the bottom; headline "Try PreCut Pro free for 7 days"; 4 benefit rows with icons: "AI Edit — describe it, we edit it", "Turn long videos into shorts", "Remove pauses and filler words", "Brand kit and one-tap multi-format export"; plan toggle Yearly (with "Best value" tag) / Monthly showing placeholder prices; primary "Start free trial"; fine print "Cancel any time in Google Play. You won't be charged until the trial ends."
A6. First-run coach marks on the Editor: dimmed editor with a spotlight cut-out around the timeline and a tooltip bubble "Drag to move through your video. Pinch to zoom." with "1 of 3" and "Next".

GROUP B — PRO AND PAYWALL
B1. Paywall (opened from a Pro tool): header illustration related to the tool that was tapped (e.g. AI Edit), headline "Unlock AI Edit with Pro", 5 benefit rows, plan cards (Yearly selected, Monthly), primary "Continue", "Restore purchases" link, small "Free forever: no watermark, 4K export, all filters and effects" reassurance line.
B2. Editor toolbar with Pro tools: show the bottom toolbar where 2 tools carry the small PRO badge, plus a tooltip chip "2 free uses left this month".
B3. Settings → PreCut Pro (subscribed): plan name, renewal date, "Manage in Google Play", "Restore purchases", usage meters for "Cloud caption minutes" and "AI Edit runs" with Roboto Mono counters.
B4. Simple mode vs Full mode: two versions of the editor bottom toolbar side by side — Simple (6 tools + "More") and Full (all tools, horizontally scrollable).

GROUP C — UPDATES
C1. Forced update overlay: full-screen, cannot be dismissed (no close button); app logo, illustration of a phone with an upward arrow; title "Update required"; text "A new version of PreCut is available. Update to keep editing — your projects are saved."; primary "Update now"; small version text "You have 1.4.0 · Latest 1.5.0" in Roboto Mono.
C2. Same overlay in the "Downloading update" state with a progress bar and percentage.
C3. Optional update banner on Home: slim card at the top of the projects list "A new version is ready" with "Update" button and dismiss (X).
C4. What's new bottom sheet after updating: title "What's new in 1.5", 3–4 bullet rows with icons, primary "Got it".

GROUP D — AI EDIT (agentic editing)
D1. AI Edit prompt screen: top bar with back and title "AI Edit"; horizontal strip of the selected clip thumbnails; large multiline prompt field with placeholder "Describe your video… e.g. A 30-second upbeat TikTok with captions, cut to the beat"; suggestion chips below ("30s TikTok, upbeat", "Calm travel recap", "Remove pauses + captions", "Highlight reel"); optional settings row (Length: Auto, Aspect: 9:16, Music: Choose); primary "Create edit" with a sparkle icon; small "2 free runs left" note.
D2. AI Edit working: preview area showing a softly animated filmstrip; step list with live status: "Understanding your clips ✓", "Picking the best moments ✓", "Cutting to the beat…" (spinner), "Adding captions" (pending); "Cancel" text button.
D3. AI Edit review: preview player at the top playing the result; summary line "12 changes · 0:31"; grouped change list (Cuts, Captions, Music, Look) with a toggle per change and an expand chevron; sticky bottom bar with "Discard", "Refine", primary "Apply". 
D4. Refine sheet: bottom sheet with a prompt field "What should change?" and chips ("Make it shorter", "Faster pace", "Different music", "Bigger captions").

GROUP E — PRO TOOLS
E1. Edit by transcript: preview on top; below, the transcript as readable paragraphs with word timings; filler words ("um", "like") highlighted in faint accent; selected words struck through to show they'll be cut; top actions "Remove filler words (14)" and "Remove silences (9)"; bottom bar "Apply cuts".
E2. Long video to shorts results: grid of 5 generated vertical clip cards with thumbnail, duration, a short title from the transcript and a "Hook score" pill; each card has "Open" and "Export"; top "Export all".
E3. One-tap multi-format export: three preview frames side by side (9:16, 1:1, 16:9) each showing the auto-reframed shot with a checkbox; resolution selector; primary "Export 3 videos".
E4. Brand kit: sections for Logo (upload tile), Colors (5 swatches + add), Fonts (2 font pickers), Intro and Outro (clip tiles), and a toggle "Apply to new projects automatically".
E5. Hook coach result: card overlay on the editor with a score ring, 3 suggestions each with a one-line reason and an "Apply" button (e.g. "Start with your strongest shot", "Add a caption in the first second").

RULES
- Original design only: no third-party brand names, logos or trademarks anywhere (no social network logos; use plain text labels)
- Realistic sample content and copy; no lorem ipsum
- Every screen has exactly one primary (accent-filled) button
- Pro is always presented respectfully: free paths stay visible, nothing looks like a trick
- Keep all components consistent with each other and with the existing PreCut screens
```
