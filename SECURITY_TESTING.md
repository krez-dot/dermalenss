# DermaLens — Security Testing

Testing date: 2026-08-24, supplemented 2026-09-17
Scope: full app (Kotlin source, `AndroidManifest.xml`, resource XML, Room DB layer, network calls)
Methodology: static code review + manifest/configuration audit, cross-checked against the OWASP Mobile Application Security Verification Standard (MASVS) categories for local data storage, network communication, authentication, and platform interaction. The 2026-09-17 pass also live-tested against a running build (`run-as` on a debuggable install) rather than relying on static review alone.

> **Update 2026-09-17**: a lot changed underneath this doc since 2026-08-24 — Firebase Authentication replaced the local password system entirely, and the Contribute to Research upload pipeline was built and shipped. Two entries below (SEC-02/SEC-03, SEC-11) describe an architecture that no longer exists; see the correction notes inline rather than trusting their original "✅ Pass" verdicts at face value. One new real finding (SEC-F5) came out of this pass.

---

## Summary

| Severity | Count | Status |
|---|---|---|
| High | 0 | — |
| Medium | 3 | Fixed (see [Resolved Findings](#resolved-findings-this-sprint)) |
| Low | 1 | Open |
| Resolved this sprint | 8 | Fixed (see [Resolved Findings](#resolved-findings-this-sprint)) |

No high-severity or remotely-exploitable vulnerabilities were found. All medium findings were local-device platform-configuration gaps (Android manifest/resource settings), not code-level bugs.

---

## Test Cases

| ID | Test Case | Method | Expected Result | Actual Result | Status |
|---|---|---|---|---|---|
| SEC-01 | SQL injection via login/register/edit-profile input | Reviewed all `UserDao`/`ScanRecordDao` queries | All queries use parameterized `@Query` bindings, no string concatenation | Confirmed — no raw SQL concatenation anywhere in the DAO layer | ✅ Pass |
| SEC-02 | Password storage | Reviewed `hashPassword()`/`verifyPassword()` in `DermaColors.kt` | Passwords not stored in plaintext | **Superseded 2026-09-17**: Firebase Authentication now owns real password verification entirely. `hashPassword()`/`verifyPassword()` still exist in source but are dead code — grepped, never called anywhere. Every `User` row's local `passwordHash` field is hardcoded to `""` at every insertion site (`Screens.kt`, `ScanResultScreen.kt`). The original salted-SHA-256 finding below is historically accurate but no longer describes how passwords are actually protected. | ✅ Pass (architecture changed) |
| SEC-03 | Backward-compatible login after the salting change | Traced `verifyPassword()`'s legacy-hash branch | Existing accounts (pre-salt) should still log in without being locked out | Same as SEC-02 — this code path is no longer reachable; login goes through Firebase, not `verifyPassword()`. Historical record only. | N/A (superseded) |
| SEC-04 | Network transport security | Grepped all network calls (`ClinicLocatorScreen.kt` — Overpass API, OSRM routing) | All external calls use HTTPS | Confirmed — no `http://` endpoints found anywhere in source | ✅ Pass |
| SEC-05 | Cleartext traffic policy | Checked `AndroidManifest.xml` | Cleartext traffic should be disabled since no endpoint needs it | Fixed — `usesCleartextTraffic` set to `false` (see SEC-F1) | ✅ Pass |
| SEC-06 | Local data backup exposure | Checked `allowBackup`, `backup_rules.xml`, `data_extraction_rules.xml` | Sensitive tables (users, password hashes) should be excluded from backup, or backup disabled | Fixed — Room database excluded from both rule files (see SEC-F2) | ✅ Pass |
| SEC-07 | Exported component surface | Checked all `<activity>`/`<service>`/`<receiver>`/`<provider>` entries | Only the launcher activity should be exported | Only `MainActivity` is exported (required for the launcher intent-filter); no other components declared | ✅ Pass |
| SEC-08 | File URI exposure (captured scan photos) | Reviewed `CameraScreen.kt`'s `Uri.fromFile()` usage | Captured photo URI shouldn't be exposed to other apps or allow path traversal | Filename is timestamp-based (no user input), URI is only used in-process for Compose Navigation, never passed via `Intent` to another app | ✅ Pass |
| SEC-09 | Crash-based DoS via malformed profile input | Tested blank name, blank email, duplicate email in Edit Profile / Register | App should show an error, not crash | Now guarded with validation + `try/catch` around `SQLiteConstraintException` (fixed this sprint — see below) | ✅ Pass |
| SEC-10 | Privacy Policy accuracy vs. actual implementation | Compared in-app Privacy Policy text against actual storage/upload behavior | Claims should match reality | Corrected this sprint — no longer claims "encrypted storage" or upload-time anonymization that don't exist | ✅ Pass |
| SEC-11 | Contributed research photos — network exposure | Traced the "Contribute to Research" toggle's data path end-to-end | Photos should not be transmitted anywhere without user awareness | **Superseded 2026-09-17**: this finding described the app *before* the upload pipeline was built. Real upload code exists now (`ContributionUploadWorker` → Google Apps Script → Drive), gated on explicit per-scan opt-in consent (a genuine yes/no dialog, not a silent side effect of saving) and `NetworkType.UNMETERED` so it never uses mobile data. The request contains only the image, a random UUID filename, and the detected condition — no account identifier. See `README.md`'s Contribute to Research pipeline section for the full trace. | ✅ Pass (architecture changed, re-verified) |
| SEC-12 | Destructive schema migration | Checked `DermaDatabase.kt` migration strategy | Should not silently destroy user data | Still uses `fallbackToDestructiveMigration()` (re-checked 2026-09-17, unchanged) — any future schema bump wipes all accounts/scans with no warning | ⚠️ Finding (Low) — see SEC-F4 |
| SEC-13 | Local backup exposure of scan photos specifically | Live-tested via `adb shell run-as` on a debuggable build, then cross-checked `backup_rules.xml`/`data_extraction_rules.xml` | Scan photos, like the database, should be excluded from Android's automatic backup | **Finding** — confirmed real scan images physically present in `files/scan_photos/` (and a leftover `files/contributed_scans/` from before an unrelated rename) via `run-as`, and confirmed the backup-exclusion rules only covered the `database` domain, not `file` — meaning these images were eligible for automatic cloud/local backup despite the Privacy Policy's "stored locally on your device" framing. Fixed (see SEC-F5). | ✅ Pass (after fix) |

---

## Findings

### SEC-F1 (Medium, FIXED) — Cleartext traffic allowed but unused
**File:** `app/src/main/AndroidManifest.xml:14`

`android:usesCleartextTraffic="true"` permitted the app to make plaintext HTTP connections. Every network call actually made (Overpass API, OSRM routing) already uses HTTPS, so this flag granted attack surface with no functional benefit — it would have allowed a downgrade to plaintext if a future network call (or a compromised/misconfigured library) accidentally used `http://`, exposing that traffic to on-path interception (e.g. on public Wi-Fi).

**Fix applied:** Set `android:usesCleartextTraffic="false"`.

### SEC-F2 (Medium, FIXED) — App data is fully backup-eligible with no exclusions
**File:** `app/src/main/AndroidManifest.xml:15-17`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`

`android:allowBackup="true"` is set, and both the legacy backup rules (API <31) and the newer data extraction rules (API 31+) are left as unmodified boilerplate — every `<include>`/`<exclude>` is commented out, meaning **no data is excluded from backup**. Combined effect:

- On API 26-30 devices, `adb backup` (available with USB debugging enabled, no root required) could extract the entire app-private storage — including the Room database (names, emails, salted password hashes, scan history) — to an attacker's machine for offline analysis.
- On API 31+ devices, the same data was eligible for Android's automatic cloud backup to the user's Google account.

**Fix applied:** Kept backup enabled (so legitimate device-transfer restore still works for everything else) but explicitly excluded the Room database file in both rule files:
```xml
<!-- backup_rules.xml -->
<full-backup-content>
    <exclude domain="database" path="." />
</full-backup-content>

<!-- data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="." />
    </device-transfer>
</data-extraction-rules>
```

### SEC-F3 (Low, informational) — "Anonymized" claim already corrected, but no anonymization exists
Already addressed in the Privacy Policy text (SEC-10), but worth restating for the record: contributed photos are copied byte-for-byte with no EXIF stripping. This has zero real-world impact today since nothing is uploaded (SEC-11), but should be implemented before any future Firebase/cloud upload feature ships, so the "opt-in, anonymized" framing stays true once photos actually leave the device.

### SEC-F4 (Low) — Silent destructive DB migration
**File:** `app/src/main/java/com/dermalens/app/data/db/DermaDatabase.kt:26`

`fallbackToDestructiveMigration()` means any future schema version bump drops and recreates every table with no warning to the user and no migration path — full account/scan data loss. Not an externally exploitable vulnerability, but a data-integrity risk worth fixing with a proper `Migration` object before more schema changes land, especially this close to a documentation/demo deadline where losing test data would be disruptive.

### SEC-F5 (Medium, FIXED, 2026-09-17) — Scan photos excluded from the database backup rule, but not from backup itself
**File:** `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`

SEC-F2 (below) excluded the Room database from backup, which was the complete fix *at the time* — no scan photos were saved outside the database's own opt-in contribution copy. That changed later in the same development cycle: every saved scan now keeps a local photo copy (`filesDir/scan_photos/`, previously `filesDir/contributed_scans/` when it only applied to contributed scans), so Progress Tracker can show what a condition looked like over time. The backup rules were never updated to match — they still only excluded `domain="database"`, leaving every scan photo (real medical images) eligible for Android's automatic cloud backup and device-transfer, directly contradicting the Privacy Policy's "stored locally on your device" claim.

**How this was found:** not just static review — confirmed live via `adb shell run-as com.dermalens.app ls .../files/scan_photos/` on a debuggable build, which listed real `.jpg` files from actual test scans. Also found a leftover `contributed_scans/` folder with an orphaned image from before an unrelated folder rename, which needed the same exclusion.

**Fix applied:** added `file`-domain exclusions alongside the existing `database` one in both rule files:
```xml
<!-- backup_rules.xml -->
<exclude domain="file" path="scan_photos/"/>
<exclude domain="file" path="contributed_scans/"/>

<!-- data_extraction_rules.xml, both <cloud-backup> and <device-transfer> -->
<exclude domain="file" path="scan_photos/"/>
<exclude domain="file" path="contributed_scans/"/>
```

**Lesson for future schema/storage changes:** backup-exclusion rules don't auto-update when new file-based storage is added — worth re-checking `backup_rules.xml`/`data_extraction_rules.xml` any time a new `filesDir`/`cacheDir` write path is introduced, not just when the Room schema changes.

---

## Resolved Findings (this sprint)

The following were found and fixed earlier in this sprint, prior to this testing pass — included here for a complete record:

1. **Unsalted SHA-256 passwords** → salted SHA-256 with `SecureRandom`, backward-compatible verification for existing accounts (SEC-02, SEC-03)
2. **False "encrypted storage" claim in Privacy Policy** → corrected to describe actual (salted-hash, unencrypted local DB) storage (SEC-10)
3. **Blank-name crash in Profile screen** (`NoSuchElementException`) → input filtered safely, falls back to `"?"` avatar initial
4. **Unhandled duplicate-email crash in Edit Profile** (`SQLiteConstraintException`) → pre-check + validation + `try/catch`
5. **Unguarded duplicate-email race in Register** → `try/catch` added as defense in depth
6. **Cleartext traffic allowed but unused** (SEC-F1) → `usesCleartextTraffic` set to `false`
7. **App data fully backup-eligible with no exclusions** (SEC-F2) → Room database excluded from both legacy and API 31+ backup rules
8. **Scan photos left out of the backup exclusion after storage behavior changed** (SEC-F5, 2026-09-17) → `file`-domain exclusions added for `scan_photos/` and `contributed_scans/` in both rule files

---

## Recommendations (priority order)

1. ~~Consider upgrading password hashing from salted SHA-256 to a purpose-built slow hash~~ **Moot as of 2026-09-17** — password storage/verification is now entirely Firebase Authentication's responsibility, not this app's local code; the old `hashPassword()`/`verifyPassword()` functions are dead code worth deleting rather than upgrading (see SEC-02).
2. Replace `fallbackToDestructiveMigration()` with a real `Migration` before the next schema change (SEC-F4) — still open.
3. The Contribute to Research upload pipeline now exists and ships real photos byte-for-byte with no EXIF stripping (SEC-F3's original concern). Worth adding EXIF stripping before upload if this project continues past the capstone defense, so the "anonymous" claim holds up against metadata analysis, not just filename/account-identifier analysis.
4. ~~Delete the now-dead `hashPassword()`/`verifyPassword()` functions~~ **Done 2026-09-17** — removed along with the private `sha256Hex()` helper (confirmed unused anywhere else first). The `passwordHash` field on `User` is left in place, deliberately: removing it would require a Room schema version bump, and with `fallbackToDestructiveMigration()` still active (SEC-F4), that would silently wipe all local data as a side effect of a code-cleanup pass — not something to do without the team's sign-off.
