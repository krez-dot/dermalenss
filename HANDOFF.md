# DermaLens — Developer Handoff

Last updated: 2026-09-16
Branch: `master`
Status: Firebase Auth live. **Multi-class merge is done (v2)** — a real 6-class YOLOv11 model (overall mAP50 0.654) is trained, bundled, and live-verified on-device, including confirming that the specific cross-condition misfire from the v1 attempt (a real acne photo scored as Scabies) is fixed. Contribute to Research now actually uploads to Google Drive. Clinic Locator moved from OSM/Overpass to Google Maps + Places.

---

## Dev Environment Notes (read this first if starting fresh)

- **JAVA_HOME isn't set by default** in this shell. JDK 17 lives at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot` — set `$env:JAVA_HOME` before running `.\gradlew.bat` anything, in PowerShell (not Git Bash — Gradle needs PowerShell/cmd here).
- **adb** is at `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`, **emulator** at `$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe`. AVD name: `DermaLensTest`. The emulator is not always running — check `adb devices` first, boot with `emulator.exe -avd DermaLensTest -WindowStyle Hidden` if empty, and poll `adb devices` until it shows `device` (takes 20-40s).
- **Git Bash mangles device-absolute paths** starting with `/sdcard/...` — double the leading slash (`//sdcard/...`) when passing paths to `adb push`/`pull`/`shell` through the Bash tool, or just use PowerShell for adb calls instead.
- **The Android system Photo Picker gets cluttered** by every screenshot taken during testing (they get indexed into the same "Recent" media view). Periodically `adb shell rm -f /sdcard/*.png` + `am force-stop com.android.providers.media.module` to reset it if the picker gets hard to navigate.
- **Windows Gradle builds can hit a file lock on `R.jar`** — VS Code's Kotlin/Java language server respawns `java.exe` almost immediately after being killed. Loop-kill java processes a few times with short sleeps between each, then retry the build immediately.
- Full team onboarding steps (Android Studio, `google-services.json`) are in `SETUP.md`.

---

## Auth — Firebase, Not Local

- **Firebase Authentication** (email/password) — real accounts, real email verification, real password reset, visible in the Firebase Console. See `FIREBASE_AUTH_PLAN.md` for the full rationale and manuscript impact (Scope & Limitations, IC1/IC2/SS1/SS3, Table 4, Figure 3).
- **Guest mode was added, then removed** (2026-08-24 → 2026-08-25) per the team's tech adviser. Registration is required — no offline/no-account path. See "Guest Mode — Removed" in `FIREBASE_AUTH_PLAN.md`.
- **Email verification is enforced**, not just sent — an unverified account is routed to a "Verify Your Email" screen instead of Home, on both fresh Register and every subsequent Login, until Firebase reports `isEmailVerified == true`. See `VerifyEmailScreen` in `Screens.kt`.
- Password reset ("Forgot password?") has been live-tested end-to-end with a real inbox — confirmed working.
- Email *change* is still NOT implemented — Edit Profile's email field is read-only. Needs Firebase's `verifyBeforeUpdateEmail()` flow. Open item, see README "Good First Issues."
- **A real bug was found and fixed this pass**: if a Room schema bump wipes the `users` table while `SharedPreferences` still reports a logged-in session (exactly what happens on a dev-stage `fallbackToDestructiveMigration()` DB bump), "Save to History" on the Scan Result screen would look up a profile row that no longer existed and silently do nothing — no error, button just looked unresponsive. `ScanResultScreen.kt` now self-heals by recreating the minimal profile row, same as Login already did for fresh installs.

---

## YOLOv11 — 6-Class Merged Model (v2), Trained and Bundled

**The swap-one-at-a-time workflow is over.** `app/src/main/assets/best.tflite` (gitignored) is now the real 6-class merged model, and `CLASS_LABELS` in `ml/YoloDetector.kt` is `listOf("Acne Vulgaris", "Eczema", "Melasma", "Tinea", "Warts", "Scabies")` — order matches `training/merge_and_train_multiclass.ipynb`'s `CONDITIONS` list exactly, which is what the class indices were trained against. Changing the order without retraining silently mislabels everything.

This is the **second** merge attempt (`RUN_NAME = "multiclass_merged_v2"`). The first (v1, interrupted at epoch 79/250) scored an overall mAP50 of 0.628 but regressed on *every single class* versus its own solo model, and live-testing surfaced a real cross-condition misfire — a genuine acne photo scored as Scabies at 52.3%. v2 fixed that.

### Per-class results (overall mAP50 0.654, up from v1's 0.628)

