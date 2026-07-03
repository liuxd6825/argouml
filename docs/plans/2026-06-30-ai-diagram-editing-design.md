# ArgoUML AI 助手 — 设计文档

> 日期：2026-06-30
> 状态：已批准
> 范围：MVP（仅类图）

## 1. 背景与目标

ArgoUML 是一款 Java/Swing 桌面端 UML 建模工具。本特性通过 AI 助手，让用户用自然语言描述需求，由 LLM 自动生成对类图（后续将扩展到时序图、用例图）的修改，并通过预览 + 一键应用的流程落地修改。

### 1.1 核心决策

| 维度 | 决策 |
|---|---|
| 功能 | 对话驱动编辑（CRUD 全量） |
| 提交方式 | 预览 + 一键应用 |
| LLM 接入 | 本地 Python 边车 + OpenAI 兼容 HTTP |
| 协议 | OpenAI Chat Completions + Tools/Function Calling |
| UI | 右侧详情 Tab（沿用 `DetailsPane`） |
| MVP 范围 | 仅类图；时序/用例后续 |
| 布局 | AI 指定 x/y 坐标 |
| 提示词语言 | 中文 |
| 交付物 | Java 模块 + Python 边车示例脚本 |

### 1.2 非目标（YAGNI）

- 流式输出（OpenAI stream 模式）
- 多用户 / 协作
- LLM 选型 UI
- 多语言切换（先中文）
- 自动布局算法（AI 选坐标，不够好再让 Java 端补刀）
- token 计数 / 费用统计
- 时序图、用例图（MVP 后补）

## 2. 架构

### 2.1 新模块

新增 Maven 模块 `src/argouml-ai/`，按 `argouml-app/pom.xml` 中 `argouml-diagrams-sequence` 的"反向依赖 argouml-app"模式构建：模块从 root reactor 排除（避免 `ProjectCycleException`），但作为 compile 依赖声明在 `argouml-app/pom.xml` 里。

#### 2.1.1 目录结构

```
src/argouml-ai/
├── pom.xml
├── META-INF/MANIFEST.MF
├── src/org/argouml/ai/
│   ├── InitAiSubsystem.java          # implements InitSubsystem
│   ├── agent/
│   │   ├── AiClient.java             # HttpURLConnection → 边车
│   │   ├── AiRequest.java
│   │   ├── AiResponse.java
│   │   └── SidecarConfig.java        # endpoint/apiKey/model
│   ├── tools/
│   │   ├── ToolDefinition.java
│   │   ├── ClassDiagramTools.java    # 10 个类图工具的 JSON Schema
│   │   └── ProjectSnapshot.java      # 当前图状态→JSON 进 system prompt
│   ├── ops/
│   │   ├── PlannedOp.java
│   │   ├── PlannedOpParser.java
│   │   └── OpExecutor.java
│   └── ui/
│       ├── AiPanel.java
│       ├── ChatPane.java
│       ├── PreviewPane.java
│       └── AiSettingsTab.java
└── examples/
    └── sidecar.py                    # fastapi + openai SDK 示例
```

#### 2.1.2 Maven 集成

- **不**在根 `pom.xml` 的 `<modules>` 块中加 `src/argouml-ai`。
- 在 `src/argouml-app/pom.xml` 现有 `argouml-diagrams-sequence` 依赖后追加：
  ```xml
  <dependency>
    <groupId>org.argouml</groupId>
    <artifactId>argouml-ai</artifactId>
    <version>${project.version}</version>
    <scope>compile</scope>
  </dependency>
  ```

构建命令：
```bash
mvn -pl src/argouml-ai install -Dmaven.test.skip=true -o
mvn -pl src/argouml-app -am package
```

### 2.2 数据流

```
用户输入 ─► ChatPane
                │
                ▼
        AiPanel 收到消息
                │
                ├─► ProjectSnapshot 拍下当前类图状态（JSON）
                │
                ▼
        AiClient POST /v1/chat/completions  ──►  Python 边车
                                                       │
                                                       ▼
                                                  OpenAI LLM
                                                       │
                                                       ▼
                                          tool_calls (JSON)
                ◄────────────────────────────────────┘
                │
                ▼
        PlannedOpParser 转成 PlannedOp 列表
                │
                ▼
        PreviewPane 渲染预览
                │
                ▼ 用户点"应用"
                │
        OpExecutor 调用 Model.getCoreFactory() + FigNodeModelElement
                │
                ▼
        UndoManager 记录
```

