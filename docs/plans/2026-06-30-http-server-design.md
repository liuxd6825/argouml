# ArgoUML HTTP Server — 设计文档

> 日期：2026-06-30
> 状态：已批准
> 范围：MVP（仅 classdiagram）

## 1. 背景与目标

ArgoUML 是一款 Java/Swing 桌面端 UML 建模工具。本特性在 ArgoUML 内部启动一个
loopback-only 的 HTTP Server，把现有的 classdiagram 增/改/删/查能力开放为 REST
API，让外部工具（脚本、CI、IDE 插件、未来 Web UI）能够不依赖 LLM 直接读写模型。

### 1.1 与现有 AI 模块的关系

`argouml-ai` 模块已是 ArgoUML 的扩展点。本特性挂在该模块下，复用现有
`OpExecutor` 的领域知识与 `ProjectSnapshot` 的 JSON 形状，但不引入 LLM：

| 组件 | 角色 | HTTP Server |
|---|---|---|
| `AiClient` (HTTP **客户端** → Python 边车 → LLM) | 已存在 | **不动** |
| `OpExecutor` (LLM tool_call → 模型层翻译) | 已存在 | **重构**：私有 `applyXxx` 改为委托新 `ClassDiagramService` |
| `ProjectSnapshot` (类图 → JSON) | 已存在 | **复用**：`SnapshotHandler` 直接调用 |
| `PlannedOp` / `PlannedOpParser` / `ClassDiagramTools` | AI 协议 | **不动** |
| 新增 HTTP **Server**（loopback 8766） | – | **核心新增** |

两条入站路径（AI 翻译层 / REST 翻译层）通过同一套 `application/classdiagram/*`
共享业务规则，行为一致性由架构强制。

### 1.2 核心决策

| 维度 | 决策 |
|---|---|
| 范围 | 仅 classdiagram（其余 5 类图留作后续 PR） |
| HTTP 技术栈 | NanoHTTPD（轻量、单 JAR、零传递依赖、与 Java 8 兼容） |
| 启动方式 | 应用启动时自动起；loopback only；端口 8766（默认）；设置页可改可关 |
| API 风格 | 纯 REST 资源（GET / POST / PUT / DELETE） |
| URL 寻址 | 多级 `/d/{diagram}/{elementType}/{id}` |
| 写实现 | 新增 `application/classdiagram/ClassDiagramService`；REST 与 `OpExecutor` 共用 |
| 分层 | Inbound → Application → Domain ← Infrastructure（Clean / Hexagonal） |
| 包约定 | 有多 kind 即必有 `common/`；全工程统一（domain / application / inbound.rest / inbound.ai） |
| 并发 | 全部走 Swing EDT；`EdtDispatcher.invokeAndWait` |
| 配置 | `~/.argouml/http-server.properties` |
| i18n | UI 字符串走 `org.argouml.i18n.Translator`；`.properties` 文件保持 ISO-8859-1 |
| 测试 | JUnit 3 only；测试包结构镜像生产 |
| 不预注册 stub | MVP 不注册 5 个其它图 service；`registry.register()` API 留给后续 |

### 1.3 非目标（YAGNI）

- 流式响应（SSE / chunked）—— MVP 只简单 request/response
- 鉴权 / TLS / 跨主机监听 —— 纯 loopback，无 token
- 多项目并发 —— 当前只服务单个打开的项目
- 持久化订阅（server-sent 事件 / WebSocket）—— REST only
- 集群 / 分布式 —— 单进程
- 时序图 / 用例图 / 活动图 / 状态图 / 部署图 —— 留 seams，不实现
- 复杂查询（图遍历、OCL）—— 简单 list / get by name 即可
- 单独写端口冲突的协调 —— 启动时如 8766 已占用则失败，提示用户改端口

## 2. 架构

### 2.1 四层 + 图类型子包

