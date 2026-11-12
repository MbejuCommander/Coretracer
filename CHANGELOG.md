## 1.7.14 — Paused queue edits and error reports / Edición de cola pausada e informes de error

- Allow adding, copying, editing, removing and reordering only pending tasks while Task Queue is paused. Save each change to the recovery checkpoint before applying it; leave the current task untouched. Removing the final pending task after the current task has finished completes the queue safely.
- Generate a local, shareable error report for terminal capture, recovery, storage and queue failures. Include internal reason codes, page and retry counts and a bounded event timeline without commands, server addresses or captured messages. Open the latest report from View records.
- Do not generate a report for a spam kick or transient recovery failure that the mod resolves automatically.

## 1.7.13 — Reliable Smart CSV resume / Reanudación fiable de CSV inteligente

- Compare parsed CoreProtect coordinates and action hints when matching saved records after reconnection; ignore visual spacing that changes as relative time grows.
- Keep UTC timestamps, action details and consecutive-record verification. Add a regression test based on the 213-page session that stopped after its coordinate caret shifted by one space.

## 1.7.12 — Configurable Smart Delay stability / Estabilidad configurable de Smart Delay

- Replace the post-spam-kick stability periods with 30, 60, 90 and 120 seconds by default; cap every configured period at two minutes.
- Add four editable periods in Settings → Capture → Smart Delay → Post-kick stability, including per-task settings and default restoration.
- Clamp previously saved longer holds to the configured period for their kick count when loading learning profiles.

## 1.7.11 — Per-page Smart Delay variation / Variación por página de Smart Delay

- Vary CoreProtect page waits by 0%, +5% and +10% around the learned baseline, with the configured minimum and 30000 ms cap.
- Increase the baseline by 1.5 times the last selected page wait after a spam kick; keep the existing stabilization periods.
- Group recent minimum, average and maximum under Last 3 commands; show the next planned wait separately. Persist the variation position with learning profiles.

## 1.7.10 — Adaptive probing and factory reset / Aprendizaje adaptable y restablecimiento

- Reduce delay by 10% after six stable windows, switching to 2% near a previously problematic delay.
- Replace the permanent learned floor with a persisted hold requiring 2/4/8/16 minutes of confirmed activity after spam kicks. Preserve the configured minimum and exclude idle time.
- Add a red main-menu factory reset button with confirmation and an active-operation guard. Restore global preferences and clear Smart Delay learning while preserving task profiles, current tasks, favorites, history, exports and recovery data.

## 1.7.9 — Recent Smart Delay statistics / Estadísticas recientes de Smart Delay

- Set the default editable minimum to 240 ms; preserve existing user settings.
- Show minimum, average and maximum of the last three CoreProtect page delays plus the next planned delay. Keep samples in the selected global/server learning profile across sessions.
- Remove the ambiguous historical minimum and safety reference from the menu.

## 1.7.8 — Smart Delay and ETA / Smart Delay y ETA

- Add an off-by-default Smart Delay switch, configurable spam kick phrase, minimum (300 ms by default) and observation window (5 seconds by default).
- Persist one global learning profile by default, with optional profiles per server address and a reset button. Adapt page pacing after stable windows and spam kicks; leave custom-command and task delays unchanged.
- Add an on-by-default HUD with hours:minutes:seconds and current-task progress, including paused/recovery states and lightweight smoothed timing.
- Add compact Capture and Smart Delay layouts for smaller windows, with bilingual labels and tooltips.

## 1.7.7 — Manual and cross-address recovery / Reanudación manual y entre direcciones

- Add Resume beside Start capture for paused or recoverable simple captures; keep normal button widths by temporarily replacing Favorite.
- Allow another server address by default when Smart CSV can validate saved records. Add the Recovery toggle and a delayed explanatory notice two seconds after the resume message.
- Limit automatic recovery to five attempts, retain partial data, and allow manual retry. A proven record mismatch stops immediately.
- Replace the 12-page prefix search limit with timestamp and event-sequence verification. Stage new front entries on disk until both overlaps are verified; export in batches and persist physical page offsets for subsequent recovery.
- Verify menu layout in a Minecraft 26.3 development client at 854- and 640-pixel widths.

## 1.7.6 — Configurable Smart CSV retries / Reintentos configurables de CSV inteligente

- Set the shifted-result retry limit in Settings → Recovery (1–10, default 5, blank 5). Existing configurations inherit 5.
- Keep the stable-page reset threshold independent (5–5000, default 20). Show the configured retry limit in recovery notices and partial-result messages.

## 1.7.5 — Stable recovery and named folders / Reanudación estable y carpetas con nombre

