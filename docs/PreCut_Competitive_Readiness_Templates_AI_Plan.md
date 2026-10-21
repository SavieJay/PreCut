# PreCut Competitive Readiness Plan

## Product Update: Ready-to-Use Templates and AI Features

**Document type:** Product roadmap and implementation brief\
**Product:** PreCut --- native Android video editor\
**Status:** Proposed plan for implementation and validation\
**Purpose:** Consolidate the competitive-readiness recommendations with
the proposed Template Studio and AI Creation Studio into one actionable
document for the product and coding agent.

------------------------------------------------------------------------

## 1. Executive Summary

PreCut already has an ambitious foundation for a native Android video
editor. Its planned and implemented editing scope includes multi-clip
timelines, text, audio, transitions, keyframes, effects, overlays, color
adjustments, speed controls, and configurable export.

The next milestone should not be defined only by the number of features
added. It should focus on **Competitive Readiness**: prove that existing
features work reliably on physical devices, make common editing tasks
intuitive, and add capabilities that help users produce good videos
faster.

Two major product additions are recommended:

1.  **Template Studio** --- a library of ready-to-use, editable video
    templates that users can populate with their own footage and
    customize.
2.  **AI Creation Studio** --- AI-assisted tools for captions, automated
    edits, audio enhancement, background removal, smart reframing, and
    prompt-to-edit workflows.

The most valuable initial combination is: - A polished pack of editable
templates. - Automatic captions with editable timing and styling. - Beat
detection and music-synchronized editing. - An AI-assisted Auto Edit
workflow that creates a normal, editable PreCut project.

AI and online services should be modular and optional. PreCut should
preserve its local-first editing experience wherever practical, clearly
identify network-dependent features, and provide sensible fallbacks when
AI processing is unavailable.

## 2. Product Goals

### Primary goals

-   Help beginners create a polished video without manually building
    every timeline element.
-   Preserve full manual control for experienced editors.
-   Reduce the time required to go from imported footage to a shareable
    video.
-   Improve the reliability and consistency of preview and export.
-   Keep generated edits editable in the existing project and timeline
    model.
-   Make feature status explicit: implemented, tested on emulator,
    verified on a physical device, or not yet verified.

### Non-goals for the first release of these additions

-   Replacing every cloud-based generative video feature found in
    competing products.
-   Building a large public template marketplace before the template
    format and licensing workflow are stable.
-   Making every AI feature work fully offline on every Android device.
-   Generating a final flattened video that users cannot meaningfully
    edit.
-   Expanding the feature list at the expense of crash resistance,
    project recovery, or export correctness.

## 3. Current Foundation and Constraints

The existing project plan describes an architecture built around: -
Jetpack Compose UI. - ViewModel and edit-state handling. - An edit
reducer and project model. - A composition builder. - Media3-based
preview and export through Transformer.

The plan already covers a broad set of manual editing capabilities,
including text and sticker layers, transitions, effects, color grading,
keyframes, audio, speed controls, blend modes, and export settings.
However, several features still need physical-device validation, and the
plan contains some inconsistencies that should be resolved before adding
more complexity.

### Required plan cleanup

1.  Reconcile the original scope note that lists non-Normal blend modes
    as unsupported with the later phase that says multiple blend modes
    have been implemented.
2.  Separate implementation status from validation status. A feature can
    be coded but not verified on a real phone.
3.  Identify which features depend on network access, downloadable
    assets, or external AI services.
4.  Verify that all template and AI-generated edits can be represented
    by the existing project model.
5.  Confirm preview/export parity for each supported effect, transition,
    blend mode, and media type.

Media3 can support compositions, effects, overlays, and multiple media
assets, but specific composition operations have API limitations.
Validate each required transition and audio-mixing behavior against the
exact Media3 version in use rather than assuming every editing operation
is supported identically in preview and export.

------------------------------------------------------------------------

## 4. Template Studio

### 4.1 Product vision

