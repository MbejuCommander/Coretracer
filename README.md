**CoreTrace 1.7.14 — Minecraft Java 26.3 · Fabric · Client only**

### Edit pending tasks while paused; local error reports (1.7.14)

Pause Task Queue to add, copy, edit, remove or reorder **pending tasks** in the queue menu. The current task stays locked because its pages or custom commands may already have been saved. Pending edits affect the current run only; they are checkpointed before taking effect and survive a client restart. If saving the checkpoint fails, the previous queue remains active. Removing the final pending task while the queue is between tasks completes it safely. The queue's Excel workbook and already generated files are preserved.

On a terminal capture, recovery, storage or queue failure, CoreTrace writes an `error-report-...txt` file alongside the capture export, in the Excel workbook folder, or in `coretrace/exports` for a queue-level failure. Open the latest report with **View records → Open latest error report**. It contains the mod/Java versions, internal error code, page and retry counts, Smart Delay state and recent internal events. It excludes commands, server addresses, chat messages, captured records and exception messages. A spam kick or a transient failure that recovers does not produce a report. Reports stay local and are never sent automatically.

### Reliable Smart CSV resume (1.7.13)

When checking saved pages after a disconnect, CoreTrace matches the CoreProtect UTC timestamp, actor, action, content and parsed coordinates across several consecutive records. The caret's leading spaces are ignored because CoreProtect changes their alignment as the displayed relative time grows (for example, from `9.98/h ago` to `10.04/h ago`). A changed timestamp or coordinate still prevents a match. If no reliable sequence is found, the capture stops and retains its recovery data. This fix applies to simple captures and Task Queue, and covers the reported 213-page session where a one-space alignment change stopped recovery.

### Configurable post-kick stability (1.7.12)

In **Settings → Capture → Smart Delay → Post-kick stability**, set the confirmed-page activity required before Smart Delay accelerates again after successive spam kicks. The four defaults are **30, 60, 90 and 120 seconds**. Each field accepts 1–120 seconds; the values must not decrease from one kick to the next. Leaving a field blank uses its default, and the submenu has a button to restore all four defaults. These settings also apply to Task Queue tasks, using each task's effective settings. Pauses, disconnections and long idle gaps do not consume the stability period. Existing longer holds saved by an older release are capped at the configured period for their kick count when loaded. Changes to settings apply from the next capture.

### Per-page Smart Delay variation (1.7.11)

Smart Delay now varies CoreProtect page waits in a repeating three-step pattern around its learned baseline: **0%, +5%, +10%**. At a baseline of 750 ms, the planned waits are **750, 788, 825 ms** (rounded to the nearest millisecond). The minimum setting and 30000 ms cap still apply. A kick for spam raises the learned baseline to **1.5 times the selected wait used for the last page**; for example, a kick after the 825 ms step raises it to about 1238 ms. This change does not affect manual delay, custom-command delays or Task Queue delay. The three-step position and recent waits are saved with the learning profile, including across resume.

The submenu groups **Min**, **Average** and **Max** under **Last 3 commands**. They are descriptive statistics of the three most recent waits actually selected for page requests. **Next** shows the wait planned for the next page; it may be above the learned baseline because of the variation. The average does not drive the adaptation algorithm. The 5-second observation window still governs when the baseline can change.

### Smart Delay and ETA (1.7.10)

In **Settings → Capture**, the switch beside the delay enables **Smart Delay**. It is **off by default**: the manual field keeps its value and works as before. Turning it on makes the manual field read-only and displays a **Smart Delay** button underneath. It applies to CoreProtect pages in simple captures and Task Queue, using each task’s effective settings. It does not change delays between tasks or custom commands.

The submenu offers:

- **Spam kick message**: `Kicked for spamming` by default. Matches that text within the actual disconnect reason, ignoring case. Blank restores the default; unrelated disconnects do not penalize pacing.
- **Minimum (ms)**: **240** by default or when blank; range **240–30000 ms**. This is the permitted lower bound, not an instruction to start at that speed. New learning starts at the manual delay, or this minimum if higher. Changes apply from the next capture. Existing saved minimum values are preserved; clear the field to use 240 ms.
- **Observation window (s)**: **5 seconds** by default or when blank; range **1–300 seconds**. This is not the delay between commands. Short windows provide less evidence. There is no fixed quota of 30 commands.
- **Post-kick stability**: four editable active-time periods, **30/60/90/120 seconds** by default, with a maximum of two minutes.
- **Separate learning per server**: **off by default**. Off uses one saved global profile shared across servers, tasks and sessions. On keeps independent profiles by server address; switching does not erase previous profiles. Two addresses are not automatically merged, even if Recovery allows record verification from another address.
- **Reset this learning profile** deletes only the selected profile and allows fresh learning.

The four read-only indicators show the **minimum, average and maximum of the last three delays selected for CoreProtect page requests**, plus the **next planned delay**. Before three requests, the available samples are shown; before the first, there is no data. These values are command waits, not response times or guaranteed server limits. For 550, 440 and 240 ms, they show 240, 410 and 550 ms. The last detected spam kick is also shown.

