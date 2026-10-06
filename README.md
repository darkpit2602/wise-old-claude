# Wise Old Claude

An Old School RuneScape companion for Claude Code. Ask "where's the nearest place to get nettles?" or "what should I
train next?" and Claude answers for *your* account: your levels, quests, gear, bank and where you are standing.

It has two halves, both in this repository, that talk through local files:

- **the RuneLite plugin** (the repository root: `src/main`, `runelite-plugin.properties`), which writes a read-only
  snapshot of your logged-in character to `~/.runelite/plugin-data/wise-old-claude/`;
- **the Claude Code plugin** (`claude-plugin/`), an MCP server that gives Claude that snapshot and the OSRS Wiki.

## Start here

### 1. Install the RuneLite plugin

In RuneLite, open the Plugin Hub and install **Wise Old Claude**.

### 2. Install the Claude Code plugin

Needs [Node.js](https://nodejs.org/) 20.11 or newer. In Claude Code:

```
/plugin marketplace add https://github.com/darkpit2602/wise-old-claude.git
/plugin install wise-old-claude@wise-old-claude
```

Claude Code runs the server from `claude-plugin/mcp-server/bundle/server.mjs`, a single file built from the
TypeScript source next to it, so there is nothing to build. If Node.js is missing or too old, Claude Code tells you
when it starts.

### 3. Ask

Log in to the game, then ask Claude anything about OSRS. Claude reads your snapshot first and tailors the answer to
it. Ask it to "show me the way" and it draws a route in RuneLite (through the
[Shortest Path](https://github.com/Skretzo/shortest-path) plugin when installed) plus the game's hint arrow.

## Your data stays on your machine

- The RuneLite plugin **sends nothing over the network**. It has no HTTP client or server; everything it produces is
  a file under `~/.runelite/plugin-data/wise-old-claude/`.
- It covers **only the logged-in player**: skills, quests, items, bank, location and the player's own game messages.
  It never records other players or their chat.
- The MCP server runs on your machine. It reads the plugin's files and calls the OSRS Wiki, its real-time prices API,
  WikiSync and the official hiscores. What Claude looks up becomes part of your conversation with Claude, like
  anything else you tell it.

## Guidance is drawn, never acted on

Claude asks for directions by writing a destination tile to `guidance/request.json` in the same folder. The plugin
only **draws** that guidance: a route through Shortest Path when it is installed, the game's hint arrow, and a chat
line. Nothing in that file can make the plugin click, walk or send any input.

## Files

| File | Contents |
|---|---|
| `<rsn>.json` | snapshot: levels, quests, inventory, equipment, bank, location; throttled by *Write interval* |
| `guidance/request.json` | written by the MCP server, read by the plugin |
| `companion/status.json` | written by the MCP server while it runs; the plugin's side panel and login reminder read it |
| other `*.json` and `objects/`, `quests/` | nearby NPCs, objects and ground items, recent game messages, open interface, quest journals |

Every file follows a JSON Schema in `contract/`; the MCP server validates what it reads against them.

## What Claude can look up

| Tool | Purpose |
|---|---|
| `get_player_snapshot` | Your character: account type, levels, location, inventory, equipment, quests, current target, slayer task, diaries, timers and more |
| `search_bank` | Items in your last-seen bank |
| `get_quests`, `get_quest_progress` | Quests by status, and the steps left in a quest whose journal you opened |
| `get_quest_guide` | Your next steps in the wiki's optimal quest guide (Ironman version for ironmen), with levels to train first and upcoming steps grouped by location for multiquesting |
| `find_nearest` | Nearest banks, deposit boxes, altars, anvils, furnaces, fairy rings, spirit trees, agility courses, minigames |
| `find_nearby_objects`, `find_nearby_npcs`, `find_ground_items` | Objects, NPCs and loot around you, nearest first; objects also world-wide |
| `get_recent_messages`, `get_open_interface` | Your own recent game messages, and the shop, bank or dialogue open right now |
| `describe_location`, `rank_by_distance` | Names a tile; orders places by distance |
| `guide_to`, `clear_guidance` | Draws or removes a route in RuneLite |
| `wait_for` | Waits for something you do in game (an item in your inventory, reaching a place, a game message), then lets Claude act on it |
| `wiki_search`, `wiki_page` | The OSRS Wiki |
| `get_item_prices`, `get_hiscores`, `get_wikisync_progress` | Live GE prices, hiscores, and WikiSync progress |

Set `WOC_SNAPSHOT_DIR` to read snapshots from another directory.

## Repository layout and building from source

| Path | What |
|---|---|
| `src/main`, `build.gradle`, `runelite-plugin.properties` | the RuneLite plugin (Java 11) |
| `contract/` | JSON Schemas for every file the two halves exchange |
| `.claude-plugin/marketplace.json` | the Claude Code marketplace, pointing at `claude-plugin/` |
| `claude-plugin/` | the Claude Code plugin: manifest, a hook that checks for Node.js, and `mcp-server/` |
| `claude-plugin/mcp-server/src` | the MCP server's TypeScript source |
| `claude-plugin/mcp-server/bundle/server.mjs` | the server and its npm dependencies in one file, built from `src` |

Build the RuneLite plugin with `./gradlew build` (JDK 11 to 17). Check that the bundle matches its source from
`claude-plugin/mcp-server/`:

```sh
npm ci
npm run bundle:check
```

`bundle:check` rebuilds the bundle from `src` with the pinned esbuild and fails unless the result is identical to the
committed file; `npm run bundle` rewrites it.

## License

BSD 2-Clause, see [LICENSE](LICENSE). The bundled npm packages and game data are listed with their licences in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Old School RuneScape is a trademark of Jagex Ltd; this is an
unofficial fan project, not affiliated with or endorsed by Jagex.
