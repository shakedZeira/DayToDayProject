// server/src/shared.d.ts is a verbatim copy of shared/src/index.ts.
// tsconfig rootDir=src prevents importing ../shared directly, so we copy it.
//   node scripts/sync-shared.cjs          -> regenerate the copy
//   node scripts/sync-shared.cjs --check  -> exit 1 if they differ
const fs = require("fs");
const path = require("path");
const src = path.resolve(__dirname, "../../shared/src/index.ts");
const dst = path.resolve(__dirname, "../src/shared.d.ts");
const norm = (s) => s.replace(/\r\n/g, "\n");
const a = norm(fs.readFileSync(src, "utf8"));
if (process.argv.includes("--check")) {
  const b = fs.existsSync(dst) ? norm(fs.readFileSync(dst, "utf8")) : "";
  if (a !== b) {
    console.error("server/src/shared.d.ts is out of sync with shared/src/index.ts. Run: npm run sync:shared --workspace server");
    process.exit(1);
  }
  console.log("shared.d.ts in sync");
} else {
  fs.writeFileSync(dst, a);
  console.log("shared.d.ts regenerated");
}
