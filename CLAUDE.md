# Local Diary — CLAUDE.md

Offline-only Android diary app. Voice recording is transcribed on-device via whisper.cpp
(JNI/C++). No LLM, no image analysis, no network calls of any kind — this is a hard product
requirement, not a temporary scope cut. The app must never request the `INTERNET` permission.
Android first; an iOS port is planned later by reusing the whisper.cpp C++ core with a Swift
binding layer, so keep the native/business logic cleanly separated from Android-specific UI code.

Full feature scope lives in `docs/GDD.md`. Implementation order and progress live in
`docs/CHECKLIST.md`. Read both before starting work that isn't a trivial fix.

## Module layout

- `:app` — Kotlin + Jetpack Compose UI, MVVM. Depends on `:core-ml` and `:core-data`. No business
  logic lives in Composables or Activities beyond state hoisting — delegate to ViewModels/use cases.
- `:core-ml` — whisper.cpp vendored as a git submodule at `core-ml/src/main/cpp/whisper.cpp`.
  JNI bridge is `core-ml/src/main/cpp/whisper_jni.cpp`, built by `core-ml/src/main/cpp/CMakeLists.txt`
  into `libdiary_whisper.so` for `arm64-v8a` (real devices) and `x86_64` (emulator). Kotlin-facing
  API is `com.localdiary.coreml.WhisperEngine` (package `com.localdiary.coreml`) — a thin facade,
  not thread-safe per-instance, matching whisper.cpp's own contract.
- `:core-data` — Room database. Entity `com.localdiary.coredata.DiaryEntry`, DAO
  `DiaryEntryDao`, database class `DiaryDatabase`.

