import path from "node:path";
import { packToFile } from "./pack.mjs";
import { unpackFromFile } from "./unpack.mjs";
import { runValidate } from "./validate.mjs";
import { diffDirectory, printDiff } from "./diff.mjs";
import { deployDirectory } from "./deploy.mjs";
import { forkSolution } from "./fork.mjs";
import { initSolution } from "./init-solution.mjs";

function usage() {
  console.log(`Usage:
  ispf pack <dir> [-o bundle.json]
  ispf unpack <bundle.json> <dir>
  ispf validate <dir|bundle.json> [--app id] [--local] [--strict]
  ispf diff <dir> --app <id>
  ispf deploy <dir> --app <id> [--strict]
  ispf fork <bundle.json|dir> <dest> --app-id <plant-id>
  ispf init-solution <dest> --app-id <id> [--name <display>]
  ispf deploy <dir> --app <id> [--strict] [--with-seeds]

Environment: ISPF_BASE_URL, ISPF_API_TOKEN

  --strict  Ship-canon: LOGIC_HOST_DEVICE is ERROR (ADR-0060); validate + deploy use ?strict=true`);
}

function parseArgs(argv) {
  const args = argv.slice(2);
  const positional = [];
  const flags = {};
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a === "--app") {
      flags.app = args[++i];
    } else if (a === "--app-id") {
      flags.appId = args[++i];
    } else if (a === "--name") {
      flags.name = args[++i];
    } else if (a === "--with-seeds") {
      flags.withSeeds = true;
    } else if (a === "-o" || a === "--output") {
      flags.output = args[++i];
    } else if (a === "--local") {
      flags.local = true;
    } else if (a === "--strict") {
      flags.strict = true;
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
        strict: flags.strict,
      });
      if (results.local) {
        if (results.local.status === "OK") {
          console.log(flags.strict ? "local schema + ship-canon OK" : "local schema OK");
        } else {
          console.error(flags.strict ? "local validate errors (--strict):" : "local schema errors:");
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
    case "init-solution": {
      const dest = positional[1];
      if (!dest || !flags.appId) {
        throw new Error("init-solution requires <dest> --app-id <id>");
      }
      const result = initSolution(dest, flags.appId, flags.name);
      console.log(`Initialized ${result.dest} schema=${result.schemaName}`);
      break;
    }
    case "fork": {
      const src = positional[1];
      const dest = positional[2];
      if (!src || !dest || !flags.appId) {
        throw new Error("fork requires <bundle.json|dir> <dest> --app-id <plant-id>");
      }
      const result = forkSolution(src, dest, flags.appId);
      console.log(`Forked to ${result.dest} schema=${result.schemaName}`);
      break;
    }
    case "deploy": {
      const dir = positional[1];
      if (!dir || !flags.app) throw new Error("deploy requires <dir> --app <id>");
      const result = await deployDirectory(path.resolve(dir), flags.app, {
        strict: !!flags.strict,
        withSeeds: !!flags.withSeeds,
      });
      console.log(
        flags.strict ? "dry-run ship-canon OK wouldApply:" : "dry-run OK wouldApply:",
        (result.dryRun.wouldApply ?? []).join(", ")
      );
      const deployStatus = result.deploy.status ?? "OK";
      if (deployStatus !== "OK") {
        console.error("deploy", deployStatus + ":", (result.deploy.errors ?? []).join("; "));
        process.exit(1);
      }
      console.log("deploy OK version:", result.deploy.version ?? result.deploy.bundleVersion ?? "—");
      break;
    }
    default:
      usage();
      process.exit(2);
  }
}
