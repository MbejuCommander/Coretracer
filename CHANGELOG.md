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
