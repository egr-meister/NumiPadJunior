# NumiPad Junior

Offline children's calculator and arithmetic practice app for Android — Kotlin + Jetpack Compose.
No account, no internet permission, no ads, analytics or cloud services. Everything stays on the device.

## Features

- **Learning Tablet main screen** – calculator panel (muted teal) and *Today Practice* panel (lavender).
  Side by side when the window is ≥ 720 dp wide (tablets, landscape, large resizable windows);
  stacked calculator-above-practice on narrow portrait phones. Calculation works immediately — no onboarding.
- **Calculator** – digits, decimal point, + − × ÷, =, Clear, Backspace; one binary operation at a time; large result;
  local history of the latest 50 calculations with "Use result".
- **Practice** – Addition, Subtraction, Multiplication, Division × Easy / Medium / Hard; 10 questions per session,
  four answer choices, no timer, feedback ("That's right!" / "Let's look at it together."), deterministic explanation,
  dot counters for small values (with a text equivalent), results, review, resume after the app is closed, end-session.
- **Progress** – today's answered / correct / incorrect / accuracy and per-topic breakdown, "Most practiced", six local
  participation badges (no coins, streaks, purchases or speed/accuracy rewards).
- **Settings** – default topic and difficulty, reduce decorative animation, clear history / progress & badges / all data
  (behind a typed grown-up check, which is an accidental-tap safeguard, not authentication), bundled privacy screen.

## Architecture

Single `:app` module, manual dependency injection (`AppContainer`).

```
com.numipad.junior
├── AppContainer, NumiPadApp, MainActivity
├── data/local          Room entities, DAOs, NumiPadDatabase (schemas exported, explicit migrations)
├── data/repository     HistoryRepository, PracticeRepository (transactions, pruning), SettingsRepository (DataStore)
├── domain/calculator   Calculator – pure state reducer, BigDecimal arithmetic
├── domain/questions    Topic, Difficulty, QuestionGenerator, Distractors, Explanations
├── domain/progress     AppClock, ProgressCalculator, BadgeRules, AnswerRules
└── ui/                 tablet, practice (question/results/review), history, progress, settings (+privacy), theme, components
```

Domain code has no Android dependencies. Randomness (`RandomProvider` / `SeededRandomProvider`) and time
(`AppClock`) are injected so tests are deterministic. ViewModels expose `StateFlow`, collected with
`collectAsStateWithLifecycle`. No string evaluation, JavaScript, WebView or remote math.

## Toolchain versions

| Component | Version |
|---|---|
| compileSdk / targetSdk / minSdk | **36 / 36 / 26** |
| Android Gradle Plugin | 8.11.1 |
| Gradle (wrapper, committed) | 8.14.3 |
| Kotlin (+ Compose compiler plugin) | 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| JDK (build) | 17 (Temurin in CI); bytecode target 17 |
| Compose BOM | 2025.08.00 (+ material-icons-core 1.7.8) |
| Material 3 | from BOM |
| Activity Compose | 1.10.1 |
| Lifecycle (runtime-compose, viewmodel-compose/ktx) | 2.9.2 |
| Navigation Compose | 2.9.3 |
| Room (runtime, ktx, compiler, Gradle plugin) | 2.7.2 |
| DataStore Preferences | 1.1.7 |
| core-ktx / core-splashscreen | 1.16.0 / 1.0.1 |
| kotlinx-coroutines | 1.10.2 |
| JUnit | 4.13.2 |
| Build tools (CI) | 36.0.0 |

All versions are pinned in `gradle/libs.versions.toml`. Every library used directly is declared directly.

## Setup & build

Requirements: JDK 17, Android SDK Platform 36 + Build-Tools 36.0.0 (`ANDROID_HOME` or `local.properties`).

```bash
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:lintRelease            # release lint
./gradlew :app:assembleDebug          # debug APK – needs no release credentials
./gradlew :app:assembleRelease :app:bundleRelease   # signed release (credentials required)
./gradlew -Pnumipad.minify=true :app:assembleRelease :app:bundleRelease   # R8 + resource shrinking
```

Outputs:

| Artifact | Path |
|---|---|
| Release APK | `app/build/outputs/apk/release/app-release.apk` |
| Release AAB | `app/build/outputs/bundle/release/app-release.aab` |
| R8 mapping (minified builds) | `app/build/outputs/mapping/release/mapping.txt` |
| CI bundle | Actions artifact `numipad-junior-release` (APK, AAB, verification reports, mapping) |

