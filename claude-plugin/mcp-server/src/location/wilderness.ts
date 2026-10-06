import type { AreaKind, TileCoordinates } from "./areaResolver.js";

/**
 * Where a tile sits in the Wilderness: the name a player would use for that PvP area and the level the
 * in-game overlay shows. The level drives the attackable combat range and the level 20 and 30 teleport
 * cut-offs, which is why it is surfaced rather than left for Claude to guess from coordinates.
 */
export interface WildernessStatus {
  name: string;
  kind: AreaKind;
  level: number;
}

/** An inclusive tile rectangle on every plane, the same shape the game's `inzone` client-script check uses. */
interface TileBox {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}

/** One branch of the game's wilderness level script: where it applies and the level it yields there. */
interface WildernessZone {
  box: TileBox;
  levelAt: (y: number) => number;
  name: string;
  kind: AreaKind;
}

const WILDERNESS = "Wilderness";
const MAP_SQUARE = 64;
const TILES_PER_LEVEL = 8;
const OVERWORLD_LEVEL_ONE_Y = 55 * MAP_SQUARE;
const UNDERGROUND_LEVEL_ONE_Y = 155 * MAP_SQUARE;

/** Covers one whole 64x64 map square, the unit most client-script zones are written in. */
function mapSquare(squareX: number, squareY: number): TileBox {
  return boxOfSquares({ fromX: squareX, toX: squareX, fromY: squareY, toY: squareY });
}

/** Covers a rectangle of whole map squares, given as inclusive square indices. */
function boxOfSquares(squares: { fromX: number; toX: number; fromY: number; toY: number }): TileBox {
  return {
    minX: squares.fromX * MAP_SQUARE,
    maxX: squares.toX * MAP_SQUARE + MAP_SQUARE - 1,
    minY: squares.fromY * MAP_SQUARE,
    maxY: squares.toY * MAP_SQUARE + MAP_SQUARE - 1,
  };
}

function contains(box: TileBox, { x, y }: TileCoordinates): boolean {
  return x >= box.minX && x <= box.maxX && y >= box.minY && y <= box.maxY;
}

/** Client scripts use integer division that truncates toward zero, unlike Math.floor on negatives. */
function divide(dividend: number, divisor: number): number {
  return Math.trunc(dividend / divisor);
}

/** Level rises by one every eight tiles north of the zone's level-one row, as in the client script. */
function bandedFrom(levelOneY: number, offset: number): (y: number) => number {
  return (y) => divide(y - levelOneY, TILES_PER_LEVEL) + offset;
}

function fixed(level: number): () => number {
  return () => level;
}

/** The client script's `scale(a, b, c)` is `a * c / b`, used here to spread levels 33-40 across one cave. */
function graduatedCave(y: number): number {
  return 33 + divide(((y % MAP_SQUARE) - 6) * 7, 50);
}

function wilderness(box: TileBox, levelAt: (y: number) => number): WildernessZone {
  return { box, levelAt, name: WILDERNESS, kind: "wilderness" };
}

/**
 * Tiles inside the zones below that the server does not treat as Wilderness. The client script only
 * computes a level; whether the overlay is shown is decided server-side, so known safe pockets are
 * carved out explicitly. Fort Daimon is the Bounty Hunter safe zone (the script's own exclusion);
 * the Ferox Enclave Dungeon is "not considered part of the Wilderness" per the OSRS Wiki; region 13723
 * is RuneLite's Slayer Tower (Morytania), which merely shares the underground band.
 */
const SAFE_POCKETS: readonly TileBox[] = [
  { minX: 53 * MAP_SQUARE + 21, maxX: 53 * MAP_SQUARE + 42, minY: 63 * MAP_SQUARE + 21, maxY: 63 * MAP_SQUARE + 42 },
  mapSquare(49, 156),
  mapSquare(53, 155),
];

/**
 * The branches of the game's `[proc,wilderness_level]` client script (script 384) in its own order,
 * first match wins, so overrides shadow the general underground band exactly as in game.
 *
 * Deliberate deviation: the script's overworld branch reaches map square row 67 (y 4351), but the
 * Wilderness tops out at level 56 (OSRS Wiki, "Wilderness levels"), which is row 61 (y 3967). Tiles
 * north of that are the Untamed Ocean, the Corporeal Beast's lair (not in the Wilderness per the wiki)
 * and the Theatre of Blood, so the band is capped there instead of reporting levels 57+.
 */
const ZONES: readonly WildernessZone[] = [
  { box: boxOfSquares({ fromX: 52, toX: 54, fromY: 62, toY: 64 }), levelAt: fixed(5), name: "Daimon's Crater", kind: "minigame" },
  wilderness(boxOfSquares({ fromX: 46, toX: 52, fromY: 55, toY: 61 }), bandedFrom(OVERWORLD_LEVEL_ONE_Y, 1)),
  wilderness(mapSquare(47, 158), bandedFrom(UNDERGROUND_LEVEL_ONE_Y, -1)),
  wilderness(mapSquare(51, 159), fixed(35)),
  wilderness(mapSquare(53, 159), fixed(35)),
  wilderness(mapSquare(52, 161), fixed(40)),
  wilderness(mapSquare(27, 180), fixed(21)),
  wilderness(mapSquare(29, 180), fixed(21)),
  wilderness(mapSquare(25, 180), fixed(29)),
  wilderness(mapSquare(52, 160), graduatedCave),
  wilderness(boxOfSquares({ fromX: 46, toX: 53, fromY: 155, toY: 169 }), bandedFrom(UNDERGROUND_LEVEL_ONE_Y, 1)),
];

/**
 * Says whether a tile is in the Wilderness and at which level, on any plane. Neither RuneLite's region
 * names nor the wiki's location list cover the open Wilderness, and RuneLite itself only reads the level
 * the game renders, so the geometry comes from the game's own level script.
 *
 * Sources: Joshua-F/osrs-dumps@fc15240f81b79e1d777bfb7f21121607a370b2f5 script/[proc,wilderness_level].cs2
 * lines 4-26 (zones and formulas) and script/[proc,inzone].cs2 lines 3-15 (inclusive bounds);
 * LostCityRS/Engine-TS src/engine/script/handlers/NumberOps.ts lines 124-127 (`scale` semantics).
 */
export function wildernessAt(tile: TileCoordinates): WildernessStatus | undefined {
  if (SAFE_POCKETS.some((pocket) => contains(pocket, tile))) {
    return undefined;
  }
  const zone = ZONES.find((candidate) => contains(candidate.box, tile));
  return zone === undefined ? undefined : { name: zone.name, kind: zone.kind, level: zone.levelAt(tile.y) };
}
