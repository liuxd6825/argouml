# ArgoUML — Agent Notes

Compact guidance for working in this repo. Only facts a future agent would
likely miss.

## TL;DR

- **Build system**: Maven multi-module. Root `pom.xml` aggregates 14 modules under `src/`.
- **Entry point**: `org.argouml.application.Main` in `src/argouml-app` (class lives in
  `src/argouml-app/src/org/argouml/application/Main.java`).
- **Runnable artifact**: produced by the `argouml-build` module, **not** `argouml-app`.
  - `mvn -pl src/argouml-build -am package`
  - Output: `src/argouml-build/target/argouml.jar` (fat jar with deps).
  - Run: `java -jar src/argouml-build/target/argouml.jar`
- **Project status**: near-dormant. Last commit Aug 2022. No CI files in repo
  (Jenkinsfile was removed in commit `abda2c4647`).

## Things that will trip you up

### 1. The parent POM is external

Root `pom.xml` inherits from `org.argouml:parentpom:0.35.5-SNAPSHOT` which is
**not in this repo**. It is resolved from `https://oss.sonatype.org/content/repositories/snapshots`
(repo declared in root `pom.xml:60-71`). If your local Maven cache is fresh
or the snapshot is missing, the build will fail until it can be resolved.

### 2. Two UML backends, switched by Maven profile

- `argouml-core-model-mdr` (NetBeans MDR / UML 1.4) — **default**.
- `argouml-core-model-euml` (Eclipse UML2 / UML 2.x) — **optional**.
- Choose at build time:
  - `mvn -pl src/argouml-build -am -P euml package` to use EUML
  - `mvn -pl src/argouml-build -am -P mdr package`  to use MDR (or just omit `-P`)
- At runtime the choice is also visible: `org.argouml.model.Model.initialise(className)`
  selects between `MDRModelImplementation` and `EUMLModelImplementation`.

### 3. `argouml-core-model` tests are explicitly disabled

In `src/argouml-core-model/pom.xml`:

```xml
<testSourceDirectory>${project.basedir}/dont-run-the-tests</testSourceDirectory>
```

This avoids a circular reference with `argouml-mdr`. **Do not "fix" this** —
the Javadoc in the POM explains the constraint. If you need model tests
to run, run them via `argouml-core-model-mdr` instead.

### 4. `modules/dev/` and `modules/jscheme/` are NOT Maven modules

They are **legacy Eclipse-PDE / Ant projects** that get loaded at runtime by
`org.argouml.moduleloader.ModuleLoader2` from the `ext/` directory. Do not
add them to the root `<modules>` block; they have their own Ant `build.xml`
(`modules/jscheme/build.xml`) and Eclipse `.project` files.

### 5. Several modules are placeholders

These directories exist but contain **no Java code** (just POM + MANIFEST +
.gitignore + .checkstyle). Don't waste time looking for source here:

- `src/argouml-core-infra/` — vestigial log4j re-export; log4j was removed
  (commit `399ffa6c85`).
- `src/argouml-core-diagrams-uml2/` — empty, **not in root pom `<modules>`**.
- `src/argouml-core-diagrams-class2/` — empty, in pom.
- `src/argouml-core-diagrams-structure2/` — one stub file
  (`StructureDiagram2Module.java` with no-op `enable()`/`disable()`).

### 6. Source encoding is `ISO-8859-1`

Set in every module POM via `<project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>`.
Do not "modernize" to UTF-8 without auditing the entire codebase for non-ASCII
content first.

### 7. Java target is a mix

- OSGi `Bundle-RequiredExecutionEnvironment`: mix of `J2SE-1.5` and
  `JavaSE-1.6` across modules.
- Source has been retrofitted to compile on Java 8 and Java 11
  (commit `16e8048648`).
- `src/argouml-app/src/bin/argouml2.{sh,bat}` are the modern launchers.
- **Verified working combination**: Java 8 (Temurin x86_64, runs via Rosetta on arm64).
  Parent POM hardcodes `<compileSource>1.7</compileSource>` and uses
  `maven-compiler-plugin:2.3.1` — this combination breaks on Java 11
  (mvn-compiler-plugin 2.3.1 doesn't emit `--release`, so `-source 1.7` fails
  with "bootclasspath not set" on Java 11 javac). `-DcompileSource=1.8` triggers
  a different bug (deprecation warnings become fatal).