## Calculator rules

- One binary operation (`a op b`); no parser, parentheses, %, powers, roots or memory.
- Max absolute operand / reusable result **1,000,000**; max **6** fractional digits per entered operand.
- `BigDecimal` arithmetic. Division rounds **HALF_UP to 6 decimals**; any result with more than 6 decimals is rounded
  the same way and shown with **≈** plus "Rounded to 6 decimal places". Trailing zeros are removed; `-0` shows as `0`.
- Negative results supported; a leading minus starts a negative operand (`5 × −3`, `5 − −3`; after `+`, minus replaces the operator).
- One decimal point per operand; `.` on an empty operand gives `0.`; leading zeros normalised.
- Another operator before the second operand replaces the operator; an operator after both operands evaluates first, then chains.
- `=` needs two valid operands; repeated `=` does nothing (no repeat, no duplicate history).
- Backspace edits the operand being typed (or removes the pending operator); after a result it makes the result an editable first operand.
- Digit after a result starts a new calculation; operator after a result reuses it.
- Errors ("You can't divide by zero. Try another number." / "That number is too large for this calculator.") never crash,
  never create history, and keep the expression for correction. Results are never truncated.

## Practice generation

| Difficulty | Operands | Notes |
|---|---|---|
| Easy | 1–10 | |
| Medium | 1–50 | |
| Hard | 1–100 | multiplication answers up to 10,000 (shown in the difficulty description) |

- Each topic/difficulty has an explicit finite pool of valid pairs: + and × use unordered pairs (order randomised on display),
  − uses a ≥ b (answer ≥ 0), ÷ uses dividend and divisor both in range with no remainder (positive integer answers only;
  Easy ÷ has 27 pairs, the smallest pool). Sessions sample the shuffled pool, so generation always terminates.
- No duplicates within a session (reversed + / × pairs count as the same); the previous session of the same topic/difficulty
  is avoided while the pool allows; pools smaller than 10 would wrap explicitly.
- Four distinct integer options: operation-specific distractors (nearby totals, ±1/±2, ±10, one group off, + vs × confusion,
  nearby quotient) with a bounded outward-walk fallback; ≥ 0 for + and −, ≥ 1 for × and ÷. Correct position is shuffled and
  never sits in the same slot three times in a row.
- All ten questions with their option order are stored in Room before the first is shown.

## Progress, sessions and data

- Each answer is written once (guarded `UPDATE … WHERE selectedAnswer IS NULL` in a transaction) with its timestamp and the
  **local date at answer time**; daily stats use that stored date, so sessions crossing midnight split across two days and
  later time-zone changes don't rewrite history. Nothing resets at midnight.
- Accuracy = correct ÷ answered × 100; with zero answers the UI shows "No answers yet".
- "Most practiced" counts answered questions per topic across all practice (ties → "Several topics: …").
- Room keeps the latest 100 sessions; before older ones are deleted their answers are folded into
  `archived_daily_total` (date × topic), so displayed totals never decrease. Badge unlocks are stored separately and never revoked.
- DataStore holds settings and the current calculator input (restored on launch).

### Room schema & migrations

`exportSchema = true`, schemas go to `app/schemas/` (Room Gradle plugin). The first build generates
`app/schemas/com.numipad.junior.data.local.NumiPadDatabase/1.json` — commit it (CI also uploads it as `room-schemas`).
For every future schema change bump `VERSION` and add a `Migration` to `NumiPadDatabase.MIGRATIONS`;
destructive migration is not enabled.

## Offline, privacy and backup

- Manifest removes `INTERNET` and `ACCESS_NETWORK_STATE` (`tools:node="remove"`), requests no runtime permissions, has no
  networking libraries, external links or WebView.
- `allowBackup="false"`, `fullBackupContent` (≤ Android 11) and `dataExtractionRules` (Android 12+) exclude every domain from
  cloud backup **and** device transfer.
- Privacy screen in Settings explains that everything stays on the device. "Clear all local data" restores first-launch state.

