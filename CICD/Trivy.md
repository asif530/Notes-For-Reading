Trivy is used to scan for vulnerabilities.
Use a `.trivyignore` file to ignore known/accepted vulnerable packages.
If the CI pipeline needs to fail upon finding a vulnerability, use the `--exit-code 1` flag.

## What it is

Trivy (by Aqua Security) is an open-source, all-in-one security scanner. Despite the name commonly being associated with "container scanning," 
it scans much more than images:

- Container images (OS packages + language dependencies)
- Filesystems and local directories
- Git repositories
- Kubernetes clusters/manifests
- IaC files (Terraform, CloudFormation, Dockerfile, Helm)
- SBOMs (can generate and consume CycloneDX/SPDX)

## What it does

- **Vulnerability detection**: matches installed packages/libraries against CVE databases (NVD, GHSA, distro security advisories, etc.).
- **Misconfiguration scanning**: checks IaC and Kubernetes manifests against best-practice policies (e.g., running as root, missing resource limits, open security groups).
- **Secret detection**: scans for hardcoded secrets/credentials (API keys, tokens, private keys) in code and images.
- **License scanning**: flags dependencies with risky or non-compliant licenses.
- **SBOM generation**: produces a software bill of materials for supply-chain traceability.

## Best practices

- Pin the Trivy version in CI (don't float on `latest`) so scan results are reproducible across builds.
- Scan early (on PRs/commits) and again at the image-build stage — "shift left" (see [[Shift-Left]]) but don't skip the final artifact scan.
- Set severity thresholds deliberately, e.g. `--severity HIGH,CRITICAL`, instead of failing on every LOW/MEDIUM finding — reduces noise and alert fatigue.
- Cache the vulnerability DB (`--cache-dir`) between CI runs to speed up scans and avoid rate-limiting on DB downloads.
- Use `.trivyignore` sparingly and always with a reason/comment and a linked ticket — it should be an exception, not a default.
- Regularly review and prune `.trivyignore` entries; an ignored CVE today may have a patch available tomorrow.
- Combine with `--ignore-unfixed` to avoid blocking builds on vulnerabilities with no available fix yet (track them separately, don't ignore forever).

## Production practices

- Fail the pipeline (`--exit-code 1`) only on CRITICAL/HIGH for production deploy pipelines; treat lower severities as warnings/tracked findings.
- Scan the final built image (not just source/deps) right before pushing to the registry, so what's scanned is what's shipped.
- Re-scan images already in the registry periodically (scheduled job) — new CVEs are disclosed after an image was built and passed its original scan.
- Generate and store SBOMs per release for audit/compliance and faster future impact analysis when a new CVE drops.
- Integrate results into a central dashboard (e.g., DefectDojo, Aqua platform) rather than only failing builds silently in logs — findings need visibility and ownership.
- Use Trivy Operator in Kubernetes for continuous in-cluster scanning of running workloads, not just build-time scanning.

## Common mistakes to look for

- Scanning only the base image / Dockerfile and never the final built image with all layers and dependencies.
- No severity filter — pipeline fails on LOW severity noise, so teams end up disabling the scan entirely out of frustration.
- `.trivyignore` used to permanently suppress findings with no expiry, review process, or justification.
- Not pinning the Trivy binary/action version — scan behavior and DB format can change silently between releases.
- Vulnerability DB not cached/updated — either slow CI (re-downloading every run) or stale DB (missing recent CVEs) if update is disabled entirely.
- Treating a clean Trivy scan as "secure" — Trivy only catches known CVEs and misconfig patterns, not business logic flaws, so it should be one layer of a broader security process (SAST/DAST/manual review), not the only one.
- Ignoring unfixed vulnerabilities forever with `--ignore-unfixed` instead of tracking them for when a patch becomes available.
- Running scans with an outdated/cached DB in air-gapped environments without a defined DB update/mirroring process.
