# Local Diary — Implementation Checklist

Working checklist, kept in sync with `docs/GDD.md`. Update this file as items complete — don't
just report progress in chat and let this drift out of date.

**Definition of done for every item below** (per `CLAUDE.md` workflow rules): built successfully,
installed and run on the emulator (or walked through live on a physical device session), the
actual user-facing behavior exercised (not just "app launches"), logcat checked clean for that
flow, and — for anything touching layout — the math-then-screenshot UI check performed. An item
does not get checked off on "the code looks right."

If a requirement is unclear when you reach an item, stop and ask before implementing — don't
guess and build the wrong thing.

---

## M1 — MVP: record, transcribe, keep

- [x] `RECORD_AUDIO` permission request flow (rationale UI → system prompt → denied-state handling)
- [x] Audio capture class (`AudioRecord` → 16kHz mono `FloatArray`), unit-testable independent of UI
- [x] Whisper model files chosen and bundled in `app/src/main/assets/` — both multilingual
      quantized tiers are shipped (`ggml-base-q5_1.bin` "Fast" ~57MB, `ggml-small-q5_1.bin`
      "Precise" ~181MB, ~238MB total), auto-detecting language (e.g. Portuguese/English) rather
      than `.en`-only tiny models, since multilingual support was a stated requirement. Real
      on-device (Galaxy A54) testing found the native build was compiling at -O0 (AGP's debug
      variant passes `CMAKE_BUILD_TYPE=Debug` straight through to the vendored whisper.cpp/ggml,
      leaving it fully unoptimized) — fixed in `core-ml/src/main/cpp/CMakeLists.txt` by forcing
      Release + `GGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16`, which resolved the "even base model
      takes ages" complaint. Model tier is now user-selectable at runtime (dropdown on the
      new-entry screen) rather than fixed — see below
- [x] Model tier picker (Fast/base vs. Precise/small) on the new-entry screen — only one tier's
      model is kept resident in RAM at a time; switching releases the old engine and reloads the
      other (shown as the existing "loading model" state), disabled while recording/transcribing
- [x] Live "streaming" transcription while recording: the growing recording is periodically
      re-transcribed in full (~every 2.5s, not true incremental/windowed streaming — see
      NewEntryViewModel) and the preview text updates live; language is detected once on the
      first tick and pinned for the rest of that recording; a final full-context pass replaces
      the preview on stop. A mutex serializes streaming ticks against the final pass since
      WhisperEngine isn't thread-safe for concurrent calls — verified on emulator (Precise tier):
      CPU stayed continuously busy during recording confirming ticks fire, stop correctly waited
      for the mutex before running the final pass, no crash/ANR/deadlock across the session
- [x] `WhisperEngine` loaded on background thread at startup; app shows a "loading model" state
      until ready
- [x] Record button wired to capture + transcribe, with a real recording/transcribing UI state
- [x] Transcription result saved as a new `DiaryEntry`
- [x] Entry is editable after save, persisting edits back through the DAO — `NewEntryScreen`/
      `NewEntryViewModel` were merged into `EntryEditorScreen`/`EntryEditorViewModel` (new/edit
      share the same screen, distinguished by an optional entryId) rather than duplicating a
      near-identical screen; `DiaryEntryDao` gained `update()` and `getById()`
- [x] Autosave: text changes (typing, streaming ticks, or the final transcription) schedule a
      persist ~2s after the last change (debounced, restarts on every further edit); a new
      entry's first autosave inserts and remembers the generated id, later ones update that row.
      Verified directly against `diary.db` via `sqlite3` *while still on the editor screen*
      (no navigation), isolating the debounce timer from the separate back-button flush below
- [x] Manual "new entry" flow that skips recording entirely
- [x] Entry list screen wired to `DiaryEntryDao.observeAll()` (`HomeViewModel`/`HomeScreen`,
      reverse-chronological, live-updating `StateFlow`) — each card shows the entry's first
      non-blank line as its title (falling back to its creation date if the entry has no text),
      plus that entry's own creation date in the bottom-right corner. Verified on emulator:
      two entries saved, list updates live without a manual refresh, correct per-entry dates
      (not a hardcoded "today"), reverse-chronological order
