/**
 * Bundles the MCP server into one self-contained ESM file that is committed to the repo.
 *
 * Installing the plugin from a GitHub marketplace clones the repository as-is, so the gitignored dist/,
 * node_modules/ and schema/ directories do not exist on the user's machine. The committed bundle carries
 * every dependency and the snapshot schema (generated into src/ by sync-contract), so `node bundle/server.mjs` runs with nothing else present.
 *
 * Usage: `node scripts/bundle.mjs write` regenerates the bundle; `node scripts/bundle.mjs check` exits
 * non-zero when the committed bundle differs from a fresh build, which is what CI enforces.
 */
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { build } from "esbuild";

const packageRoot = join(import.meta.dirname, "..");
const bundlePath = join(packageRoot, "bundle", "server.mjs");

const requireShimBanner = [
  'import { createRequire as __wocCreateRequire } from "node:module";',
  "const require = __wocCreateRequire(import.meta.url);",
].join("\n");

/**
 * Normalises line endings so the output does not depend on how git checked the files out.
 *
 * @param {string} text
 * @returns {string}
 */
function toLf(text) {
  return text.replace(/\r\n/g, "\n");
}

/**
 * Builds the bundle in memory. Output is deterministic for a given source tree and esbuild version
 * (esbuild is pinned exactly in package.json): no timestamps, no source maps, and paths in comments are
 * relative to the package root rather than to the machine the build ran on.
 *
 * @returns {Promise<string>} the bundle text
 */
async function buildBundle() {
  const result = await build({
    absWorkingDir: packageRoot,
    entryPoints: ["src/index.ts"],
    outfile: bundlePath,
    bundle: true,
    write: false,
    platform: "node",
    format: "esm",
    target: "node20.11",
    legalComments: "eof",
    charset: "utf8",
    logLevel: "warning",
    banner: { js: requireShimBanner },
  });
  const [output] = result.outputFiles;
  return toLf(output.text);
}

/**
 * @param {string} text
 * @returns {string}
 */
function sha256(text) {
  return createHash("sha256").update(text).digest("hex");
}

async function writeBundle() {
  const text = await buildBundle();
  mkdirSync(dirname(bundlePath), { recursive: true });
  writeFileSync(bundlePath, text);
  console.log(`Wrote ${relative(packageRoot, bundlePath)} (${Buffer.byteLength(text)} bytes, sha256 ${sha256(text)})`);
}

async function checkBundle() {
  const fresh = await buildBundle();
  const committed = existsSync(bundlePath) ? toLf(readFileSync(bundlePath, "utf8")) : "";
  if (committed !== fresh) {
    console.error(
      `${relative(packageRoot, bundlePath)} is stale (committed sha256 ${sha256(committed)}, fresh sha256 ${sha256(fresh)}). Run \`npm run bundle\` and commit the result.`,
    );
    process.exitCode = 1;
    return;
  }
  console.log(`${relative(packageRoot, bundlePath)} is up to date (sha256 ${sha256(fresh)})`);
}

const commands = { write: writeBundle, check: checkBundle };
const command = commands[process.argv[2] ?? ""];
if (command === undefined) {
  console.error("Usage: node scripts/bundle.mjs <write|check>");
  process.exitCode = 2;
} else {
  await command();
}
