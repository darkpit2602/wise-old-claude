/**
 * Builds the name test shared by every "find the nearest X" tool (live objects, static object table, NPCs):
 * the query must appear at a word start, ignoring case. Players say "oak" for "Oak tree" and "range" for
 * "Cooking range", but "oak" must not hit "Cloak rack" and "cow" must not hit "Scow": the first hit becomes a
 * walking route, so all tools must agree on what matches.
 *
 * @param query what the player asked for
 * @returns a predicate over candidate names
 */
export function nameMatcher(query: string): (candidate: string) => boolean {
  const needle = ` ${wordsOf(query)}`;
  return (candidate) => ` ${wordsOf(candidate)}`.includes(needle);
}

function wordsOf(text: string): string {
  return text.trim().toLowerCase().split(/[^a-z0-9']+/).filter(Boolean).join(" ");
}
