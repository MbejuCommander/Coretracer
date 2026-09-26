**CoreTrace 1.2.1 — Minecraft Java 26.3 · Fabric · Client only**

CoreTrace captures CoreProtect lookup results, requests the following pages, and saves a transcript, a summary, a CSV and a JSON session. It is an independent client assistant; the server still needs a working CoreProtect installation and your normal lookup permissions.

Install:

1. Use Minecraft Java **26.3**, Java **25**, and **Fabric Loader 0.19.5 or later**.
2. Install [Fabric API for 26.3](https://modrinth.com/mod/fabric-api/versions?g=26.3), version **0.161.0+26.3** or a later compatible version.
3. Put `coretrace-1.2.1+26.3.jar` in the instance's `mods` folder. Remove the old CoreTrace JAR if upgrading. Your preferences and query history are retained.
4. Optionally install a [Mod Menu version compatible with 26.3](https://modrinth.com/mod/modmenu). F8 and `/coretrace` also open the menu without it.

To select English, open **F8 → Ajustes → Idioma** and click the language button until it shows **Language: English**. Use **Settings → Language** to switch back to Español. The choice persists independently of Minecraft's language. Menus and new notifications change immediately; the report language is fixed when each capture starts. Existing chat messages and previously saved files are not rewritten.

**CSV columns always stay in English. Server records are never translated.** With an English CoreProtect server, actions such as `broke`, `logged in` and `removed` remain English even with the Spanish menu selected. Chat, commands, signs, object names and hover text retain their original content. A server using another language retains that original language as well.

Run a lookup normally:

```text
/co l a:-block u:jose t:3d
```

Automatic capture is enabled by default. CoreTrace waits for each page footer, then requests `/co l 2`, `/co l 3`, etc., at a default delay of 1500 ms. It preserves the server's page size. Use F8 to pause, resume, cancel and save, inspect or copy filtered results, manage favorites, change settings, or open the exports folder.

All public CE lookup categories are handled: **block, session, chat, command, click, container, item, inventory, kill, sign and username**. Use the same lookup syntax, for example `/co l a:chat u:jose t:3d` or `/co l a:container u:jose t:3d`. Each query is captured separately; CoreTrace does not automatically run every category. See [CSV-SCHEMA.md](CSV-SCHEMA.md) for the 24-column schema, classifications and unavailable fields.

Exports are stored in a separate timestamped folder per capture under:

```text
.minecraft/coretrace/exports/
```

| English report | Spanish report | Contents |
| --- | --- | --- |
| `transcript.log` | `transcript.log` | Captured messages, receive timestamps, hover text and strikethrough indicators. Written during capture. |
| `summary.txt` | `resumen.txt` | Status, scope and record counts grouped by actor, event type, action and object. |
| `records.csv` | `registros.csv` | User-selected English columns and original server records. UTF-8 with BOM, comma delimiter. |
| `session.json` | `sesion.json` | Structured pages, pending messages, notices and final status. |

The summary counts **event records**, not item quantities or net damage. Repeated records are retained. Strikethrough is preserved as metadata and does not automatically subtract a record. Exact server timestamps require the timestamp tooltip to be present and tooltip capture to be enabled. Missing values remain empty.

Chat and commands share the same CE message format, so their type comes from the query's `a:` filter. If it cannot be determined, the row remains `message` with `parse_status=ambiguous`. Unknown actions retain their raw text and receive `parse_status=unrecognized`. CE can display inventory operations as `added` or `removed` without exposing crafting or trading subtypes; those hidden details cannot be recovered by this client.

Spreadsheet-like formulas in arbitrary text receive a protective apostrophe in CSV cells. The captured transcript and JSON keep those messages without the added apostrophe. Minecraft formatting codes are removed when components are converted to plain text. If your spreadsheet's regional settings expect semicolons, import the file explicitly as UTF-8 with a comma delimiter.

Choose CSV columns, split large exports every N pages (0 disables splitting), and select vanilla Minecraft start and finish sounds in Settings. Each numbered CSV repeats the original header.

Under **Settings → CSV export → CSV file name**, enter `lookup` or `lookup.csv`. The result is `lookup.csv`, or `lookup_1.csv`, `lookup_2.csv`, etc. when split into multiple parts. Leave it empty for the default name. Save before starting a capture; the name and columns are fixed for that session.

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

Settings are saved in `config/coretrace.json`. Choose CSV columns and split large exports every N pages (0 disables splitting); numbered CSV files each repeat the header. The menu also lets you pick vanilla Minecraft start and finish sounds or turn either sound off. Defaults are a 1500 ms page delay, 30000 ms response timeout, 3500 ms no-footer wait, 500 pages, tooltip capture on, and captured chat visible. Timings, CSV options and report language apply to the next capture. Increase the delay if your server limits command frequency.

A final page footer confirms sequential page completion. CoreProtect commonly omits the footer for a single page; CoreTrace saves this as **end inferred, not confirmed** after the no-footer wait. Cancellation, disconnect, timeouts, limits and unexpected pagination produce a partial export. A transcript without a `FINAL:` line must be treated as interrupted.

Manual CoreProtect commands are blocked during capture to avoid replacing its cached query. Avoid using the inspector at the same time. Cancel first if you need a different command. After an uncertain result, CoreTrace waits for late responses before another capture. It does not retry timed-out pages or resume across servers. Starting explicitly from an existing page, such as `/coretrace start /co l 3`, is allowed and marks the missing earlier pages in the report. New queries must not use `p:`, `page:` or `#count`.

This client reads received system messages. It does not access the database, add permissions, or send rollback/restore/purge/teleport/give commands. Exports remain local. It cannot guarantee a transactionally consistent database snapshot while the server's data changes. Custom messages, translations or incompatible plugin versions may require parser changes. Memory limits are 512 messages on a pending page and 100000 captured result messages per session.

The parser follows the [official CoreProtect CE v24.0 lookup renderer](https://github.com/PlayPro/CoreProtect/blob/v24.0/src/main/java/net/coreprotect/command/lookup/StandardLookupThread.java) and [English phrases](https://github.com/PlayPro/CoreProtect/blob/v24.0/src/main/java/net/coreprotect/language/Language.java). CoreTrace targets the **26.3 client** and requires CoreProtect already working on your server.

The package includes source under MIT, the mod JAR, Spanish and English instructions, and clearly labeled synthetic examples. Minecraft, Fabric API, Mod Menu and CoreProtect are not bundled.

Build with **JDK 25 or newer** and the included wrapper: `./gradlew build` on Linux/macOS or `gradlew.bat build` on Windows. The standard configuration uses Loom 1.17.20 and Gradle 9.5.1. Alternatively run `python build-portable.py` using Python 3.10+ and JDK 25+ (`JAVA_HOME`). This alternative verifies published dependency hashes and compiles against the actual unobfuscated Minecraft 26.3 classes, Fabric and optional Mod Menu APIs. It also runs the tests. Install the ordinary JAR from `build/libs`, not a sources JAR.

The 26.3 common and client source sets compile with Gradle. The inherited automated test suite has 72 tests from the prior release; **the game client, visible menu and a real server session were not launched or tested end to end here.**

For your first server check, use a small known lookup and compare its last page and transcript with the chat output. 