New feature modules should follow this same pattern: `core-*` for engine/data logic with no
Android UI dependencies, `feature-*` only once a feature has enough code to justify its own
module (don't create empty feature module shells ahead of time).

## Design principles (apply to every change, not just new modules)

- **GRASP**: assign responsibility by Information Expert (the class with the data does the work
  on it — e.g. `DiaryEntryDao` owns queries, not the ViewModel); keep coupling low and cohesion
  high between `:app` / `:core-ml` / `:core-data`; use Controller classes (ViewModels) to
  coordinate between UI and data/ML layers rather than letting Composables call DAOs or
  `WhisperEngine` directly; reach for Pure Fabrication (a small dedicated class with no domain
  meaning, e.g. an audio-capture helper) before bloating an entity or ViewModel with unrelated work.
- **GoF patterns**, applied where they earn their place (don't force a pattern where a plain
  function is clearer — this still follows the "no premature abstraction" rule below):
  Repository pattern for `:core-data` access, Strategy if/when a second STT or export format is
  added, Observer via Kotlin `Flow` for entry list updates, Facade for `WhisperEngine` (already
  the shape it's in) and for any future biometric-auth wrapper.
- These are organizing principles, not a checklist to satisfy — a one-off diary bugfix does not
  need a pattern name attached to it.

## Workflow rules

1. **Ask before assuming.** If a request is ambiguous about product behavior, data model shape,
   or UX flow, ask a clarifying question (prefer the structured question tool when there are 2-4
   concrete options) before writing code. Don't silently pick a direction on anything that would
   be expensive to reverse.
2. **Announce the plan, then wait for explicit go-ahead before implementing.** At the start of
   every implementation task (not just ambiguous ones — this is separate from rule 1), state in
   plain terms what you're about to do — which files/modules will change, the approach, and the
   order of steps — and then stop and wait for the user to explicitly confirm (e.g. "go", "start
   implementing") before making any edits or running build/install commands. Do not treat silence,
   a topic change, or the plan simply going unchallenged as approval — wait for an actual go-ahead.
   This applies to the first edit of a new task; small follow-up fixes within an already-approved
   task don't each need a fresh confirmation.
3. **Never say a feature is done without having tested it.** "Compiles" is not "done." Before
   reporting a feature complete:
   - Build (`./gradlew :app:assembleDebug`) and confirm it succeeds.
   - Install and launch it on a running emulator (see Testing below) or, if the user says they're
     on a physical device session, guide them through it live.
   - Exercise the actual feature path (tap the button, record audio, check the entry appears,
     etc.), not just "the app didn't crash on launch."
   - Check logcat for errors/exceptions during the flow, not just at launch:
     `adb logcat -d -t 300 | grep -iE "localdiary|AndroidRuntime|FATAL|Exception"`
   - If the user has since added an Android MCP tool (check the current tool list / system
     reminders each session), prefer it over the manual adb pipeline below for install/launch/UI
     inspection — but the manual pipeline is the fallback and must still work.
4. **UI changes get a math pass before a screenshot pass.** Whenever a change touches layout:
   - First compute expected geometry by hand: screen size and density (`adb shell wm size`,
     `adb shell wm density`), the dp values used in the Compose modifiers, and sum
     padding/margins/heights to get expected bounding boxes in dp (and px, using the density
     scale factor). Predict whether anything should overlap, clip, or run off-screen.
   - Then take a screenshot (`adb shell screencap -p /sdcard/screen.png` →
     `adb pull /sdcard/screen.png <path>` — on Windows Git Bash, set `MSYS_NO_PATHCONV=1` and use
     a native `D:/...` destination path, not a `/d/...` one, or `adb pull` will fail to resolve it)
     and visually inspect it against the prediction.
   - Save verification screenshots to the session scratchpad directory, never into the repo.
   - Report any mismatch between predicted and actual layout explicitly — don't just eyeball the
     screenshot and call it fine if the math said something else.
5. **Don't over-scope.** Follow the system-level "no premature abstraction / no hypothetical
   future-proofing" rule even while applying GRASP/GoF above — these principles guide how to
   structure code that's actually being written, not a license to add layers nothing needs yet.
6. **Never generate art or visual assets.** No launcher icons, illustrations, placeholder images,
   generated logos, or similar — not via image-generation tools, not as inline SVG/vector art
   standing in for real art, not even as "temporary" placeholders. All such assets are supplied by
   the user. If a task needs one (e.g. the M5 launcher icon item), stop and ask the user for the
   asset file rather than producing a substitute. Plain UI chrome that is unambiguously code, not
   art — layout, spacing, Material color/typography choices, standard Compose/Material icons from
   the official icon library — is not "art" for purposes of this rule.

## Testing strategy (see the earlier design discussion, unchanged)

- Unit tests (JVM, no device): `./gradlew test`.
- Emulator (everyday driver, no physical device needed): AVD `LocalDiary_API36`
  (API 36, x86_64, google_apis) or the pre-existing `Pixel_8` AVD (API 37, x86_64). Launch with
  `emulator -avd LocalDiary_API36`; the native lib is built for both `arm64-v8a` and `x86_64` so
  it runs correctly on this AVD.
- Weekly physical device slot: reserved specifically for what the emulator can't tell us honestly
  — real transcription latency on ARM silicon, battery/thermal under sustained inference, real
  mic quality. Keep a running list in `docs/CHECKLIST.md` of what to check next time device access
  is available.

## Known environment facts (so you don't rediscover these the hard way)

- AGP 9.2.1 + Gradle 9.4.1 + Kotlin 2.2.10, built-in Kotlin support (no `kotlin-android` plugin
  applied). `compileSdk`/`targetSdk` = 37 (platform 37.1 installed locally). NDK
  27.2.12479018, CMake 3.22.1.
- `gradle.properties` sets `android.disallowKotlinSourceSets=false` — required because KSP (Room's
  annotation processor) still registers sources via the pre-AGP9 DSL; remove this once KSP ships
  built-in-Kotlin support and confirm the build still works before removing.
- Package namespace root: `com.localdiary.*` (placeholder — rename before shipping if a real
  package/app identity is decided).
- No launcher icon yet — cosmetic, low priority.
- Bundled Whisper models are gitignored (`app/src/main/assets/models/*.bin`, ~239MB combined —
  too large for git history). Fetch them before building:
  ```
  curl -L -o app/src/main/assets/models/ggml-base-q5_1.bin \
    https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin
  curl -L -o app/src/main/assets/models/ggml-small-q5_1.bin \
    https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin
  ```