**Permission verification:** CI runs `aapt2 dump permissions` on the release APK and fails on INTERNET, network state, camera,
microphone, location, contacts or notification permissions. Locally:
`$ANDROID_HOME/build-tools/36.0.0/aapt2 dump permissions app/build/outputs/apk/release/app-release.apk`
(also inspect `app/build/intermediates/merged_manifests/release/AndroidManifest.xml`).

## Android 16 (API 36) notes

- Edge-to-edge is mandatory: `enableEdgeToEdge()` + `WindowInsets.safeDrawing` / Scaffold insets handle system bars and cutouts.
  System bars stay visible; no immersive mode; the screen is not kept awake.
- Predictive Back: `android:enableOnBackInvokedCallback="true"`; Back is handled by Navigation Compose (no `onBackPressed`).
  Practice → main screen (session kept), review → results, history/progress/settings → parent, main screen → exits.
- Large screens ignore orientation/resizability restrictions — the app declares none and adapts its layout.

## 16 KB page size

All dependencies are JVM/Kotlin libraries; no NDK code is used, so **no native `.so` files are expected**. CI checks this on every
release (`native-report.md` in the artifact): it lists `.so` files in the APK and AAB and, if any appear, verifies
`zipalign -c -P 16` and ELF `LOAD` alignment ≥ 16 KB, failing otherwise. Runtime testing on a 16 KB emulator is only required
if native libraries ever show up. Targeting API 36 alone is not treated as proof.

## Release signing (PKCS12)

`app/build.gradle.kts` defines `signingConfigs.release` (`storeType = "PKCS12"`, v1+v2+v3) and assigns it to the release
build type. Credentials come from environment variables or an uncommitted `keystore.properties`
(see `keystore.properties.example`). Release tasks (`assemble*/bundle*/package*/sign*Release`) **fail** when credentials are
missing — there is no debug-signing fallback. Debug builds need no credentials. `*.p12`, `*.jks`, `keystore.properties` are git-ignored.

GitHub Secrets:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | base64 of the `.p12` file (one line) |
| `ANDROID_KEYSTORE_PASSWORD` | store password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password (same as store password for PKCS12) |

Google Play App Signing: this keystore is the **upload key**. Google holds the **app signing key** and re-signs what users
install. Keep the upload key private and backed up; if it is lost or leaked, request an upload-key reset in Play Console.
The upload certificate is self-signed, which is normal — CI checks integrity and the expected fingerprint, not a CA chain.

## CI (`.github/workflows/android-release.yml`)

1. JDK 17 + Android SDK Platform 36 / Build-Tools 36.0.0, wrapper validation, committed Gradle Wrapper.
2. Unit tests and `lintRelease` (reports + Room schemas uploaded).
3. Decodes the PKCS12 keystore into a temp dir, builds signed release APK + AAB (`minify` input for the R8 step).
4. `apksigner verify --print-certs` – fails on error or `CN=Android Debug`.
5. `jarsigner -verify` on the AAB + SHA-256 fingerprint must equal the release key (APK too).
6. Permission check, target/min/compile SDK check, native-library / 16 KB check.
7. Uploads verified artifacts; always deletes the temporary keystore. No emulator required.

**Submit only the `.aab` to Google Play.** The APK is for local installation and verification.

## R8 / shrinking

Default release builds are **not minified** (first verify the signed non-minified release). Once verified, run the workflow with
`minify = true` (or `-Pnumipad.minify=true`), repeat calculator, generation, persistence and navigation checks on a device, and
keep `mapping.txt` (uploaded by CI). `proguard-rules.pro` only keeps enum names (persisted via `name()`); Room, DataStore,
Navigation and Compose ship their own consumer rules.

## Local device verification

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat --pid=$(adb shell pidof -s com.numipad.junior)
# process-death test: open a session, press Home, then
adb shell am kill com.numipad.junior
```

Checklist (airplane mode first launch, calculator & errors, history reuse, every topic × difficulty, feedback/explanations/review,
resume after process death, daily progress & badges, rotation & two-panel layout, 200 % font + TalkBack, predictive Back,
data reset, no permission prompts) and its current status are in [`docs/VERIFICATION.md`](docs/VERIFICATION.md).

## Completed checks and pending items

See `docs/VERIFICATION.md`. Summary: domain unit tests (45) were executed and pass; Android build, lint, signing verification,
permission/16 KB inspection and on-device checks are **pending** until the first CI run / device session — they are not reported as passed.
