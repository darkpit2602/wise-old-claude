import { TtlCache } from "../cache.js";

const API_URL = "https://oldschool.runescape.wiki/api.php";
const PAGE_URL = "https://oldschool.runescape.wiki/w/";

/** The wiki asks API clients for a descriptive User-Agent; anonymous ones get throttled or blocked. */
const USER_AGENT = "wise-old-claude/0.1.0 (local Claude Code OSRS assistant)";

export type FetchFn = (input: string | URL, init?: RequestInit) => Promise<Response>;

export interface SearchResult {
  title: string;
  snippet: string;
  url: string;
}

export interface WikiPage {
  title: string;
  url: string;
  wikitext: string;
  truncated: boolean;
}

export interface WikiClientOptions {
  /** Cap on returned wikitext, protecting Claude's context from very long articles. */
  maxPageChars?: number;
  cacheTtlMs?: number;
}

/**
 * Read-only client for the OSRS Wiki MediaWiki API. Returns raw wikitext rather than rendered HTML because
 * infobox templates carry the structured facts (stats, requirements, locations) Claude needs.
 */
export class WikiClient {
  private readonly maxPageChars: number;
  private readonly cache: TtlCache<unknown>;

  constructor(
    private readonly fetchFn: FetchFn = fetch,
    options: WikiClientOptions = {},
  ) {
    this.maxPageChars = options.maxPageChars ?? 20_000;
    this.cache = new TtlCache(options.cacheTtlMs ?? 60 * 60 * 1000);
  }

  /** Full-text search over article titles and bodies. */
  async search(query: string, limit = 10): Promise<SearchResult[]> {
    const body = (await this.get({ action: "query", list: "search", srsearch: query, srlimit: String(limit) })) as {
      query?: { search?: { title: string; snippet: string }[] };
    };
    return (body.query?.search ?? []).map((hit) => ({
      title: hit.title,
      snippet: stripTags(hit.snippet),
      url: pageUrl(hit.title),
    }));
  }

  /** Fetches an article's wikitext, following redirects so item aliases resolve. */
  async page(title: string): Promise<WikiPage> {
    const full = await this.fullPage(title);
    return {
      ...full,
      wikitext: full.wikitext.slice(0, this.maxPageChars),
      truncated: full.wikitext.length > this.maxPageChars,
    };
  }

  /**
   * Fetches an article's complete wikitext for a tool that parses it server-side, where the length cap that
   * protects Claude's context does not apply.
   *
   * @throws Error when the page does not exist or the request fails
   */
  async fullPage(title: string): Promise<Omit<WikiPage, "truncated">> {
    const body = (await this.get({ action: "parse", page: title, prop: "wikitext", redirects: "1" })) as {
      parse?: { title: string; wikitext: { "*": string } };
      error?: { info: string };
    };
    if (body.parse === undefined) {
      throw new Error(`Wiki page "${title}" not found: ${body.error?.info ?? "no parse result"}`);
    }
    return { title: body.parse.title, url: pageUrl(body.parse.title), wikitext: body.parse.wikitext["*"] };
  }

  private get(params: Record<string, string>): Promise<unknown> {
    const url = new URL(API_URL);
    for (const [key, value] of Object.entries({ ...params, format: "json" })) {
      url.searchParams.set(key, value);
    }
    return this.cache.getOrLoad(url.toString(), async () => {
      const response = await this.fetchFn(url, { headers: { "User-Agent": USER_AGENT } });
      if (!response.ok) {
        throw new Error(`OSRS Wiki request failed: HTTP ${response.status}`);
      }
      return response.json();
    });
  }
}

function pageUrl(title: string): string {
  return PAGE_URL + encodeURIComponent(title.replace(/ /g, "_"));
}

const NAMED_ENTITIES: Record<string, string> = { quot: '"', apos: "'", lt: "<", gt: ">", amp: "&", nbsp: " " };

/**
 * Converts a search snippet's HTML to plain text. Entities are decoded in a single pass so an escaped
 * ampersand such as `&amp;lt;` yields the literal `&lt;`, not `<`.
 */
function stripTags(html: string): string {
  return html
    .replace(/<[^>]*>/g, "")
    .replace(/&(#\d+|#x[0-9a-f]+|[a-z]+);/gi, (entity, code: string) => {
      if (code.startsWith("#x") || code.startsWith("#X")) {
        return String.fromCodePoint(parseInt(code.slice(2), 16));
      }
      if (code.startsWith("#")) {
        return String.fromCodePoint(parseInt(code.slice(1), 10));
      }
      return NAMED_ENTITIES[code.toLowerCase()] ?? entity;
    });
}
