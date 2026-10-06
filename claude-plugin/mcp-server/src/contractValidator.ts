import { Ajv2020, type ValidateFunction } from "ajv/dist/2020.js";
import addFormatsModule from "ajv-formats";

const addFormats = addFormatsModule as unknown as typeof addFormatsModule.default;

/**
 * Compiles a shared-contract schema into the validator every plugin-file reader uses. The RuneLite plugin updates
 * on its own schedule, so a newer plugin can write fields this server predates; those are dropped wherever the
 * contract closes an object, instead of failing the whole file. Breaking changes still fail, through
 * `schemaVersion`, and map-shaped fields (open `additionalProperties`) keep every entry.
 *
 * @param schema the contract's JSON Schema
 */
export function compileContract(schema: Record<string, unknown>): ValidateFunction {
  const ajv = new Ajv2020({ allErrors: true, removeAdditional: true });
  addFormats(ajv);
  return ajv.compile(schema);
}
