import path from "node:path";
import { packToFile } from "./pack.mjs";
import { unpackFromFile } from "./unpack.mjs";
import { runValidate } from "./validate.mjs";
import { diffDirectory, printDiff } from "./diff.mjs";
import { deployDirectory } from "./deploy.mjs";

function usage() {
  console.log(`Usage:
  ispf pack <dir> [-o bundle.json]
  ispf unpack <bundle.json> <dir>
  ispf validate <dir|bundle.json> [--app id] [--local]
  ispf diff <dir> --app <id>
  ispf deploy <dir> --app <id>

Environment: ISPF_BASE_URL, ISPF_API_TOKEN`);
}

function parseArgs(argv) {
  const args = argv.slice(2);
  const positional = [];
  const flags = {};
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a === "--app") {
      flags.app = args[++i];
    } else if (a === "-o" || a === "--output") {
      flags.output = args[++i];
    } else if (a === "--local") {
      flags.local = true;
    } else if (a === "--help" || a === "-h") {
      flags.help = true;
    } else if (a.startsWith("-")) {
      throw new Error(`Unknown flag: ${a}`);
    } else {
      positional.push(a);
    }
  }
  return { positional, flags };
}

export async function runCli(argv) {
  const { positional, flags } = parseArgs(argv);
  const cmd = positional[0];

  if (flags.help || !cmd) {
    usage();
    process.exit(cmd ? 0 : 2);
  }

  switch (cmd) {
    case "pack": {
      const dir = positional[1];
      if (!dir) throw new Error("pack requires <dir>");
      const out = flags.output ?? path.join(path.resolve(dir), "bundle.json");
      packToFile(path.resolve(dir), path.resolve(out));
      console.log(`Wrote ${out}`);
      break;
    }
    case "unpack": {
      const bundlePath = positional[1];
      const outDir = positional[2];
      if (!bundlePath || !outDir) throw new Error("unpack requires <bundle.json> <dir>");
      unpackFromFile(path.resolve(bundlePath), path.resolve(outDir));
      console.log(`Unpacked to ${outDir}`);
      break;
    }
    case "validate": {
      const input = positional[1];
      if (!input) throw new Error("validate requires <dir|bundle.json>");
      const results = await runValidate(path.resolve(input), {
        appId: flags.app,
        localOnly: flags.local,
      });
      if (results.local) {
        if (results.local.status === "OK") {
          console.log("local schema OK");
        } else {
          console.error("local schema errors:");
          for (const e of results.local.errors) console.error(" ", e);
          process.exit(1);
        }
      }
      if (results.remote) {
        if (results.remote.status !== "OK") {
          console.error("remote validate errors:", results.remote.errors);
          process.exit(1);
        }
        console.log(
          "remote validate OK",
          results.remote.warnings?.length ? `warnings=${results.remote.warnings.length}` : ""
        );
      }
      break;
    }
    case "diff": {
      const dir = positional[1];
      if (!dir || !flags.app) throw new Error("diff requires <dir> --app <id>");
      const { summary, local, remote } = await diffDirectory(path.resolve(dir), flags.app);
      printDiff(summary, local, remote);
      process.exit(summary.length ? 1 : 0);
      break;
    }
    case "deploy": {
      const dir = positional[1];
      if (!dir || !flags.app) throw new Error("deploy requires <dir> --app <id>");
      const result = await deployDirectory(path.resolve(dir), flags.app);
      console.log("dry-run OK wouldApply:", (result.dryRun.wouldApply ?? []).join(", "));
      console.log("deploy OK version:", result.deploy.version ?? result.deploy.bundleVersion ?? "—");
      break;
    }
    default:
      usage();
      process.exit(2);
  }
}