```
                       外部世界
  ┌──────────┐      ┌──────────┐      ┌──────────┐
  │  HTTP    │      │  Java AI │      │ (未来)   │
  │  Client  │      │  Planner │      │  Web UI  │
  └────┬─────┘      └────┬─────┘      └────┬─────┘
       │                 │                 │
       ▼                 ▼                 ▼
  ═══════════════════════════════════════════════
  ┃ Inbound Adapters                              ┃
  ┃   rest/handlers/  (按 kind 分包)              ┃
  ┃   ai/OpExecutor    (按 kind 分包)              ┃
  ══════════┬════════════════════════════════════
            ▼
  ┌─────────────────────────────────────────────┐
  │ Application Service Layer                     │
  │   application/common/DiagramServiceRegistry   │
  │     ├─ application/classdiagram/             │
  │     │     ClassDiagramService     [MVP 唯一] │
  │     └─ 未来: usecase, sequence, activity,     │
  │              state, deployment (按需注册)      │
  └─────────────┬─────────────────────────────────┘
                ▼
  ┌─────────────────────────────────────────────┐
  │ Domain Layer（每个 kind 一个 bounded context） │
  │   domain/common/   ModelKind, DiagramLocator  │
  │   domain/classdiagram/   [MVP 唯一实现]       │
  │     ClassOperations / InterfaceOperations /   │
  │     AttributeOperations / OperationOperations│
  │     RelationshipOperations                   │
  │   domain/{usecase,sequence,activity,...}      │
  │     [未来补]                                  │
  └─────────────────────────────────────────────┘
                ▼
  ┌─────────────────────────────────────────────┐
  │ Infrastructure（与 kind 无关）                 │
  │   model/ undo/ thread/ config/ http/ json/   │
  └─────────────────────────────────────────────┘
```

依赖方向：Inbound → Application → Domain ← Infrastructure；Domain 不依赖 Inbound/Infrastructure。

### 2.2 包结构总览

```
argouml-ai/src/org/argouml/ai/
├── InitHttpServerSubsystem.java    [新]
│
├── application/
│   ├── common/                     [新]
│   │   ├── DiagramService.java          (公共接口)
│   │   ├── DiagramServiceException.java (业务错误基类)
│   │   ├── DiagramServiceRegistry.java  (注册表：register/forKind)
│   │   └── UndoScope.java               (try-with-resources 包裹 UndoManager)
│   └── classdiagram/               [新, MVP 唯一]
│       └── ClassDiagramService.java
│
├── domain/
│   ├── common/                     [新]
│   │   ├── DiagramLocator.java          (name → ArgoDiagram)
│   │   └── ModelKind.java               (枚举：MVP 仅 CLASS)
│   └── classdiagram/               [新, MVP 唯一]
│       ├── AttributeOperations.java
│       ├── ClassOperations.java
│       ├── InterfaceOperations.java
│       ├── OperationOperations.java
│       └── RelationshipOperations.java
│
├── infrastructure/                 [新, 与 kind 无关]
│   ├── config/
│   │   ├── HttpServerSettingsTab.java   (设置 Tab)
│   │   ├── ServerConfig.java            (数据类)
│   │   └── ServerConfigStore.java       (读写 ~/.argouml/http-server.properties)
│   ├── http/
│   │   └── NanoHttpAdapter.java         (启停 NanoHTTPD, loopback 绑定)
│   ├── json/
│   │   ├── JsonBodyReader.java
│   │   ├── JsonError.java
│   │   ├── JsonMini.java                [移] 自 ops/JsonMini
│   │   └── JsonWriter.java
│   ├── model/
│   │   └── ModelGateway.java            (Model API 薄包装，便于 mock)
│   ├── thread/
│   │   └── EdtDispatcher.java           (HTTP 线程 → EDT 单向 dispatch)
│   └── undo/
│       └── UndoAdapter.java             (项目 UndoManager 包裹)
│
├── inbound/
│   ├── ai/
│   │   ├── common/                 [不动]
│   │   │   ├── AiBatchMemento.java
│   │   │   ├── PlannedOp.java
│   │   │   └── PlannedOpParser.java
│   │   └── classdiagram/           [改]
│   │       └── OpExecutor.java       (10 个 applyXxx 改委托 ClassDiagramService)
│   └── rest/                       [新]
│       ├── common/
│       │   ├── Dispatcher.java
│       │   ├── PathMatcher.java
│       │   ├── Router.java
│       │   └── handlers/common/
│       │       ├── GetDiagramHandler.java
│       │       ├── HealthHandler.java
│       │       ├── ListDiagramsHandler.java
│       │       └── SnapshotHandler.java
│       └── classdiagram/
│           ├── json/                  (若无专属 serializer 则复用 infra/json)
│           └── handlers/
│               ├── attribute/
│               │   ├── AddAttributeHandler.java
│               │   ├── DeleteAttributeHandler.java
│               │   ├── GetAttributeHandler.java
│               │   └── ListAttributesHandler.java
│               ├── operation/
│               │   ├── AddOperationHandler.java
│               │   ├── DeleteOperationHandler.java
│               │   ├── GetOperationHandler.java
│               │   └── ListOperationsHandler.java
│               ├── relationship/
│               │   ├── AddAssociationHandler.java
│               │   ├── AddDependencyHandler.java
│               │   ├── AddGeneralizationHandler.java
│               │   ├── DeleteRelationshipHandler.java
│               │   ├── ListAssociationsHandler.java
│               │   ├── ListDependenciesHandler.java
│               │   └── ListGeneralizationsHandler.java
│               ├── CreateClassHandler.java
│               ├── CreateInterfaceHandler.java
│               ├── DeleteClassHandler.java
│               ├── GetClassHandler.java
│               ├── ListClassesHandler.java
│               ├── UnsupportedHandler.java
│               └── UpdateClassHandler.java
│
├── tools/                           [不动]
│   ├── ClassDiagramTools.java
│   ├── ProjectSnapshot.java         (HTTP SnapshotHandler 复用)
│   └── ToolDefinition.java
│
├── agent/                           [不动]
├── ui/                              [不动] (HttpServerSettingsTab 在 infra.config)
├── InitAiSubsystem.java             [不动]
└── (args/applications/javadoc) —
```