Template Studio should let users select a professionally designed
editing structure, replace its media with their own clips or photos,
personalize the content, and then continue editing in PreCut's normal
timeline.

**Core principle:** A template is an editable project recipe, not simply
a pre-rendered video.

### 4.2 Template categories

#### A. Cinematic and travel

-   Travel montages and destination highlights.
-   Cinematic color grades and title treatments.
-   Slow-motion moments and speed changes.
-   Music-led sequences and transitions.
-   Vlog intros and outros.

#### B. Social media and trends

-   Short-form vertical videos.
-   Fast-paced cuts and animated captions.
-   Beat-synchronized transitions.
-   Kinetic typography and social-safe layouts.
-   Reusable formats for Reels, Shorts, and similar platforms.

#### C. Business and product promotion

-   Product showcases and advertisements.
-   App and website launch promos.
-   Service and business introductions.
-   Logo placement, brand colors, and calls to action.
-   Reusable brand styles and typography.

#### D. Birthday, events, and photo stories

-   Birthday and celebration videos.
-   Weddings and family memories.
-   Church and community event highlights.
-   Photo-to-video slideshows.
-   Name, date, message, and event-detail placeholders.

#### E. Creator and educational content

-   Talking-head videos.
-   Tutorial and explainer layouts.
-   Intro/outro sequences.
-   Lower thirds and section headings.
-   Caption-first layouts.

### 4.3 User workflow

1.  User opens Templates.
2.  User browses or searches categories and previews a sample.
3.  User taps **Use template**.
4.  PreCut explains the required number and type of media slots.
5.  User selects photos and video clips.
6.  PreCut maps selected media into template slots and fits or trims
    them according to template rules.
7.  User previews the result.
8.  User edits text, clips, music, colors, transitions, or other
    supported elements.
9.  User chooses **Edit timeline** to open the generated project in the
    full editor.
10. User exports through the existing export pipeline.

The workflow should handle missing media, extra media, media with
incompatible aspect ratios, very short clips, and cancelled selection
gracefully.

### 4.4 Template data model

Define a versioned template schema. The exact Kotlin classes and
serialization format should be selected after inspecting the existing
project model.

A template definition should support:

-   Stable template ID and schema version.
-   Title, description, category, tags, and preview asset.
-   Author or source attribution.
-   Required media slots and accepted media types.
-   Suggested and minimum slot durations.
-   Slot order and placement on the timeline.
-   Clip transforms, crop/fill behavior, and aspect ratio.
-   Text placeholders, fonts, styles, animations, and editable values.
-   Audio and music slots with volume and fade settings.
-   Transitions and supported effects.
-   Overlay and sticker placement.
-   Template duration and target aspect ratio.
-   Optional brand colors, logo slots, and reusable style tokens.
-   Asset license and attribution metadata where applicable.
-   Compatibility requirements and minimum app/template version.

Use stable identifiers for template elements so the user can replace or
edit an element without breaking unrelated elements.

### 4.5 Template engine requirements

-   Convert a template into a normal PreCut project.
-   Reuse the existing project, timeline, preview, and export systems.
-   Resolve media slots to user-selected files.
-   Preserve editable text, audio, effects, and transitions where
    supported.
-   Validate unsupported operations before project creation.
-   Provide safe defaults when a requested feature is not supported.
-   Never silently discard an element when applying a template.
-   Save the generated project as a separate project; do not overwrite
    source media.
-   Preserve template metadata so users can identify the original
    template later.
-   Support template schema versioning and migration.

### 4.6 Template library and asset delivery

For the first release, prefer a curated, bundled starter pack with an
architecture that can later support downloadable packs.

Requirements: - Template category and search. - Preview thumbnail and
sample playback. - Favorites and recently used templates. - Download
progress and cancellation if downloadable packs are introduced. - Cache
management and storage-aware behavior. - Clear attribution and license
tracking for fonts, music, footage, and effects. - A way to remove
downloaded assets without damaging projects that depend on them; warn
the user or preserve required assets when appropriate. - A consistent
visual quality bar for included templates.

