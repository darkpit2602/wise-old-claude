# Wise Old Claude

An Old School RuneScape companion for Claude Code. Ask "where's the nearest place to get nettles?" or "what should I
train next?" and Claude answers for *your* account: your levels, quests, gear, bank and where you are standing.

It has two halves that talk through local files:

- **this Claude Code plugin**, an MCP server that gives Claude your character and the OSRS Wiki;
- **the [Wise Old Claude RuneLite plugin](https://github.com/darkpit2602/runelite-wise-old-claude)**, which writes a
  read-only snapshot of your logged-in character to `~/.runelite/plugin-data/wise-old-claude/`.

## Start here

### 1. Install the Claude Code plugin

Needs [Node.js](https://nodejs.org/) 20.11 or newer. In Claude Code:

```
/plugin marketplace add https://github.com/darkpit2602/wise-old-claude.git
/plugin install wise-old-claude@wise-old-claude
```

The server ships as one prebuilt file, so there is nothing to build. Use the HTTPS URL: the
`darkpit2602/wise-old-claude` shorthand clones over SSH and fails without a GitHub SSH key.

### 2. Install the RuneLite plugin

In RuneLite, open the Plugin Hub and install **Wise Old Claude**. Until it is listed there, its source and build
instructions are at https://github.com/darkpit2602/runelite-wise-old-claude.

### 3. Ask

Log in to the game, then ask Claude anything about OSRS. Claude reads your snapshot first and tailors the answer to
it. Ask it to "show me the way" and it draws a route in RuneLite (through the
[Shortest Path](https://github.com/Skretzo/shortest-path) plugin when installed) plus the game's hint arrow.

## What Claude can look up

| Tool | Purpose |
|---|---|
| `get_player_snapshot` | Your character: account type, levels, location, inventory, equipment, quests, current target, slayer task, diaries, timers and more |
| `search_bank` | Items in your last-seen bank |
| `get_quests`, `get_quest_progress` | Quests by status, and the steps left in a quest whose journal you opened |
| `find_nearest` | Nearest banks, deposit boxes, altars, anvils, furnaces, fairy rings, spirit trees, agility courses, minigames |
| `find_nearby_objects`, `find_nearby_npcs`, `find_ground_items` | Objects, NPCs and loot around you, nearest first; objects also world-wide |
| `get_recent_messages`, `get_open_interface` | Your own recent game messages, and the shop, bank or dialogue open right now |
| `describe_location`, `rank_by_distance` | Names a tile; orders places by distance |
| `guide_to`, `clear_guidance` | Draws or removes a route in RuneLite |
| `wait_for` | Waits for something you do in game (an item in your inventory, reaching a place, a game message), then lets Claude act on it |
| `wiki_search`, `wiki_page` | The OSRS Wiki |
| `get_item_prices`, `get_hiscores`, `get_wikisync_progress` | Live GE prices, hiscores, and WikiSync progress |

Set `WOC_SNAPSHOT_DIR` to read snapshots from another directory.

## Your data

- The RuneLite plugin sends nothing over the network. It covers only the logged-in player, never other players or
  their chat, and only draws guidance: nothing Claude writes can make it click, walk or send input.
- The MCP server runs on your machine. It reads the plugin's files and calls the OSRS Wiki, its real-time prices API,
  WikiSync and the official hiscores. What Claude looks up becomes part of your conversation with Claude.

## License

BSD 2-Clause, see [LICENSE](LICENSE). The bundled npm packages and game data are listed with their licences in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Old School RuneScape is a trademark of Jagex Ltd; this is an
unofficial fan project, not affiliated with or endorsed by Jagex.
