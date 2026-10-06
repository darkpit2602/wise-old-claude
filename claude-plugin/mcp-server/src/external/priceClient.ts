import { TtlCache } from "../cache.js";
import { REQUEST_HEADERS, type ClientClockOptions, type FetchFn } from "./http.js";

const API_BASE = "https://prices.runescape.wiki/api/v2/osrs";

/** Item metadata changes only with game updates, so a day-old mapping is fine. */
const MAPPING_TTL_MS = 24 * 60 * 60 * 1000;

/** Real-time prices move constantly; a minute keeps answers fresh without hammering the bulk endpoint. */
const LATEST_TTL_MS = 60 * 1000;

/** One entry of `/mapping`. Limit, alch and value are absent for some items (untradeable-on-GE, legacy). */
export interface RawItemMapping {
  id: number;
  name: string;
  members: boolean;
  examine: string;
  limit?: number;
  highalch?: number;
  lowalch?: number;
  value?: number;
  icon?: string;
}

/** One entry of `/latest`; a side is null when no instant-buy (high) or instant-sell (low) was ever seen. */
export interface RawLatestPrice {
  high: number | null;
  highTime: number | null;
  low: number | null;
  lowTime: number | null;
}

/** Price joined with mapping metadata, so one answer covers buy/sell, GE limit and alch questions. */
export interface ItemPrice {
  id: number;
  name: string;
  members: boolean;
  /** Latest instant-buy price; null when the item has never been seen trading. */
  high: number | null;
  highTime: string | null;
  /** Latest instant-sell price. */
  low: number | null;
  lowTime: string | null;
  /** GE buy limit per 4 hours. */
  geLimit: number | null;
  highAlch: number | null;
}

/** Partial success is the norm for multi-item requests, so misses are listed rather than failing the call. */
export interface PriceLookup {
  prices: ItemPrice[];
  /** Inputs that matched no GE-tradeable item, reported back so Claude can correct a spelling. */
  unresolved: string[];
}

interface ItemIndex {
  byId: Map<number, RawItemMapping>;
  byName: Map<string, RawItemMapping>;
}

/**
 * Client for the OSRS Wiki real-time prices API. Always fetches the bulk `/latest` document and answers every
 * item from it: the API's usage policy asks tools not to loop over `?id=` requests.
 */
export class PriceClient {
  private readonly mappingCache: TtlCache<ItemIndex>;
  private readonly latestCache: TtlCache<Record<string, RawLatestPrice>>;

  constructor(
    private readonly fetchFn: FetchFn = fetch,
    options: ClientClockOptions = {},
  ) {
    this.mappingCache = new TtlCache(MAPPING_TTL_MS, options.now);
    this.latestCache = new TtlCache(LATEST_TTL_MS, options.now);
  }

  /**
   * Resolves item names (case-insensitively) or ids and returns their latest prices with GE limit and high alch.
   *
   * @throws Error when the prices API answers with a non-success status.
   */
  async lookup(items: (string | number)[]): Promise<PriceLookup> {
    const [index, latest] = await Promise.all([this.itemIndex(), this.latestPrices()]);
    const prices: ItemPrice[] = [];
    const unresolved: string[] = [];
    for (const item of items) {
      const mapping = resolveItem(index, item);
      if (mapping === undefined) {
        unresolved.push(String(item));
        continue;
      }
      prices.push(toItemPrice(mapping, latest[String(mapping.id)]));
    }
    return { prices, unresolved };
  }

  private itemIndex(): Promise<ItemIndex> {
    return this.mappingCache.getOrLoad("mapping", async () => {
      const mappings = (await this.getJson("/mapping")) as RawItemMapping[];
      return {
        byId: new Map(mappings.map((mapping) => [mapping.id, mapping])),
        byName: new Map(mappings.map((mapping) => [mapping.name.toLowerCase(), mapping])),
      };
    });
  }

  private latestPrices(): Promise<Record<string, RawLatestPrice>> {
    return this.latestCache.getOrLoad("latest", async () => {
      const body = (await this.getJson("/latest")) as { data: Record<string, RawLatestPrice> };
      return body.data;
    });
  }

  private async getJson(path: string): Promise<unknown> {
    const response = await this.fetchFn(API_BASE + path, { headers: REQUEST_HEADERS });
    if (!response.ok) {
      throw new Error(`OSRS Wiki prices request failed (${path}): HTTP ${response.status}`);
    }
    return response.json();
  }
}

/** Numeric input (or an all-digit string) is an item id; anything else is a name. */
function resolveItem(index: ItemIndex, item: string | number): RawItemMapping | undefined {
  if (typeof item === "number") {
    return index.byId.get(item);
  }
  const trimmed = item.trim();
  if (/^\d+$/.test(trimmed)) {
    return index.byId.get(Number(trimmed));
  }
  return index.byName.get(trimmed.toLowerCase());
}

function toItemPrice(mapping: RawItemMapping, latest: RawLatestPrice | undefined): ItemPrice {
  return {
    id: mapping.id,
    name: mapping.name,
    members: mapping.members,
    high: latest?.high ?? null,
    highTime: isoFromUnixSeconds(latest?.highTime),
    low: latest?.low ?? null,
    lowTime: isoFromUnixSeconds(latest?.lowTime),
    geLimit: mapping.limit ?? null,
    highAlch: mapping.highalch ?? null,
  };
}

function isoFromUnixSeconds(seconds: number | null | undefined): string | null {
  return seconds === null || seconds === undefined ? null : new Date(seconds * 1000).toISOString();
}