## 3. 核心机制：AI 如何改图

LLM 不直接调 Java 方法。整个链路上有唯一的翻译层 `OpExecutor` 负责把 tool_calls 翻译成 ArgoUML 的 API 调用。

### 3.1 LLM 工具集（MVP 类图）

OpenAI Tools 协议，`tools` 字段定义如下 JSON Schema：

| 工具名 | 用途 | 关键参数 |
|---|---|---|
| `list_classes` | 查询当前图已有哪些类/接口 | `diagram_id?` |
| `add_class` | 新建一个类并放到画布 | `name, x, y, stereotype?, isAbstract?` |
| `add_interface` | 新建接口 | `name, x, y, stereotype?` |
| `add_attribute` | 给已有类加属性 | `className, name, type, visibility?` |
| `add_operation` | 给已有类加方法 | `className, name, returnType?, visibility?` |
| `add_association` | 两类之间建关联 | `classA, classB, labelA?, labelB?, multA?, multB?` |
| `add_generalization` | 泛化（继承） | `subclass, superclass` |
| `add_dependency` | 依赖 | `client, supplier` |
| `rename_class` | 改名字 | `oldName, newName` |
| `delete_class` | 删一个类（含其图节点） | `name` |

LLM 决定调用哪个、调用多少次；OpenAI 协议支持多轮 function-calling（LLM 可先 `list_classes` 看现状，再 `add_*`）。

### 3.2 翻译表：tool_call → ArgoUML API

`OpExecutor.exec(PlannedOp op)` 是唯一能碰 `Model` / `Fig` 的代码。

```java
void execAddClass(PlannedOp op) {
    String name = requireName(op);
    Object ns = currentDiagram.getNamespace();
    CoreFactory cf = Model.getCoreFactory();
    Object cls = cf.buildClass(name, ns);
    // 可选：stereotype
    if (op.getString("stereotype") != null) {
        Object stereo = Model.getExtensionMechanismsFactory()
                             .buildStereotype(op.getString("stereotype"), ns);
        Model.getCoreHelper().addStereotype(cls, stereo);
    }
    // 可选：isAbstract（int 0/1，JsonMini.getInt 读出 0 表示 false）
    if (op.getInt("isAbstract") == 1) {
        Model.getCoreHelper().setAbstract(cls, true);
    }
    MutableGraphModel gm = (MutableGraphModel) currentDiagram.getGraphModel();
    gm.addNode(cls);
    Fig fig = (Fig) currentDiagram.presentationFor(cls);
    fig.setLocation(op.getInt("x"), op.getInt("y"));
}

void execAddAssociation(PlannedOp op) {
    Object c1 = findClassByName(op.getString("classA"));
    Object c2 = findClassByName(op.getString("classB"));
    Object assoc = Model.getCoreFactory().buildAssociation(c1, c2);
    Object[] ends = Model.getFacade().getConnections(assoc).toArray();
    if (op.getString("labelA") != null) {
        Model.getCoreHelper().setName(ends[0], op.getString("labelA"));
    }
    if (op.getString("labelB") != null) {
        Model.getCoreHelper().setName(ends[1], op.getString("labelB"));
    }
    if (op.getString("multA") != null) {
        Model.getCoreHelper().setMultiplicity(ends[0], op.getString("multA"));
    }
    if (op.getString("multB") != null) {
        Model.getCoreHelper().setMultiplicity(ends[1], op.getString("multB"));
    }
    MutableGraphModel gm = (MutableGraphModel) currentDiagram.getGraphModel();
    gm.addEdge(assoc);
}

void execAddGeneralization(PlannedOp op) {
    Object child  = findClassByName(op.getString("subclass"));
    Object parent = findClassByName(op.getString("superclass"));
    Model.getCoreFactory().buildGeneralization(child, parent);
}
```

### 3.3 AI 实际调用的 ArgoUML 方法清单

