# Local Diary — Design Document

## Vision

A private, offline voice diary. The user speaks (or types), the app transcribes locally via
whisper.cpp, and the entry is stored encrypted, on-device, forever reachable without a network
connection. Android first; iOS later, sharing the C++ transcription core.

No LLM writing assistance, no image analysis, no cloud sync, no telemetry, no `INTERNET`
permission. If a future feature seems to need network access, that is a sign it doesn't belong
in this app, not a sign the manifest needs updating.

## Product principles

- **Offline-first is absolute**, not a fallback mode. Every feature must work in airplane mode
  because the app has no other mode.
- **The diary is private by default.** Assume the user does not want entries readable by anyone
  who picks up an unlocked phone, which is a weaker guarantee than the diary itself needs.
- **Whisper output is a draft, not a transcript of record.** STT will mishear things; the user
  must always be able to see and fix what was written.
- **Small and fast beats accurate and heavy.** Model choices should favor tiny/base quantized
  Whisper variants that keep the app responsive on mid-range phones, not maximum transcription
  accuracy.

## Status legend

`[x]` shipped and verified working · `[ ]` not started · `[~]` in progress

## Feature tiers

### M0 — Foundation (done)

- [x] Gradle multi-module scaffold (`:app`, `:core-ml`, `:core-data`), AGP 9 / Kotlin 2.2.10
- [x] whisper.cpp vendored + JNI bridge, builds for `arm64-v8a` and `x86_64`
- [x] `WhisperEngine` Kotlin facade (load model / transcribe / release)
- [x] Room `DiaryEntry` entity + DAO (unencrypted — superseded by M2)
- [x] Compose UI shell with a placeholder record button
- [x] Emulator-based test loop confirmed working end to end

### M1 — MVP: record, transcribe, keep

The smallest version of the app that is actually useful as a diary.

- [ ] `RECORD_AUDIO` runtime permission request with rationale UI (explain *why*, once, before
      the system prompt)
- [ ] Audio capture pipeline: `AudioRecord` → 16kHz mono `FloatArray`, feeding `WhisperEngine`
      directly (Information Expert: the capture class owns buffering/resampling, not the ViewModel)
- [ ] Whisper model bundled in `assets/` (tiny or base, quantized) and loaded on a background
      thread at app start, with a loading/"not ready yet" UI state
- [ ] Record button wired to real capture + transcription, with a visible recording/transcribing
      state (not just a label swap)
- [ ] Save the resulting transcript as a `DiaryEntry` via the DAO
- [ ] **Editable transcript**: after transcription (or at any later time), the user can edit the
      entry's text and save corrections
- [ ] **Manual entry creation**: a "new entry" path that skips recording entirely and opens a
      blank text editor
- [ ] Entry list screen: reverse-chronological, backed by `DiaryEntryDao.observeAll()` (`Flow`)
- [ ] Entry detail screen: view, edit, delete a single entry
- [ ] Error/empty states: no entries yet, model failed to load, recording permission denied,
      transcription returned empty text
- [ ] Navigation between list / detail / new-entry (introduces Navigation Compose — first real
      multi-screen structure)

### M2 — Privacy: lock it down

Chosen explicitly: the diary needs its own lock, and the database needs to actually be encrypted
(not deferred any further, now that a lock screen implies "there is something worth locking").

- [ ] Wire SQLCipher into `:core-data` (`DiaryDatabase` opened with a passphrase, not a bare
      `Room.databaseBuilder`) — this was deferred during scaffolding specifically until a real
      security requirement existed; it now does
- [ ] Passphrase/key storage via Android Keystore (never store the raw passphrase in
      SharedPreferences or a plain file)
- [ ] App-level lock screen using `androidx.biometric.BiometricPrompt` (biometric with PIN/device
      credential fallback), shown on cold start and after the app has been backgrounded past a
      timeout
- [ ] Lock-state as a small dedicated class (Pure Fabrication — e.g. `AppLockController`), not
      logic bolted onto `MainActivity`

### M3 — Finding entries again

- [ ] Room FTS (full-text search) virtual table over entry transcripts
- [ ] Search UI: query box over the entry list, results ranked by relevance/recency
- [ ] Calendar view: pick a date, jump to entries from that day; reuses existing
      `createdAtEpochMillis` data, no new entity needed
- [ ] Navigation entry point between list view and calendar view

### M4 — Backup without the cloud

- [ ] Encrypted export: user-triggered, writes a single encrypted archive (entries + any
      attached media) via the Storage Access Framework (user picks the destination — SD card,
      USB, a synced folder — the app never talks to a network endpoint itself)
- [ ] Import flow: pick a previously exported file, decrypt, and merge or replace — needs an
      explicit conflict story (merge by entry id/timestamp vs. full replace) decided before
      building, not during
- [ ] Export/import covered by an actual round-trip test (export, wipe app data, import, verify
      entries match) before being marked done

### M5 — Polish

- [ ] Light/dark theme (Material3 dynamic color or a defined palette)
- [ ] Accessibility pass: content descriptions on icon-only controls, TalkBack sanity check on
      the record button and entry list
- [ ] Launcher icon and app branding
- [ ] First-run onboarding: one screen explaining the mic permission ask and that everything
      stays on-device

### Later / explicitly out of scope for now

Flag before starting any of these — they were not part of the current scope decisions and may
need their own clarifying-questions pass:

- iOS port (reuse whisper.cpp core via Swift binding; UI rewritten native or via KMP-shared logic
  layer — revisit the native-vs-KMP call from the original architecture discussion at that point)
- Photo attachment to an entry (the `DiaryEntry.photoFilePath` field already exists in the schema
  as a placeholder from scaffolding, but no capture/UI/analysis was scoped in this pass — confirm
  before building anything here, since "image analysis" was explicitly cut earlier)
- Multi-language Whisper models / language selection
- Reminders/notifications to write an entry
- Home-screen widget / quick-capture from lock screen

## Non-functional requirements

- **No `INTERNET` permission in the manifest, ever.** Any dependency that silently wants it
  (an SDK, an analytics library) is disqualifying.
- **Model + app size**: keep the bundled Whisper model in the tens-to-low-hundreds-of-MB range
  (tiny/base tier); don't casually upgrade to a larger model without checking the size/latency
  tradeoff against real phone hardware during the weekly device slot.
- **Transcription should not block the UI thread** — recording and inference run off the main
  thread with a visible progress state, never a frozen button.
- **Every write to the diary database should survive a process death mid-write** (Room defaults
  handle this, but don't introduce manual file I/O for entries that bypasses it).