| Condition | v2 AP50 | v1 AP50 | Solo baseline |
|---|---|---|---|
| Tinea | 0.828 | 0.742 | ~0.86–0.90 (near parity) |
| Scabies | 0.698 | 0.684 | 0.701 (near parity) |
| Eczema | 0.674 | 0.649 | 0.735 |
| Warts | 0.658 | 0.641 | ~0.6+ (at/above parity) |
| Melasma | 0.569 | 0.569 | 0.696 — **didn't move at all**, see below |
| Acne Vulgaris | 0.499 | 0.481 | 0.536 |

### What actually fixed v1's cross-condition confusion: instance-aware oversampling
v1's `labels.jpg` plot showed Acne averaging ~12 boxes/image vs. Tinea/Melasma's ~1/image — so even with the existing *image*-count oversampling (capped at 1000 images/class), Acne still dominated actual gradient updates by raw box-instance count (20,528 vs. Tinea's 1,736 in v1, an 11x gap). That tracked closely with which classes regressed most in v1's validation. v2 adds a second balancing pass (step 5c in the notebook) that counts real box instances per condition and tops up any class still short of a target (median of the non-largest classes, clamped 3000-6000) — on top of, not instead of, the image-level pass. Confirmed via v2's confusion matrix: cross-condition confusion between real classes now tops out at 0.09 (was the dominant failure mode in v1); the remaining weakness is missed detections (predicted background), not wrong-condition guesses.

### Melasma is the one open problem
Unchanged at 0.569 AP50 between v1 and v2, despite the instance-balancing fix targeting exactly this kind of underrepresented class — still meaningfully behind its 0.696 solo result. Not yet diagnosed why the fix didn't move it (dataset size, target instance count too low for it specifically, or something else). Still classifies correctly in live testing (71.8% on a real photo), just not as strong as it could be. Worth another look before considering the merge fully done.

### A new Acne dataset was tried and ruled out (not a dataset-quality problem)
A newer, more heavily-augmented Acne dataset (`acne-fixed-hzi22` v1 — cleaner audit than the current one: no stray classes, no whole-image boxes, no watermarks, no leakage) was solo-tested via `retrain_yolo.ipynb` and scored mAP50=0.534 — statistically identical to the current dataset's 0.536. This suggests Acne's ~0.5 ceiling is closer to an inherent-difficulty limit (small, numerous, fuzzy-bordered lesions) than a fixable data-quality issue. Credentials for both are in memory (`project_proven_datasets.md`) if this gets revisited.

### The confidence threshold is derived from this model's own F1 curve
`CONFIDENCE_THRESHOLD = 0.247f` in `YoloDetector.kt`, taken directly from this run's `BoxF1_curve.png` ("all classes 0.63 at 0.247"). It drives both the per-box candidate filter and the "is the verdict good enough to show" gate — deliberately unified, not two separate constants (see the code comment). Re-derive this any time the model is retrained.

### Acne subtype differentiation — tried, not ready, documented for later
A standalone 5-class model (blackhead/whitehead/papula/pustula/nodules, sourced by splitting one multi-class Roboflow project via `source_class` in the merge notebook) scored only **0.234 mAP50**, with blackhead — despite having by far the most training data (2,588 of ~3,457 instances) — sitting at **1.5% recall**. That's not a data-volume problem; blackheads are small, numerous, low-contrast dots, which is a genuinely hard small-object-detection case distinct from a single larger lesion. Papula alone scored a usable 0.481, proving the concept can work for some subtypes. Full writeup and the exact dataset details: `training/acne_subtypes_future/README.md`. Not folded into the bundled model.

### Real bugs found and fixed along the way (all live-tested)
- **`resume=True` silently resuming a stale/mismatched checkpoint** — a class-count safety check now refuses to resume onto a checkpoint whose `len(model.names)` doesn't match the current `CONDITIONS` list.
- **`resume=True` also silently reuses the *original* run's saved hyperparameters**, ignoring newly-passed `epochs`/`patience` — this is what let v1 train to 250 epochs/patience 40 even after the config was corrected to 150/25. Fixed by giving every genuinely fresh attempt its own `RUN_NAME` rather than trying to resume in place.
- **Severity badge was fabricated on the result screen** — removed from that display; `DetectionResult.severity` is still real and used by Progress Tracker's trend feature.
- **Channels-first vs. channels-last auto-detection**, and **stretch-resize (not letterboxed) preprocessing** — `YoloDetector.kt`'s `preprocess()` and `bestClass()` handle both automatically; see the code comments there.
- **Don't over-zoom when scanning** — cropping tightly onto a single lesion measurably *lowered* confidence on the same photo (28.6% → 17.4%), because the training images are framed with the lesion in surrounding skin context, not filling the frame edge to edge. Worth surfacing as in-app guidance if not already (check `HomeScreen.kt`'s "Tip of the Day" rotation).