After six stable observation windows, Smart Delay reduces pacing by 10%. Once it is at or below 150% of a previously problematic delay, it uses 2% reductions (rounded to the nearest millisecond, at least 1 ms); a fast step cannot skip this boundary. The configured minimum is always respected. A matching spam kick raises the learned baseline to 1.5 times the last page wait (up to 30000 ms), remembers the problematic value and requires the configured confirmed-activity period before accelerating again. Pauses, disconnected time and long idle gaps do not reduce the remaining hold. The hold and kick history persist across sessions. The menu shows remaining active seconds. The learned floor is no longer permanent: after stabilization, cautious probing can try lower values again. Repeated kicks can still occur while probing.

The recent-three statistics remain descriptive; adaptation uses full observation windows. The post-kick reconnect/recovery cooldown is still at least one window or two delays. Learning is saved in `coretrace/smart-delay.json`. CSV/Excel exports and recovery data retain their existing behavior.

The red **Factory reset** button in the main menu opens a confirmation dialog. Confirming restores all global preferences, including language, and clears all global/per-server Smart Delay learning. It keeps task profiles, the current task list and its per-task overrides, favorites, query history, generated files and recovery data. It is unavailable during an active capture, queue, save or recovery operation. Cancelling the dialog makes no changes.

**Show ETA and progress**, below **Accept single page without footer**, is **on by default**. It controls the whole indicator above the hotbar: `ETA 01:23:45 · 67 %`. Both time and percentage refer to the current capture/task, not all future queue tasks. A smoothed average uses real intervals between confirmed pages, including waits. It starts with **Calculating…**, shows **Paused** or **Recovering…** when appropriate and recalculates after those interruptions. Only successful completion displays 100 %. The calculation keeps a few variables, never rereads CSV files and refreshes the text at most once per second.

### Resume from another address (1.7.7)

In the main menu, **Resume** appears beside **Start capture** when a simple capture is paused or a capture/queue has pending recovery. It temporarily replaces Favorite. It continues a pause or starts another recovery cycle for the saved data.

**Settings → Recovery → Resume from another address** is enabled by default. Another address is allowed only when the saved capture has confirmed pages and Smart CSV enabled. CoreTrace reruns the query and requires matching records at the start and near the saved end before incorporating new data. CoreProtect access alone does not establish that the results belong to the same query. An unverifiable overlap retains partial results without restarting the whole capture. Without verifiable saved pages, or with Smart CSV disabled, the original address is required. A queue between tasks also lacks pending capture records to verify another address.

Disconnected capture recovery allows up to **5 automatic attempts**. On exhaustion, progress is retained and Resume lets you try again. A verified record mismatch stops automatic attempts immediately; pressing Resume permits another check. Request timeouts and late responses are respected before retrying. This limit is independent of connection retries and shifted-result retries during capture. With cross-address recovery enabled, a red notice is scheduled **2 seconds after** the resume message; it is cancelled if recovery is discarded or finishes before it appears.

New entries are staged in `.recovery-prefix-<id>-N.json` chunks. Only after both head and tail overlaps are verified is `.recovery-prefix.json` published; existing pages remain intact until then. CSV, summary and JSON output is written in batches. The page shift is also saved for another disconnection. Uncommitted chunks are cleaned after an attempt; committed recovery data is removed after successful completion or cancel-and-save. A logical CSV page may contain multiple physical pages inserted at the beginning.

### Configurable Smart CSV retries (1.7.6)

In **Settings → Recovery**, **Smart CSV retries** accepts **1–10** shifted-result retries; blank uses **5**. If the limit is reached before enough stable pages are confirmed, the capture ends as a partial result and keeps its data for review. Accidental inspection recovery has a separate limit. Notices show the selected retry limit. This also applies to Task Queue captures through their effective settings.

### Stable recovery and named folders (1.7.5)

In **Settings → Recovery**, **Stable pages** accepts 5–5000 pages; blank uses **20**. The Smart CSV retry limit is configured separately. After an interruption, it counts newly confirmed pages; reaching the chosen number without another shift resets the retry counter. For example, after a problem on page 325, reaching page 345 without another shift lets CoreTrace recover a later change without exhausting the earlier budget. Accidental inspection recovery keeps its separate five-page rule. If the result overlap cannot be verified, the capture remains partial.

In **Task Queue → Excel mode**, the typed workbook name also names a new queue folder: `commandOctober30` creates `coretrace/exports/commandOctober30/commandOctober30.xlsx`. If that folder already exists, CoreTrace reserves `commandOctober30 (2)`, `commandOctober30 (3)`, and so on while keeping the workbook filename. Blank uses `tasks.xlsx` in an automatic `queue-<date>-<id>` folder. Saved queues resume in their original folders; existing runs are neither renamed nor overwritten.

### Smart recovery without repeating hundreds of pages (1.7.4)

After reconnecting or detecting shifted results, CoreTrace keeps confirmed pages. It queries the start to include new entries, then looks for up to five matching entries, including at least three distinct ones near the last saved page. Matching uses the server timestamp and stable content because relative text such as “1 minute ago” changes over time. The overlapping area is replaced, and capture continues near the saved end rather than rereading every page between. For example, if page 322 was saved and two new records shift the results, CoreTrace looks for the join near pages 322–323 and continues there.

The initial search advances to the saved timestamp and matching record sequence without the former 12-page limit. All events sharing that instant are checked; passing into older records without the required match stops recovery. The tail search remains limited to 14 pages and uses the shift detected at the beginning. If a reliable match is not found, it retains the CSV and recovery data as a partial result and warns instead of automatically restarting the entire query. Three distinct entries are required, so very short queries can end as partial. CoreProtect provides neither unique event IDs nor a fixed server snapshot: unseen changes in middle pages and identical events within the same second cannot always be resolved. A saved page can contain more rows than a physical server page after front entries are inserted; `page_requested` in the CSV then represents a logical page in the recovered file.

