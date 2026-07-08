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
  Parent POM declares `<compileSource>1.8</compileSource>` and uses
  `maven-compiler-plugin:2.3.1`. `<showDeprecation>` was flipped to `false`
  to avoid deprecation-warnings-become-fatal on Java 8.
  The parent POM is **vendored** at `parentpom-0.35.5-SNAPSHOT.pom` at the
  project root; the root `pom.xml` references it via
  `<relativePath>./parentpom-0.35.5-SNAPSHOT.pom</relativePath>`. The same
  POM is installed into `~/.m2` via `mvn install:install-file` so modules
  built standalone (e.g. `mvn -f src/argouml-ai/pom.xml ...`) resolve it
  identically. `maven-compiler-plugin:2.3.1` doesn't emit `--release`, so
  `-source 1.8` would fail on Java 11 javac with the same "bootclasspath
  not set" error that `-source 1.7` did.
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

### 8a. `StandaloneHttpServer.java` 与 `InitHttpServerSubsystem.java` 维护两份 router
两份代码各有一份 `buildRouter()`,原 Javadoc 注明 _"Kept in sync by hand; if a
route is added/removed there, update this method too"_。Phase 4 在 InitHttpServerSubsystem
加了 17 个 usecase 路由(`/d/{d}/usecasediagram/{actors,usecases,...}`),StandaloneHttpServer
没跟上。症状:启动 `StandaloneHttpServer` 后访问
`POST /d/{d}/usecasediagram/actors` 返回 404 ROUTE_NOT_FOUND,smoke test 全部失败。

修复:在 `StandaloneHttpServer.java:160 buildRouter()` 内,先取
`UseCaseDiagramService ucSvc = DiagramServices.useCaseSvc();`,再 import 14 个 usecase
handler 类,然后末尾追加 17 行 `router.add(Method.X, "/d/{d}/usecasediagram/...", new ...)`。
mirror `InitHttpServerSubsystem.java:259-296`。

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
| `src/argouml-ai/` | AI 子系统 HTTP REST API（基于 NanoHTTPD），含 50+ 端点服务于类图/用例图/时序图。**不在根 reactor 中**；需独立构建。详见下方"argouml-ai REST API"小节。 |

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

## argouml-ai REST API (src/argouml-ai/)

通过 `Main.java:466` 的
`SubsystemUtility.initSubsystem(new InitHttpServerSubsystem())`
接入主程序，启动时执行一次。路由在启动时**只读一次**，新增端点
必须重启 JVM 才能生效。

### 故意不加入根 reactor

`argouml-ai` 被排除在根 `pom.xml` 的 `<modules>` 之外，因为它
**编译期依赖 `argouml-app`**，加入会形成循环依赖。需用 `-f` 显式
构建：

```bash
mvn -f src/argouml-ai/pom.xml -am test                                       # 全部 665 个测试
mvn -f src/argouml-ai/pom.xml -am test -Dtest=TestCleanupDatatypes          # 单个测试
mvn -f src/argouml-ai/pom.xml -am install -DskipTests -o                     # 发布到 ~/.m2
```

### 包分层规则（按图类型划分）

**每种图类型（`class` / `usecase` / `sequence` / `state` ...）
必须包含 3 个对称子包 + 1 个 handlers 子包。** 现有 `classdiagram`
是模板——新增图类型时复制其结构：

```
src/main/java/org/argouml/ai/
├── application/
│   ├── <kind>/                       ← <Kind>DiagramService
│   └── common/                       ← DiagramService 接口、异常、注册表
├── domain/
│   ├── <kind>/                       ← <Element>Operations（纯 model 操作）
│   └── common/                       ← DiagramOperations、DiagramLocator、ModelKind
├── inbound/rest/
│   ├── <kind>/                       ← （可选聚合路径）
│   │   └── handlers/
│   │       └── <subarea>/            ← Get/Create/Delete*Handler
│   └── common/                       ← Router、Dispatcher、IRequestHandler
└── infrastructure/                  ← JSON、NanoHTTPD、UndoScope、EDT
```

测试在 `src/argouml-ai/tests/.../<同路径>/` 镜像此结构。

### 新增图类型（例如 `usecasediagram`）

5 处文件改动 + 3 个新子包：

1. `ModelKind`（`domain/common/ModelKind.java`）：加
   `USECASE("usecasediagram")` 枚举值。
2. `DiagramOperations.create()` 和 `kindOf()`
   （`domain/common/DiagramOperations.java`）：加 `case` 分支和
   `instanceof` 识别 `UMLUseCaseDiagram`。
3. `DiagramServices`（`application/common/DiagramServices.java`）：
   在 static 块加
   `REG.register(ModelKind.USECASE, new UseCaseDiagramService());`。
4. 3 个新子包：
   - `application/usecasediagram/UseCaseDiagramService.java`
     （实现 `DiagramService`，返回 `ModelKind.USECASE`）
   - `domain/usecasediagram/{Actor,UseCase,Include,Extend}Operations.java`
   - `inbound/rest/usecasediagram/handlers/{actor,usecase,relationship}/`
