# Verification log

Never mark a check as passed unless it was actually performed.

## Performed (2026-10-01)

| Check | Environment | Result |
|---|---|---|
| Domain unit tests: calculator, question generation, explanations, progress, badges, pruning (45 tests in `app/src/test`) | Kotlin 2.2.10 compiler (`kotlinc`) + a minimal JUnit-compatible runner, JDK 21, Linux | **45 passed, 0 failed** |
| Explanation correctness: every `x op y = z` fragment in every explanation, for every operand pair of every topic/difficulty pool (both operand orders for + and ×) | same | passed (>10 000 equations checked) |
| Seeded generation across all 12 topic/difficulty combinations × 60 seeds | same | passed |
| Release PKCS12 keystore generated, `keytool -list` shows one PrivateKeyEntry (alias `numipad_upload`, RSA 4096, CN=NumiPad Junior — not a debug cert) | JDK 21 keytool | passed |
| CI workflow YAML syntax | PyYAML | passed |

## Pending (not yet performed)

The authoring environment has no access to Google Maven / Gradle Plugin Portal, so the
Android build itself has not been run yet. These run in GitHub Actions or locally:

| Check | Where | Status |
|---|---|---|
| `./gradlew :app:testDebugUnitTest` with real JUnit | CI job `test-and-lint` | pending |
| `./gradlew :app:lintRelease` | CI | pending |
| Room schema export (`app/schemas/.../1.json`) — download the `room-schemas` CI artifact and commit it | CI | pending |
| Signed non-minified release APK + AAB | CI job `release` | pending |
| `apksigner verify --print-certs`, no `CN=Android Debug` | CI | pending |
| AAB `jarsigner -verify` + signer fingerprint = release key | CI | pending |
| Release permissions (no INTERNET/ACCESS_NETWORK_STATE etc.), target/min/compile SDK in badging | CI | pending |
| Native `.so` inspection / 16 KB alignment (expected: none — all dependencies are pure JVM/Kotlin) | CI `native-report.md` | pending |
| R8 + resource shrinking build (`workflow_dispatch` with `minify=true`) after the above passes, then re-test | CI + device | pending |
| `adb install` of signed APK + `adb logcat` review | device/emulator | pending |
| First launch in airplane mode | device | pending |
| Calculator input, errors, history, "Use result" | device | pending |
| All topics × difficulties, feedback, explanations, review | device | pending |
| Resume after process death (`adb shell am kill com.numipad.junior` while backgrounded) | device | pending |
| Daily progress, badges, reset actions | device | pending |
| Rotation, two-panel layout (≥ 720 dp), font scale 200 %, TalkBack, predictive Back | device | pending |
| 16 KB runtime test (only needed if native libraries appear) | 16 KB emulator image | n/a until CI report |

Record for each device run: device/emulator model, Android version, artifact (APK SHA-256), result.