### Timing limits and safe task cancellation (1.7.3)

Mandatory limits already reject incompatible timings: 750–30000 ms between pages, a 5000–180000 ms request timeout, and at least 1500 ms without a footer while remaining 1000 ms below the timeout. The menu now identifies which value must be corrected; the engine enforces the same no-footer/timeout relationship. Resuming a paused capture keeps the remaining page delay instead of starting a full new delay.

After **Cancel current task** during a capture, CoreTrace waits at least that query's request timeout before starting the next task, even when Task delay is shorter. Late CoreProtect responses extend this wait. Short task and command delays remain allowed because safety depends on the server and command; Task delay below 3000 ms keeps its warning triangle. Recovery and reconnect delays also allow zero by user choice, without mixing queries: recovery additionally waits for a quiet period.

### Smart CSV page-shift recovery (1.7.2)

If a newer record appears on a later page, Smart CSV detects that results shifted during the query. It discards the unfinished page and, after the recovery countdown (4000 ms by default) and a quiet period without CoreProtect responses, repeats the original command to find the joins described above. This also works in Task Queue, which does not advance while capture recovery is pending. Up to the configured number of retries are allowed before the configured number of stable pages is reached; then the counter resets. A changed timezone or unreadable timestamp still stops capture without a retry. It cannot guarantee a snapshot of a server that keeps adding records.

### Pause the queue or cancel only the current task (1.7.2)

In **Task Queue**, **Pause queue** becomes **Resume queue** when pressed. A response already arriving from CoreProtect may finish, but the pause prevents another page request, custom command or task from starting. Pending delays are frozen. The paused state is saved for recovery after closing the client; a pending query is not sent until **Resume queue** is pressed. Hovering each button explains its behavior.

**Cancel current task** saves an active capture as partial and skips its custom commands. Before the next task, it waits at least the greater of Task delay and that capture's request timeout; late CoreProtect responses can extend the wait. If capture is already complete but custom commands are pending, it skips them and uses the normal Task delay. Cancelled tasks remain partial and their source folders are kept even if the rest of the queue completes; Excel mode retains the partial sheet. **Stop queue** cancels the current task and does not run the remaining tasks.

### Queue Excel mode (1.7.1)

**Task Queue → Excel mode (.xlsx)** is **off by default**. Enable it before starting a queue to show **Excel workbook name**. Blank creates `tasks.xlsx` in an automatic folder; for example, `julio` or `julio.xlsx` creates `coretrace/exports/julio/julio.xlsx`. If the folder already exists, `(2)`, `(3)`, and so on are added to the folder name without changing the workbook filename. The workbook has one sheet per task. Hover over the button for an explanation. **Open last export** opens the workbook folder. During the queue, individual captures keep their CSVs, summary, JSON and transcript.

Each sheet uses the task's CSV name, or `Task N` when blank, and respects its selected columns. Sheet names are adapted to Excel (31 characters maximum, allowed characters and unique names). All CSV parts of a task are combined in its sheet with a single header. Coordinates, amounts and requested pages are numeric cells; server timestamps and other text retain their representation. The header row is frozen and has filters. Tasks exceeding 1,048,575 records continue in another sheet with headers: Excel's row limit includes the header. CSV page splitting still applies to the CSVs and does not determine workbook sheets.

The workbook is updated whenever a task is saved, including partial captures. Writing runs in the background and reads CSVs record by record without loading the entire queue into memory. Each update rebuilds the workbook and may take longer as the queue grows. The previous workbook remains available until writing finishes; its `.xlsx.tmp` file is published with atomic replacement where supported. Excel does not need to be installed.

**Recovery:** the workbook directory and name are saved with the queue's progress, independently of the current toggle value. After a disconnect or client restart, previous sheets remain and the resumed task's sheet is replaced rather than duplicated. Smart CSV and inspection recovery still operate on the capture before Excel generation. The CSVs and `queue-excel.json` are needed to rebuild the workbook while the queue can still resume. After all tasks complete successfully, the mod removes those capture folders and `queue-excel.json`: only the `.xlsx` remains in that run directory. Cleanup only affects this queue's task folders; it does not delete older unrelated exports. If a file is locked or an unrelated file appears in those folders, the mod warns and keeps the remaining files.

**Cancel and save** writes previous tasks and the current task's partial results into the workbook before discarding recovery. In that case CSVs, JSON, summaries and transcripts remain; queue cleanup runs only after all tasks complete successfully. It does not create sheets for tasks that never ran. If CSV or Excel writing fails, the queue does not advance or run subsequent commands, and recovery plus the last valid workbook are retained. If Excel keeps the file open and blocks replacement, close it before retrying. A cell longer than 32,767 characters stops Excel export to avoid truncation; the CSVs and recovery remain available.

This is a queue-wide setting: profiles still store task lists and do not change this toggle. When disabled, exports behave as before. The mod still requires Minecraft 26.3 and Java 25.

### Cancel and save: recovery cleanup (1.6.1)