---

## Contribute to Research — Now Actually Uploads

Previously scans were only saved locally with a `contributedForTraining` flag and nothing happened after that. Now:

1. User opts in (Profile toggle, or the first-run Home prompt).
2. On Save to History, if consented, the image is copied into app-private storage and flagged.
3. `ContributionUploadWorker` (WorkManager, `NetworkType.UNMETERED`, ~12h period) POSTs pending images to a **Google Apps Script Web App** (`apps-script/ContributionUpload.gs`), which files each into `DermaLens Contributions/<condition>/` in the script owner's own Google Drive. The row then gets `uploadedForTraining = true` so it can't repeat.

**Why Apps Script, not Firebase Storage:** Firebase/Cloud Storage now requires the Blaze billing plan — the Spark free tier no longer includes Storage at all — and Google Cloud additionally wanted a one-time $10 prepayment that wasn't available. Apps Script runs under the owner's own Google account with zero billing, and critically means no real credential ships in the APK, only a low-value shared secret.

**Anonymity is structural, not just promised**: the upload request contains only image bytes, a random-UUID filename, and the detected condition — no user ID, email, or device identifier anywhere. Images land in one shared per-condition folder tree, deliberately not per-user, since there's no identifier to key a per-user folder on anyway.

**Setup** (one-time, whoever owns the receiving Drive): deploy the `.gs` file as a Web App (Execute as: Me, access: Anyone — **must** be "Web app" type, not "Library", a real mistake that cost real debugging time), set `SHARED_SECRET` in Script Properties, put the resulting `/exec` URL and the same secret into `local.properties` as `APPS_SCRIPT_URL` / `CONTRIBUTION_UPLOAD_SECRET`. If `APPS_SCRIPT_URL` is blank the worker no-ops cleanly, so the app builds fine without any of this configured.

**Testing this end-to-end is not obvious**: `adb shell cmd jobscheduler run -f` is unreliable for a `NetworkType.UNMETERED`-constrained job — it bypasses timing/idle constraints but not connectivity checks, and produced zero logcat output both times it was tried. What actually works: temporarily add a `OneTimeWorkRequestBuilder<ContributionUploadWorker>()` trigger, call it once, verify via `adb logcat | grep WM-WorkerWrapper`, then revert.

---

## What's Done

### Camera & Gallery
- CameraX with `PreviewView.ImplementationMode.COMPATIBLE`, real `ImageCapture`, pinch-to-zoom, gallery picker with pan/pinch/crop-to-frame. Camera screen now also has a scanning-sweep animation while `isScanning` is true (`ScanningSweepEffect` in `CameraScreen.kt`).