Do not ship third-party music, fonts, footage, or effects without
verifying usage and redistribution rights.

### 4.7 Template acceptance criteria

A template is ready only when: - It previews without crashing. - Its
media slots are clearly described. - Selected media map to the expected
slots. - Users can replace media after creation. - Text and supported
elements remain editable. - The generated project opens in the normal
timeline editor. - Preview and exported output match within documented
tolerances. - Unsupported operations are handled explicitly. - Missing
or moved media produces a recoverable error. - The template is tested on
at least one lower-end and one recent physical Android device. - License
and attribution requirements are satisfied.

------------------------------------------------------------------------

## 5. AI Creation Studio

### 5.1 Product vision

AI Creation Studio should make complex editing tasks easier without
hiding the underlying project from the user. AI should propose or
generate edits that become normal, editable PreCut timeline elements.

### 5.2 AI feature roadmap

#### Feature 1 --- Automatic captions

**Priority: Highest**

Capabilities: - Speech-to-text transcription. - Caption timing aligned
with speech. - Punctuation and sentence segmentation. - Editable text
and timing. - Caption styles and presets. - Word-by-word or
phrase-by-phrase highlighting where supported. - Manual correction and
regeneration of selected segments. - Caption placement that respects the
selected aspect ratio and social-safe areas. - Optional subtitle export,
if supported by the export architecture.

Acceptance criteria: - User can generate captions from a clip or
supported audio track. - Captions are inserted as editable timeline
elements. - Each caption has editable text, start time, end time, and
style. - User can correct transcription mistakes without regenerating
the entire track. - Caption timing remains correct after supported
timeline edits. - Empty speech, noisy audio, unsupported languages, and
processing errors are handled clearly. - The app reports whether
processing occurs on-device or requires a network service. - Long
recordings do not block the UI. - Progress, cancellation, and retry are
supported where feasible.

#### Feature 2 --- Beat detection and music synchronization

**Priority: Very high**

Capabilities: - Analyze an audio track for likely beat positions. -
Display beat markers in the timeline. - Allow the user to enable or
disable beat snapping. - Use detected beats as suggestions for clip cuts
and transitions. - Let users change the music track and regenerate the
proposed edit.

Acceptance criteria: - Beat detection runs off the main thread. - Users
can manually adjust or ignore beat markers. - Detection does not modify
the project destructively. - A regenerated edit preserves the user's
original media and can be undone. - Test quiet, loud, irregular, and
speech-heavy audio.

#### Feature 3 --- AI-assisted Auto Edit

**Priority: Very high**

Initial version: - User selects media, target duration, aspect ratio,
and a style. - The system proposes clip order, trims, transitions, title
placement, and music timing. - The edit can use an existing template as
a starting point. - User previews the proposed result and accepts,
rejects, or regenerates it. - Accepted results become a normal editable
project.

Later improvements: - Scene and shot detection. - Duplicate or blurry
shot detection. - Candidate selection for visually interesting
moments. - Speech-aware clip selection. - Story structure suggestions
such as hook, body, and ending. - Automatic intro/outro and title
suggestions.

Do not describe a rule-based montage generator as fully AI-powered if it
does not use an AI model. Name the capability honestly and keep the
architecture ready for more advanced analysis.

Acceptance criteria: - Original media remain unchanged. - Generated
clips reference valid source ranges. - The user can inspect and change
every proposed edit. - Regeneration does not overwrite the original
project. - The user can undo the operation. - Empty, incompatible, or
insufficient media selections produce clear guidance.

#### Feature 4 --- Voice enhancement

**Priority: High**

Potential capabilities: - Reduce background noise. - Improve speech
clarity. - Normalize speech loudness. - Reduce unwanted hum where
feasible. - Optional vocal/music separation in a later stage.

