# DermaLens
An Android skin disease detection app built with Jetpack Compose. DermaLens lets users scan their skin, track conditions over time, and find nearby dermatology clinics — developed as a Capstone Project at Tarlac State University, 2026.

---

## Features
- **Skin Scan** — Capture via camera (pinch-to-zoom) or pick from gallery (pan + pinch-to-zoom to position, cropped to exactly what's in the guide frame before scanning); AI detects condition, severity, and confidence, with real bounding boxes (multi-region, NMS-filtered) drawn on the full result image. A result below the confidence floor shows an honest "Low Confidence" popup prompting a retake instead of a shaky guess.
- **Family Tree** — Condition subtype/lookalike reference screens, reached from Scan Result's "Related Conditions" card — original schematic icons per condition, not stock photos.
- **Scan History & Progress Tracker** — Timeline view per condition with trend indicators; every saved scan keeps its own photo and an editable note, and tapping any entry reopens the real Scan Result screen loaded from that saved record (not a fresh re-inference).
- **Condition Guidance** — Description, common symptoms, and recommendations shown directly on the scan result screen (content lives with the result it applies to, not in a separate browsable Care Guide screen).
- **Clinic Locator** — GPS-based Google Map with custom markers, dermatology clinics found via the Google Places API, and driving routes drawn from OSRM. No results render until location access is actually granted — no fallback-location results shown alongside a "permission needed" banner.
- **Contribute to Research** — Opt-in, and it actually uploads: after Save to History, a genuine yes/no prompt asks whether to also contribute that scan (never a silent side effect of saving). Consented scans are sent over Wi-Fi to a Google Apps Script bridge that files them into per-condition folders in the project owner's own Google Drive, ready to fold into a future retraining run. Anonymous by construction — the filename is a random UUID plus the detected condition, with no account identifier anywhere in the request (see [Contribute to Research pipeline](#contribute-to-research-pipeline) below).
- **Account management** — Registration and password changes require 8+ characters with an uppercase letter, lowercase letter, number, and special character. Delete Account (Profile) reauthenticates, deletes local scan records and their photos, deletes the Firebase account, and clears the session.
- **Accessibility** — Font size slider, high contrast mode, propagated across all screens.
- **Privacy Policy** — Full in-app privacy policy dialog.

## Tech Stack
| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation |
| Database | Room (SQLite) |
| Camera | CameraX (`PreviewView.ImplementationMode.COMPATIBLE`) |
| Gallery | `ActivityResultContracts.PickVisualMedia` (Android 13+) |
| Image loading | Coil (`AsyncImage`) |
| Maps | Google Maps (`maps-compose`) + Google Places API (clinic search) + OSRM (routing only) |
| Location | `play-services-location` |
| AI Model | YOLOv11 TFLite — real 6-class merged model (v2), overall mAP50 0.654, live-verified on-device (see [AI Model — Training & Evaluation](#ai-model--training--evaluation)) |
| Auth | Firebase Authentication (email/password) — registration required, no guest/offline path |
| Research uploads | Google Apps Script Web App → project owner's Google Drive, via WorkManager (`NetworkType.UNMETERED`) |

## Project Structure
```
app/src/main/java/
├── com/dermalens/app/
│   ├── data/
│   │   ├── db/          # Room database, DAOs
│   │   └── model/       # ScanRecord, User entities
│   ├── ml/
│   │   └── YoloDetector.kt   # TFLite inference, NMS, CLASS_LABELS, confidence floor
│   ├── navigation/      # NavGraph, Screen sealed class
│   └── ui/
│       ├── screens/     # All screen composables
│       │   ├── Screens.kt            # Login + Register + Privacy Policy
│       │   ├── HomeScreen.kt         # Home + bottom nav bar
│       │   ├── CameraScreen.kt       # Camera + gallery (+ scan sweep effect)
│       │   ├── ScanResultScreen.kt   # Detection result, condition content, save
│       │   ├── FamilyTree.kt / FamilyTreeScreen.kt   # Condition subtype/lookalike reference
│       │   ├── ProgressTrackerScreen.kt
│       │   ├── ClinicLocatorScreen.kt
│       │   └── ProfileScreen.kt      # Profile + EditProfile + Delete Account
│       ├── AppSettings.kt    # Font scale + high contrast state
│       └── DermaColors.kt    # Colors, DermaPrefs, animation helpers
└── worker/              # (package com.dermalens.app.worker — flat dir is intentional)
    ├── ScanReminderWorker.kt
    ├── ContributionUploadWorker.kt   # Research upload, Wi-Fi only
    └── NotificationScheduler.kt      # + ContributionUploadScheduler
```

Training/ML lives outside the app module:
```
training/
├── merge_and_train_multiclass.ipynb   # the real multi-class merge + train pipeline
├── retrain_yolo.ipynb                 # single-condition solo runs (sanity checks)
├── train_acne_subtypes.ipynb          # standalone acne subtype experiment
└── acne_subtypes_future/README.md     # why subtype splitting is parked
apps-script/ContributionUpload.gs      # the Drive upload endpoint (deploy as a Web App)
```

> The `.ipynb` files are **gitignored** — they carry pasted Roboflow API keys, so they're shared via Drive/Colab rather than committed (see `.gitignore`). Ask a team member for a copy. `acne_subtypes_future/README.md` and the Apps Script *are* committed.

## Building & Running
1. Clone the repo
2. Open in Android Studio (Hedgehog or later)
3. Let Gradle sync complete
4. Run on a device or emulator (API 26+)

> VS Code will show "Unresolved reference" errors on Compose/Room imports — these are fake and disappear after Gradle sync in Android Studio.

> **Want to help code?** See [SETUP.md](SETUP.md) for the full onboarding steps, including how to get `google-services.json` (needed to build — it's not in the repo, see below).

## Firebase Auth
Registered accounts (Login/Register) go through real Firebase Authentication — a new account is a real, Firebase-Console-visible user, gets a real verification email, and password reset goes through Firebase's real "Forgot password?" email flow. Registration is required to use the app — there is no guest/offline-account path (guest mode was implemented and briefly live on 2026-08-24, then removed on 2026-08-25 per the team's tech adviser — see "Guest Mode — Removed" in `FIREBASE_AUTH_PLAN.md`). Everything else (scan history, profile stats) stays fully local in Room DB.

Email verification is enforced, not just sent: an unverified account is routed to a "Verify Your Email" screen (with Resend and Log Out options) instead of Home, on both fresh Register and every subsequent Login, until Firebase reports `isEmailVerified == true`. The `is_logged_in` session flag is only set once verified, so there's no way to reach the app's main features with an unconfirmed email.

Full rationale, the code-change list, and the capstone-manuscript impact (Scope & Limitations, IC1/IC2/SS1/SS3, Table 4, Figure 3, Privacy Policy text) are written up in [FIREBASE_AUTH_PLAN.md](FIREBASE_AUTH_PLAN.md) — read that before touching this area or updating the paper.

`app/google-services.json` is required to build but is **gitignored** (it's tied to the Firebase project). Ask Mark Joseph for a copy, or get added to the Firebase project and download your own from the Console.

**Live-verified:** Register (real Firebase account + verification email + local profile), Login (Firebase auth + resolves matching local profile), Logout (correctly signs out of Firebase too), Change Password (reauthenticate + update, confirmed via a live "Saved!" state), Verify Your Email (an unverified account is correctly blocked, Resend Email works), Delete Account (reauthenticates, deletes local scan records + photos, deletes the Firebase account). **Not yet live-verified:** "Forgot password?" — see [Known Gaps](#known-gaps--good-first-issues).

## Contribute to Research pipeline
The Profile toggle isn't decorative — consented scans genuinely leave the device. How it works end to end:

1. User enables the feature once (Profile → Contribute to Research) — this only controls whether the per-scan prompt below ever shows; it does **not** by itself upload anything.
2. After **Save to History**, if the feature is enabled, a genuine yes/no dialog asks whether to also contribute *this specific scan*. Only "Yes" copies the image and flags the row `contributedForTraining`. (Every saved scan's photo is kept locally either way, for Progress Tracker's per-scan photo view — contribution is a separate, later, explicit choice on top of that.)
3. A WorkManager job (`ContributionUploadWorker`, `NetworkType.UNMETERED`, ~12h period) POSTs pending images to a **Google Apps Script Web App**, which files each one into `DermaLens Contributions/<condition>/` in the script owner's own Google Drive. The row is then flagged `uploadedForTraining` so nothing uploads twice. Tapping "Yes" also triggers an immediate one-time upload attempt, so a contribution made while already on Wi-Fi doesn't sit queued for up to 12h.

**Why Apps Script and not Firebase Storage:** Firebase/Cloud Storage now requires the project to be on the Blaze plan — the Spark free tier no longer includes Storage at all, and activating Blaze wanted a refundable prepayment we didn't want to spend on a student project. Apps Script runs under the owner's own Google account with no billing attached, and crucially it means **no real credential ships in the APK** — only a low-value shared secret, since the script itself holds the Drive permissions.

**Privacy properties, by construction rather than by promise:**
- The request contains only the image bytes, a random-UUID filename, and the detected condition. No user ID, email, device ID, or timestamp-of-account is sent.
- All contributions land in one shared per-condition folder tree — deliberately **not** per-user folders, since there's no user identifier to key them on and adding one would break the anonymity the consent dialog promises.
- Wi-Fi only, so it never quietly consumes someone's mobile data.
- Transmission is standard HTTPS to the Apps Script endpoint plus a shared-secret header — there's no additional encryption layer, and the shared secret is access control, not full authentication. Worth naming plainly if asked about transmission security.

**Setup** (only needed once, by whoever owns the receiving Drive): deploy `apps-script/ContributionUpload.gs` as a Web App (Execute as: Me, Who has access: Anyone), set a `SHARED_SECRET` script property, then put the resulting `/exec` URL and the same secret into `local.properties` as `APPS_SCRIPT_URL` and `CONTRIBUTION_UPLOAD_SECRET`. Full step-by-step is in the comment header of the `.gs` file. Both values are read via `BuildConfig`, same pattern as `MAPS_API_KEY` — **never commit them**, this repo is public.

> If `APPS_SCRIPT_URL` is blank the worker exits cleanly as a no-op, so the app builds and runs fine without any of this configured.

## AI Model — Training & Evaluation
**Current model (v2):** YOLOv11-small, TFLite export, real 6-class merged model, **overall mAP50 0.654**, live-verified on-device.

| Condition | v2 AP50 | Solo baseline |
|---|---|---|
| Tinea | 0.828 | ~0.86–0.90 (near parity) |
| Scabies | 0.698 | 0.701 (near parity) |
| Eczema | 0.674 | 0.735 |
| Warts | 0.658 | ~0.6+ (at/above parity) |
| Melasma | 0.569 | 0.696 — **didn't move between v1 and v2, still an open item** |
| Acne Vulgaris | 0.499 | 0.536 |

- **Confidence floor: `CONFIDENCE_THRESHOLD = 0.30`** in `ml/YoloDetector.kt`. The F1-optimal value from this model's own F1-confidence curve is 0.247, but live testing after v2 shipped found a non-skin textured surface (a woven fabric close-up) scoring 26.9% for Eczema — confidently enough to clear 0.247 despite not being skin at all. 0.30 is a deliberate, blunt trade-off away from the F1-optimal point: every genuine skin result observed live this session scored well above it, so this hasn't rejected a real case yet, but re-evaluate if a legitimately low-confidence-but-correct result starts getting rejected.
- **Cross-condition confusion** (a wrong condition entirely, not just a weak score) tops out at 0.09 off-diagonal in the v2 confusion matrix — the remaining weakness is missed detections, not wrong-condition guesses.
- **`CLASS_LABELS` in `ml/YoloDetector.kt` must match the training class order exactly** — the merge notebook prints the exact line to paste. See `HANDOFF.md`.
- Auto-detects channels-first `[1,3,H,W]` vs. channels-last `[1,H,W,3]` tensor layout at runtime, and expects a plain stretch-to-square resize (not letterboxing) — matching Roboflow's default "Resize: Stretch" preprocessing.
- Individual scan confidence percentages vary widely photo-to-photo even for correct detections; that's expected and not a defect (mAP is the aggregate measure, a single scan's percentage is not).
- **Scanning guidance from testing:** don't over-zoom. Cropping tightly onto a single lesion measurably *lowered* confidence (28.6% → 17.4% on the same wart photo) because training images are framed with the lesion in surrounding skin context, not filling the frame edge to edge.

<details>
<summary><b>How we got here (model history)</b></summary>

- **Early solo runs.** 4 of 9 originally-planned classes trained as isolated single-class models (sanity checks). One (Melasma) exported to TFLite and confirmed real on-device inference at 77.7% confidence — this surfaced the channels-first tensor layout and stretch-to-square resize requirements noted above.
- **Bounding-box bug found & fixed.** An audit against the "visual overlay" requirement found the inference code was discarding box coordinates entirely, keeping only a single best box. Fixed to extract all real boxes, filtered with confidence thresholding + NMS — verified live on a bilateral melasma photo (correctly drew two separate boxes, one per cheek).
- **v1 merge (superseded).** First 6-class merge: overall mAP50 0.557. Every class scored *below* its own solo-trained model, and live testing surfaced a genuine cross-condition misfire (a real acne photo scored as Scabies at 52.3%). Root cause, found via the training run's `labels.jpg` plot: Acne averages ~12 box instances per image vs. Tinea/Melasma's ~1, so even with images capped at the same *image* count per class, Acne still dominated actual training signal by raw *instance* count (20,528 vs. Tinea's 1,736).
- **Preprocessing mismatch, found and fixed.** Two of six source datasets (Melasma, Scabies) were exported with Roboflow's "Fit within" (letterbox) resize while the other four used "Stretch to" — mismatched geometry within one merged dataset. Fixing both moved Scabies 0.297→0.360 and Melasma 0.572→0.602.
- **Scabies annotation issue, identified but not yet fixed.** The v1 confusion matrix showed Scabies wasn't confused with other conditions — it was simply missed (62% predicted as background). Rendering the actual boxes found why: 34% of source images have a single whole-image box (90–100% of frame) over photos containing 6–15 discrete lesions, contradicting the other 66% that are correctly boxed per-lesion. 114 affected images are identified and still pending re-annotation.
- **v2 fix: instance-aware oversampling.** On top of the existing image-level balancing, a second pass tops up under-represented classes by real box-*instance* count, not just image count. Result: overall mAP50 0.654 (table above), and the v1 cross-condition misfire is gone (0.09 off-diagonal ceiling vs. it being the dominant failure mode before).
- **Explored but not shipped:** a `yolo11m` (medium) solo Melasma run scored 0.696 mAP50 vs. 0.602 for `yolo11s` on the same data — a real capacity lever for the smaller classes, but not yet retrained into the full merge (latency cost on-device not yet benchmarked). A more heavily-augmented Acne dataset was solo-tested as a candidate fix for Acne's gap and scored statistically identical (0.534 vs. 0.536) — ruled out; Acne's ceiling looks like an inherent small-numerous-lesion difficulty, not a fixable data-quality problem. A standalone 5-class acne subtype model (blackhead/whitehead/papula/pustula/nodules) scored only 0.234 mAP50 with blackhead recall at 1.5%, so subtype differentiation is parked — see `training/acne_subtypes_future/README.md`.

</details>

## Known Limitations
*As of now — to be updated as development progresses.*

| Feature | Is it real? |
|---|---|
| Clinic Locator | Real — Google Map + live Google Places results + OSRM routes; shows an honest empty state if none found nearby |
| Skin Scan Results | Real, with a caveat — a trained 6-class model (v2, mAP50 0.654) is live-verified on-device, but the `.tflite` is gitignored, so a **fresh clone falls back to random mock results** until you drop the model into `app/src/main/assets/best.tflite` |
| Progress Tracker | Real — pulls actual scan history from Room DB, grouped by condition with trend indicators; every scan keeps its own photo and an editable note |
| Contribute to Research | Real — uploads to Drive via Apps Script, Wi-Fi only, anonymous; no-ops cleanly if unconfigured |
| Scan Reminders | Real — the Profile toggle persists and enables/disables the daily reminder worker; a "Test Notification" button fires a real one-time notification (~5s later) to confirm the feature works without a full day's wait |

- **Clinic Locator** — On Google Maps + Google Places (not OpenStreetMap); the map is `maps-compose`'s `GoogleMap` with custom `BitmapDescriptorFactory` markers, clinics come from the Places API (`places:searchText`, query "dermatology clinic", 15 km location bias), and **OSRM is still used, but only for drawing driving routes**. The screen checks real connectivity (`ConnectivityManager`) and shows a distinct "No Internet Connection" + Retry state, separate from "no clinics found nearby," so a dead network doesn't look identical to an empty result. Clinic ratings aren't displayed yet, though the Places API does expose them (a possible future feature, not a data limitation).
- **Camera permission denial** — If a user permanently denies camera access ("Don't ask again"), the app detects this (`shouldShowRequestPermissionRationale`) and offers a real "Open Settings" button instead of retrying an in-app dialog Android will never show again.
- **Security review** — `SECURITY_TESTING.md` closed out the only two real findings found (unused cleartext traffic permission, scan photos/DB not excluded from Android backup) — nothing high or medium severity remains open.

## Known Gaps / Good First Issues
Not urgent, left for later. Good entry points if you want to help:
- **Email change isn't implemented.** Edit Profile can change your name but the email field is read-only. Firebase requires a `verifyBeforeUpdateEmail()` flow (sends a confirmation link to the *new* address) — deliberately scoped out of the initial Firebase pass, see the "Open Questions" section of `FIREBASE_AUTH_PLAN.md`.
- **"Forgot password?" email flow is implemented but not yet live-tested end-to-end** (i.e. actually clicking the reset link from a real inbox and confirming login with the new password works). Worth a pass before relying on it in a live demo.
- **Firebase BoM is pinned to `33.5.1`** in `app/build.gradle.kts` (see the comment there) because newer BoMs need Kotlin 2.3.0 and this project is pinned to Kotlin 2.0.21. Don't bump it without bumping Kotlin first, or the build breaks with a metadata-version mismatch.
- **The Places API cert SHA-1 is hardcoded**, in `ANDROID_CERT_SHA1` in `ClinicLocatorScreen.kt`. It's the SHA-1 of one specific debug keystore, so clinic search silently returns nothing for anyone building with a different one (and would need the release keystore's SHA-1 added for a signed build). Either add your own SHA-1 to the restricted key in Google Cloud Console and update the constant, or read it at runtime from the app's own signing info instead of hardcoding.
- **No OTA / server-pushed model update mechanism.** `best.tflite` ships baked into the APK as a bundled asset — a new model version requires a full app update. A versioned model manifest + downloadable `.tflite` would allow improving detection without app-store releases.
- **No formal accessibility audit.** Font scaling and high contrast are real and manually verified to propagate across all screens without truncation/clipping, but there's no formal WCAG 2.1 contrast-ratio audit and no TalkBack (screen reader) compatibility testing yet.
- **Scabies re-annotation** — 114 images identified as needing per-lesion (not whole-image) boxes; see [AI Model history](#ai-model--training--evaluation).

## Development Timeline
CP2 Development Plan — May – November 2026

| Sprint | Duration | Track | Sprint Goal | Key Deliverables | Technologies | Lead | Status | Priority |
|---|---|---|---|---|---|---|---|---|
| Sprint 1 | May 25 – Jun 7, 2026 | App Development | Project setup, user authentication (register/login/logout), initial UI scaffolding | Login & register screens; auth flow; GitHub repo with project structure | Android Studio, Kotlin, Jetpack Compose, Firebase Auth / Room DB | Chrisent Dayniel | ✅ Done | 🔴 Critical |
| Sprint 2 | Jun 8 – Jun 21, 2026 | App Dev + AI | CameraX real-time integration; start YOLOv11 model training on Google Colab | Working camera capture screen; initial YOLOv11 training pipeline; preliminary model weights | CameraX, Camera2 API, Python, Ultralytics YOLO, Kaggle/Roboflow dataset | Reynaldo | 🔄 In Progress | 🔴 Critical |
| Sprint 3 | Jun 22 – Jul 5, 2026 | AI + Integration | Fine-tune YOLOv11, convert to TFLite, integrate on-device inference into the app | Optimized .tflite model in APK; real-time detection screen with bounding box + confidence score | TensorFlow Lite, GPU/NNAPI delegates, Ultralytics YOLO export, Google Colab T4 | Mark Joseph | 🔄 In Progress | 🔴 Critical |
| Sprint 4 | Jul 6 – Jul 19, 2026 | App Development | Detection result screen, skincare guidance content, Room DB for scan history | Complete result screen; skincare guide for all 6 conditions; working Room DB schema | Jetpack Compose, Room DB, SQLite, pre-built knowledge base JSON | Reicee Owen | ✅ Done | 🟠 High |
| Sprint 5 | Jul 20 – Aug 2, 2026 | App Development | Progress tracking dashboard, clinic locator via Google Maps, scan reminders | Progress tracker with charts; clinic locator with directions; WorkManager notifications | Google Maps SDK, Places API, WorkManager, MPAndroidChart / Compose Charts | Chrisent Dayniel | ✅ Done | 🟠 High |
| Sprint 6 | Aug 3 – Aug 16, 2026 | Testing | Full system integration, functional testing (FR1–FR11), performance testing, survey | Stable DermaLens APK; functional + performance test results; 100-respondent survey data | Android Profiler, Likert scale questionnaire | All Members | ⬜ Not started | 🟠 High |
| Sprint 7 | Aug 17 – Aug 30, 2026 | Bug Fixing | Resolve bugs from testing; UI/UX polish; start Chapter 5 documentation | Refined APK; resolved bug report; Chapter 5 draft; updated methodology docs | Android Studio Debugger, Compose Previews | Mark Joseph | 🔄 In Progress | 🟡 Medium |
| Sprint 8 | Aug 31 – Sep 27, 2026 | Documentation | Final documentation, complete all chapters, defense preparation | Final capstone paper (all chapters); defense slides; submitted manuscript; archived APK | Google Docs / MS Word, PowerPoint / Canva | All Members | ⬜ Not started | 🟡 Medium |
| Post-Sprint | Oct – Nov 2026 | Wrap-up | Address panel feedback, finalize approved manuscript, archive project repository | Revised approved manuscript; archived repo; all submission requirements fulfilled | GitHub, Google Drive | All Members | ⬜ Not started | 🟢 Low |

## Team
- Mark Joseph Garcia
- Reynaldo Manio Jr.
- Reicee Owen Pastrana
- Chrisent Dayniel Tolentino

*Tarlac State University — Capstone 2026*
