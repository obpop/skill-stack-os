import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { universe } from "../data/universe";

const target = resolve(process.cwd(), "backend/src/main/resources/seed/demo-universe.json");
writeFileSync(target, `${JSON.stringify(universe, null, 2)}\n`, "utf8");
console.log(`Exported ${universe.nodes.length} nodes and ${universe.edges.length} edges to ${target}`);
