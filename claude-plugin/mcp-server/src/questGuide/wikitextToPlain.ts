/** Innermost template call: no braces inside, so nested templates resolve from the inside out. */
const INNERMOST_TEMPLATE = /\{\{([^{}]*)\}\}/;

/**
 * A skill icon directly followed by a link to the same skill renders as one word in game guides; dropping the
 * icon keeps "on {{SCP|Herblore}}[[Herblore]]" from reading "on Herblore Herblore".
 */
const ICON_BEFORE_LINK = /\{\{SCP\|[^|}]+(?:\|link=yes)?\}\}(?=\[\[)/gi;

/**
 * Turns a wiki fragment into the text a reader sees on the rendered page, so guide notes reach Claude as prose
 * instead of markup. Only templates the quest guides use are rendered; unknown ones carry no reader-visible text
 * worth keeping (icons, sortable values), so they are dropped.
 *
 * @param wikitext a fragment of article wikitext, such as one table cell
 * @returns plain text with checklist bullets as "- " lines, indented two spaces per nesting level
 */
export function wikitextToPlain(wikitext: string): string {
  const linked = wikitext
    .replace(/<!--[\s\S]*?-->/g, "")
    .replace(ICON_BEFORE_LINK, "")
    .replace(/\[\[(?:File|Image):([^|\]]+?)(?:\.\w+)?(?:\|[^\]]*)?\]\]/gi, "$1")
    .replace(/\[\[[^\]|]+\|([^\]]+)\]\]/g, "$1")
    .replace(/\[\[([^\]]+)\]\]/g, "$1");
  const rendered = renderTemplates(linked)
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<[^>]+>/g, "")
    .replace(/'{2,}/g, "");
  return formatLines(rendered);
}

function renderTemplates(text: string): string {
  let current = text;
  for (let match = INNERMOST_TEMPLATE.exec(current); match !== null; match = INNERMOST_TEMPLATE.exec(current)) {
    current = current.slice(0, match.index) + renderTemplate(match[1] ?? "") + current.slice(match.index + match[0].length);
  }
  return current;
}

interface TemplateArguments {
  positional: string[];
  named: Map<string, string>;
}

/** Renderers for the templates the quest guides use, keyed by lower-case template name. */
const TEMPLATE_RENDERERS: Readonly<Record<string, (args: TemplateArguments) => string>> = {
  scp: ({ positional: [skill = "", level] }) => (level === undefined ? skill : `${level} ${skill}`),
  plink: ({ positional: [page = ""], named }) => named.get("txt") ?? page,
  coins: ({ positional: [amount = ""] }) => `${formatAmount(amount)} coins`,
  floornumber: ({ named }) => floorName(named.get("uk")),
  gep: ({ positional: [item = ""] }) => item,
  "optimal quest/action": ({ positional: [action = ""] }) => action,
};

/**
 * Renders one template call. Checklist keeps its body verbatim because the body is the content, and splitting it
 * on "|" or "=" would cut sentences apart.
 */
function renderTemplate(call: string): string {
  const [rawName = "", ...rawArgs] = call.split("|");
  const name = rawName.trim().toLowerCase();
  if (name === "checklist") {
    return rawArgs.join("|");
  }
  return TEMPLATE_RENDERERS[name]?.(templateArguments(rawArgs)) ?? "";
}

function templateArguments(args: string[]): TemplateArguments {
  const positional: string[] = [];
  const named = new Map<string, string>();
  for (const arg of args) {
    const equals = arg.indexOf("=");
    if (equals === -1) {
      positional.push(arg.trim());
    } else {
      named.set(arg.slice(0, equals).trim().toLowerCase(), arg.slice(equals + 1).trim());
    }
  }
  return { positional, named };
}

function formatAmount(amount: string): string {
  const value = Number(amount);
  return Number.isFinite(value) ? value.toLocaleString("en-US") : amount;
}

/** Guides number floors the British way (ground, 1st, 2nd), which is how the game's own text counts them too. */
function floorName(ukFloor: string | undefined): string {
  const floor = Number(ukFloor);
  if (!Number.isInteger(floor) || floor < 0) {
    return "floor";
  }
  if (floor === 0) {
    return "ground floor";
  }
  const suffix = floor % 100 >= 11 && floor % 100 <= 13 ? "th" : (["th", "st", "nd", "rd"][floor % 10] ?? "th");
  return `${floor}${suffix} floor`;
}

/** Wiki bullets ("*", "**") become "- " lines indented per level; runs of blank lines collapse to one. */
function formatLines(text: string): string {
  return text
    .split("\n")
    .map((line) => {
      const bullet = /^(\*+)\s*(.*)$/.exec(line.trim());
      const content = bullet === null ? line.trim() : `${"  ".repeat((bullet[1] ?? "*").length - 1)}- ${bullet[2] ?? ""}`;
      return content.replace(/(\S) {2,}/g, "$1 ").trimEnd();
    })
    .join("\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}