| 类别 | 方法 | 来源 |
|---|---|---|
| 建模 | `Model.getCoreFactory().buildClass/buildInterface/buildAssociation/buildGeneralization/buildDependency/buildAttribute/buildOperation` | `argouml-core-model/.../CoreFactory.java` |
| 改属性 | `Model.getCoreHelper().setMultiplicity/setName/setType` | `argouml-core-model/.../CoreHelper.java` |
| 挂画布 | `((MutableGraphModel) diagram.getGraphModel()).addNode/addEdge` | `MutableGraphModel`（GEF）、`UMLMutableGraphSupport` |
| 拿 Fig 并定位 | `diagram.presentationFor(modelElement).setLocation(x,y)` | GEF `Fig` |
| 撤销 | `org.tigris.gef.base.Globals.curEditor().getUndoManager()` | GEF `UndoManager` |

### 3.4 Context：当前图状态喂给 LLM

`ProjectSnapshot` 在每次 LLM 调用前：

1. 取当前类图（`ProjectManager.getManager().getCurrentProject().getActiveDiagram()`）。
2. 遍历 graph model 的 `getNodes()`，对每个 model element 调 `Model.getFacade().getName/isAClass/getAttributes/getOperations`。
3. 序列化成紧凑 JSON 进 system prompt：

```json
{
  "diagram": {"id":"d1","type":"Class","namespace":"MyModel"},
  "classes": [
    {"name":"Customer","attrs":["id:int","name:String"],"ops":["save():void"]},
    {"name":"Order","attrs":["id:int","date:Date"],"ops":[]}
  ],
  "associations": [{"a":"Customer","b":"Order","multA":"1","multB":"0..*"}]
}
```

### 3.5 设计 ↔ 实现 差异记录

| 字段 | 设计文档 | 实际实现 | 原因 |
|---|---|---|---|
| `class_name` | snake_case | `className` (camelCase) | 与 `PlannedOpParser` (Task 3) 的字段读取保持一致；`ClassDiagramTools` 的 JSON Schema 也用 camelCase |
| `class_a` / `class_b` | snake_case | `classA` / `classB` | 同上 |
| `multiplicity_a` / `multiplicity_b` | snake_case | `multA` / `multB` | 同上 |
| `return_type` | snake_case | `returnType` | 同上 |
| `old_name` / `new_name` | snake_case | `oldName` / `newName` | 同上 |
| `child` / `parent` | snake_case | `subclass` / `superclass` | 同上 |
| `params` | 在 add_operation schema | **未实现** | MVP YAGNI；parser 不读，executor 不用 |
| `diagram_id` | 在 list_classes schema | **未实现** | MVP YAGNI；list_classes 当前是无参 no-op |
| `add_class` 的 `fig.setSize` | 设计伪码里有 | **未调用** | GEF Fig 没有 `getMinimumWidth/Height` 方法；用 renderer 默认大小 |
| `addStereotype` | 设计写 `ExtensionMechanismsHelper` | 实现在 `CoreHelper` | 实际 API 在 `CoreHelper.java:1337` |
| 撤销机制 | `Globals.curEditor().getUndoManager()` | `org.tigris.gef.undo.UndoManager.getInstance().startChain()` + `addMemento(AiBatchMemento)` | 跟 ArgoUML 现有 `ModeAddToDiagram` 一致；`DiagramUndoManager` 自动转发到项目 `UndoManager` |

测试 `ClassDiagramToolsTest.testToolNamesMatchParserType` 锁住了工具名与 parser 字段名的一致性。任何未来修改必须同步更新 `ClassDiagramTools`、`PlannedOpParser`、`OpExecutor` 三处。

## 4. UI 设计

### 4.1 面板注册

`InitAiSubsystem` 沿用现有 `InitSubsystem` SPI：

```java
public class InitAiSubsystem implements InitSubsystem {
    public List<AbstractArgoJPanel> getDetailsTabs() {
        return List.of(new AiPanel());
    }
    public List<GUISettingsTabInterface> getSettingsTabs() {
        return List.of(new AiSettingsTab());
    }
    public void init() {}
}
```

并在 `src/argouml-app/src/org/argouml/application/Main.java:418-432` 的 `initializeSubsystems` 链尾追加：

```java
SubsystemUtility.initSubsystem(new InitAiSubsystem());
```

### 4.2 AiPanel 布局

