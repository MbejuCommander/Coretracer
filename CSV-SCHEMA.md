**CoreTrace 1.2.0 — CSV schema 2**

One row represents one captured event. This schema is identical in Spanish and English mode. Columns and machine identifiers are English; `action`, `content`, names and raw messages preserve the original server language. Files are `records.csv` for an English report or `registros.csv` for a Spanish report.

The settings menu can export any non-empty subset of these columns. Optional splitting creates numbered files at page boundaries; each file has the selected header and retains the same CSV encoding and quoting rules. With the page limit set to `0`, the full capture goes into the original single CSV file.

| Column | Meaning |
| --- | --- |
| `page_requested` | Page requested by the client. |
| `page_confirmed` | Whether a matching pagination footer confirmed the page. `false` for pending or inferred pages. |
| `query_action` | Original `a:` or `action:` parameter, when present. |
| `event_type` | `block`, `session`, `chat`, `command`, `click`, `container`, `item`, `inventory`, `kill`, `sign`, `username`, `message` or `unknown`. |
| `server_timestamp` | Exact timestamp extracted from the event's tooltip, if supplied. Its timezone is preserved as supplied. |
| `time_relative` | Original time prefix, such as `0.05/h ago`. |
| `actor` | Actor name shown in the event. |
| `action` | Original server verb, such as `broke`, `logged in` or `picked up`. Empty for colon-formatted chat, commands and signs, which have no verb. |
| `action_id` | Stable interpretation: e.g. `block_break`, `session_login`, `item_pickup`, `chat`, `command`, `sign_text`, or `unclassified`. |
| `action_sign` | Original `+`, `-` or `−` marker. This is not a quantity. |
| `amount` | Explicit `xN` amount, as a number. Empty if not supplied. |
| `object` | Displayed block, item or entity. The optional give-item arrow and sentence-ending period are removed from this parsed field; `raw_text` retains them. |
| `world` | World named in the coordinate line. |
| `x` | X coordinate, signed integer. |
| `y` | Y coordinate, signed integer. |
| `z` | Z coordinate, signed integer. |
| `content` | Original chat message, command or sign text, including line breaks. |
| `recorded_username` | Username in `actor logged in as username`. No old/new rename direction is inferred. |
| `strikethrough` | Whether the event component contained strikethrough formatting. |
| `parse_status` | `parsed`, `partial`, `ambiguous` or `unrecognized`; see below. |
| `raw_text` | Complete plain-text event message captured from the server. |
| `raw_coordinates` | Complete associated coordinate message, including any `(a:item)`-style hint. |
| `hover_text` | Event tooltips, joined by line breaks. |
| `coordinate_hover_text` | Coordinate-message tooltips, joined by line breaks. |

Absent values are empty, not fabricated zeros. Block and session records have no implicit amount of one. Counts in the summary count rows, not summed amounts.

`parsed` means the visible event fields were recognized; it does not mean the database query is complete. `partial` means an expected detail was absent or did not fit the known phrase. `ambiguous` means a colon-formatted message could not be confidently identified as chat, command or sign from the query. `unrecognized` preserves an event with a time/sign envelope that uses an unknown format or action. Page/capture completion is separate from parsing status.

CE uses the same rendered prefix for chat and commands. The client uses an unambiguous query action to distinguish them and does not assume that a slash means a command. If a capture starts with a page number without query context, a text event can remain `message`. Explicit event verbs and CoreProtect's coordinate action hints allow object events to be classified even without an `a:` filter.

The English CE v24.0 phrases cover placed/broken blocks, login/logout, chat, commands, clicks, container additions/removals, item pickups/drops, Ender chest deposits/withdrawals, throws/shots, inventory movements, entity kills, sign text and username observations. Inventory events can be rendered as additions/removals without exposing their internal crafting or trade subtype. Server-only details absent from chat or hover components cannot be recovered.

UTF-8 BOM, CRLF record delimiters and standard CSV quoting are used. Embedded quotes are doubled; embedded newlines remain inside quoted fields. Arbitrary text that begins, after whitespace, with `=`, `+`, `-` or `@` receives a protective leading apostrophe so spreadsheets do not evaluate it. The captured transcript and JSON retain that text without the added apostrophe. Validated numeric coordinates/amounts remain numeric. Minecraft formatting codes and unsupported control characters are removed at capture; an individual component's plain text is capped at 32768 characters, and at most 16 distinct tooltips are stored per component.

`ejemplo-simulado/all-event-types.csv` is generated using the actual exporter from multiple synthetic queries. It demonstrates the categories without claiming a real server capture or a single all-category query. The per-query inputs are in `all-event-types.json`, and the reproducible generator is `examples/GenerateExamples.java`.