5. 在 `InitHttpServerSubsystem.buildRouter()` 注册路由。
   **无需改 Main.java**——现有 `InitHttpServerSubsystem.init()` 自动加载新路由。

### 在已有图类型上新增端点

1. 新建 `inbound/rest/<kind>/handlers/<subarea>/MyHandler.java`，
   实现 `IRequestHandler`（一个方法 `handle(pathParams, queryParams, body)`
   返回 `ResponseEnvelope`）。构造函数注入该 kind 的 service 实例。
2. 在 `InitHttpServerSubsystem.buildRouter()` 加
   `router.add(Method.VERB, "/path", new MyHandler(svc));`。
3. JUnit 3 测试在
   `tests/.../inbound/rest/<kind>/handlers/<subarea>/` 继承 `TestCase`。
   `setUp()` 必须调 `InitializeModel.initializeDefault()`，并在
   `Project.makeEmptyProject()` 上创建新的 `UMLClassDiagram`。
4. **重启 JVM**——`InitHttpServerSubsystem.init()` 只在启动跑一次；
   新增路由需要进程重启。

### argouml-ai 抽象基类与编码规则

按图类型分层后，**所有新代码必须优先复用现有 5 个抽象基类**：

| 基类 | 位置 | 用途 | 子类需实现 |
|---|---|---|---|
| `AbstractDiagramElementOperations<T>` | `domain/common/` | 节点（Class/Actor/UseCase）的 build / findByName / delete / setPosition | `buildImpl(diagram, name)` + `isTargetType(node)` |
| `AbstractDiagramServiceHelper` | `application/common/` | 共享 `requireDiagram(name)` + `requireNonEmptyName(name)` | 无（静态方法） |
| `HandlerJsonHelper` | `inbound/rest/common/` | 共享 JSON 解析：`strEmpty` / `intVal` / `boolVal` | 无（静态方法） |
| `AbstractListHandler<S, V>` | `inbound/rest/common/handlers/` | `GET /d/{d}/<kind>s` 列表 | `doList(diagram)` + `toView(view)` |
| `AbstractDeleteHandler<S>` | `inbound/rest/common/handlers/` | `DELETE /d/{d}/<kind>s/{name}` | `idPathKey()` + `doDelete(diagram, name)` |
| `AbstractDiagramHandlerTestCase` | `tests/.../inbound/rest/common/` | JUnit 3 setUp/tearDown + `pp()` / `ppWithId()` | `createDiagram(name, namespace)` |

**编码规则**：

1. **静态方法 vs 实例方法**：抽象基类提供**实例**方法。子类 service / handler 内部
   用 `private static final XxxOps INSTANCE = new XxxOps();` 共享实例，调用
   `((XxxOps) INSTANCE).findByName(d, name)`。**不要**在子类里
   把继承的实例方法再次声明为 `static`（会编译错误）。
2. **属性持久化（Description）**：通过 ArgoUML tagged value 机制
   (`ExtensionMechanismsFactory.createTaggedValue()`) 写入。当前 MDR
   build **不保证**回读，因此服务读取时为 `""`。
3. **私有构造器保留**：`AttributeOperations` / `OperationOperations` /
   `InterfaceOperations` / `RelationshipOperations` 保留 `private` 构造器 +
   静态 API，**不要**让它们继承 `AbstractDiagramElementOperations`——它们的
   `build` 签名是 `(ownerClass, name, type, visibility)`，与基类的
   `(diagram, name)` 不兼容。
4. **Pre-existing 编译问题（已修）**：`InitializeModel` 之前只存在于
   `argouml-core-model/tests/`，未被编入 jar，导致 `StandaloneHttpServer.java`
   （以及 20+ 测试文件）编译时找不到 `org.argouml.model.InitializeModel`。
   **修复方式**：把 `InitializeModel.java` 从 `tests/` 移到
   `src/org/argouml/model/`，去掉 `junit.framework.TestCase` 依赖，把
   `TestCase.fail(e)` 替换成 `throw new IllegalStateException(..., e)`，让
   它从测试 helper 变成 production class（与 `Model.java` 等同级）。
   `argouml-core-model/tests/.../TestXmi.java` 仍按 §3 跳过编译。
   `dont-run-the-tests/` 目录加了一个空占位符以满足 parent pom 中的
   `<testSourceDirectory>` 路径引用。
5. **新图类型成本**：仅需写 4-5 个文件（service + 4 个 domain ops +
   handler 注册），约 200 行代码。**禁止**复制已有 handler / ops / service
   的源码——必须继承抽象基类。
6. **测试**：所有 handler 测试必须继承 `AbstractDiagramHandlerTestCase`
   （提供 setUp / tearDown / `pp()` / `ppWithId()`），子类只需实现
   `createDiagram(name, namespace)` 一个方法。

**未来图类型（State/Sequence/Activity）实施模板**：

1. `domain.<kind>.<Element>Operations extends AbstractDiagramElementOperations<Object>`
   实现两个抽象方法（~30 行）
2. `application.<kind>.<Kind>DiagramService implements DiagramService` 用
   `AbstractDiagramServiceHelper.requireDiagram()` + `private static final XxxOps`