### 2.3 与现有代码的边界

- **不动**：`tools/ClassDiagramTools`、`tools/ProjectSnapshot`、`agent/*`、`ui/*`、`InitAiSubsystem`、`PlannedOp`、`PlannedOpParser`、`AiBatchMemento`、`Main.java:418-432`（仅追加 1 行）、smoke test、AGENTS.md、根 POM。
- **破坏面**：仅 `org.argouml.ai.ops.JsonMini` 包路径改为 `org.argouml.ai.infrastructure.json.JsonMini`；编译器驱动所有 stale import 修复，无行为变化。

### 2.4 命名约定

| 用途 | 命名 | 例 |
|---|---|---|
| bounded context | `<diagram-kind>`（小写、单数） | `classdiagram`、`usecase` |
| 跨 kind 公共子包 | `common/` | `domain/common/` |
| 接口/适配命名 | `<Kind><Role>` PascalCase | `ClassDiagramService`、`UseCaseOperations` |
| 元素类型二级子包 | 单数 | `attribute/`、`operation/`、`relationship/` |
| handler 命名 | 动词 + 元素 + `Handler` | `AddAttributeHandler`、`DeleteClassHandler` |

## 3. 数据流

### 3.1 写请求旅程（POST /d/ClassDiagram1/classes）

```
HTTP 客户端                       ArgoUML (loopback:8766)
─────────                        ─────────────────────
POST /d/ClassDiagram1/classes
{ "name":"Order","x":220,"y":120,
  "stereotype":"entity" }
                                 1. NanoHTTPD accept（worker 线程）
                                 2. Dispatcher.handle(session)
                                    └─ EdtDispatcher.toEdt(() -> …)  // 移交 EDT
                                 3. Router.resolve("POST", "/d/ClassDiagram1/classes")
                                    → CreateClassHandler
                                 4. PathMatcher 提参 {d:"ClassDiagram1"}
                                 5. JsonBodyReader.read(session, CreateClassBody.class)
                                    ├─ Content-Type == application/json ?
                                    ├─ 反序列化 → CreateClassBody(name, x, y, stereotype)
                                    └─ 字段校验（name 非空、x/y 整数范围）
                                 6. registry.forKind(ModelKind.CLASS)
                                    → ClassDiagramService
                                 7. service.createClass("ClassDiagram1", body)
                                    ├─ DiagramLocator.find("ClassDiagram1") → ArgoDiagram
                                    │   └─ absent → DiagramNotFound → 404
                                    ├─ UndoScope.open("Create class Order")
                                    │   ├─ domain.ClassOperations.build(name, ns)
                                    │   ├─ gm.addNode(cls) → Fig 自动出现
                                    │   ├─ fig.setLocation(x, y)
                                    │   ├─ 如有 stereotype → addStereotype
                                    │   ├─ UndoAdapter.addMemento(...)
                                    │   └─ UndoScope.close() ← commit
                                    └─ Return ServiceResult<ClassHandle>
                                 8. JsonWriter.ok(result.handle())
                                    → 201 + { "ok":true, "data":{ "name":"Order", "id":"..." } }
                                 9. NanoHTTPD 写回客户端（EDT 同步完成）
```

