/**
 * MCP tool result builders shared by every tool module, so all tools present text, JSON and errors to
 * Claude in one consistent shape.
 */
export const text = (value: string) => ({ content: [{ type: "text" as const, text: value }] });

/** Pretty-printed JSON; indentation costs a few tokens but makes nested snapshot data far easier for Claude to read. */
export const json = (value: unknown) => text(JSON.stringify(value, null, 2));

/** A result flagged as a tool error, for failures Claude should not treat as an answer. */
export const failure = (message: string) => ({ ...text(message), isError: true });

/**
 * The error for a plugin file that exists but fails schema validation, which means the RuneLite plugin and this
 * server disagree on the file format.
 *
 * @param label what the file holds, e.g. "NPC index"
 * @param invalid where the file is and why it failed validation
 */
export const contractMismatch = (label: string, invalid: { path: string; errors: string }) =>
  failure(
    `${label} ${invalid.path} does not match the contract (plugin and server versions out of sync?): ${invalid.errors}`,
  );

/**
 * Drops the keys whose value is undefined, so optional fields are absent from a result rather than present and
 * empty.
 *
 * @param value an object with optional fields
 * @returns a copy without the undefined entries
 */
export function withoutUndefined<T extends object>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined)) as T;
}