- [x] Tapping a card opens it in the editor pre-filled with its saved text (`DiaryScreen.Editor`
      now carries an optional entryId instead of being a singleton "new entry" state) — the
      back arrow also silently persists any non-blank pending text before closing (no validation
      error shown, unlike the explicit Save button), so leaving without tapping Save doesn't
      lose an autosave that hasn't fired yet. Full view/delete beyond editing text is still open
- [x] Empty-state UI (no entries yet)
- [x] Error states: permission denied (mic disabled + rationale dialog), model load failure,
      empty/failed transcription (verified on emulator — silent virtual mic input correctly
      surfaces "Didn't catch that")
- [ ] Navigation Compose wired for list ↔ detail ↔ new-entry — home ↔ new-entry currently uses a
      lightweight `AnimatedContent` state switch instead (deliberate, see CLAUDE.md discussion);
      revisit once list/detail screens exist
- [x] New-entry pass: typed entry saved and verified in `diary.db` via `sqlite3` on the emulator;
      voice path exercised through permission grant → record → transcribe (native pipeline
      confirmed working, CPU-bound ~90s on emulator for empty/silent input — real speech latency
      needs the weekly physical-device slot); logcat clean throughout both paths

## M2 — Privacy

- [ ] SQLCipher added to `:core-data`, `DiaryDatabase` opened with a passphrase
- [ ] Passphrase generated and stored via Android Keystore (not SharedPreferences, not a plain file)
- [ ] Existing (unencrypted, scaffold-era) DB migration path decided — likely just "wipe and
      recreate" since no real user data exists yet; confirm before assuming
- [ ] Biometric/PIN lock screen (`androidx.biometric.BiometricPrompt`) shown on cold start
- [ ] Lock re-triggered after backgrounding past a timeout (decide the timeout value)
- [ ] Verify: force-close the app, relaunch, confirm lock screen blocks entry until authenticated
- [ ] Verify: pull the DB file off the emulator (`adb pull`) and confirm it is not a plain SQLite
      file (i.e. actually encrypted, not just gated by the lock screen)

## M3 — Search & calendar

- [ ] Room FTS virtual table over transcript text
- [ ] Search UI + query wiring, verified against multiple real saved entries
- [ ] Calendar view screen, verified that tapping a date jumps to that day's entries
- [ ] Navigation updated to reach calendar view from the main list

## M4 — Backup

- [ ] Export format decided (structure of the encrypted archive: entries + referenced media)
- [ ] Export flow via Storage Access Framework, encrypted with a user-provided or
      keystore-derived key (decide which, and how the user would recover it)
- [ ] Import flow: pick file, decrypt, and an explicit merge-vs-replace decision surfaced to the user
- [ ] Round-trip test performed and verified: export → wipe app data → import → entries match

## M5 — Polish

- [ ] Light/dark theme applied and checked in both modes
- [ ] Accessibility pass: content descriptions added, TalkBack walkthrough of record button + list
- [ ] Launcher icon added
- [ ] First-run onboarding screen (permission + offline-only explanation)

---

## Weekly physical-device checklist

Reset/update this list each time before a physical device session so the limited time is spent
on what the emulator genuinely can't verify. Remove items once actually checked; add new ones as
they come up.

- [ ] Real transcription latency (tiny vs. base model) on actual hardware
- [ ] Real speech transcription accuracy for the bundled multilingual `ggml-base-q5_1.bin` model,
      in both Portuguese and English — emulator testing so far only confirmed the pipeline runs
      end-to-end (permission → record → transcribe → save) against silent virtual-mic input, not
      actual recognition quality
- [ ] Battery/thermal behavior during a sustained multi-minute recording + transcription
- [ ] Real microphone quality/noise handling vs. emulator/PC mic
- [ ] APK install size and first-launch model-load time from real storage
- [ ] Biometric prompt behavior on real fingerprint/face hardware (once M2 lands)

## Explicitly deferred / not yet scoped (do not build without confirming first)

- iOS port
- Photo attachment capture/UI (schema field exists, nothing else scoped)
- Multi-language Whisper model selection
- Reminders/notifications
- Home-screen widget / quick capture
