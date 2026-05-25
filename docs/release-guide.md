# Release Guide

## Pre-Release Checklist

Before triggering a release, verify the following:

- [ ] All tests passing on the `main` branch
- [ ] Version numbers consistent across all module POMs
- [ ] No unintended SNAPSHOT dependencies
- [ ] Documentation updated for new features
- [ ] Breaking changes documented in the [Migration Guide](migration-guide.md)

## Triggering a Release

Export the version variables:

```shell
export CURRENT_DEVELOPMENT_VERSION=0.1.1
export NEXT_DEVELOPMENT_VERSION=0.2.0
```

Trigger the release automation:

```shell
gh workflow run release -f currentDevelopmentVersion=${CURRENT_DEVELOPMENT_VERSION} -f nextDevelopmentVersion=${NEXT_DEVELOPMENT_VERSION}
```

## Post-Release

After the release workflow completes:

1. Verify release artifacts on the [GitHub Releases page](https://github.com/wanaku-ai/camel-integration-capability/releases)
2. Update the [Wanaku documentation site](https://wanaku.ai/docs/) if needed