- Java 17+ not tested; expected to fail for similar plugin-compat reasons.

### 8. **The `jar-with-dependencies` assembly is broken — does NOT include external deps**

`mvn -pl src/argouml-build -am package` produces a 7.3 MB
`argouml-jar-with-dependencies.jar`, but it contains ONLY `org/argouml/*`
classes — no `org.tigris.gef.*`, `org.apache.xmlgraphics.*`, MDR jars, etc.
The maven-assembly-plugin 2.4 with `jar-with-dependencies` descriptor fails to
include transitive deps. Running `java -jar` on this jar errors with
`NoClassDefFoundError: org/tigris/gef/base/Globals`.

**Workaround** (already verified): build classpath manually.

```bash
# Copy all transitive deps of argouml-app to a local dir
mvn -pl src/argouml-app -Dmaven.test.skip=true dependency:copy-dependencies \
  -DoutputDirectory=/tmp/argouml-deps

# Run with full classpath
java -Xms64m -Xmx1024m -cp \
  src/argouml-build/target/argouml-jar-with-dependencies.jar:$(echo /tmp/argouml-deps/*.jar | tr ' ' ':') \
  org.argouml.application.Main
```

A pre-made launcher exists at `src/argouml-app/src/bin/run-argouml.sh`.

## Execution gotchas

### Sequence diagram won't open if `-Dargouml.modules=` is missing