**Cancel and save** stops capture and saves a partial result: CSV, summary, session JSON and transcript. The `.recovery-page-N.json` files and `coretrace/recovery.json` pointer are removed **after** the partial export completes. If saving fails, they remain available for recovery of confirmed pages. Pressing cancel during an ongoing save waits until that operation finishes before discarding recovery. This also applies to the active queue task; its later custom commands are not run.

### Progressive export and large captures (1.6.0)

CSVs are written page by page. With a split of 500, the first part appears when page 500 closes, the second at page 1000, and so on. Each part repeats the header and Excel options. The open part uses `.csv.tmp`; closing it publishes `.csv` with an atomic replacement where supported. Finishing also publishes the last incomplete part. If there is only one part, the final file uses the name without a suffix. Without splitting, the temporary file grows during capture and is published at the end.

The global **100,000-message limit** has been removed. The configurable maximum is now **9,999,999 pages**, retaining a default of 500. Entering more than 5000 shows a red triangle recommending parts of **500 pages or fewer**; this is advice, not a restriction. The protection against an abnormal response exceeding 512 messages on one page remains.

Only the latest **16 pages** stay in memory for the results preview; search and copy in that screen apply to that recent preview. CSV and JSON retain the complete capture. Requests wait when disk writes fall behind. Final JSON and summary are generated by reading pages from disk; summary totals remain exact, but additional categories are grouped after 4096 distinct categories per group to bound memory.

Recovery still checkpoints **each page**, even before a CSV part closes. Do not delete recovery files during a capture: selected CSVs can omit columns and are not standalone recovery checkpoints. Normal recovery validates saved pages and rebuilds parts from disk without loading the whole session into memory or replaying interrupted-page rows twice. Preparation can take time on large captures. Automatic cleanup runs only after completing the outputs and leaving the recovery state. A Smart CSV refresh may leave older parts until successful completion; the final summary identifies the definitive output.

### Accidental inspection recovery (1.6.0)

**Settings → Recovery → Recover inspection** defaults to on. Unexpected headers, pagination changes and CoreProtect responses following a block interaction during capture are treated as suspicious. Chat does not uniquely identify each query, so detection is conservative: an ordinary interaction followed by a response may also trigger recovery. Neither timestamps alone nor five matching entries are treated as unique identifiers.

The mod discards the current page, retains complete pages and posts a red notice. It uses the editable **4000 ms** resume delay; blank restores 4000, and a lower value shows a warning. It also waits the configured no-data settle period after the last CoreProtect response. Zero is displayed for at least 250 ms before query reconstruction. If responses continue throughout the request-timeout period after zero, recovery stops with a partial result.

`entity.villager.no` plays at interruption, `entity.experience_orb.pickup` accompanies the crosshair countdown, and `entity.villager.yes` plays when reconstruction starts. Reconnection recovery also uses experience sounds and the villager sound at countdown completion. The default sequence displays 4, 3, 2, 1, 0; severe client lag can skip visual steps.

With Smart CSV off, the original command is repeated, bootstrap page 1 is ignored and the first pending page is requested: incomplete page 185 is read again; a fully saved page 185 continues at 186. A changed total during reconstruction stops recovery before mixing queries. With Smart CSV on, the start and last saved overlap are compared. Normal inspection recovery permits **3 consecutive attempts**, reset after 5 stable pages. The queue stays on its current task and runs no subsequent custom commands until export succeeds. Cancelling or disconnecting stops the countdown. Per-task settings can override Recover inspection and its delay.

### Smart CSV and recovery cleanup (1.6.0)

**Settings → CSV export → Smart CSV** defaults to on for new configurations. Existing saved preferences are respected. It is also available in each task's CSV settings and is preserved by copies and profiles. While enabled, `server_timestamp` is required and cannot be deselected; turn off Smart CSV first to remove it. **Clear all** keeps that column. Enabling the mode adds it automatically. Disabling it permits page-number recovery, with a risk of missing or duplicating records if results changed.

When resuming a capture in this mode, CoreTrace reads from page 1 until it recognizes saved entries, retains the middle pages, and locates the old end near its former position. New entries at the front are included without rereading hundreds of pages. Old entries that have fallen outside the `t:7d` range may remain in saved pages: recovery preserves a historical file rather than reconstructing an exact snapshot of the current range. Already completed queue tasks are not repeated.

The mod automatically captures the tooltip needed for `server_timestamp` and checks for valid timestamps from newest to oldest, including across page boundaries. Equal timestamps are allowed and identical events remain separate records. A missing valid timestamp or changed timezone stops capture with a partial result; newer records on a later page trigger the overlap search described above. Entries remain in order without artificially sorting the CSV. Page numbers in a joined file may be logical and differ from current server pagination.

The option is frozen when starting a capture or queue, like other CSV settings. Changing it does not alter an existing pending run's saved settings. With the option off, normal recovery resumes from the interrupted page.

After all final export files are saved successfully and recovery state advances, `.recovery-page-N.json` files and their temporary files are deleted from that folder. They are retained if still needed for pending recovery or following interruption or export failure. When a refresh produces fewer CSV parts, obsolete parts of the same export name are also removed. The transcript, summary and session JSON are retained.

### Recovery and automatic reconnect (1.5.0)

Under **Settings → Recovery**, automatic resume defaults to **on**, with a **4000 ms** delay (also used for a blank field). Any nonnegative signed 64-bit integer is accepted. After returning to the same server, a red chat reminder explains how to disable it, and a countdown near the crosshair sounds once per second.