### 3.2 读请求差异

GET 类请求同样走 EDT，但调用 `ClassDiagramService.query*`（不涉及 Undo）。
`SnapshotHandler` 直接调 `ProjectSnapshot.snapshot(diagram)` 复用现有逻辑。

### 3.3 线程模型

| 阶段 | 线程 | 备注 |
|---|---|---|
| HTTP accept → NanoHTTPD 解析 | NanoHTTPD worker | 短，不碰模型 |
| ED dispatch → Router + Handler → service → model | **Swing EDT** | `EdtDispatcher.toEdWait` |
| UndoManager 交互 | EDT | 项目 UndoManager 也是 EDT-安全 |
| 序列化响应 → NanoHTTPD 写回 | EDT 同步 | invokeAndWait 后返回 |

### 3.4 跨 kind 边缘情形

`POST /d/UseCaseDiagram1/usecases` →
Dispatcher 查图 `kind = USE_CASE` → `registry.forKind(USE_CASE)` 返回空 →
`UnsupportedHandler` → 501 + `{"ok":false,"error":{"code":"UNSUPPORTED_DIAGRAM_KIND",…}}`。

## 4. REST API Surface

### 4.1 通用响应壳

- Content-Type: `application/json; charset=utf-8`
- 成功：`{"ok":true,"data":<payload>}`，HTTP 状态按操作语义
- 失败：`{"ok":false,"error":{"code":"<UPPER_SNAKE>","message":"<人类可读>"}}`

### 4.2 通用端点（跨 kind）

| Method | Path | Handler | 用途 | 状态码 |
|---|---|---|---|---|
| GET | `/health` | `HealthHandler` | `{version,enabled,port,currentProject}` | 200 |
| GET | `/project/diagrams` | `ListDiagramsHandler` | 列所有图 | 200, 404 PROJECT_NOT_FOUND |
| GET | `/project/diagrams/{name}` | `GetDiagramHandler` | 单图元信息 | 200, 404 DIAGRAM_NOT_FOUND |
| GET | `/project/diagrams/{name}/snapshot` | `SnapshotHandler` | 复用 `ProjectSnapshot` | 200, 404, 501 |

### 4.3 classdiagram 端点（路径简写 `/d/...` = `/project/diagrams/...`）

#### Classes & Interfaces

| Method | Path | Handler | Body | 成功 | 错误码 |
|---|---|---|---|---|---|
| GET | `/d/{d}/classes` | ListClassesHandler | – | 200 | 404 DIAGRAM_NOT_FOUND, 501 |
| GET | `/d/{d}/classes/{c}` | GetClassHandler | – | 200 | 404 CLASS_NOT_FOUND, 501 |
| POST | `/d/{d}/classes` | CreateClassHandler | `{name, x, y, stereotype?, isAbstract?}` | 201 | 400 INVALID_*, 404, 409 DUPLICATE_CLASS, 501 |
| PUT | `/d/{d}/classes/{c}` | UpdateClassHandler | `{newName?, x?, y?, stereotype?, isAbstract?}` | 200 | 400, 404 CLASS_NOT_FOUND, 501 |
| DELETE | `/d/{d}/classes/{c}` | DeleteClassHandler | – | 204 | 404, 501 |
| POST | `/d/{d}/interfaces` | CreateInterfaceHandler | `{name, x, y, stereotype?}` | 201 | 400, 404, 409 DUPLICATE_INTERFACE, 501 |

#### Attributes