Acceptance criteria: - Processing is asynchronous and cancellable where
feasible. - The user can compare original and processed audio. -
Original audio remains available. - Processed output does not
unexpectedly clip or desynchronize. - Quality and performance are tested
on speech, music, and mixed recordings. - Processing requirements and
any network dependency are disclosed.

#### Feature 5 --- Background removal

**Priority: High, technically demanding**

Capabilities: - Segment a person or supported subject from video. -
Replace the background with a color, image, or video. - Provide a
preview and edge-quality controls if feasible. - Preserve timing and
audio.

Acceptance criteria: - Segmentation is temporally stable enough for
supported use cases. - Hair, fast movement, occlusion, and complex
backgrounds are tested. - Unsupported devices or media receive a clear
fallback. - Processing does not freeze the UI. - Output is checked for
frame alignment, audio sync, memory use, and export correctness.

#### Feature 6 --- Smart reframe

**Priority: High**

Capabilities: - Reframe landscape video to vertical or square formats. -
Track a main subject and keep it within the output frame. - Allow manual
correction when tracking is wrong. - Support keyframe adjustments to the
crop.

Acceptance criteria: - Users can compare original and reframed output. -
Tracking can be adjusted manually. - Crop changes remain editable. -
Fast motion, multiple people, and subject exits are tested. - Preview
and export use consistent crop behavior.

#### Feature 7 --- Prompt-to-edit assistant

**Priority: Later phase**

Example request: "Make a 30-second product advertisement from these
clips, with clean titles and upbeat music."

Initial scope: - Interpret a prompt into a structured edit brief. - Ask
for missing essentials such as target duration, format, and preferred
style. - Recommend templates and editing actions. - Create a storyboard
or timeline proposal using user-provided media and installed assets. -
Let the user review the proposal before applying it.

Later scope: - Optional generated voiceovers. - Optional generation of
titles, scripts, or supporting assets. - Optional integrations with
external generative video services.

Safety and reliability: - Do not claim a generated asset exists until it
has actually been created. - Identify which operations require network
access, accounts, credits, or paid services. - Ask before sending
personal media to an external service. - Provide clear errors and a
retry path. - Keep the generated result editable.

### 5.3 Future AI candidates

Consider only after the initial AI workflow is reliable: - Automatic
silence removal. - Scene and shot detection. - Object tracking. -
Stabilization. - Smart color matching. - AI voiceovers. - Vocal
isolation. - Background removal improvements. - Image or video
generation. - Advanced slow motion using optical-flow techniques. -
Automatic social-platform reframing and format variants.

These features should be ranked by user value, device feasibility, cost,
privacy, licensing, and implementation complexity before being
scheduled.

------------------------------------------------------------------------

## 6. Combined Workflow: Templates + AI

The key experience should connect templates and AI instead of treating
them as unrelated features.

### Example: Event video

1.  User selects 12 event clips and a few photos.
2.  PreCut analyzes media duration and available audio.
3.  PreCut suggests event and celebration templates.
4.  User selects a template.
5.  PreCut maps media to template slots.
6.  AI-assisted tools propose a usable clip order and timing.
7.  Beat detection can align selected cuts with the soundtrack.
8.  Automatic captions can transcribe a speech or message.
9.  User edits names, titles, media, music, and timing.
10. PreCut opens the result in the normal timeline editor.
11. User exports using the existing export system.

### Required principle

AI-generated edits and template-based edits must converge on the same
project model. Avoid creating a separate timeline format for AI or
templates.

### Suggested flow

`Select media → Analyze → Recommend template → Generate editable timeline → Review and customize → Export`

At every stage, provide a clear way to cancel, go back, change media, or
continue manually.

------------------------------------------------------------------------

## 7. Release Hardening Before Major Expansion

Before expanding the feature surface, complete a dedicated reliability
pass.

### 7.1 Build and release verification

-   Exercise the R8/minified release build feature by feature.
-   Add keep rules only when supported by observed failures or
    documented requirements.