Progress is saved per page in `coretrace/exports/<folder>/.recovery-page-N.json`; `coretrace/recovery.json` identifies the pending run. Keep both to recover after closing the client. With Smart CSV off, if interrupted on page 185, the mod repeats the original query, discards its initial page 1 response, waits for its confirmed footer and requests `/co l 185`. Earlier pages are retained and the interrupted page is read again, without adding replay duplicates to the CSV. Recovery reuses the original folder and export options. The text transcript retains earlier lines, including partial ones, and appends a resume marker.

The queue is also restored: current task, remaining queries, custom-command position and remaining inter-command or inter-task delay. Run settings stay frozen. Commands are reserved on disk before dispatch to avoid repeating uncertain sends: an abrupt exit between reservation and dispatch can skip that command. There is no server execution acknowledgment. **Cancel and save** removes the recovery reference and page checkpoints after exporting successfully, without deleting partial outputs. **Discard saved recovery** removes the recovery reference without deleting exported files.

Under **Settings → Recovery → Auto reconnect**, reconnect is **off by default**. The initial retry delay is **5000 ms**, with a maximum of **3 attempts**; blank fields use those defaults. Delays accept nonnegative signed 64-bit integers and attempt limits accept 1–2147483647. The green task-start reminder defaults to on and can be disabled here. Retries run from the disconnection screen; returning to the server menu or cancelling the connection stops them. Mod settings pause retries. Voluntarily leaving to the menu does not reconnect, and restarting the game does not initiate a connection automatically. Once connected again, the separate resume delay applies. Realms is excluded from automatic reconnect.

Recovery requires an original filtered query, not an isolated numeric page command. An abrupt exit restores the last checkpoint written to disk; pending lines may need to be captured again. The query is executed again: relative filters such as `t:7d` and server changes can alter results. In normal recovery, a missing initial footer or changed page count stops recovery. An unchanged count does not guarantee identical events; CoreProtect does not provide an immutable snapshot here.

### Select or clear all CSV columns (1.4.2)

Under **Settings → CSV export → CSV columns**, one button beside the top hint switches between **Select all** and **Clear all**. It acts across all pages, keeping `server_timestamp` when clearing, and also works in a task’s own CSV settings. After clearing, choose the columns you need; at least one must be selected before returning to the menu. An empty selection is not saved.

### Automatic columns in Excel (1.4.1)

Under **Settings → CSV export → Automatic columns (Excel)**, enable automatic column separation when opening the CSV in Excel. It is **off by default**: disabling it preserves the previous format exactly. The option is also available in each task's CSV settings; tasks without their own CSV settings inherit the global option. Task copies and profiles preserve their own CSV settings.

When enabled, it adds `sep=,` after the UTF-8 BOM and before the column header in every file, including split parts. Rows, selected columns, accents and quoting are preserved. It does not create styled tables or filters; those require a format such as XLSX. CSV readers that do not recognize the hint may display it as an extra row; leave the option off for those readers. The additional cost is 7 bytes per file, with no extra per-event work; no noticeable export slowdown is expected. Opening the result in Excel has not been tested in this environment.

### Task copies and profiles (1.4.1)

**Copy** duplicates a task immediately after the original and opens it for editing. It includes the query, CSV name, command list with delays and task-specific settings, with independent copies of all editable values. **Clear tasks** removes the current list while keeping saved profiles.

Under **Task queue → Task profiles**, enter a unique name and click **Save copy** to store all current tasks in their current order. Select a profile to replace the current task list; **×** deletes a saved profile. Profiles persist across restarts. Editing or executing a loaded task list never changes its saved profile. Global settings, queue sounds and Task delay remain current; profiles store tasks and their individual options.

Task delay defaults to **3000 ms** for new configurations and blank fields. Existing saved delays are preserved. Individual command delays still default to **750 ms**.

In **Task queue**, a warning triangle appears beside Task delay when the value is below 3,000 ms. Hovering it says, “A delay below 3,000 ms may cause problems in some cases.” Any valid delay can still be saved and used. A blank field means 3,000 ms and does not show the warning. Lower values are still allowed.

### Single-page results and multiple commands (1.4.1)

**Settings → Capture → Accept single page without footer** defaults to on. When CoreProtect omits the first page's pagination footer, the mod waits for the settle period without data, saves and continues the queue. The export reports “Single page saved”; `page_confirmed` stays false because no server footer was received. This does not accept missing later pages as complete. Turning it off preserves the inferred/unconfirmed outcome and stops the queue after saving. Tasks inherit this option or override it through their own capture settings.

Under **Task queue → Edit task → Custom commands**, use **Add**, **×** to remove and **↑ / ↓** to reorder. There is no fixed command-count limit; the editor is paginated. Each command has its own delay in ms (blank: 750), measured after export or the preceding command. After the final command, the Task delay runs before the next task. Empty lists skip custom commands. Older single-command settings automatically migrate with their delay.


### Task queue (1.4.1)

Open **F8 → Task queue**. Use **Add**, **×** to delete and **↑ / ↓** to reorder. Tasks persist across restarts; only an interrupted run can resume automatically on connection.

Each task has a full CoreProtect lookup command, CSV name, optional CSV overrides and an optional list of custom commands. **Universal settings** defaults to on; turn it off to edit capture/audio settings independently. Per-task sounds default to off. Configure whole-queue sounds under **Queue sounds**.