3. handler 全部继承 `AbstractListHandler` / `AbstractDeleteHandler`
4. `DiagramServices` 静态块加 `REG.register(...)`（1 行）
5. `InitHttpServerSubsystem` 注册路由（每端点 1 行）

**零重复代码**——预计每种新图类型 < 300 行实现 + < 200 行测试。

### 实体命名约定（Phase 2 锁定）

所有 per-kind 实体类名格式：`<Kind><Element>Entity`，
其中 `Kind = ModelKind.shortKind()`（Usecase / Sequence / 后续 Class / State / Activity）。

例：
- `UsecaseActorEntity`（旧名 `ActorEntity`，Phase 2 rename）
- `UsecaseUseCaseEntity`（旧名 `UseCaseEntity`，Phase 2 rename）
- `SequenceClassifierRoleEntity`（Phase 2 新增）
- `SequenceLifelineEntity`（Phase 2 新增）
- `SequenceMessageEntity`（Phase 2 新增）
- `SequenceDiagramEntity`（Phase 2 新增）

3 个 interface 不变：`Identified` / `ElementEntity` / `DiagramEntity`。
`kind()` 方法返回的 wire discriminator 不变（向后兼容）。

### 方法提取（Phase 2 锁定，syncCall only）

`POST /d/{d}/sequencediagram/messages` 在以下条件下会自动在
to-lifeline 关联的 class 上新增方法：

- `messageType == "syncCall"`
- `actionSignature` 非空（形如 `"cancelOrder(Long, String)"`）
- to-lifeline 的角色已绑定 class（无则按 role 名自动创建）

返回的 message entity 含 `methodUuid` 字段（已存在则返原 uuid，幂等）。

只支持 syncCall；asyncCall / reply / create / delete 跳过此路径。

调用链：`SequenceDiagramService.createMessage` →
`MethodOperations.addMethod` →
`Model.getCoreFactory().buildOperation2 + buildParameter`。

> **MDR 限制**：`findOrCreateClassForRole` 不绑定 ClassifierRole 到 MClass（`CollaborationsHelperMDRImpl.setBase` 对 ClassifierRole 抛 `IllegalArgumentException`，仅支持 `AssociationRole`/`AssociationEndRole`）。返回的 `baseUuid` 字段对 `createRole` 永远为 `""`，但 MClass 已通过名字查找可被后续 message 找到。EUML backend 切换后会改为真正的绑定。

### MDR / UML 1.4 后端的关键 quirk（Phase 2 发现）

在 `argouml-core-model-mdr` 默认 backend 下，UML 1.4 元类的实现与 UML 2.x 差异：

- **`Lifeline` 不是独立 metaclass**。`Model.getCollaborationsFactory().buildLifeline(collaboration)` 实际返回一个 `ClassifierRole`（带 multiplicity 1,1）；`buildClassifierRole(collaboration)` 也是 `ClassifierRole`（默认 multiplicity）。两者底层都是同一个 `MClassifierRole`。
- 业务层要区分 `/roles` 与 `/lifelines`，只能在 service 层用 uuid Set 显式追踪哪些元素是 `/roles` 创建、哪些是 `/lifelines` 创建（`SequenceDiagramService.ROLE_UUIDS_BY_DIAGRAM` / `LIFELINE_UUIDS_BY_DIAGRAM`）。`listRoles` 与 `listLifelines` 各过滤各自的 Set；`getRoleByName/ByUuid` / `getLifelineByName/ByUuid` 同理。
- 切到 EUML 后端时，这两套 Set 的过滤逻辑可以废弃，metaclass 自带区分（UML 2.x 有独立 `MLifeline`）。
- **UMLSequenceDiagram 构造器签名是 `(Object collaboration)`，不是 `(String name, Object ns)`**。`name` 要后调 `setName(...)`，包 try/catch `PropertyVetoException`。
- **`Model.getFacade().getUUID(ArgoDiagram)` 抛 IllegalArgumentException**。`diagramUuid` 用 namespace 的 UUID 代理。

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
| Add a new diagram type | UML 侧：新建 `uml/diagram/x/` 包 + `InitXxxDiagram` + 追加到 `Main.java:418-432`。**AI REST 侧：见上文"argouml-ai REST API"小节。** |
| Add a notation provider | new `notation/providers/uml/XxxNotationUml.java` + register in `InitNotationUml` |
| Add a standalone extension | new `modules/my-module/` (Ant build, deploy to `ext/`) |
| Add a JUnit 3 test | `tests/org/argouml/.../TestXxx.java` extending `TestCase`; `setUp()` **must** call `InitializeModel.initializeDefault()` |
| 新增 AI REST 端点 | new `inbound/rest/<kind>/handlers/<subarea>/MyHandler.java` 实现 `IRequestHandler` + 在 `InitHttpServerSubsystem.buildRouter()` 注册 + 加 JUnit 3 测试 |
| 新增 AI 图类型 | 按上文"argouml-ai REST API"小节中 5 步流程——修改 `ModelKind`、`DiagramOperations`、`DiagramServices`，加 3 个新子包 |

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