| Method | Path | Handler | Body | 成功 | 错误码 |
|---|---|---|---|---|---|
| GET | `/d/{d}/classes/{c}/attributes` | ListAttributesHandler | – | 200 | 404, 501 |
| GET | `/d/{d}/classes/{c}/attributes/{a}` | GetAttributeHandler | – | 200 | 404 ATTRIBUTE_NOT_FOUND, 501 |
| POST | `/d/{d}/classes/{c}/attributes` | AddAttributeHandler | `{name, type, visibility?}` | 201 | 400, 404, 501 |
| DELETE | `/d/{d}/classes/{c}/attributes/{a}` | DeleteAttributeHandler | – | 204 | 404, 501 |

#### Operations

| Method | Path | Handler | Body | 成功 | 错误码 |
|---|---|---|---|---|---|
| GET | `/d/{d}/classes/{c}/operations` | ListOperationsHandler | – | 200 | 404, 501 |
| GET | `/d/{d}/classes/{c}/operations/{op}` | GetOperationHandler | – | 200 | 404 OPERATION_NOT_FOUND, 501 |
| POST | `/d/{d}/classes/{c}/operations` | AddOperationHandler | `{name, returnType?, params?, visibility?}` | 201 | 400, 404, 501 |
| DELETE | `/d/{d}/classes/{c}/operations/{op}` | DeleteOperationHandler | – | 204 | 404, 501 |

#### Relationships

| Method | Path | Handler | Body | 成功 | 错误码 |
|---|---|---|---|---|---|
| GET | `/d/{d}/associations` | ListAssociationsHandler | – | 200 | 404, 501 |
| POST | `/d/{d}/associations` | AddAssociationHandler | `{classA, classB, labelA?, labelB?, multA?, multB?}` | 201 | 400, 404, 501 |
| GET | `/d/{d}/generalizations` | ListGeneralizationsHandler | – | 200 | 404, 501 |
| POST | `/d/{d}/generalizations` | AddGeneralizationHandler | `{subclass, superclass}` | 201 | 400, 404, 501 |
| GET | `/d/{d}/dependencies` | ListDependenciesHandler | – | 200 | 404, 501 |
| POST | `/d/{d}/dependencies` | AddDependencyHandler | `{client, supplier}` | 201 | 400, 404, 501 |
| DELETE | `/d/{d}/relationships/{id}` | DeleteRelationshipHandler | – | 204 | 404 RELATIONSHIP_NOT_FOUND, 501 |

### 4.4 请求体示例

```json
POST /d/ClassDiagram1/classes
{ "name": "Order", "x": 220, "y": 120,
  "stereotype": "entity", "isAbstract": false }

POST /d/ClassDiagram1/classes/Order/attributes
{ "name": "id", "type": "long", "visibility": "private" }

POST /d/ClassDiagram1/associations
{ "classA": "Customer", "classB": "Order",
  "multA": "1", "multB": "0..*",
  "labelA": "places", "labelB": "placedBy" }
```

### 4.5 响应体示例

```json
201 Created
{"ok":true,"data":{"name":"Order","id":"MDR-…"}}

404 Not Found
{"ok":false,"error":{"code":"DIAGRAM_NOT_FOUND",
  "message":"No diagram named ClassDiagram1 in current project"}}

400 Bad Request
{"ok":false,"error":{"code":"INVALID_NAME",
  "message":"Class name must match [A-Za-z_$][A-Za-z0-9_$]*"}}

501 Not Implemented
{"ok":false,"error":{"code":"UNSUPPORTED_DIAGRAM_KIND",
  "message":"UseCase diagram editing is not yet implemented"}}
```

### 4.6 错误码字典