Opening **CSV settings** without saving or changing options keeps global inheritance. **Use global CSV settings** removes overrides. The task's CSV name always takes precedence, including a blank name.

Execution order: lookup → capture and export → each custom command after its own delay → Task delay → next lookup. Both delays accept 0–3600000 ms. When blank, **Task delay defaults to 3000 ms**, while **each command delay defaults to 750 ms**. An empty command list skips custom commands. No extra Task delay follows the last task. Options and timing are frozen when the queue starts.

Example: `/co l a:command t:3d`, CSV `Simply1`, universal settings on, custom command `/simply 2`. After saving, the command delay elapses, `/simply 2` is sent, then the Task delay elapses before the next lookup. Increase the Task delay for slow world changes: the mod waits for the configured interval, not a server teleport acknowledgment.

Failed lookups, timeouts, page limits, cancellation, storage errors and disconnection stop the queue. Saved data is retained; after disconnection, recovery can continue pending tasks. Custom commands use the player's normal server permissions.

### Export folders and automatic name clearing

A CSV named `july.csv` is saved under `coretrace/exports/july/`. If that folder already exists, CoreTrace creates `july (2)`, `july (3)`, and so on to preserve earlier exports. An empty name keeps the legacy timestamp/identifier folder format.

**Settings → CSV export → Auto-clear** defaults to off. When enabled, it clears the name after a successful save, including a task name when enabled in its CSV settings. A different name entered during capture is preserved.


CoreTrace accompanies CoreProtect Community Edition. It detects lookups you send in chat, captures their results, requests following pages, and saves a transcript and summary on your computer. This independent assistant neither includes nor replaces the CoreProtect plugin.

Install:

