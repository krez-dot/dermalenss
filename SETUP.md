# DermaLens — Dev Environment Setup

For a groupmate getting set up to help code. This assumes the normal path (Android Studio),
which is much simpler than doing everything by command line.

---

## 1. Install Android Studio

Download and install **Android Studio** (Hedgehog or later) from
[developer.android.com/studio](https://developer.android.com/studio). This bundles everything
needed to build and run the app:

- JDK (Java 17)
- Android SDK + platform tools
- An emulator (AVD Manager)

During first launch, let it run through the setup wizard and install the default SDK
components. No extra manual SDK setup needed — Android Studio handles it.

## 2. Clone the repo

```
git clone https://github.com/krez-dot/dermalenss.git
cd dermalenss
```

Firebase Authentication is already merged into `master` — just clone and stay on `master`, no
branch switch needed.

## 3. Open in Android Studio

`File → Open` → select the cloned `DermaLens` folder. Let Gradle sync run — it can take a few
minutes the first time as it downloads dependencies. If it fails on first sync, just try
`File → Sync Project with Gradle Files` again once.

## 4. Get `google-services.json`

The app now uses Firebase Authentication, which requires a config file that isn't something
Android Studio generates for you.

- If you've been added as a member of the Firebase project: go to
  [console.firebase.google.com](https://console.firebase.google.com) → DermaLens project →
  ⚙️ Project Settings → scroll to "Your apps" → download `google-services.json`.
- Otherwise: ask Mark Joseph to send you the file directly.

Place it at:
```
app/google-services.json
```
(same folder as `app/build.gradle.kts`, **not** the repo root)

Without this file, the build will fail as soon as it hits the `google-services` Gradle plugin
step.

## 5. (Optional) Get the trained model file

The YOLO model (`best.tflite`) isn't committed to git — it's a large binary that changes with
every training run, so it's shared separately (Drive/Colab), not through version control.

If you're working on anything AI/detection-related, ask for the current `best.tflite` and place
it at:
```
app/src/main/assets/best.tflite
```
If you don't have it, the app still builds and runs fine — the scan screen just falls back to
mock/random results instead of real detections, which is fine for UI/feature work unrelated to
the model itself.

## 6. Run the app

Either:
- **Emulator**: `Tools → Device Manager → Create Device` (any phone profile, API 26+), then hit
  the green Run button with that emulator selected.
- **Physical device**: enable Developer Options + USB Debugging on an Android phone, plug it in
  via USB, select it in the device dropdown, hit Run.

## 7. A couple of known non-issues

- **VS Code** (if anyone opens the project there instead of Android Studio) will show
  "Unresolved reference: androidx" on every Compose/Room import. This is fake — VS Code doesn't
  fully understand the Gradle build. The project builds fine through Android Studio/Gradle
  regardless. Don't "fix" these.
- First Gradle sync can be slow (dependency downloads). Subsequent syncs are much faster.

---

## Project structure, quick orientation

```
app/src/main/java/com/dermalens/app/
├── data/
│   ├── db/          # Room database, DAOs
│   └── model/       # ScanRecord, User entities
├── ml/              # YoloDetector.kt -- on-device inference
├── navigation/       # NavGraph, Screen sealed class
└── ui/
    ├── screens/     # All screen composables (one file per feature area mostly)
    └── DermaColors.kt / AppSettings.kt   # Theming, prefs, shared helpers
```

See `HANDOFF.md` for a deeper walkthrough of what's built and how each feature works, and
`FIREBASE_AUTH_PLAN.md` for what's currently in progress on this branch.