| HTTP | code | 触发 |
|---|---|---|
| 200/201/204 | – | 成功 |
| 400 | `INVALID_NAME` / `INVALID_BODY` / `MISSING_FIELD` / `INVALID_COORD` / `INVALID_VISIBILITY` | 校验 |
| 404 | `PROJECT_NOT_FOUND` / `DIAGRAM_NOT_FOUND` / `CLASS_NOT_FOUND` / `INTERFACE_NOT_FOUND` / `ATTRIBUTE_NOT_FOUND` / `OPERATION_NOT_FOUND` / `RELATIONSHIP_NOT_FOUND` | 不存在 |
| 409 | `DUPLICATE_CLASS` / `DUPLICATE_NAME` / `RELATIONSHIP_EXISTS` | 命名/关系冲突 |
| 413 | `PAYLOAD_TOO_LARGE` | body > 1MB |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content-Type ≠ application/json |
| 500 | `INTERNAL_ERROR` | 未捕获异常 |
| 503 | `SERVER_SHUTTING_DOWN` | 关闭中 |
| 504 | `EDT_TIMEOUT` | EDT 调度 30s 未完成 |
| 501 | `UNSUPPORTED_DIAGRAM_KIND` / `UNSUPPORTED_OPERATION` | 非类图 / 未实现 |

## 5. 错误处理 / 并发 / 测试 / 配置

### 5.1 异常分层

```
DiagramServiceException（业务可预测错误 → 4xx）
 ├─ InvalidArgumentException         → 400
 ├─ NotFoundException                → 404
 ├─ DuplicateException               → 409
 └─ UnsupportedException             → 501
java.lang.RuntimeException / 其他    → 500
```

每个 `*Exception` 携带 `code`（UPPER_SNAKE）+ `message`（人类可读）。

### 5.2 Undo 失败语义

- `UndoScope` 用 try-with-resources；任何领域 op 抛异常即整体事务回滚。
- `UndoScope.close()` 默认 commit；显式 `markRollback()` 丢弃本次事务。
- 跨多次领域调用的批量操作（建类 + 加属性 + 建关联）作为一个事务。

### 5.3 HTTP 防御

- Handler 用 `try/catch (DiagramServiceException e)` 显式映射；`catch (Throwable t)` 兜底 500 + traceId。
- Content-Type 校验：非 `application/json` → 415。
- Body > 1MB → 413。
- 单请求超 30s → 504。

### 5.4 并发

```java
public class EdtDispatcher {
    public static <T> T toEdt(Callable<T> task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return task.call();
        FutureTask<T> ft = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(ft);
        return ft.get();   // 异常透传
    }
}
```

- HTTP worker 线程不可直接动 model；统一通过 `EdtDispatcher.toEdt(...)`。
- 读请求也走 EDT（`ProjectManager.getCurrentProject()` 非线程安全）。
- NanoHTTPD 默认最多 50 worker；EDT 串行化保证模型一致性。

### 5.5 配置

`~/.argouml/http-server.properties`：

```properties
http.enabled=true
http.port=8766
http.bind=127.0.0.1
http.timeoutSec=30
http.maxBodyBytes=1048576
```

- `InitHttpServerSubsystem.init()` 加载；启动前检查端口可用性。
- `HttpServerSettingsTab` 提供 UI 入口（端口、开关），修改后写回。
- 启动如 8766 已占用则启动失败并提示用户改端口（不自动选新端口 —— 可预测）。

### 5.6 测试策略（JUnit 3 only — AGENTS.md 第 9 条）

| 测试类 | 覆盖 | 关键 mock |
|---|---|---|
| `TestDiagramServiceRegistry` | register/forKind/未注册返回空 | – |
| `TestClassDiagramService` | 6 类 op；Undo 事务回滚；参数校验 | `InitializeModel.initializeDefault()` |
| `Test{Class,Interface,Attribute,Operation,Relationship}Operations` | 各领域方法正确性 + 错误抛 | 真实 model |
| `TestUndoAdapter` | startInteraction / addMemento 流程 | – |
| `TestEdtDispatcher` | 非 EDT 线程提交，执行在 EDT | `isEventDispatchThread()` 断言 |
| `TestServerConfigStore` | 读写 properties；缺省值 | tmp dir |
| `TestNanoHttpAdapter` | start/stop；loopback 端口绑定 | – |
| `TestRouter` / `TestPathMatcher` / `TestDispatcher` | 路径匹配；分发 | – |
| `TestHealthHandler` | 200 + payload shape | – |
| `Test{Verb}{Element}Handler` | 每个 handler 一个；mock `ClassDiagramService` | service fake |

测试包结构镜像生产（`tests/.../inbound/rest/common/handlers/common/...`、`classdiagram/handlers/attribute/...`）。