-   Test the signed release artifact on physical devices.
-   Verify app startup, project creation, media import, editing,
    save/reopen, and export.
-   Confirm versioning and crash reporting strategy.
-   Verify privacy disclosures and store materials.

### 7.2 Physical-device test matrix

Test on at least: - One lower-end device with limited RAM and processing
power. - One recent mid-range device. - One high-performance device if
available.

Test: - Long and short clips. - Portrait and landscape media in the same
project. - MP4/H.264, HEVC, MOV where supported, images, and clips
without audio. - Multiple audio and visual layers. - Heavy effects and
keyframes. - Backgrounding the app during editing and export. - Low
storage, export cancellation, and interrupted processing. - Device heat,
battery use, and memory pressure. - Reopening saved projects and
relinking missing media. - Preview/export consistency.

### 7.3 Editing interaction audit

Explicitly test: - Dragging clips to a different timeline time. - Moving
text and sticker clips independently of the playhead. - Trimming and
splitting clips. - Moving audio clips and adjusting fades. - Snapping at
different timeline zoom levels. - Multi-select and group operations. -
Undo/redo after complex edits. - Editing short clips and overlapping
layers. - Touch target behavior when panels open or close. - Accidental
playhead scrubbing versus dragging an object.

The editor should make it obvious which object is selected and what a
drag gesture will move.

### 7.4 Export and project safety

-   Ensure export progress, cancellation, and error states are reliable.
-   Test long exports and low-storage conditions.
-   Confirm that failed export does not corrupt the project.
-   Ensure background export behavior is correct.
-   Verify project recovery after app termination.
-   Check that preview and exported video use consistent effects,
    timing, transformations, and audio behavior.
-   Verify HDR handling on actual devices; do not treat unverified
    behavior as complete.

------------------------------------------------------------------------

## 8. Architecture and Engineering Guidelines

### 8.1 Reuse the existing project model

Template and AI features should create or modify standard project
entities using the existing reducer/state-update pathway where possible.

### 8.2 Keep processing off the UI thread

Media analysis, transcription, beat detection, segmentation, audio
enhancement, and template asset preparation must not block Compose
rendering or timeline interaction.

### 8.3 Make long-running work observable

For time-consuming operations, provide: - Progress where measurable. -
Cancellation where technically safe. - Error reporting. - Retry
behavior. - A clear final state. - Recovery from app backgrounding or
process recreation where applicable.

### 8.4 Preserve non-destructive editing

-   Never overwrite source media.
-   Store source ranges and edit decisions rather than flattening the
    project prematurely.
-   Keep original audio available when applying enhancement.
-   Allow users to undo generated changes.
-   Preserve the pre-generation project or create a recoverable revision
    before applying a major AI edit.

### 8.5 Feature capability and fallback model

Each AI or template operation should declare: - Required input media. -
Supported codecs and dimensions. - Minimum device or runtime
requirements, if any. - Whether processing is on-device or remote. -
Network requirements. - Expected storage needs. - Supported
preview/export behavior. - Known limitations and fallback behavior.

### 8.6 Privacy and licensing

-   Tell users when media leaves the device.
-   Obtain appropriate consent before uploading user media to remote
    processing.
-   Avoid sending media to a remote service by default without clear
    disclosure.
-   Track licensing and attribution for templates, fonts, music,
    footage, and generated assets.
-   Provide a privacy policy consistent with actual data handling.

------------------------------------------------------------------------

## 9. Prioritized Roadmap

### Milestone 0 --- Competitive Readiness and Release Hardening

**Goal:** establish a dependable baseline.

Tasks: - Reconcile plan contradictions. - Audit existing feature
status. - Run the R8 release build and physical-device tests. - Validate
project save/reopen and recovery. - Test timeline interactions and
preview/export parity. - Benchmark timeline performance and export
behavior. - Fix release-blocking issues before expanding scope.