```
┌─ AiPanel (JPanel, BorderLayout) ─────────────────────────┐
│ ┌─── TopBar ─────────────────────────────────────────┐   │
│ │ [对话] [预览 3]  ⓘ  ⚙  🗑清空   当前图:ClassDiagram1 │   │
│ └────────────────────────────────────────────────────┘   │
│                                                            │
│ ┌─── ChatPane (JScrollPane + JTextArea, 只读) ──────┐    │
│ │  [系统] 你是 ArgoUML 助手，可调用以下工具……          │    │
│ │  [用户] 加一个 Order 类                              │    │
│ │  [助手] 好的，我先查一下当前类。                      │    │
│ │        [工具] list_classes → 返: [Customer]           │    │
│ │  [助手] 我会做这些修改：                              │    │
│ └────────────────────────────────────────────────────┘    │
│                                                            │
│ ┌─── PreviewPane (JTable, 行=操作) ────────────────┐     │
│ │ # │ 操作    │ 对象          │ 参数摘要      │ 状态  │     │
│ │ 1 │ 新建类  │ Order         │ x=200,y=100   │ 待应  │     │
│ │ 2 │ 加属性  │ Order.id:int  │               │ 待应  │     │
│ │ 3 │ 建关联  │ Customer-Order│ 1 ↔ 0..*     │ 待应  │     │
│ └────────────────────────────────────────────────────┘    │
│                                                            │
│ ┌─── InputBar (JTextField + 发送按钮) ─────────────┐     │
│ │ [输入需求___________________________] [发送] [取消]│     │
│ └────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────┘
```

### 4.3 关键交互

| 用户动作 | 系统反应 |
|---|---|
| 输入需求点发送 | `ChatPane` 追加用户消息；调 `AiClient.send(userText)`；UI 显示"思考中…" |
| 收到 tool_calls | `PlannedOpParser` 解析；填到 `PreviewPane`；聊天区显示助手文字 + 表格 |
| 用户点"应用" | `OpExecutor.apply(ops)` 整体包 Undo 事务；成功后续追加"✅ 已应用 3 项" |
| 用户点"取消" | 表格清空，聊天区可继续 |
| 用户点"⚙" | 跳到设置页（endpoint / apiKey / modelName） |
| 用户切图 | 不自动重发；下次发送时 `ProjectSnapshot` 重拍新图状态 |

### 4.4 i18n

按项目惯例（AGENTS.md "Coding conventions"）：

- 所有 UI 字符串走 `org.argouml.i18n.Translator.localize("ai.button.send")`
- `AiPanel.properties` 等 properties 文件保持 ISO-8859-1
- key 命名：`ai.<component>.<element>`，例如 `ai.panel.title`、`ai.button.send`、`ai.preview.column.status`

## 5. 错误处理

| 错误源 | 检测点 | 处理 |
|---|---|---|
| 边车连不上（refused/timeout） | `AiClient` 捕获 `IOException` | 聊天区红字"边车不可达，检查 127.0.0.1:8765 与 API Key"；不阻塞 UI |
| LLM 4xx（key 错、模型名错） | `AiClient` 读 response code | 弹出 `ExceptionDialog`；提示用户检查设置 |
| LLM 5xx/网络抖 | `AiClient` 捕获 | 聊天区"LLM 服务异常：{message}，请重试"；按钮可点"重发" |
| LLM 返回非 JSON | `PlannedOpParser` 抓 `JsonParseException` | 聊天区"模型返回格式异常，原文：…"；不进入预览 |
| LLM 引用不存在的类 | `OpExecutor.exec*` 抛 `ClassNotFoundException` | 该行标红"❌ 类 X 不存在"，其他行继续；用户可点"重试"让 LLM 再调 `list_classes` |
| 坐标越界/重叠 | 暂不校验（YAGNI） | GEF 自己处理；后续可加 layout hint |
| 并发改图 | `OpExecutor` 前后拿 `Project` 快照比对 | 不一致就提示"图在 AI 处理过程中被修改过，请重发请求" |
| Undo 链断裂 | `OpExecutor` 在 try/finally 包事务 | 失败也保证 `end()` 被调 |

## 6. 配置存储

API Key 等敏感信息存于本地用户配置目录：

- 路径：`~/.argouml/ai-config.properties`
- 格式（明文，本地工具）：
  ```properties
  ai.endpoint=http://127.0.0.1:8765
  ai.apiKey=sk-xxx
  ai.model=gpt-4o-mini
  ai.timeoutSec=60
  ```
