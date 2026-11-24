// Compat shim for get-git-auth-url across semantic-release 25/26.
// - sr25: semantic-release/lib/get-git-auth-url.js (backmerge's original import)
// - sr26: moved to @semantic-release/core/lib/get-git-auth-url.js, whose
//   package `exports` map hides the subpath, so resolve core's entry point and
//   import the file by absolute URL.
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { pathToFileURL } from "node:url";

const require = createRequire(import.meta.url);

async function tryImport(specifier) {
  try {
    return (await import(specifier)).default;
  } catch {
    return undefined;
  }
}

// sr26 first
let getGitAuthUrl;
try {
  const coreMain = require.resolve("@semantic-release/core");
  getGitAuthUrl = await tryImport(
    pathToFileURL(join(dirname(coreMain), "lib/get-git-auth-url.js")).href
  );
} catch {
  // no @semantic-release/core (sr25) — fall through
}

// sr25 / fallback: semantic-release's own lib
if (!getGitAuthUrl) {
  const srMain = require.resolve("semantic-release");
  getGitAuthUrl = await tryImport(
    pathToFileURL(join(dirname(srMain), "lib/get-git-auth-url.js")).href
  );
}

if (!getGitAuthUrl) {
  throw new Error(
    "piko: cannot resolve get-git-auth-url from semantic-release 25/26"
  );
}
export default getGitAuthUrl;
