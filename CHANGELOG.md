**1.5.1 — Minecraft 26.3 / Java 25**

- Add opt-in Smart CSV to global and per-task CSV settings: resume by refreshing all pages from page 1, including new events and their current page assignments.
- Validate server timestamps in descending order without merging legitimate identical events; missing or inconsistent timestamps stop with a partial result.
- Keep server_timestamp when clearing all columns. Manual deselection disables Smart CSV and displays a red tooltip warning.
- Remove completed recovery page files only after export succeeds and persisted recovery state no longer depends on them.
- Remove obsolete CSV parts after a successful shorter refresh.

**1.5.0 — Minecraft 26.3 / Java 25**

- Persist page checkpoints and frozen task queue state across disconnection and client restarts.
- Recreate the original query, discard bootstrap page 1 and resume the interrupted page in the original export folder.
- Add global recovery settings, a 4000 ms default delay, red chat reminder, crosshair countdown and per-second sound.
- Add opt-in server reconnect, a configurable 5000 ms retry delay and 3-attempt default limit.
- Add a configurable green reconnect reminder at each task start, in Spanish and English.
- Preserve custom-command progress using a durable reservation before sending; uncertain sends are not replayed.
- Extend tests for recovery, persistence, interrupted queues, countdown and reconnect scheduling.

**1.4.2 — Minecraft 26.3 / Java 25**

- Add one Select all / Clear all button to the CSV column picker, affecting every page and both global and per-task CSV settings.
- Allow an empty draft while choosing a subset; require at least one column before leaving and do not persist an empty selection.
- Cover the full, partial and empty selection transitions and persistence.

**1.4.1 — Minecraft 26.3 / Java 25**

- Optional Automatic columns (Excel) in CSV export, off by default. Adds a delimiter hint to each exported file without changing data rows or the default format.
- Supports global and per-task CSV settings, copied tasks, saved profiles and split exports.
- Adds regression coverage for encoding, unchanged default output, frozen settings, split files and task/profile persistence.

**1.4.0 — Minecraft 26.3 / Java 25**

- Task delay defaults to 3000 ms when blank or in new configurations; existing explicit delays and 750 ms command defaults are preserved.
- Duplicate tasks with independent copies of commands and per-task settings; clear the entire current queue.
- Named task profiles: save a copy of the current task list, load it or delete saved profiles. Saved profiles remain independent from active task edits and executions.

**1.3.2 — Minecraft 26.3 only**

- Show a hoverable warning triangle beside Task delay when the effective value is below 3,000 ms, including the blank-field default of 750 ms. Short delays remain valid.

**1.3.1 — Minecraft 26.3 only**

- Accept single-page lookups without pagination footers by default after the settle period, save them and continue the task queue. The option is available in global and per-task capture settings; disabling it retains the previous behavior. Export confirmation flags remain accurate.
- Each task now supports a paginated, unlimited-length command list with individual pre-command delays, add/remove and arrow ordering. Legacy single commands migrate automatically.
- Regression coverage for single-page acceptance, exports, inheritance, migration, ordered command delays and cancellation.

**1.3.0 — Minecraft 26.3 only**

- Named export folders with collision-safe numeric suffixes; optional CSV name auto-clear (off by default).
- Persistent sequential task queue with add/delete, arrow ordering, per-task capture and CSV overrides, optional custom commands and configurable delays (750 ms by default).
- Separate queue start/end sounds; per-task sounds default to off.
- Queue progression waits for export completion and stops on unsuccessful outcomes, cancellation, disconnection and storage failures.
- Frozen capture settings, protected in-flight exports and last-export recovery by modification time.
- Automated coverage for queue ordering, delays, failure paths, configuration inheritance, persistence, naming and reset behavior. Live Minecraft/server testing remains pending.

**1.2.1 — Minecraft 26.3**

- Custom CSV names with optional extension and Windows filename validation. Split files use `name_1.csv`, `name_2.csv`, etc.; summaries list the actual files.
- Folder buttons use native SDL folder opening, with a direct Windows Explorer fallback instead of AWT Desktop. Last export recovers older sessions when no saved CSV path is available.
- Sound selection now reads all loaded Minecraft sound events, including music and discs, with search by ID/subtitle and paginated results.
- Preview, stop and independent mute controls; previews stop when leaving the sound menu.
- Refreshed menu colors, focus states and progress panel. Settings are divided into Capture, CSV and Sounds tabs; lists adapt to window height.

**1.2.0 — Minecraft 26.3**

- Updated the Fabric client to Minecraft 26.3, including SDL-era key constants and opening export folders through the desktop API.
- Added selectable CSV columns while preserving the existing English column names and cell formatting.
- Added optional CSV splitting by a user-selected number of complete pages.
- Added selectable vanilla Minecraft start and completion sounds, with independent mute options.

**1.1.0 — Minecraft 26.2**

- Persistent Spanish / English selector in F8 → Settings → Language, independent of Minecraft language.
- Translated menus, notices and report labels. A capture keeps the report language chosen at its start.
- CSV schema 2: 24 stable English columns. Original server verbs, messages, commands, sign text and hover text are not translated.
- Export classification for blocks, sessions, chat, commands, clicks, containers, items, inventory, entity kills, signs and usernames.
- Explicit quantities, world and numeric coordinates, content, recorded usernames and parsing status.
- Multiline records are captured even with tooltip storage disabled. Ambiguous chat/command formats and unknown actions remain visible.
- English instructions, CSV schema reference and synthetic examples generated with the production exporter.
- 72 passing automated tests. Actual game UI and live server verification remain pending.

**1.0.0**

- Initial client-only pagination, transcript, summary, basic CSV, query history and menu.