## 6. 改动清单

| 模块 / 路径 | 改动类型 | 说明 |
|---|---|---|
| `src/argouml-ai/pom.xml` | 改 | 加 NanoHTTPD Maven 依赖 |
| `argouml-ai/src/org/argouml/ai/InitHttpServerSubsystem.java` | 新 | 注册并启动 NanoHttpAdapter；注册 ClassDiagramService |
| `src/argouml-ai/src/org/argouml/ai/application/**` | 新 | 4 common + 1 classdiagram service |
| `src/argouml-ai/src/org/argouml/ai/domain/**` | 新 | 2 common + 5 classdiagram operations |
| `src/argouml-ai/src/org/argouml/ai/infrastructure/**` | 新 | model/undo/thread/config/http/json 6 类 ~11 文件 |
| `src/argouml-ai/src/org/argouml/ai/inbound/rest/**` | 新 | common + classdiagram 两组 handlers + json |
| `src/argouml-ai/src/org/argouml/ai/inbound/ai/classdiagram/OpExecutor.java` | 改 | 10 个 applyXxx 私有方法委托 ClassDiagramService；外部 apply() 签名不变 |
| `src/argouml-ai/src/org/argouml/ai/inbound/ai/common/JsonMini.java` | 移 | `org.argouml.ai.ops.JsonMini` → `org.argouml.ai.infrastructure.json.JsonMini` |
| `src/argouml-app/src/org/argouml/application/Main.java:432` | 改 | 追加 `SubsystemUtility.initSubsystem(new InitHttpServerSubsystem());` |
| `tests/argouml-ai/src/org/argouml/ai/**` | 新 | 测试镜像生产结构 ~30 文件 |

**估算**：~75 新文件 + 3 改动文件 + 1 依赖行。

## 7. 风险与缓解

| 风险 | 缓解 |
|---|---|
| `OpExecutor` 重构破坏现有 AI 测试 | 保持 `apply(List<PlannedOp>)` 公共签名不变；10 op 字段名/camelCase 不变；改私有方法委托；现有 `TestOpExecutor` 通过（只调换内部 mock） |
| `JsonMini` 包路径变化带来 import 错误 | 编译器一次性提示；grep 全工程统一改；CI 跑通即覆盖 |
| NanoHTTPD 与 Java 8 / MDR 共存 | 选 NanoHTTPD 2.x（核心 jar 兼容 JDK 8）；起停验证 |
| EDT 调度超时死锁 | `invokeAndWait` 设置 timeout（默认 30s）；超时转 504 |
| `ProjectManager.getCurrentProject()` 在无项目时返回 null | handler 显式判断 → 404 PROJECT_NOT_FOUND |
| 命名空间已存在同名类 | `DUPLICATE_CLASS` 409；操作前 `ClassOperations.findByName()` 预检 |
| Diagram name 含特殊字符（URL 编码） | URLDecoder 解码；含 `/` 时报 400 INVALID_NAME |
| 读端点被洗流量但不需要 mutation 锁 | 全部走 EDT；NanoHTTPD 默认最多 50 worker |
| 端口 8766 占用 | 启动失败 + 提示用户；不自动选新端口（可预测） |
| HTTP body 过大导致 OOM | NanoHTTPD 限制 + Handler 复检 body > 1MB → 413 |
| 并发写者争用 GEF UndoManager | EDT 串行化已避免；UndoManager 自身不假设线程安全 |

## 8. 后续路线（MVP 之外）

1. **其它图类型**：use case / sequence / activity / state / deployment 每个
   走同样三层（domain 子包 + service 实现 + registry 注册 + handlers 组）
2. **Bearer token 鉴权**：启动时随机写 `~/.argouml/http-token`，要求非 loopback 时强制
3. **事件订阅**：增加 `/project/diagrams/{d}/events` SSE 流，模型变更推送
4. **OCL / OCL-like 查询**：GET 参数支持 `?filter=...`
5. **Bulk 端点**：`POST /d/{d}/operations` body 是 List<Op>，一次性提交（与 AI 工具语义对齐）
6. **WebSocket**：替代 polling 的双向通道

---

设计完毕。下一步是 `writing-plans` skill 拆分实施计划。