- `SidecarConfig` 在 `InitAiSubsystem.init()` 时加载；用户可在 `AiSettingsTab` 中修改并回写。
- 不走 Java KeyStore（重，YAGNI）。

## 7. 边车示例脚本

`src/argouml-ai/examples/sidecar.py`：fastapi + openai SDK 实现的最小边车。

```python
# 启动：OPENAI_API_KEY=sk-xxx python sidecar.py
# 端点：POST http://127.0.0.1:8765/v1/chat/completions  （OpenAI 兼容）
from fastapi import FastAPI
from openai import OpenAI

app = FastAPI()
client = OpenAI()  # 读环境变量 OPENAI_API_KEY / OPENAI_BASE_URL

@app.post("/v1/chat/completions")
async def chat(req: dict):
    return client.chat.completions.create(**req).model_dump()
```

依赖：`pip install openai fastapi uvicorn`。README 里写明如何启动。

## 8. 测试策略

按 AGENTS.md 第 9 条，JUnit 3 only；不测 LLM 真实调用，只测翻译层。

| 测试类 | 测什么 | 关键 mock |
|---|---|---|
| `TestPlannedOpParser` | tool_call JSON → `PlannedOp` 列表；坏 JSON 抛异常 | 直接构造 JSON 字符串 |
| `TestOpExecutor` | 每个 op 类型都跑通真正的 `Model.getCoreFactory()` | `InitializeModel.initializeDefault()` 拿真 model（项目惯例，参考 `TestProject`） |
| `TestProjectSnapshot` | 构造 `UMLClassDiagram` 含 2 类，验 snapshot JSON 字段 | 真 model + 假图 |
| `TestAiClient` | HTTP 层用 `HttpURLConnection`；用 `LocalServerSocket` 起本地 mock 验 request/response | 不走真实边车 |
| `TestAiPanel` | Swing 烟雾测试：`new AiPanel()` 不抛；按钮可达 | JUnit 3 |

`InitializeModel.initializeDefault()` 是项目里所有 model 测试的标配，不破坏现状。

## 9. 改动清单

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `src/argouml-ai/**` | 新增 | 整个新模块 |
| `src/argouml-ai/examples/sidecar.py` | 新增 | 边车示例脚本 |
| `src/argouml-ai/README.md` | 新增 | 模块说明（如何启动边车、如何配置） |
| `src/argouml-app/pom.xml` | 修改 | 在 `argouml-diagrams-sequence` 后追加 `argouml-ai` compile 依赖 |
| `src/argouml-app/src/org/argouml/application/Main.java` | 修改 | `Main.java:418-432` 链尾追加 `new InitAiSubsystem()` |
| 根 `pom.xml` | **不**改 | 不把 `src/argouml-ai` 加进 `<modules>`（反向依赖模式） |

## 10. 风险与缓解

| 风险 | 缓解 |
|---|---|
| Maven 反向依赖模式在多模块下容易踩坑 | 严格按 `argouml-diagrams-sequence` 模板；CI 在 PR 上跑 `mvn -pl src/argouml-ai install -Dmaven.test.skip=true -o && mvn -pl src/argouml-app -am package` |
| LLM 幻觉类名/字段名 | 强制 LLM 先 `list_classes` 查现状；`OpExecutor` 引用不存在的类时标红而不抛 |
| Undo 链断裂 | `try/finally` 保证 `end()` 被调；Op 列表应用失败时整体回滚 |
| Java 8 兼容性 | 不引第三方 HTTP 库；用 JDK 自带 `HttpURLConnection` |
| ISO-8859-1 编码 | properties 文件保持 ISO-8859-1；Java 源文件保持 ISO-8859-1 |

## 11. 后续路线（MVP 之外）

1. 时序图：`tools/SequenceDiagramTools.java`、把 `MutableGraphModel.addNode/addEdge` 适配到 `UMLCollaborationDiagram` / `sequence2` 模块。
2. 用例图：`tools/UseCaseDiagramTools.java`、适配 `UMLUseCaseDiagram` + `UseCasesFactory`。
3. 流式输出：HTTP 切换 SSE；UI 用 `JTextArea.append` 流式追加。
4. 多个边车预设：设置页加 dropdown（OpenAI / DeepSeek / Moonshot / Ollama）。
5. 批量操作优化：一次请求里允许 AI 输出大量 ops；OpExecutor 内部合并同类。
