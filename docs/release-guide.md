# Release Guide

## Pre-Release Checklist

Before triggering a release, verify the following:

- [ ] All tests passing on the release branch
- [ ] Version numbers consistent across all module POMs
- [ ] No unintended SNAPSHOT dependencies
- [ ] Documentation updated for new features
- [ ] Breaking changes documented in the [Migration Guide](migration-guide.md)

## Triggering a Release

Releases are cut from release branches following the `X.Y.x` naming convention (e.g., `0.1.x`, `0.2.x`).

### Create the release branch (first release of a minor version only)

```shell
git checkout main
git checkout -b 0.2.x
git push origin 0.2.x
```

### Set the versions

```shell
export RELEASE_BRANCH=0.2.x
export CURRENT_DEVELOPMENT_VERSION=0.2.0
export NEXT_DEVELOPMENT_VERSION=0.2.1
```

### Trigger the release

```shell
gh workflow run release -r ${RELEASE_BRANCH} -f releaseBranch=${RELEASE_BRANCH} -f currentDevelopmentVersion=${CURRENT_DEVELOPMENT_VERSION} -f nextDevelopmentVersion=${NEXT_DEVELOPMENT_VERSION}
```

> [!NOTE]
> The `-r` flag tells GitHub to run the workflow from the release branch. The `releaseBranch` input tells the
> workflow which branch to check out and push version bump commits to.

## Post-Release

After the release workflow completes:

1. Verify release artifacts on the [GitHub Releases page](https://github.com/wanaku-ai/camel-integration-capability/releases)
2. Update the [Wanaku documentation site](https://wanaku.ai/docs/) if needed
