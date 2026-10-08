**CoreTrace 1.4.0 — Minecraft Java 26.3 · Fabric · Client only**

### Task copies and profiles (1.4.0)

**Copy** duplicates a task immediately after the original and opens it for editing. It includes the query, CSV name, command list with delays and task-specific settings, with independent copies of all editable values. **Clear tasks** removes the current list while keeping saved profiles.

Under **Task queue → Task profiles**, enter a unique name and click **Save copy** to store all current tasks in their current order. Select a profile to replace the current task list; **×** deletes a saved profile. Profiles persist across restarts. Editing or executing a loaded task list never changes its saved profile. Global settings, queue sounds and Task delay remain current; profiles store tasks and their individual options.

Task delay defaults to **3000 ms** for new configurations and blank fields. Existing saved delays are preserved. Individual command delays still default to **750 ms**.

In **Task queue**, a warning triangle appears beside Task delay when the value is below 3,000 ms. Hovering it says, “A delay below 3,000 ms may cause problems in some cases.” Any valid delay can still be saved and used. A blank field means 3,000 ms and does not show the warning. Lower values are still allowed.

### Single-page results and multiple commands (1.4.0)

**Settings → Capture → Accept single page without footer** defaults to on. When CoreProtect omits the first page's pagination footer, the mod waits for the settle period without data, saves and continues the queue. The export reports “Single page saved”; `page_confirmed` stays false because no server footer was received. This does not accept missing later pages as complete. Turning it off preserves the inferred/unconfirmed outcome and stops the queue after saving. Tasks inherit this option or override it through their own capture settings.

Under **Task queue → Edit task → Custom commands**, use **Add**, **×** to remove and **↑ / ↓** to reorder. There is no fixed command-count limit; the editor is paginated. Each command has its own delay in ms (blank: 750), measured after export or the preceding command. After the final command, the Task delay runs before the next task. Empty lists skip custom commands. Older single-command settings automatically migrate with their delay.


### Task queue (1.4.0)

Open **F8 → Task queue**. Use **Add**, **×** to delete and **↑ / ↓** to reorder. Tasks persist across restarts but never start automatically on connection.

Each task has a full CoreProtect lookup command, CSV name, optional CSV overrides and an optional list of custom commands. **Universal settings** defaults to on; turn it off to edit capture/audio settings independently. Per-task sounds default to off. Configure whole-queue sounds under **Queue sounds**.

Opening **CSV settings** without saving or changing options keeps global inheritance. **Use global CSV settings** removes overrides. The task's CSV name always takes precedence, including a blank name.

Execution order: lookup → capture and export → each custom command after its own delay → Task delay → next lookup. Both delays accept 0–3600000 ms. When blank, **Task delay defaults to 3000 ms**, while **each command delay defaults to 750 ms**. An empty command list skips custom commands. No extra Task delay follows the last task. Options and timing are frozen when the queue starts.

Example: `/co l a:command t:3d`, CSV `Simply1`, universal settings on, custom command `/simply 2`. After saving, the command delay elapses, `/simply 2` is sent, then the Task delay elapses before the next lookup. Increase the Task delay for slow world changes: the mod waits for the configured interval, not a server teleport acknowledgment.

Failed lookups, timeouts, page limits, cancellation, storage errors and disconnection stop the queue. Saved data is retained and pending tasks are not executed. Custom commands use the player's normal server permissions.

### Export folders and automatic name clearing

A CSV named `july.csv` is saved under `coretrace/exports/july/`. If that folder already exists, CoreTrace creates `july (2)`, `july (3)`, and so on to preserve earlier exports. An empty name keeps the legacy timestamp/identifier folder format.

**Settings → CSV export → Auto-clear** defaults to off. When enabled, it clears the name after a successful save, including a task name when enabled in its CSV settings. A different name entered during capture is preserved.


CoreTrace accompanies CoreProtect Community Edition. It detects lookups you send in chat, captures their results, requests following pages, and saves a transcript and summary on your computer. This independent assistant neither includes nor replaces the CoreProtect plugin.

Install:

1. Use a Minecraft Java **26.3** instance with **Fabric Loader 0.19.5 or later**. The game uses Java 25; the official launcher normally manages its runtime. Select Java 25 in other launchers.
2. Install [Fabric API for 26.3](https://modrinth.com/mod/fabric-api/versions?g=26.3), version **0.161.0+26.3** or a later compatible version.
3. Put `coretrace-1.4.0+26.3.jar` in the instance's `mods` folder. Remove the old CoreTrace JAR if upgrading. Your preferences and query history are retained.
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

**Settings → Sounds** lists all loaded Minecraft sound events, including music and discs. Search by ID (`allay`, `music`, `note block`) or by subtitle in the game's language. Preview and stop controls let you audition sounds; leaving the menu stops its preview. Resource packs may replace the audio, and game volume settings still apply. **Last export** opens the most recent session folder containing the CSV or its parts; **Export folder** opens the exports root using native folder opening.

Local commands:

| Command | Effect |
| --- | --- |
| `/coretrace` | Open the menu. |
| `/coretrace start a:chat u:jose t:3d` | Start a query using filters. A full `/co l ...` command also works. |
| `/coretrace pause` | Pause new page requests while retaining an outstanding response. |
| `/coretrace resume` | Resume on the same connection. |
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
| Maximum pages per capture | 500 | 1–5000 |
| Capture hover text | On | On / off |
| Hide captured chat messages | Off | On / off |
| Accept first page without footer | On | On / off |

Capture timing and maximum page settings are fixed when a capture starts. Changes apply to the next capture. Increase the page delay if the server limits command frequency. The exporter also has memory limits: 512 messages on a pending page and 100,000 result messages per session; reaching either saves a partial capture.

Manual CoreProtect commands are blocked during capture to avoid replacing its cached query. Avoid using the inspector at the same time. Pausing does not release the query; cancel first if you need a different command. After an uncertain result, CoreTrace waits for late responses before another capture. It does not retry timed-out pages or resume across servers. Query history lets you start a capture again.

This client reads received system messages. It does not access the database or add permissions. Automatic capture sends lookup commands; the queue can also send the custom command explicitly configured by the user. Exports remain local. It cannot guarantee a transactionally consistent database snapshot while the server's data changes. Custom messages, translations or incompatible plugin versions may require parser changes. Memory limits are 512 messages on a pending page and 100000 captured result messages per session.

**Server compatibility:** CoreTrace targets the **26.3 client** and requires CoreProtect to already work on the server you join. The mod cannot make an incompatible plugin installation work. Check [official CoreProtect releases](https://www.spigotmc.org/resources/coreprotect-community-edition.8631/) and [permissions](https://docs.coreprotect.net/permissions/).

The parser follows the public **CoreProtect CE v24.0** format for headers, page links, results and coordinates. Messages changed by other plugins, custom translations and other plugin versions are not guaranteed to work. Page footers are not database snapshots: if server data or cache changes during a lookup, the client cannot guarantee a transactionally consistent set of events. It stops with a partial result when it detects a changed total or unexpected page. In a pending CSV page, `page_requested` records the requested page and `page_confirmed=false` means its correspondence could not be confirmed.

Technical references:

- [Fabric for Minecraft 26.3](https://fabricmc.net/2026/09/15/263.html).
- [Official Fabric example mod](https://github.com/FabricMC/fabric-example-mod).
- [CoreProtect commands](https://docs.coreprotect.net/commands/).
- [CoreProtect CE v24.0 public pagination](https://github.com/PlayPro/CoreProtect/blob/v24.0/src/main/java/net/coreprotect/utility/ChatUtils.java).
- [CoreProtect CE v24.0 lookup output format](https://github.com/PlayPro/CoreProtect/blob/v24.0/src/main/java/net/coreprotect/command/lookup/StandardLookupThread.java).
- [Mod Menu source](https://github.com/TerraformersMC/ModMenu).

The project includes MIT-licensed source, these instructions and synthetic example exports, including `ejemplo-simulado/all-event-types.csv`. The combined example uses multiple simulated queries; it does not represent one server query. Minecraft, Fabric API, Mod Menu and the CoreProtect plugin are not included.

Build with **JDK 25** and the included wrapper: `./gradlew build` on Linux/macOS or `gradlew.bat build` on Windows. The standard configuration uses Loom 1.17.20 and Gradle 9.5.1. Alternatively run `python build-portable.py` using Python 3.10+ and JDK 25+ (`JAVA_HOME`). This alternative verifies published dependency hashes and compiles against the actual unobfuscated Minecraft 26.3 classes, Fabric and optional Mod Menu APIs. It also runs the tests. Install the ordinary JAR from `build/libs`, not a sources JAR.

Version 1.4.0 built successfully with Gradle (`gradlew.bat test build --offline`) and passed **104 automated tests**, with no failures or errors. All 51 JAR classes were verified as Java 25 bytecode (major version 69). **The game UI and a live Minecraft/CoreProtect session remain untested.**

For your first server check, use a small known lookup and compare its last page and transcript with the chat output. The Spanish [README_es](README_es.md) contains the equivalent instructions in Spanish.

The working project lives directly in this directory. `src/` contains sources and tests; `gradle/` and wrapper scripts support builds. JARs are generated under `build/libs/`. Caches, build outputs and release archives are not source files. The first build after cache cleanup may need to download dependencies.
