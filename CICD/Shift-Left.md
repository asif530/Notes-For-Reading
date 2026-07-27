# Shift Left

## What it is

"Shift left" means moving a process earlier in the software development timeline. The name comes from visualizing the pipeline as a left-to-right flow:

```
Code -> Build -> Test -> Deploy -> Production
```

Shifting a practice "left" means moving it toward the `Code`/`Build` end instead of leaving it near `Deploy`/`Production`.

## Why

The cost and blast radius of fixing a problem grows the later it's found:

- Found while coding: a local edit, no one else affected.
- Found in code review/PR: a small diff, caught before merge.
- Found in CI/build: blocks the pipeline, but before release.
- Found in staging: delays a release.
- Found in production: outage/incident, customer impact, hotfix under pressure.

Shifting left catches issues at the cheapest, lowest-risk point.

## Common applications

- **Security** (the usual context for Trivy): scan dependencies/images/IaC in the IDE or on PR, not just at deploy time. See [[Trivy]].
- **Testing**: write and run unit/integration tests alongside development instead of relying on a QA phase at the end.
- **Static analysis/linting**: run on every commit or PR rather than as a pre-release gate.
- **Performance**: load-test early builds instead of only just before launch.
- **Compliance**: check policy-as-code (e.g., Terraform/K8s manifests) before merge, not during an audit.

## Trade-offs / things to watch

- Shifting everything left can slow down developer inner loop (e.g., slow scans on every commit) — balance thoroughness against feedback speed.
- Shifting left doesn't replace checks later in the pipeline (build-time, pre-deploy, production monitoring) — new issues (e.g., new CVEs disclosed after merge) still require scanning later. It's "also scan early," not "only scan early."
- Requires tooling that gives fast, actionable feedback close to the developer (IDE plugins, PR checks) — a slow or noisy early check gets ignored or disabled.
