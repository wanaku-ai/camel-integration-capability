# Release Guide

This document describes the release process for the Camel Integration Capability.

## Pre-Release Checklist

Before triggering a release, verify the following:

- [ ] **All tests passing**: Check that CI builds on the `main` branch are green
- [ ] **CHANGELOG.md updated**: Add release notes describing new features, bug fixes, and breaking changes
- [ ] **Version numbers consistent**: Verify all module POMs reference the correct version
- [ ] **No SNAPSHOT dependencies**: Check that production dependencies do not include `-SNAPSHOT` versions
  - **Exception**: The Wanaku SDK may be a SNAPSHOT if co-releasing
- [ ] **Documentation updated**: Ensure README, usage guides, and API docs reflect new features
- [ ] **Security review**: Check for known vulnerabilities in dependencies (`mvn verify`)
- [ ] **Breaking changes documented**: If the release includes breaking changes, update migration guides

## Version Numbering

The project follows [Semantic Versioning](https://semver.org/):

**Format:** `MAJOR.MINOR.PATCH`

- **MAJOR**: Increment for incompatible API changes (e.g., 1.0.0 → 2.0.0)
- **MINOR**: Increment for backward-compatible new features (e.g., 0.1.0 → 0.2.0)
- **PATCH**: Increment for backward-compatible bug fixes (e.g., 0.1.0 → 0.1.1)

**Snapshot Versions:**
- Development versions use the `-SNAPSHOT` suffix (e.g., `0.1.1-SNAPSHOT`)
- Snapshot versions are not released; they represent work-in-progress

**Release Versions:**
- Release versions have no suffix (e.g., `0.1.1`)
- Once released, a version is immutable (never re-release the same version)

## Release Process

The release is fully automated via GitHub Actions. The workflow handles:

1. Version bumping in all module POMs
2. Building the project
3. Running tests
4. Creating a Git tag
5. Publishing release artifacts to GitHub Releases
6. Bumping to the next development version

### Triggering a Release

Export the version variables:

```shell
export CURRENT_DEVELOPMENT_VERSION=0.1.1
export NEXT_DEVELOPMENT_VERSION=0.2.0
```

Trigger the release workflow:

```shell
gh workflow run release -f currentDevelopmentVersion=${CURRENT_DEVELOPMENT_VERSION} -f nextDevelopmentVersion=${NEXT_DEVELOPMENT_VERSION}
```

**Parameters:**
- `currentDevelopmentVersion`: The current SNAPSHOT version in `main` (e.g., `0.1.0-SNAPSHOT` → `0.1.0`)
- `nextDevelopmentVersion`: The next SNAPSHOT version after release (e.g., `0.1.1-SNAPSHOT`)

**Example:**

To release version `0.1.1` and prepare for `0.1.2-SNAPSHOT`:

```shell
export CURRENT_DEVELOPMENT_VERSION=0.1.1
export NEXT_DEVELOPMENT_VERSION=0.1.2

gh workflow run release -f currentDevelopmentVersion=${CURRENT_DEVELOPMENT_VERSION} -f nextDevelopmentVersion=${NEXT_DEVELOPMENT_VERSION}
```

### What Happens During Release

The GitHub Actions workflow performs these steps:

1. **Checkout repository**: Clone the `main` branch
2. **Set release version**: Update all POMs to remove `-SNAPSHOT` (e.g., `0.1.1-SNAPSHOT` → `0.1.1`)
3. **Build project**: Run `mvn clean install`
4. **Run tests**: Execute the full test suite
5. **Create Git tag**: Tag the release commit (e.g., `v0.1.1`)
6. **Build distribution artifacts**:
   - Standalone JAR with dependencies
   - Shaded plugin JAR
7. **Create GitHub Release**: Publish the release with artifacts attached
8. **Bump to next development version**: Update POMs to the next SNAPSHOT (e.g., `0.1.2-SNAPSHOT`)
9. **Commit and push**: Push version bumps and tag to GitHub

### Distribution Artifacts

Each release includes two primary artifacts (defined in `jreleaser.yml`):

| Artifact | File Name | Purpose |
|----------|-----------|---------|
| **Standalone JAR** | `camel-integration-capability-main-{version}-jar-with-dependencies.jar` | Self-contained application for standalone deployment |
| **Shaded Plugin JAR** | `camel-integration-capability-plugin-{version}-shaded.jar` | SPI plugin for embedding in existing Camel applications |

Both artifacts are available on the [GitHub Releases page](https://github.com/wanaku-ai/camel-integration-capability/releases).

## Post-Release

After the release workflow completes successfully:

1. **Verify release artifacts**:
   - Visit the [GitHub Releases page](https://github.com/wanaku-ai/camel-integration-capability/releases)
   - Verify both JAR files are attached
   - Download and test each artifact
2. **Update downstream documentation**:
   - Update the [Wanaku documentation site](https://wanaku.ai/docs/) if needed
   - Announce new features in user-facing guides
3. **Announce the release**:
   - Post in community channels (Slack, mailing lists, etc.)
   - Update project website with release highlights
   - Share on social media if a major release
4. **Monitor for issues**:
   - Watch GitHub Issues for bug reports
   - Check CI builds for regressions
   - Review user feedback and questions

## Rollback

If a release has critical issues, **do not rollback**. Instead, create a **patch release** with the fix.

**Why not rollback?**
- Git tags are immutable and public
- Users may have already downloaded the release
- Changing a released version causes confusion
- Semantic versioning requires forward-only progression

**Patch Release Process:**

1. **Identify the issue**: Confirm the bug and its impact
2. **Fix on main**: Develop and test the fix on the `main` branch
3. **Cherry-pick to release branch** (if using release branches):
   - Create a branch from the release tag (e.g., `release/0.1.x`)
   - Cherry-pick the fix commit
4. **Release a patch version**: Follow the standard release process with an incremented patch version (e.g., `0.1.1` → `0.1.2`)
5. **Document the fix**: Update CHANGELOG.md and release notes

### Emergency Rollback (Extreme Cases Only)

If a release causes catastrophic issues (data loss, security breach), an emergency rollback may be necessary:

1. **Delete the GitHub release** (via web UI or `gh release delete`)
2. **Delete the Git tag** locally and remotely:
   ```bash
   git tag -d v0.1.1
   git push origin :refs/tags/v0.1.1
   ```
3. **Communicate widely**: Notify all users immediately
4. **Fix and re-release**: Address the issue and release a new version (increment the version number, do not reuse)

**Note:** This is a last resort. Always prefer patch releases.

## Hotfix Releases

For urgent fixes that cannot wait for the next scheduled release:

1. **Create a hotfix branch** from the release tag:
   ```bash
   git checkout -b hotfix/0.1.2 v0.1.1
   ```
2. **Apply the fix**: Make minimal changes to address the issue
3. **Test thoroughly**: Run the full test suite
4. **Release the hotfix**: Use the release workflow with the hotfix version
5. **Merge back to main**: Ensure the fix is included in future releases

## Release Schedule

The project does not have a fixed release schedule. Releases are triggered based on:

- **Feature readiness**: When a significant feature is complete and tested
- **Bug severity**: Critical bugs warrant immediate patch releases
- **Community demand**: If users request a feature or fix urgently
- **Dependency updates**: Major Camel or Wanaku SDK upgrades may trigger releases

**Typical Cadence:**
- **Major releases**: Every 6-12 months (breaking changes)
- **Minor releases**: Every 4-8 weeks (new features)
- **Patch releases**: As needed (bug fixes)

## Version History

Check the [CHANGELOG.md](../CHANGELOG.md) for a detailed history of all releases.