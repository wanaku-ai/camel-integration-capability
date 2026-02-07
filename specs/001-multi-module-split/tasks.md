# Tasks: Multi-Module Project Split

**Input**: Design documents from `/specs/001-multi-module-split/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Tests are included as existing tests must pass after restructuring (FR-009). New plugin code requires unit tests per constitution.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Multi-Module Structure)

**Purpose**: Create multi-module Maven project structure with parent aggregator

- [x] T001 Convert root pom.xml to parent aggregator with packaging=pom in /pom.xml
- [x] T002 Create common module directory structure at /camel-integration-capability-common/
- [x] T003 Create runtimes aggregator pom.xml at /camel-integration-capability-runtimes/pom.xml
- [x] T004 [P] Create plugin module directory structure at /camel-integration-capability-runtimes/camel-integration-capability-plugin/
- [x] T005 [P] Create main module directory structure at /camel-integration-capability-runtimes/camel-integration-capability-main/

---

## Phase 2: Foundational (Parent POM & Module POMs)

**Purpose**: Configure Maven build structure that MUST be complete before code can be moved

**⚠️ CRITICAL**: No code movement can begin until this phase is complete

- [x] T006 Configure parent pom.xml with dependencyManagement for all shared dependencies in /pom.xml
- [x] T007 Configure parent pom.xml with pluginManagement for spotless, assembly, camel plugins in /pom.xml
- [x] T008 [P] Create common module pom.xml with Camel, Wanaku SDK, Jackson, JGit dependencies in /camel-integration-capability-common/pom.xml
- [x] T009 [P] Create runtimes aggregator pom.xml listing plugin and main modules in /camel-integration-capability-runtimes/pom.xml
- [x] T010 [P] Create plugin module pom.xml with common dependency and camel-api in /camel-integration-capability-runtimes/camel-integration-capability-plugin/pom.xml
- [x] T011 [P] Create main module pom.xml with common dependency, picocli, log4j2, and assembly plugin in /camel-integration-capability-runtimes/camel-integration-capability-main/pom.xml
- [x] T012 Verify multi-module build compiles with `mvn compile` from root

**Checkpoint**: Maven reactor builds all modules - code movement can now begin

---

## Phase 3: User Story 1 - Library Consumer Integrates Common Components (Priority: P1) 🎯 MVP

**Goal**: Extract reusable business logic into common module so library consumers can use it independently

**Independent Test**: Build common module standalone and verify no CLI/logging dependencies leak

### Implementation for User Story 1

- [x] T013 [US1] Create source directories at /camel-integration-capability-common/src/main/java/ai/wanaku/capability/camel/
- [x] T014 [US1] Create resources directory at /camel-integration-capability-common/src/main/resources/
- [x] T015 [P] [US1] Move model/ package from /src/main/java/ai/wanaku/capability/camel/model/ to /camel-integration-capability-common/src/main/java/ai/wanaku/capability/camel/model/
- [x] T016 [P] [US1] Move util/ package from /src/main/java/ai/wanaku/capability/camel/util/ to /camel-integration-capability-common/src/main/java/ai/wanaku/capability/camel/util/
- [x] T017 [P] [US1] Move init/ package from /src/main/java/ai/wanaku/capability/camel/init/ to /camel-integration-capability-common/src/main/java/ai/wanaku/capability/camel/init/
- [x] T018 [P] [US1] Move downloader/ package from /src/main/java/ai/wanaku/capability/camel/downloader/ to /camel-integration-capability-common/src/main/java/ai/wanaku/capability/camel/downloader/
- [x] T019 [P] [US1] Move grpc/ package from /src/main/java/ai/wanaku/capability/camel/grpc/ to /camel-integration-capability-common/src/main/java/ai/wanaku/capability/camel/grpc/
- [x] T020 [P] [US1] Move spec/ package from /src/main/java/ai/wanaku/capability/camel/spec/ to /camel-integration-capability-common/src/main/java/ai/wanaku/capability/camel/spec/
- [x] T021 [US1] Move WanakuCamelManager.java from /src/main/java/ai/wanaku/capability/camel/ to /camel-integration-capability-common/src/main/java/ai/wanaku/capability/camel/
- [x] T022 [US1] Move cic-version.txt from /src/main/resources/ to /camel-integration-capability-common/src/main/resources/
- [x] T023 [US1] Move existing unit tests to /camel-integration-capability-common/src/test/java/ (McpRulesReaderTest)
- [x] T024 [US1] Verify common module builds independently with `mvn -pl camel-integration-capability-common compile`
- [x] T025 [US1] Verify common module has no picocli or log4j dependencies (FR-003)

**Checkpoint**: Common module is a standalone library that can be consumed independently

---

## Phase 4: User Story 2 - Plugin Developer Creates Camel Plugin (Priority: P1)

**Goal**: Create ContextServicePlugin implementation with properties/env configuration

**Independent Test**: Build plugin module and verify it can be loaded via SPI without main module

### Implementation for User Story 2

- [x] T026 [US2] Create source directories at /camel-integration-capability-runtimes/camel-integration-capability-plugin/src/main/java/ai/wanaku/capability/camel/plugin/
- [x] T027 [US2] Create resources directory at /camel-integration-capability-runtimes/camel-integration-capability-plugin/src/main/resources/
- [x] T028 [US2] Create META-INF/services directory at /camel-integration-capability-runtimes/camel-integration-capability-plugin/src/main/resources/META-INF/services/
- [x] T029 [P] [US2] Implement PluginConfiguration.java with properties loading and env var override in /camel-integration-capability-runtimes/camel-integration-capability-plugin/src/main/java/ai/wanaku/capability/camel/plugin/PluginConfiguration.java
- [x] T030 [P] [US2] Create default camel-integration-capability.properties template in /camel-integration-capability-runtimes/camel-integration-capability-plugin/src/main/resources/camel-integration-capability.properties
- [x] T031 [US2] Implement CamelIntegrationPlugin.java with load() and unload() methods in /camel-integration-capability-runtimes/camel-integration-capability-plugin/src/main/java/ai/wanaku/capability/camel/plugin/CamelIntegrationPlugin.java
- [x] T032 [US2] Create SPI service file at /camel-integration-capability-runtimes/camel-integration-capability-plugin/src/main/resources/META-INF/services/org.apache.camel.spi.ContextServicePlugin
- [ ] T033 [US2] Create unit tests for PluginConfiguration in /camel-integration-capability-runtimes/camel-integration-capability-plugin/src/test/java/
- [ ] T034 [US2] Create unit tests for CamelIntegrationPlugin in /camel-integration-capability-runtimes/camel-integration-capability-plugin/src/test/java/
- [x] T035 [US2] Verify plugin module builds independently with `mvn -pl runtimes/camel-integration-capability-plugin -am compile`

**Checkpoint**: Plugin module can be added as dependency and discovered via SPI

---

## Phase 5: User Story 3 - Operator Runs Standalone Application (Priority: P2)

**Goal**: Move CLI entry point to main module preserving exact current behavior

**Independent Test**: Run main module with `--help` and verify identical output to pre-split version

### Implementation for User Story 3

- [x] T036 [US3] Create source directories at /camel-integration-capability-runtimes/camel-integration-capability-main/src/main/java/ai/wanaku/capability/camel/
- [x] T037 [US3] Create resources directory at /camel-integration-capability-runtimes/camel-integration-capability-main/src/main/resources/
- [x] T038 [US3] Move CamelToolMain.java from /src/main/java/ai/wanaku/capability/camel/ to /camel-integration-capability-runtimes/camel-integration-capability-main/src/main/java/ai/wanaku/capability/camel/
- [x] T039 [US3] Move log4j2.properties from /src/main/resources/ to /camel-integration-capability-runtimes/camel-integration-capability-main/src/main/resources/
- [x] T040 [US3] Update CamelToolMain.java imports if needed after code move
- [x] T041 [US3] Configure maven-assembly-plugin in main module pom.xml for jar-with-dependencies
- [x] T042 [US3] Move existing integration tests to /camel-integration-capability-runtimes/camel-integration-capability-main/src/test/java/ (WanakuCamelRouteLoaderIT)
- [x] T043 [US3] Verify main module builds fat jar with `mvn -pl runtimes/camel-integration-capability-main -am package`
- [x] T044 [US3] Verify CLI works: `java -jar runtimes/camel-integration-capability-main/target/*-jar-with-dependencies.jar --help`

**Checkpoint**: Standalone application works identically to pre-split version

---

## Phase 6: CI/CD & Container Updates

**Purpose**: Update build automation and container configuration for multi-module structure

- [x] T045 [P] Update Dockerfile jar path from target/ to runtimes/camel-integration-capability-main/target/ in /Dockerfile
- [x] T046 [P] Update main-build.yml if artifact paths need adjustment in /.github/workflows/main-build.yml
- [x] T047 [P] Update pr-builds.yml if needed in /.github/workflows/pr-builds.yml
- [x] T048 [P] Update early-access.yml if artifact paths need adjustment in /.github/workflows/early-access.yml
- [x] T049 [P] Update release-artifacts.yml for new module structure in /.github/workflows/release-artifacts.yml
- [x] T050 Update jreleaser.yml if artifact paths changed in /jreleaser.yml
- [ ] T051 Verify container builds with `podman build -t test .`

**Checkpoint**: CI/CD pipeline works with multi-module structure

---

## Phase 7: Documentation

**Purpose**: Create plugin documentation and update existing guides

- [x] T052 [P] Create plugin-usage.md with installation, configuration reference, and OpenShift deployment guide in /docs/plugin-usage.md
- [x] T053 [P] Update usage.md to reference plugin as alternative deployment option in /docs/usage.md
- [x] T054 Update README.md to describe multi-module structure and available deliverables in /README.md
- [x] T055 Update building.md with multi-module build commands in /docs/building.md

**Checkpoint**: Documentation complete for all deployment options

---

## Phase 8: Polish & Validation

**Purpose**: Final cleanup and verification

- [ ] T056 Remove old /src/main/java/ directory after confirming all code moved
- [ ] T057 Remove old /src/main/resources/ directory after confirming all resources moved
- [x] T058 Run full test suite with `mvn verify` from root
- [x] T059 Verify all existing tests pass (FR-009)
- [x] T060 Run spotless formatting check with `mvn spotless:check`
- [x] T061 Verify independent module builds: common, plugin, main
- [ ] T062 Test container image runs correctly
- [ ] T063 Final review of dependency tree to ensure no unwanted transitive dependencies

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup - BLOCKS all code movement
- **User Story 1 (Phase 3)**: Depends on Foundational - Creates common module
- **User Story 2 (Phase 4)**: Depends on User Story 1 (needs common module)
- **User Story 3 (Phase 5)**: Depends on User Story 1 (needs common module)
- **CI/CD (Phase 6)**: Depends on User Stories 1, 2, 3
- **Documentation (Phase 7)**: Depends on User Story 2 (plugin must exist)
- **Polish (Phase 8)**: Depends on all previous phases

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational - Creates the common module that others depend on
- **User Story 2 (P1)**: Depends on User Story 1 (plugin imports common module)
- **User Story 3 (P2)**: Depends on User Story 1 (main imports common module)

Note: User Stories 2 and 3 can run in parallel after User Story 1 completes.

### Within Each User Story

- Directory creation before code movement
- Code movement in parallel where files are independent
- Build verification after all code moved
- Tests after implementation complete

### Parallel Opportunities

**Phase 1** (can run in parallel after T003):
- T004, T005 (create plugin and main directories)

**Phase 2** (can run in parallel after T007):
- T008, T009, T010, T011 (all module POMs)

**Phase 3 - US1** (can run in parallel after T014):
- T015, T016, T017, T018, T019, T020 (all package moves)

**Phase 4 - US2** (can run in parallel after T028):
- T029, T030 (configuration implementation)

**Phase 6** (all CI/CD files can be updated in parallel):
- T045, T046, T047, T048, T049

**Phase 7** (documentation can be written in parallel):
- T052, T053

---

## Parallel Example: User Story 1 Code Movement

```bash
# Launch all package moves together (after directories created):
Task: "Move model/ package to common module"
Task: "Move util/ package to common module"
Task: "Move init/ package to common module"
Task: "Move downloader/ package to common module"
Task: "Move grpc/ package to common module"
Task: "Move spec/ package to common module"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (directory structure)
2. Complete Phase 2: Foundational (Maven POMs)
3. Complete Phase 3: User Story 1 (common module)
4. **STOP and VALIDATE**: Verify common module builds and can be imported
5. This alone enables library consumers

### Incremental Delivery

1. Setup + Foundational → Multi-module structure ready
2. Add User Story 1 → Common module usable → Library consumers unblocked
3. Add User Story 2 → Plugin module usable → Plugin consumers unblocked
4. Add User Story 3 → Main module usable → Operators unblocked
5. Add CI/CD + Docs → Production ready

### Parallel Team Strategy

With multiple developers after User Story 1 completes:
- Developer A: User Story 2 (Plugin)
- Developer B: User Story 3 (Main)
- Developer C: CI/CD updates
- Developer D: Documentation

---

## Notes

- Use `git mv` for all file moves to preserve history
- Run `mvn compile` after each phase to catch issues early
- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story
- Commit after each task or logical group
- Stop at any checkpoint to validate independently