### Scan Result
- Real inference via `runYoloInference()`, falls back to `mockDetectionResults.random()` only if no model bundled or `imageUri` is null — never falls back on a low-confidence *real* result (that's the confidence-floor path, a genuine "no condition" result).
- Condition guidance (description, symptoms, recommendation, distinguishing feature vs. other conditions) is shown directly on this screen now — see "Care Guide" below for what replaced it.

### Progress Tracker
- Real scan history from Room DB, grouped per condition and plotted as **confidence % over time**
  (`ScanEntry.confidence`) — not severity; `ProgressTrackerScreen.kt` has no `severity` reference at
  all. An earlier version of this doc claimed severity-based trend indicators, which was never
  actually built; corrected here rather than carried forward.

### Care Guide — REMOVED, replaced by inline condition guidance
`CareGuideScreen.kt` is deleted. The description/symptoms/recommendation content that used to live there is now shown directly on the Scan Result screen (`mockDetectionResults` in `ScanResultScreen.kt`), tied to the condition that was actually detected rather than browsed separately. If you're looking for where the OTC-product-recommendation content went, it didn't migrate — that was scoped out with the screen. Flag if it's still wanted somewhere.

### Clinic Locator — moved to Google Maps + Places (no longer OSM/Overpass)
- Map is `maps-compose`'s `GoogleMap` with custom `BitmapDescriptorFactory` markers; clinic search is the Google Places API (`places:searchText`, query "dermatology clinic", 15km location bias, raw `HttpURLConnection` call rather than the Places SDK — see the header-attachment comment in `ClinicLocatorScreen.kt`, it needs `X-Android-Package`/`X-Android-Cert` manually or Google blocks it). OSRM is still used, but now only for drawing driving routes.
- **`ANDROID_CERT_SHA1` in `ClinicLocatorScreen.kt` is hardcoded to one specific debug keystore.** Clinic search will silently return nothing for anyone building with a different debug keystore, or for a release build. Needs the correct SHA-1 added to the restricted API key in Google Cloud Console, and ideally read at runtime instead of hardcoded.
- **`osmdroid` dependency removed** from `build.gradle.kts` (Sept 2026) — it was imported in zero files after this migration, pure dead weight.
- **Known gap, unchanged by the migration**: "Open Now" badge defaults to `true` whenever Places doesn't return `regularOpeningHours.openNow` (`ClinicLocatorScreen.kt` line ~147) — misleading, since a clinic's actual open/closed status isn't known in that case. Fix would be defaulting to "Hours unknown" instead of `true`.
- **Known gap**: Places' text search can miss real dermatology clinics that don't come up for that exact query phrasing, same class of limitation the old Overpass tag-match had, just with a different failure mode.

### Contribute to Research
See the dedicated section above — this used to be local-only, now it's a real pipeline.

### Profile
- Password change goes through Firebase reauthentication (`EmailAuthProvider` + `reauthenticate()` + `updatePassword()`), not local hash comparison. Email field is read-only (see Auth section).
- Contribute to Research toggle now actually schedules/cancels the upload worker, not just a local flag.

---

## Key Files Reference

| File | What it does |
|---|---|
| `DermaColors.kt` | Color constants, `DermaPrefs` keys, shared animation helpers (`pressScale`, `EntranceAnimation`) |
| `Screens.kt` | Login, Register, VerifyEmailScreen, Privacy Policy dialog |
| `NavGraph.kt` | All routes; push/pop slide transitions (no fade) |
| `ml/YoloDetector.kt` | `runYoloInference()`, `CLASS_LABELS` (6-class, order-sensitive), `MIN_CONFIDENCE_PERCENT` (32, F1-derived), `BOX_CONFIDENCE_THRESHOLD` (0.25) |
| `ScanResultScreen.kt` | `DetectionResult`, `mockDetectionResults` (condition guidance content, 11 entries — 6 real conditions + 5 parked acne subtypes), the self-heal fix for missing profile rows |
| `worker/ContributionUploadWorker.kt` | Posts pending contributions to the Apps Script endpoint, Wi-Fi only |
| `worker/NotificationScheduler.kt` | `NotificationScheduler` (scan reminders) + `ContributionUploadScheduler` |
| `apps-script/ContributionUpload.gs` | The Drive upload endpoint — deploy as a Web App under your own Google account |
| `data/model/User.kt` | No `isGuest` field (removed with guest mode) |
| `data/model/ScanRecord.kt` | Has `contributedForTraining` and `uploadedForTraining` (separate flags — consented-and-saved vs. actually-uploaded) |
| `DermaDatabase.kt` | Room DB **v6** now |
| `training/merge_and_train_multiclass.ipynb` | The real 6-class merge + train pipeline (gitignored — ask a teammate for a copy) |

## DB Version History

| Version | Change |
|---|---|
| 1 | Initial schema |
| 2 | Added fields to User |
| 3 | Added `imagePath` + `contributedForTraining` to ScanRecord |
| 4 | Added `firebaseUid`, `isGuest` to User (Firebase Auth) |
| 5 | Removed `isGuest` from User (guest mode removed) |
| 6 | Added `uploadedForTraining` to ScanRecord (Contribute to Research upload tracking) |

Still uses `fallbackToDestructiveMigration()` — acceptable for dev/capstone, wipes local data on every version bump. **Known consequence, hit for real this pass**: a version bump can wipe the `users` table while a logged-in session persists in `SharedPreferences`, which silently broke "Save to History" until the self-heal fix (see Auth section).

## Known Non-Issues (VS Code)

VS Code shows "Unresolved reference: androidx" on every import in Kotlin files. **These are fake.** The project builds fine via Gradle (see Dev Environment Notes above for the JAVA_HOME gotcha) or in Android Studio.

## Reference Docs

- `FIREBASE_AUTH_PLAN.md` — Firebase Auth rationale, guest mode history, manuscript impact
- `THRESHOLD_EXPERIMENT.md` — `BOX_CONFIDENCE_THRESHOLD` experiment, full methodology and results (still accurate, this constant hasn't changed)
- `SETUP.md` — groupmate onboarding
- `README.md` — feature overview, "Good First Issues" list, Contribute to Research pipeline writeup
- `training/acne_subtypes_future/README.md` — why acne subtype differentiation is parked, and exactly what to check before resuming it

---

*DermaLens — Tarlac State University Capstone 2026*
