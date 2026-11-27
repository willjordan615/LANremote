import json
import os
import sys
import urllib.request

with open(sys.argv[1], encoding="utf-8") as file:
    modules = json.load(file)
if not modules:
    raise SystemExit("No resolved runtime dependencies; audit incomplete")
queries = [{"package": {"ecosystem": "Maven", "name": m["group"] + ":" + m["name"]},
            "version": m["version"]} for m in modules]
request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
    data=json.dumps({"queries": queries}).encode(), headers={"Content-Type": "application/json"})
with urllib.request.urlopen(request, timeout=45) as response:
    result = json.load(response)
if len(result.get("results", [])) != len(queries):
    raise SystemExit("Incomplete advisory response")
findings = []
for module, entry in zip(modules, result["results"]):
    for vuln in entry.get("vulns", []):
        findings.append({"module": module, "id": vuln["id"]})
with open("dependency-audit.json", "w", encoding="utf-8") as file:
    json.dump({"modules": modules, "findings": findings}, file, indent=2)
summary = f"OSV checked {len(modules)} resolved runtime artifacts; {len(findings)} advisory matches.\n"
for finding in findings:
    m = finding["module"]
    summary += f"- {m['group']}:{m['name']}:{m['version']} — {finding['id']}\n"
print(summary)
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as file:
        file.write(summary)
if findings:
    raise SystemExit("Advisories require applicability review; no automatic suppression")