**Exit criteria:** no known release-blocking crashes, lost edits,
unrecoverable projects, or consistently broken exports in the tested
matrix.

### Milestone 1 --- Template Studio Foundation

**Goal:** ship a small but polished collection of editable templates.

Tasks: - Define the versioned template schema. - Map template elements
to existing project entities. - Implement media-slot replacement. -
Build template browsing, preview, categories, and favorites. - Create an
initial curated template pack. - Add license and attribution metadata. -
Test generated projects and exports.

**Exit criteria:** users can choose a template, populate it, customize
it, open it in the regular timeline, save it, reopen it, and export it.

### Milestone 2 --- Automatic Captions and Caption Styles

**Goal:** add a high-value AI-assisted creator workflow.

Tasks: - Choose and evaluate a transcription approach. - Implement
background processing and cancellation. - Convert transcription segments
into editable caption timeline elements. - Add caption styling
presets. - Support correction, timing edits, and regeneration. - Add
clear processing/privacy indicators. - Test language coverage and noisy
audio.

**Exit criteria:** captions are editable, timing is stable, errors are
recoverable, and long jobs do not block the UI.

### Milestone 3 --- Music Sync and AI-Assisted Auto Edit

**Goal:** shorten the path from imported footage to a finished draft.

Tasks: - Implement beat analysis and timeline markers. - Build a
non-destructive montage proposal system. - Add duration, aspect ratio,
and style options. - Integrate templates into Auto Edit. - Add preview,
accept/reject, regenerate, and undo. - Verify that output is a normal
editable project.

**Exit criteria:** users can create a draft quickly, inspect every
decision, modify the timeline, and export successfully.

### Milestone 4 --- Audio and Visual AI

**Goal:** improve common footage and audio quality.

Tasks: - Evaluate voice enhancement. - Evaluate background-removal
options. - Implement smart reframe. - Define device capability and
performance requirements. - Add original-versus-processed comparisons. -
Test thermal, memory, and export behavior.

**Exit criteria:** each feature meets its own quality bar on supported
devices and fails gracefully elsewhere.

### Milestone 5 --- Prompt-to-Edit and Expanded Creative Ecosystem

**Goal:** make PreCut's workflows more discoverable and extensible.

Tasks: - Convert natural-language requests into structured edit
briefs. - Recommend templates and editing actions. - Build a curated
asset and preset workflow. - Consider optional voiceover and external
generative integrations. - Add downloadable template packs only after
license, cache, and compatibility handling are stable.

**Exit criteria:** generated proposals are transparent, editable,
recoverable, and honest about network or paid-service requirements.

------------------------------------------------------------------------

## 10. Acceptance Criteria and Definition of Done

A feature is not complete merely because its screen or button exists.

For every new feature, require: 1. Functional implementation. 2.
Loading, empty, error, cancellation, and retry states. 3. Project
save/reopen validation. 4. Preview and export validation. 5. Undo/redo
or a documented reason why it is not applicable. 6. Tests for short and
long media and relevant format combinations. 7. Physical-device testing
on supported device classes. 8. Performance and memory checks. 9.
Privacy and licensing review where relevant. 10. Documentation of known
limitations.

Track status separately: - **Implemented** - **Unit-tested** -
**Emulator-tested** - **Physical-device-tested** -
**Release-build-verified** - **Known limitations documented**

Do not mark a feature as fully verified based only on a static code
check or emulator run.

------------------------------------------------------------------------

## 11. Success Metrics

### Editing experience

-   Time from opening the app to first successful export.
-   Time from selecting media to completing a template.
-   Percentage of template projects that users customize and export.
-   Percentage of AI proposals accepted, edited, or rejected.
-   Frequency of undo operations after generated edits.
-   Rate of failed media replacement or timeline operations.

### Reliability

-   Crash-free editing sessions.
-   Export success and failure rate.
-   Lost or unrecoverable project incidents.
-   Preview/export mismatch reports.
-   Project reopen success rate.
-   Cancellation and recovery success.