- Reset Smart CSV's three-shift retry counter after a configurable number of newly confirmed pages without another shift. The Recovery menu accepts 5–5000, defaulting to 20; existing configs inherit that default.
- Name new Task Queue Excel folders after the workbook when a name is supplied. Reserve numbered folders on collisions, keep the workbook filename, and preserve existing recovery paths.
- Keep the generated queue folder name when the workbook name is blank. Update bilingual tooltips and guides.

## 1.7.4 — Smart overlap recovery / Reanudación por puntos de unión

- Preserve confirmed pages after reconnecting or detecting shifted CoreProtect results. Read the query start for new entries, find up to five matching entries with at least three distinct events near the saved end, then continue without replaying the middle pages.
- Match using server timestamps and stable event content instead of relative time. Rebuild the CSV from durable pages after inserting new front entries; retain partial data if either overlap cannot be verified.
- Keep this behavior for individual captures and Task Queue, including Excel mode. Document bounded searches, logical page numbers, and the absence of a guaranteed fixed server snapshot.

## 1.7.3 — Timing safeguards / Protección de tiempos

- Quarantine responses after cancelling a live capture: wait at least its request timeout and extend the hold when late CoreProtect messages arrive before starting the next queued task.
- Preserve only the remaining page delay across a pause and enforce a 1000 ms separation between no-footer wait and request timeout in both UI and engine.
- Show specific capture timing errors instead of a generic invalid-values message. Keep deliberately permitted short queue, custom command, recovery and reconnect delays.

## 1.7.2 — Smart CSV recovery and queue controls / Recuperación CSV y controles de cola

- When a newer server timestamp appears on a later page, wait for CoreProtect responses to settle and restart the complete query from page 1 instead of immediately ending the capture.
- Allow three full-query retries per capture, including in Task Queue. Keep a partial result and recovery state if the server continues changing.
- Treat a changed timestamp timezone as a separate error; it does not trigger page-shift retries.
- Preserve the configured recovery countdown and sounds, with a specific warning for changing results.
- Add Pause/Resume queue and Cancel current task to Task Queue, with bilingual hover help. Pause survives recovery; cancelling one task saves partial results and continues without its custom commands.
- Keep source folders if any queue task was cancelled, including in Excel mode. Stop queue continues to end the entire run.

## 1.7.1 — Custom queue workbook names / Nombre del libro Excel

- Show an Excel workbook name field in Task Queue only when Excel mode is enabled; blank uses `tasks.xlsx`.
- Accept a plain name or `.xlsx` suffix and validate it as a portable filename.
- Freeze both the unique run directory and the workbook filename in queue recovery, including across client restarts and later configuration edits.
- Keep CSVs and per-page recovery until every task finishes; on successful queue completion, remove task capture folders and the workbook manifest, leaving only the XLSX in its run folder. Cancelled or failed queues retain their inputs.
- Add filename, legacy recovery, collision and cleanup tests.
- Update the Spanish and English guides. Minecraft 26.3 / Java 25 unchanged.

## 1.7.0 — Queue Excel mode / Modo Excel de la cola

- Optional queue-wide XLSX export, off by default, with a bilingual tooltip and one sheet per task.
- Streams CSV parts into numeric coordinates and literal text cells, with frozen headers and filters; handles empty tasks, duplicate names and sheet row overflow.
- Publishes an updated workbook after each saved task, preserving prior output on failure. Queue recovery keeps the same workbook and replaces resumed-task sheets.
- Cancel and save includes partial results before recovery cleanup. CSVs and workbook manifest remain available for rebuilding; tests cover restart, cancellation and write failure.
- Updated Spanish and English documentation. Minecraft 26.3 / Java 25 unchanged.

**1.6.1 — Minecraft 26.3 / Java 25**

- Keep recovery checkpoints and the pointer until an explicitly cancelled capture finishes exporting its partial result.
- Remove recovery files after a saved cancellation, including one requested while an export is in progress; keep them if exporting fails.
- Stop queue commands on cancellation and retain the partial CSV, summary, JSON and transcript.
- Add cancellation success and export-failure regression coverage.

**1.6.0 — Minecraft 26.3 / Java 25**

- Write CSV parts during capture, publish completed splits immediately and retain per-page recovery checkpoints.
- Replace full in-memory capture history with disk-backed pages and a 16-page preview; stream final JSON and summary.
- Remove the global 100,000-message protection, retain the 512-message page guard, and allow up to 9,999,999 pages with a red split recommendation above 5000.
- Recover suspected accidental block inspections after an editable 4000 ms countdown and a quiet period; discard interrupted/bootstrap pages and preserve the active queue task.
- Add angry/positive villager sounds and experience countdown sounds, a visible zero, and a three-attempt recovery limit reset after five stable pages.
- Enable Smart CSV by default for new configurations and require server_timestamp while enabled; preserve existing explicit preferences.
- Bound summary category memory, retain exact total counts and update the Spanish/English wiki.

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