1. Use a Minecraft Java **26.3** instance with **Fabric Loader 0.19.5 or later**. The game uses Java 25; the official launcher normally manages its runtime. Select Java 25 in other launchers.
2. Install [Fabric API for 26.3](https://modrinth.com/mod/fabric-api/versions?g=26.3), version **0.161.0+26.3** or a later compatible version.
3. Put `coretrace-1.7.11+26.3.jar` in the instance's `mods` folder. Remove the old CoreTrace JAR if upgrading. Your preferences and query history are retained.
4. Optionally install a [Mod Menu version compatible with 26.3](https://modrinth.com/mod/modmenu). F8 and `/coretrace` also open the menu without it.

To select English, open **F8 → Settings → Language** and click the language button until it shows **Language: English**. Use **Settings → Language** to switch back to Español. The choice persists independently of Minecraft's language. Menus and new notifications change immediately; the report language is fixed when each capture starts. Existing chat messages and previously saved files are not rewritten.

**CSV columns always stay in English. Server records are never translated.** With an English CoreProtect server, actions such as `broke`, `logged in` and `removed` remain English even with the Spanish menu selected. Chat, commands, signs, object names and hover text retain their original content. A server using another language retains that original language as well.

Run a lookup normally:

```text
/co l a:-block u:jose t:3d
```

Automatic capture is enabled by default. CoreTrace waits for each page footer, then requests `/co l 2`, `/co l 3`, etc., at a default delay of 1500 ms. It preserves the server's page size. Use F8 to pause, resume, cancel and save, inspect or copy filtered results, manage favorites, change settings, or open the exports folder.

All public CE lookup categories are handled: **block, session, chat, command, click, container, item, inventory, kill, sign and username**. Use the same lookup syntax, for example `/co l a:chat u:jose t:3d` or `/co l a:container u:jose t:3d`. Each query is captured separately; CoreTrace does not automatically run every category. See [CSV-SCHEMA.md](CSV-SCHEMA.md) for the 24-column schema, classifications and unavailable fields.

Each export is stored in its own folder under:

```text
.minecraft/coretrace/exports/
```

| English report | Spanish report | Contents |
| --- | --- | --- |
| `transcript.log` | `transcript.log` | Captured messages, receive timestamps, hover text and strikethrough indicators. Written during capture. |
| `summary.txt` | `resumen.txt` | Status, scope and record counts grouped by actor, event type, action and object. |
| `records.csv` | `registros.csv` | User-selected English columns and original server records. UTF-8 with BOM, comma delimiter. |
| `session.json` | `sesion.json` | Structured pages, pending messages, notices and final status. |

The menu includes a command/filter box, capture progress, pause/resume/cancel, a searchable results viewer with filtered-copy support, up to 20 recent queries and 20 favorites, access to the latest export and exports folder, Spanish/English language selection, and settings for capture timing, page limits, hover text, captured-chat visibility, CSV columns/splitting and vanilla sounds.

CoreTrace supports these lookup categories, not just blocks:

| Type | Example filter | Specific data |
| --- | --- | --- |
| Blocks | `a:block`, `a:-block`, `a:+block` | Action and block. |
| Sessions | `a:session` | Player logins and logouts. |
| Chat | `a:chat` | Message content. |
| Commands | `a:command` | Recorded command. |
| Interactions | `a:click` | Clicked object. |
| Containers | `a:container` | Action, amount and item. |
| Items | `a:item` | Pickup, drop, deposit, withdrawal, throw or shoot. |
| Inventory | `a:inventory` | Movement, amount and item shown by CoreProtect. |
| Entity kills | `a:kill` | Recorded entity. |
| Signs | `a:sign` | Text, including line breaks. |
| Usernames | `a:username` | Displayed and login-registered name. |

For example, `/co l a:chat u:jose t:3d` and `/co l a:container u:jose t:3d` are paged and exported the same way. Run each query separately: the filters CoreProtect allows you to combine depend on the plugin. Selecting one category does not make the mod query all categories.

The CSV schema has **24 columns**: requested and confirmed page, action filter, event type, exact date, relative time, user, original action, action ID, sign, amount, item, world, X/Y/Z, content, registered name, strikethrough, parse status, raw text, coordinate line and hover text. See [CSV-SCHEMA.md](CSV-SCHEMA.md) for the full definition.

The summary counts **event records**, not item quantities or net damage. Repeated records are retained. Strikethrough is preserved as metadata and does not automatically subtract a record. Exact server timestamps require the timestamp tooltip to be present and tooltip capture to be enabled. Missing values remain empty.

`action` stores the original verb; `action_id` and `event_type` are stable English identifiers. Coordinates, amounts and dates absent from the server response remain empty. CoreProtect CE uses the same message format for chat and commands, so the query's `a:` filter distinguishes them. An ambiguous or partial lookup is marked `message` and `parse_status=ambiguous`. Unknown actions keep their text and are marked `unrecognized`. Custom message formats may require parser changes.

CoreProtect CE can report some inventory movements, including internal crafting or trading, only as `added` or `removed`. The client preserves that information and does not invent a subtype that the response does not provide. Summary counters count rows; they do not add the `amount` values.

When opening a CSV in Excel or LibreOffice, import it as **UTF-8, comma-delimited** if your regional settings do not recognize commas automatically. Commas, quotes and line breaks are escaped using CSV rules. Text that could be interpreted as a spreadsheet formula receives a protective apostrophe in the CSV; transcripts and JSON preserve the captured text without it. Minecraft formatting codes are removed when components are converted to plain text.

Hover text can preserve the exact server timestamp CoreProtect supplies for a relative time, when tooltip capture is enabled. Duplicate events remain separate records. The strikethrough flag preserves that visual signal from the plugin results.

Choose CSV columns, split large exports every N pages (0 disables splitting), and select vanilla Minecraft start and finish sounds in Settings. Each numbered CSV repeats the original header.

Under **Settings → CSV export → File name**, enter `lookup` or `lookup.csv`. The result is `lookup.csv`; if split into four parts, the files are `lookup_1.csv` through `lookup_4.csv`. Leave it empty for the default name. Save settings before starting a capture; the name and columns are fixed for that session.

**Settings → Sounds** lists all loaded Minecraft sound events, including music and discs. Search by ID (`allay`, `music`, `note block`) or by subtitle in the game's language. Preview and stop controls let you audition sounds; leaving the menu stops its preview. Resource packs may replace the audio, and game volume settings still apply. **Last export** opens the most recent session folder containing the CSV or its parts, or the `.xlsx` workbook folder when Excel mode was used; **Export folder** opens the exports root using native folder opening.

Local commands:

| Command | Effect |
| --- | --- |
| `/coretrace` | Open the menu. |
| `/coretrace start a:chat u:jose t:3d` | Start a query using filters. A full `/co l ...` command also works. |
| `/coretrace pause` | Pause new page requests while retaining an outstanding response. |
| `/coretrace resume` | Continue a paused capture or retry pending recovery. |
| `/coretrace cancel` | Save received data and stop. |
| `/coretrace status` | Display status. |
| `/coretrace folder` | Open the exports folder. |

An explicit numeric lookup such as `/coretrace start /co l 3` can start at an existing page; the report marks earlier pages as missing. Automatic chat capture begins with a new filtered query, not an isolated page command. The parser accepts `co`, `core`, `coreprotect` and the `coreprotect:` namespace. New lookups cannot use `p:`, `page:` or `#count`; start from the first page with a normal query.

Capture outcomes:

- **Last page confirmed:** sequential pages were received through the announced total.
- **Single page saved:** CoreProtect commonly omits pagination for a one-page lookup. After 3500 ms with no new data, the result is saved and the queue continues by default. Disable **Accept single page without footer** to retain the inferred/unconfirmed outcome and stop the queue after saving. In either case, no server confirmation is claimed. Increase the no-footer wait if responses are slow.
- **No results:** explicitly reported by the server.
- **Partial:** cancellation, disconnection, page limit, unexpected response, server error or timeout.

If the game closes abruptly, `transcript.log` may retain messages written so far. Without a `FINAL:` line, treat it as interrupted. Summary files are created after normal completion or when the running client saves a cancellation/disconnection.

Settings are saved in `config/coretrace.json`. Initial values and ranges are:

| Setting | Default | Range |
| --- | --- | --- |
| Language | Spanish | Spanish / English |
| Automatic capture | On | On / off |
| Delay between pages | 1500 ms | 750–30000 ms |
| Request timeout | 30000 ms | 5000–180000 ms |
| No-footer settle time | 3500 ms | 1500 ms to timeout minus 1000 ms |
| Maximum pages per capture | 500 | 1–9999999 |
| Capture hover text | On | On / off |
| Hide captured chat messages | Off | On / off |
| Accept first page without footer | On | On / off |

Capture timing and maximum page settings are fixed when a capture starts. Changes apply to the next capture. Increase the page delay if the server limits command frequency. The exporter retains a 512-message per-page guard; exceeding it saves a partial capture. The global 100,000-message limit is removed.

Manual CoreProtect commands are blocked during capture to avoid replacing its cached query. Avoid using the inspector at the same time. Pausing does not release the query; cancel first if you need a different command. After an uncertain result, CoreTrace waits for late responses before another capture. Transient recovery failures allow up to five attempts; a timeout during normal capture retains a partial result. Records are verified before resuming across addresses when enabled. Query history lets you start a capture again.

This client reads received system messages. It does not access the database or add permissions. Automatic capture sends lookup commands; the queue can also send the custom command explicitly configured by the user. Exports remain local. It cannot guarantee a transactionally consistent database snapshot while the server's data changes. Custom messages, translations or incompatible plugin versions may require parser changes. The per-page guard remains at 512 messages; there is no global captured-message limit.

**Server compatibility:** CoreTrace targets the **26.3 client** and requires CoreProtect to already work on the server you join. The mod cannot make an incompatible plugin installation work. Check [official CoreProtect releases](https://www.spigotmc.org/resources/coreprotect-community-edition.8631/) and [permissions](https://docs.coreprotect.net/permissions/).

The parser follows the public **CoreProtect CE v24.0** format for headers, page links, results and coordinates. Messages changed by other plugins, custom translations and other plugin versions are not guaranteed to work. Page footers are not database snapshots: if server data or cache changes during a lookup, the client cannot guarantee a transactionally consistent set of events. It attempts inspection recovery for a changed total or unexpected page; if disabled or unsuccessful, it stops with a partial result. In a pending CSV page, `page_requested` records the requested page and `page_confirmed=false` means its correspondence could not be confirmed.

Technical references:

- [Fabric for Minecraft 26.3](https://fabricmc.net/2026/09/15/263.html).
- [Official Fabric example mod](https://github.com/FabricMC/fabric-example-mod).
- [CoreProtect commands](https://docs.coreprotect.net/commands/).
- [CoreProtect CE v24.0 public pagination](https://github.com/PlayPro/CoreProtect/blob/v24.0/src/main/java/net/coreprotect/utility/ChatUtils.java).
- [CoreProtect CE v24.0 lookup output format](https://github.com/PlayPro/CoreProtect/blob/v24.0/src/main/java/net/coreprotect/command/lookup/StandardLookupThread.java).
- [Mod Menu source](https://github.com/TerraformersMC/ModMenu).

The project includes MIT-licensed source, these instructions and synthetic example exports, including `ejemplo-simulado/all-event-types.csv`. The combined example uses multiple simulated queries; it does not represent one server query. Minecraft, Fabric API, Mod Menu and the CoreProtect plugin are not included.

Build with **JDK 25** and the included wrapper: `./gradlew build` on Linux/macOS or `gradlew.bat build` on Windows. The standard configuration uses Loom 1.17.20 and Gradle 9.5.1. Alternatively run `python build-portable.py` using Python 3.10+ and JDK 25+ (`JAVA_HOME`). This alternative verifies published dependency hashes and compiles against the actual unobfuscated Minecraft 26.3 classes, Fabric and optional Mod Menu APIs. It also runs the tests. Install the ordinary JAR from `build/libs`, not a sources JAR.

For the tested development client, use `gradlew.bat runClient -PwithoutModMenu`. This omits the optional runtime Mod Menu dependency: version 20.0.1, used to compile its API integration, does not support Minecraft 26.3 at runtime. F8 works without it.

Version 1.7.14 built successfully with JDK 25.0.4 and Gradle (`gradlew.bat test build --offline`) and passed **231 automated tests**, with no failures or errors. All JAR classes were verified as Java 25 bytecode (major version 69). In version 1.7.1 a generated workbook was opened in Microsoft Excel 16.0 to verify sheets, numeric coordinates, rows, quotes, line breaks and preservation of text that could resemble formulas. Excel tests also cover the custom filename across recovery, safe removal of task folders after the last task and command, cancellation with partial results, write failures, backup retention, different columns and sheet splitting. In 1.7.8, Capture and Smart Delay were visually checked in a Minecraft 26.3 development client at widths of 640, 854 and 1024 pixels, with no overlapping controls. Smart Delay and ETA toggles were tested by clicking. The main and Recovery menus had been verified in the previous release. ETA calculations and kick detection were tested automatically; the HUD during a capture and kicks against a live server still need verification. **Recovery against a live CoreProtect server remains untested end to end.** Coverage still includes a simulated 5001-page capture with 105021 records and 115023 messages, export before completion, crash recovery and inspection recovery with the queue held on its current task.

For your first server check, use a small known lookup and compare its last page and transcript with the chat output. The Spanish [README](README_es.md) contains the equivalent instructions in Spanish.

The working project lives directly in this directory. `src/` contains sources and tests; `gradle/` and wrapper scripts support builds. JARs are generated under `build/libs/`. Caches, build outputs and release archives are not source files. The first build after cache cleanup may need to download dependencies.

In 1.7.9, the four statistics fields were checked geometrically at the narrow menu width (296 GUI pixels). The row was subsequently checked visually in 1.7.10 in a Minecraft development client. The factory reset button and confirmation were checked at 640 and 1024 pixels wide, including clicks to cancel and confirm in the development instance.