### Performance

-   Timeline playback and scrubbing responsiveness.
-   Export time relative to video duration and resolution.
-   Peak memory usage.
-   Device temperature and battery impact during intensive processing.
-   AI processing time and cancellation rate.

### Product value

-   Template usage and repeat usage.
-   Caption generation completion rate.
-   Auto Edit completion and export rate.
-   User-reported editing difficulty.
-   Retention after first successful export.

Collect analytics only with appropriate disclosure and privacy
practices.

------------------------------------------------------------------------

## 12. Risks and Mitigations

  -----------------------------------------------------------------------
  Risk                                Mitigation
  ----------------------------------- -----------------------------------
  Too many features before            Make release hardening the first
  reliability is proven               milestone

  Templates produce broken or         Generate standard projects and
  uneditable projects                 validate each template end to end

  Preview differs from export         Test supported operations through
                                      both pipelines

  AI processing is too slow on        Benchmark early; use
  lower-end phones                    smaller/on-device models or
                                      optional remote processing

  AI output is inaccurate             Keep all generated output editable
                                      and provide manual correction

  Network-dependent features fail     Clearly identify dependencies and
  offline                             offer manual workflows

  Asset licensing problems            Track source, rights, attribution,
                                      and allowed redistribution

  AI unexpectedly changes user work   Use non-destructive operations,
                                      revisions, and undo

  Template schema changes break older Version schemas and implement
  projects                            migration or compatibility handling

  Excessive scope delays release      Ship captions and a small curated
                                      template pack before advanced AI
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 13. Instructions for the Coding Agent

Before implementation, inspect the current repository and existing
`PLAN.md`. Do not assume a feature is absent or incomplete solely from
this brief.

1.  Audit the actual source code, project model, timeline behavior,
    media pipeline, and tests.
2.  Compare the implementation with this roadmap and produce a concise
    gap analysis.
3.  Identify existing reusable classes and APIs before proposing new
    architecture.
4.  Preserve current behavior unless a change is necessary and covered
    by tests.
5.  Implement one milestone at a time in priority order.
6.  For each task, state the files to change, expected behavior, edge
    cases, and tests.
7.  Do not create placeholder UI and report the feature as implemented.
8.  Do not introduce a second project or timeline model for templates or
    AI.
9.  Run relevant tests after each change and report exact outcomes.
10. If a physical device is required but unavailable, mark the feature
    as not device-verified instead of claiming success.
11. Update `PLAN.md` to distinguish implementation from validation.
12. Keep AI providers and network-dependent services behind replaceable
    interfaces.
13. Ask before introducing paid APIs, credentials, telemetry, or
    external media uploads.
14. Do not silently remove existing features or change export behavior.
15. End each milestone with a summary of completed work, tests run,
    known limitations, and the next recommended task.

### Required output from the coding agent before coding

-   Repository and architecture summary.
-   Feature status audit.
-   Template compatibility analysis.
-   AI processing options and trade-offs.
-   Proposed schema and integration points.
-   Test plan.
-   Small, ordered implementation task list.

Only proceed to implementation after this inspection and plan, unless
the user explicitly requests immediate coding.

------------------------------------------------------------------------

## 14. Final Recommendation

PreCut should pursue a clear sequence:

1.  **Prove reliability** of the current editor on physical devices.
2.  **Ship editable templates** that reuse the existing project and
    export pipeline.
3.  **Add automatic captions** with genuinely editable text and timing.
4.  **Add beat detection and AI-assisted Auto Edit** to connect
    templates with user footage.
5.  **Expand into audio and visual AI** after measuring device
    feasibility and output quality.
6.  **Introduce prompt-to-edit and larger asset ecosystems** only when
    the earlier workflows are stable.

The strategic goal is not to copy every feature in a competitor's
catalog at once. It is to make PreCut a dependable editor where users
can either create manually or reach a polished, editable result quickly
with templates and AI assistance.