Clicking "New Sequence Diagram" with the Sequence module NOT enabled
produces a model element named **`unattachedCollaboration`** (set in
`ActionNewDiagram.java:206`) but **no** real `ArgoDiagram` instance.
`DiagramFactory.java:173` calls `factories.get(DiagramType.Sequence)`
which returns `null` (the module isn't loaded), then falls through to
`createDiagram(null, ...)` which throws `IllegalArgumentException`.
The user sees the unattached Collaboration in the Navigator and can't open it.

**Mandatory JVM arg**: `-Dargouml.modules=org.argouml.sequence2.SequenceDiagramModule;[others]`.
Same pattern for `Activity2`, `State2`, `Deployment2`, `Notation2`,
`XmlPropertyPanels`, `Transformer`, `DeveloperModule`.

The list lives in `src/argouml-app/src/bin/run-argouml.sh` (the launcher
this repo provides) and was copied from `src/argouml-app/tools/eclipse/ArgoUML (live GEF).launch`.

### 9. JUnit 3 only

Every test imports `junit.framework.TestCase`. The codebase has **not adopted
JUnit 4 or 5**. Do not write `@Test` annotation-style tests.

### 10. Checkstyle config is external

Each module has a `.checkstyle` and/or `.checkclipse` file referencing
`/argouml-core-tools/checkstyle/...` — that workspace project lives in a
**separate repo**. Eclipse Checkstyle / Checkclipse builders won't work
without it. There is no Checkstyle Maven plugin configured.

### 11. Test models are external

`src/argouml-app/pom.xml` declares
`<dependency><groupId>org.argouml</groupId><artifactId>testmodels</artifactId>...</dependency>`
which lives in a **separate repo** (`argouml-testmodels`). Don't expect
`tests/testmodels/` to be checked in everywhere; some are populated at
test time.

## Module map (the parts that actually matter)

| Path | Purpose |
|---|---|
| `src/argouml-app/` | Main app: UI, kernel, persistence, cognitive, notation. **75% of the code.** |
| `src/argouml-core-model/` | Abstract model API (`org.argouml.model.Model`, `Facade`, factories, helpers). |
| `src/argouml-core-model-mdr/` | MDR/JMI implementation (UML 1.4). Largest class: `FacadeMDRImpl` (4,779 lines). |
| `src/argouml-core-model-euml/` | Eclipse UML2 implementation. `FacadeEUMLImpl` (2,824 lines). |
| `src/argouml-core-notation/` | New "notation2" framework (mostly skeletal). |
| `src/argouml-core-transformer/` | Element-rewriting engine (`SimpleStateTransformer`, `EventTransformer`). |
| `src/argouml-core-umlpropertypanels/` | XML-driven property panels (declarative `meta/*.xml` files). |
| `src/argouml-core-diagrams-activity2/` | New UML2 activity diagram renderer. **The showcase for the new XML-driven approach.** |
| `src/argouml-core-diagrams-sequence2/` | New UML2 sequence diagram (no legacy counterpart). |
| `src/argouml-core-diagrams-state2/` | Thin wrapper around legacy state diagram, gated on UML 2. |
| `src/argouml-core-diagrams-deployment2/` | Same pattern as state2. |
| `src/argouml-build/` | POM-only assembly module. `mvn package` here = fat jar. |

## Architecture landmarks

- **Model layer**: Bridge/Strategy via `Model` (service locator) → `ModelImplementation` → `MDRModelImplementation` or `EUMLModelImplementation`. The rest of the app talks only to `Model.getFacade()` etc.
- **Critics system** (`src/argouml-app/src/org/argouml/cognitive/`): ArgoUML's
  signature feature — 91 `Cr*` classes run on a background thread in
  `Designer`, producing `ToDoItem`s in the ToDo pane. Knowledge types are
  `KT_CORRECTNESS / COMPLETENESS / CONSISTENCY / SYNTAX / SEMANTICS /
  OPTIMIZATION / PRESENTATION / ORGANIZATIONAL / EXPERIENCIAL / TOOL / DESIGNERS`.
  **The old Javadoc comment "if you add a new critic, add a line here" is
  stale** — `InitCognitiveCritics.init()` is empty. Critics are registered
  inside the **profile** classes, which `ProfileManagerImpl` loads:
  - `src/argouml-app/src/org/argouml/profile/internal/ProfileUML.java` — UML
    well-formedness rules (~line 174 onward, calls `critics.add(new CrXxx())`)
  - `src/argouml-app/src/org/argouml/uml/cognitive/critics/ProfileGoodPractices.java`
  - `src/argouml-app/src/org/argouml/uml/cognitive/critics/ProfileCodeGeneration.java`
  - `src/argouml-app/src/org/argouml/pattern/cognitive/critics/InitPatternCritics.java`
- **Diagram editing**: built on **GEF 0.13.7** (`org.tigris.gef.*`).
  `FigNodeModelElement` (2,674 lines) and `FigEdgeModelElement` (1,812 lines)
  in `argouml-app/.../uml/diagram/ui/` are the shared base classes.
- **Startup**: `Main.java:151 main()` calls `initializeSubsystems()` which
  **explicitly** `new`s and `init()`s every `InitSubsystem` implementation
  (`InitNotation`, `InitUiCmd`, `InitCognitiveCritics`, `InitActivityDiagram`,
  etc.) — this is intentional, to avoid dependency cycles. New subsystems
  must be added to this list.
- **Persistence**: `PersistenceManager` singleton in
  `argouml-app/.../persistence/`. Default format is `.zargo` (a zip with
  `.argo` + `.xmi` + `.pgml` + `.todo` + `.profile` members). `USE_SAFE_SAVES`
  does atomic write-then-rename.
- **Property panels**: declarative. `argouml-core-umlpropertypanels/meta/*.xml`
  describe panel layout per UML metatype. `MetaDataCache` picks `metamodel.xml`
  vs `metamodel2.xml` based on `Model.getFacade().getUmlVersion()`.

## Build & test commands

```bash
# Full build (reactor, all 14 modules)
mvn -f src/argouml-build/pom.xml -am package

# Build the runnable fat jar (most common task)
mvn -pl src/argouml-build -am package

# Same, but with Eclipse UML2 backend
mvn -pl src/argouml-build -am -P euml package

# Run all tests (JUnit 3 surefire)
mvn test

# Run tests for a single module
mvn -pl src/argouml-app test
mvn -pl src/argouml-core-diagrams-sequence2 test

# Note: argouml-core-model tests are disabled (see "things that will trip you up" #3)

# Run a single test class
mvn -pl src/argouml-app test -Dtest=TestProject

# Clean IDE / Eclipse launch files are committed (look for *.launch)
# They expect the workspace to include the external argouml-core-tools project.
```

## Coding conventions specific to this repo

- **No Lombok, no modern frameworks.** Pure Java + Swing + GEF.
- **4 "god classes" > 3,000 lines** that are refactor candidates but **not
  to be touched casually**:
  - `argouml-core-model/src/.../model/Facade.java` (3,631)
  - `argouml-core-model-mdr/src/.../mdr/FacadeMDRImpl.java` (4,779)
  - `argouml-core-model-mdr/src/.../mdr/CoreHelperMDRImpl.java` (3,619)
  - `argouml-core-model-euml/src/.../euml/FacadeEUMLImpl.java` (2,824)
- **i18n** via `org.argouml.i18n.Translator` + per-class property files in
  `src/argouml-app/src/org/argouml/...` mirroring the package path.
- **No `var`, no streams in hot paths** (legacy style).
- **OSGi bundle metadata in `META-INF/MANIFEST.MF`** — these matter for
  the legacy Eclipse-PDE runtime. The `argouml-app` manifest exports 132
  packages and bundles 12 legacy JARs on `Bundle-ClassPath`.
- **Old `dev` module** uses the trick `Eclipse-RegisterBuddy: org.argouml.core.infra`
  to see the removed log4j re-export. That module is dead now (see #5).

## Useful file references

- Root POM: `pom.xml:1-72`
- Main entry: `src/argouml-app/src/org/argouml/application/Main.java:151`
- Subsystem init chain (new subsystems go here): `Main.java:418-432`
- Main window: `src/argouml-app/src/org/argouml/ui/ProjectBrowser.java`
- Model facade: `src/argouml-core-model/src/org/argouml/model/Model.java`
- `InitSubsystem` SPI: `src/argouml-app/src/org/argouml/application/api/InitSubsystem.java`
- Critic base: `src/argouml-app/src/org/argouml/cognitive/Critic.java:1`
- Critic registry (singleton): `src/argouml-app/src/org/argouml/cognitive/Agency.java`
- **Real critic registration** (not `InitCognitiveCritics`):
  - `src/argouml-app/src/org/argouml/profile/internal/ProfileUML.java:171+`
  - `src/argouml-app/src/org/argouml/uml/cognitive/critics/ProfileGoodPractices.java`
  - `src/argouml-app/src/org/argouml/uml/cognitive/critics/ProfileCodeGeneration.java`
- Persistence manager: `src/argouml-app/src/org/argouml/persistence/PersistenceManager.java`
- Module loader: `src/argouml-app/src/org/argouml/moduleloader/ModuleLoader2.java`
- Property panel metadata: `src/argouml-core-umlpropertypanels/src/org/argouml/core/propertypanels/meta/*.xml`
- `.gitignore` already excludes `build`, `target`, `bin`, `DIST` build artifacts.

## Extension points (where to add features)

| Want to... | Edit |
|---|---|
| Add a design rule (critic) | new `CrXxx.java` extending `CrUML` + i18n `CrXxx.properties` + register in one of the 3 Profile classes above |
| Add a property-panel field | edit `core-umlpropertypanels/meta/<Type>.xml` (pure XML, no Java) |
| Add a menu / toolbar action | new `ui/cmd/ActionXxx.java` + wire into `InitUiCmdSubsystem` |
| Add a new diagram type | new `uml/diagram/x/` package + `InitXxxDiagram` + append to `Main.java:418-432` |
| Add a notation provider | new `notation/providers/uml/XxxNotationUml.java` + register in `InitNotationUml` |
| Add a standalone extension | new `modules/my-module/` (Ant build, deploy to `ext/`) |
| Add a JUnit 3 test | `tests/org/argouml/.../TestXxx.java` extending `TestCase`; `setUp()` **must** call `InitializeModel.initializeDefault()` |

## Git workflow

**Do not create commits, issues, or branches** in this repo when working as an agent. Edit files in place only. The user manages the git history.

## Stale launch files (log4j references)

All Eclipse `.launch` files (e.g. `src/argouml-app/tools/eclipse/ArgoUML*.launch`)
set `-Dlog4j.configuration=org/argouml/resource/full_console.lcf` and reference
`/argouml-core-infra/lib/log4j-1.2.6.jar`. **log4j was removed in commit
`399ffa6c85`** — these flags are no-ops. Ignore them or strip them when
copying JVM args.

## License

**No `LICENSE` file is present in the repo root.** The project has historically
been BSD-licensed; verify before redistribution.
