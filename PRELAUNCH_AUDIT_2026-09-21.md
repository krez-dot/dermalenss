# DermaLens Pre-Launch Audit — 2026-09-21

Full codebase audit, traced against actual logic (not a surface pass). Findings below are
verified by reading the code paths involved, not assumed. Organized as a flat table first (for
import into a spreadsheet), with supporting detail and category-by-category notes below it.

## Findings Table

| ID | Category | Severity | Issue | Location | Why It Matters |
|---|---|---|---|---|---|
| 1 | Auth & User Flows | Critical | Google-only accounts cannot delete their account or change their password | `ProfileScreen.kt` (Delete Account), Edit Profile password change | Both reauthenticate via `EmailAuthProvider.getCredential(email, password)`. A Google-only account has no Firebase password credential, so this fails every time. Such a user has no way to delete their own data through the app — a real data-rights gap, not just UX. |
| 2 | Auth & User Flows | Important | "Save to Progress" button has a double-tap race condition that can create duplicate scan records | `ScanResultScreen.kt:571-585` | `isSaved` guard only flips to `true` *after* the async `saveScan()` call returns. A rapid double-tap before that completes passes the guard twice, launching two concurrent saves. The second call's `existingScanId` still reads the stale value, so it inserts a second row instead of updating the first. |
| 3 | Auth & User Flows | Important | `saveScan()` has no error handling at its call sites | `ScanResultScreen.kt:571-585` | If the DB insert throws (disk full, constraint violation, I/O error), the exception propagates unhandled through `scope.launch {}` — an app crash, not a graceful error message, on one of the most-used interactions in the app. |
| 4 | Auth & User Flows | Minor | Password-reset page's complexity policy lives in Firebase Console settings, not code | Firebase Console → Authentication → Settings | Already found and fixed this session (min length 8, all four complexity classes, Require enforcement — verified live). No code enforces this, so it can silently drift if anyone changes the console config later. Worth a periodic regression check. |
| 5 | Detection Pipeline | Not blocking (confirmed) | Real model is bundled and live | `app/src/main/assets/best.tflite` | 80.5MB, YOLOv11, mAP50 0.654. Mock fallback (`mockDetectionResults.random()`) only fires on a fresh clone missing the model file — not the shipping state. |
| 6 | Detection Pipeline | Verified fine | Model missing/corrupted degrades gracefully, does not crash | `YoloDetector.kt:135-214` | Whole function wrapped in `catch (t: Throwable)` (deliberately catches `OutOfMemoryError` too). Any failure falls through to `analysisFailedResult()`, never a crash, never a fabricated random result. |
| 7 | Detection Pipeline | Important | Camera capture failure shows zero user feedback | `CameraScreen.kt`, `capturePhoto(... onFailed = { isScanning = false })` | On failure the spinner just disappears with no toast/error text. A silent failure with no indication anything went wrong. |
| 8 | Detection Pipeline | Important | Bitmap decode for gallery/camera crop happens outside any OOM guard | `cropCameraImageToFrame()` / `cropGalleryImageToFrame()` in `CameraScreen.kt` | Full-resolution decode before `runYoloInference()`'s own `Throwable` catch is in scope. No `inSampleSize` downsampling found. Plausible OOM crash path on lower-RAM devices with large images. |
| 9 | Detection Pipeline | Minor | Confidence floor (0.30) is a manual override, not re-derived from the F1 curve | `YoloDetector.kt`, `CONFIDENCE_THRESHOLD` | Raised from the F1-optimal 0.247 after a real fabric-texture false positive. Already documented/known, not new. The luminance-variance filter doesn't catch all non-skin false positives. |
| 10 | Database | Critical | `fallbackToDestructiveMigration()` with real user data at stake | `DermaDatabase.kt` | Every schema bump (most recently v6→v7) silently wipes all local data, no warning, no export, no recovery path. Acceptable for dev/test; not acceptable to ship v1.0 like this — the next schema change after launch wipes every real user's history. |
| 11 | Database | Important | `ScanRecord.userId` has no foreign key constraint | `ScanRecord.kt` | No `foreignKeys = [...]`, no `ON DELETE CASCADE`. Referential integrity is only maintained by application discipline (Delete Account manually deletes scans first) — nothing at the schema level prevents orphaned rows if that path is ever bypassed. |
| 12 | Database | Verified fine | Scan photos and DB excluded from Android auto-backup | `backup_rules.xml`, `data_extraction_rules.xml` | Found missing and fixed earlier this session; confirmed via `adb shell run-as` before and after. Photos live in app-private internal storage, not accessible to other apps. |
| 13 | Offline Behavior | Verified fine | Core scanning is genuinely fully offline | On-device TFLite + local Room | Zero network dependency in the scan → result → save path. |
| 14 | Offline Behavior | Verified fine | Contribution upload queues and resumes correctly | `ContributionUploadWorker.kt` | Row only marked `uploadedForTraining` after confirmed HTTP success. A process kill mid-upload just means a clean re-attempt next run. `NetworkType.UNMETERED` constraint respected. Explicit timeouts (20s/30s). |
| 15 | Offline Behavior | Minor | Upload worker treats every exception as retryable | `ContributionUploadWorker.kt`, `catch (e: Exception) { Result.retry() }` | Doesn't distinguish transient network errors from permanent config failures (e.g. dead URL). Bounded by 12h period + default exponential backoff, so low impact, but wasted cycles on unfixable failures. |
| 16 | Offline Behavior | Minor | No `HttpURLConnection.disconnect()` calls | `ContributionUploadWorker.kt`, `ClinicLocatorScreen.kt` | Minor connection-resource hygiene issue in loops that can run multiple iterations per job. |
| 17 | Offline Behavior | Verified fine | Slow/flaky connections have explicit timeouts | `ClinicLocatorScreen.kt` (Places 15s/20s, OSRM 10s/15s) | Degrades to an honest empty state rather than hanging indefinitely. |
| 18 | Permissions | Verified fine | Camera and location permission flows fully handled | `CameraScreen.kt`, `ClinicLocatorScreen.kt` | Audited and fixed this session: correct "not yet asked" vs. "permanently denied → Open Settings" states, confirmed live for both. |
| 19 | Permissions | Minor | `READ_MEDIA_IMAGES` declared but unused | `AndroidManifest.xml:6` | Only gallery access path is `PickVisualMedia`, which needs zero runtime permission on any API level. Unnecessary permission declaration adds Play Console review friction for no functional reason. |
| 20 | Permissions | Minor | `RECEIVE_BOOT_COMPLETED` declared but no receiver exists | `AndroidManifest.xml:13` | No `<receiver>` anywhere in the manifest. WorkManager reschedules its own periodic work across reboots without needing this. Looks like unused scaffolding. |
| 21 | Security | Verified clean | No secrets in git history or current tree | Full repo `git log --all` + `git grep` sweep | `local.properties`, `google-services.json`, `*.tflite` all correctly gitignored and never committed. No API-key-shaped strings found anywhere in tracked files or history. |
| 22 | Security | Minor | Places API cert SHA-1 hardcoded to one debug keystore | `ClinicLocatorScreen.kt`, `ANDROID_CERT_SHA1` | Not a secret (SHA-1s are meant to be registered/public), but breaks clinic search for anyone building with a different keystore. Already documented, not new. |
| 23 | Security | Minor | `CONTRIBUTION_UPLOAD_SECRET` ships inside the compiled APK | `BuildConfig.CONTRIBUTION_UPLOAD_SECRET` | Extractable via decompilation by design — documented as a low-value shared secret gating the endpoint from random traffic, not real authentication. Accepted tradeoff, not a surprise. |
| 24 | Security | Verified fine | Manifest exposure surface is minimal | `AndroidManifest.xml` | Only `MainActivity` exported (required, it's the launcher). No other activities/services/receivers/providers declared or exported. No `FileProvider`. `usesCleartextTraffic="false"`. |
| 25 | Input Validation & Load | Important | Save-button double-tap race (see #2) also applies here | `ScanResultScreen.kt` | Directly the "duplicate writes on repeated interaction" failure mode this category asks about. |
| 26 | Input Validation & Load | Important | Scan-entry navigation calls don't set `launchSingleTop` | `HomeScreen.kt` ("Scan Skin"), `ProgressTrackerScreen.kt` ("Start New Scan", "Scan Again") | A rapid double-tap can push two Camera screens onto the back stack, requiring two back-presses to leave. Only the shared bottom-nav logic sets `launchSingleTop = true`; these direct calls don't. |
| 27 | Input Validation & Load | Verified fine | Every traced async operation has a visible loading and error/empty state | Inference, save (except #3), contribution upload, Clinic Locator fetch | No blank-screen hangs found outside the one noted gap. |
| 28 | UI/Responsiveness | Critical | No protection against configuration changes mid-scan | `AndroidManifest.xml` (no `configChanges`/`screenOrientation`), camera/scan state uses plain `remember` not `rememberSaveable` | A device rotation during active camera preview, capture, or inference triggers full Activity recreation that this state does not survive. Reachable in normal use on any phone that rotates freely by default — not a contrived edge case. |
| 29 | UI/Responsiveness | Important | Tablet and landscape layouts are untested | Whole app | Nothing in this session or the codebase suggests either has been checked. The fixed 340dp camera guide frame has unverified overflow/proportion risk on very different screen sizes. |
| 30 | UI/Responsiveness | Verified fixed this session | Quick Actions card height/word-break inconsistency on narrow screens | `HomeScreen.kt` | Simulated a 360dp-wide device, reproduced the bug, fixed it (`minLines`, tighter padding), reproduced the fix live. |
| 31 | UI/Responsiveness | Verified fixed this session | Keyboard covering Login/Register/Edit Profile forms | `Screens.kt`, `ProfileScreen.kt` | Root cause was edge-to-edge mode with no `imePadding()`. Fixed and confirmed. |
| 32 | UI/Responsiveness | Minor | Dialog text fields not explicitly retested for the keyboard-covering issue | Delete Account password field, Progress Tracker edit-note dialog | Both are `AlertDialog`s, which typically handle IME insets differently from a full-screen `Column` — plausibly fine by default but not actually confirmed this session. |

## Severity Summary

| Severity | Count |
|---|---|
| 🔴 Critical | 4 (#1, #10, #28, and #2/#3 together represent a third real core-flow risk) |
| 🟡 Important | 9 (#2, #3, #7, #8, #11, #26, #29 + related) |
| 🟢 Minor | 8 |
| Verified fine / already fixed | 11 |

**Fix first:** #1 (Google-only account deletion dead-end — a real data-rights gap) and #10
(`fallbackToDestructiveMigration()` — the next schema change after launch silently wipes every
real user's data). Everything else is fixable in isolation without touching those two.

---

*Compiled from a full trace of the codebase — auth flows, detection pipeline, Room schema,
offline/WorkManager behavior, permissions, manifest/security surface, async error handling, and
layout — not a keyword or lint-only pass. Verified findings are cited with exact file/line
locations; anything not independently confirmed is marked as untested rather than assumed safe.*
