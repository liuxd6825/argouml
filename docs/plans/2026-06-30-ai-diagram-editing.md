# ArgoUML AI 助手 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a chat-driven AI assistant to ArgoUML that lets users describe class-diagram edits in natural Chinese and apply them through a preview-then-confirm flow.

**Architecture:** New Maven module `argouml-ai` (reverse-dependency pattern, like `argouml-diagrams-sequence`). Talks to a local Python sidecar via HTTP, which proxies to an OpenAI-compatible LLM using tool/function-calling. LLM output (`tool_calls` JSON) is parsed into `PlannedOp` records, shown in a preview table, and translated by `OpExecutor` into the existing `Model.getCoreFactory()` / `MutableGraphModel.addNode` API.

**Tech Stack:** Java 8, JUnit 3, JDK `HttpURLConnection` (no extra deps), Python 3.9+ with `fastapi` + `openai` (sidecar example).

**Encoding note:** All Java sources and `.properties` files in this repo are ISO-8859-1 (`<project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>` in every module POM). Do not introduce UTF-8 BOM. The design doc and plan (markdown) are UTF-8.

**Commit policy:** This repo's `AGENTS.md` forbids the agent from creating commits. Each task ends with a "verify" step (build/test) instead of `git commit`. The user manages git history.

---

## Task 1: Bootstrap `argouml-ai` Maven module

**Files:**
- Create: `src/argouml-ai/pom.xml`
- Create: `src/argouml-ai/META-INF/MANIFEST.MF`
- Create: `src/argouml-ai/.gitignore`
- Create: `src/argouml-ai/README.md`

**Step 1:** Create `src/argouml-ai/pom.xml`. Copy structure from `src/argouml-core-diagrams-sequence2/pom.xml` (same reverse-dependency shape: parent is `argouml-core`, artifactId is `argouml-ai`, depends on `argouml` via `argouml-model`). Keep `<project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>`, `Bundle-RequiredExecutionEnvironment: J2SE-1.5` in MANIFEST, OSGi metadata matches.

**Step 2:** Create `src/argouml-ai/META-INF/MANIFEST.MF` with `Bundle-SymbolicName: argouml-ai`, `Bundle-Version: ${pom.version}`, package exports under `org.argouml.ai.*`.

**Step 3:** Create `src/argouml-ai/.gitignore` containing just `target/`.

**Step 4:** Create `src/argouml-ai/README.md` explaining:
- How to install the module: `mvn -pl src/argouml-ai install -Dmaven.test.skip=true -o`
- How to start the sidecar: `pip install fastapi uvicorn openai && OPENAI_API_KEY=sk-xxx python src/argouml-ai/examples/sidecar.py`
- How to configure endpoint/apiKey in ArgoUML: 工具→设置→AI 助手

**Step 5:** Verify module compiles (no sources yet, just skeleton):
```bash
mvn -pl src/argouml-ai install -Dmaven.test.skip=true -o
```
Expected: `BUILD SUCCESS`, no errors.

---

## Task 2: Define `PlannedOp` value object

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/ops/PlannedOp.java`
- Create: `src/argouml-ai/tests/org/argouml/ai/ops/TestPlannedOp.java`

**Step 1:** Write the test in JUnit 3 style (`extends junit.framework.TestCase`).

```java
public class TestPlannedOp extends TestCase {
    public void testAddClassCarriesAllFields() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", "Order");
        op.setInt("x", 200);
        op.setInt("y", 100);
        assertEquals("Order", op.getString("name"));
        assertEquals(200, op.getInt("x"));
    }
}
```

**Step 2:** Run to verify it fails (no `PlannedOp` class yet):
```bash
mvn -pl src/argouml-ai test -Dtest=TestPlannedOp
```
Expected: compile error.

**Step 3:** Implement `PlannedOp` as an immutable record-like class:
- `public enum Type { ADD_CLASS, ADD_INTERFACE, ADD_ATTRIBUTE, ADD_OPERATION, ADD_ASSOCIATION, ADD_GENERALIZATION, ADD_DEPENDENCY, RENAME_CLASS, DELETE_CLASS, LIST_CLASSES }`
- Backing `Map<String, Object> fields` with typed accessors `getString/getInt/setString/setInt`.
- Constructor takes `Type`; all fields default-initialized.

**Step 4:** Run test:
```bash
mvn -pl src/argouml-ai test -Dtest=TestPlannedOp
```
Expected: PASS.

**Step 5:** Verify (no commit per AGENTS.md).

---

## Task 3: Implement `PlannedOpParser`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/ops/PlannedOpParser.java`
- Create: `src/argouml-ai/tests/org/argouml/ai/ops/TestPlannedOpParser.java`

**Step 1:** Test cases covering:
- Single `add_class` tool_call → one `PlannedOp(ADD_CLASS)`.
- Multiple `tool_calls` in one response → list in order.
- Bad JSON (`arguments` not a JSON object) → `IllegalArgumentException`.
- Unknown tool name → `IllegalArgumentException`.

Use simple manual JSON parsing (no Jackson dep; we control the schema and the LLM emits well-formed OpenAI responses). Implement a tiny `JsonMini` helper inside the parser that handles nested objects and string/int/boolean literals.

**Step 2:** Run test, expect compile fail.

**Step 3:** Implement `PlannedOpParser.parse(AiResponse response)` returning `List<PlannedOp>`. Switch on `toolCall.name` to choose `Type`; pull fields by name with `getString/getInt/getBoolean` from `JsonMini`.

**Step 4:** Run test, expect PASS.

**Step 5:** Verify.

---

## Task 4: Define `AiRequest` / `AiResponse` / `SidecarConfig`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/agent/SidecarConfig.java`
- Create: `src/argouml-ai/src/org/argouml/ai/agent/AiRequest.java`
- Create: `src/argouml-ai/src/org/argouml/ai/agent/AiResponse.java`
- Create: `src/argouml-ai/tests/org/argouml/ai/agent/TestSidecarConfig.java`

**Step 1:** Test `SidecarConfig`:
- Loads from `~/.argouml/ai-config.properties` if present.
- Falls back to `http://127.0.0.1:8765` / empty apiKey / `gpt-4o-mini` / 60s timeout when file missing.
- `save(Properties p)` round-trips.

**Step 2:** Run, expect compile fail.

**Step 3:** Implement:
- `SidecarConfig`: loads via `java.util.Properties` from `System.getProperty("user.home") + "/.argouml/ai-config.properties"`. Static `getInstance()` + `save()`.
- `AiRequest`: POJO with `model`, `messages` (List of `{role, content}`), `tools` (List of tool defs). `toJson()` uses `JsonMini`.
- `AiResponse`: POJO parsed from the OpenAI response. Field `List<ToolCall> toolCalls`; nested `ToolCall { String name; String arguments; }`. Parsed by `JsonMini`.

**Step 4:** Run, expect PASS.

**Step 5:** Verify.

---

## Task 5: Define `ClassDiagramTools`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/tools/ToolDefinition.java`
- Create: `src/argouml-ai/src/org/argouml/ai/tools/ClassDiagramTools.java`

**Step 1:** Implement `ToolDefinition`:
```java
public class ToolDefinition {
    private final String name;
    private final String description;       // Chinese
    private final Map<String, Object> parameters;  // JSON Schema
    public String toJson() { ... }
}
```

**Step 2:** Implement `ClassDiagramTools.all()` returning the 10 tool definitions from design §3.1, with Chinese descriptions. Use `JsonMini` to assemble the `parameters` schema object for each tool.

**Step 3:** Add a JUnit 3 smoke test `TestClassDiagramTools.testTenTools()` asserting `all().size() == 10` and that each `toJson()` is non-empty and contains `"name"` + `"parameters"`.

**Step 4:** Run, expect PASS.

**Step 5:** Verify.

---

## Task 6: Implement `ProjectSnapshot`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/tools/ProjectSnapshot.java`
- Create: `src/argouml-ai/tests/org/argouml/ai/tools/TestProjectSnapshot.java`

**Step 1:** Test: build a `UMLClassDiagram` with 2 classes (Customer, Order) and an association, then call `snapshot(diagram).toJson()` and assert it contains `"Customer"`, `"Order"`, and the multiplicity `"0..*"`.

**Step 2:** Run, expect compile fail.

**Step 3:** Implement `ProjectSnapshot.snapshot(ArgoDiagram diagram)`:
- Gets the graph model via `diagram.getGraphModel()`.
- For each `Object node` in `getNodes()`: if `Model.getFacade().isAClass(node)`, extract name/attrs/ops; if `isAInterface`, extract name/ops.
- For each edge in `getEdges()`: extract endpoints + multiplicities.
- Returns a `Snapshot` POJO whose `toJson()` produces the structure shown in design §3.4.

Use `org.argouml.kernel.Project`'s `findFigInDiagrams` is NOT needed; we work off the graph model directly.

**Step 4:** Run, expect PASS. Tests must `InitializeModel.initializeDefault()` in `setUp()` (project convention, AGENTS.md "Extension points").

**Step 5:** Verify.

---

## Task 7: Implement `OpExecutor` — `add_class` and `add_interface`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/ops/OpExecutor.java`
- Create: `src/argouml-ai/tests/org/argouml/ai/ops/TestOpExecutor.java`

**Step 1:** Test: `add_class` on a fresh `UMLClassDiagram` creates the MClass, attaches Fig, and Fig is at the AI-specified coords.

```java
public void testAddClassPlacesFig() {
    UMLClassDiagram d = new UMLClassDiagram();
    OpExecutor exec = new OpExecutor(d);
    PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
    op.setString("name", "Order");
    op.setInt("x", 200); op.setInt("y", 100);
    exec.apply(Collections.singletonList(op));
    Fig f = d.presentationFor(/* find created MClass */);
    assertEquals(200, f.getX());
    assertEquals(100, f.getY());
}
```

**Step 2:** Run, expect compile fail.

**Step 3:** Implement `OpExecutor`:
- Constructor takes the target `ArgoDiagram`.
- `apply(List<PlannedOp> ops)` wraps in `org.tigris.gef.base.Globals.curEditor().getUndoManager()` start/end pair.
- Inner `execAddClass`, `execAddInterface` use `Model.getCoreFactory().buildClass/buildInterface` then `MutableGraphModel.addNode` then `presentationFor(...).setLocation(x,y)`.
- `findClassByName(String)` walks `getNodes()` looking up by `Model.getFacade().getName`.

**Step 4:** Run, expect PASS.

**Step 5:** Verify.

---

## Task 8: Extend `OpExecutor` — attributes, operations, association

**Files:**
- Modify: `src/argouml-ai/src/org/argouml/ai/ops/OpExecutor.java`
- Modify: `src/argouml-ai/tests/org/argouml/ai/ops/TestOpExecutor.java`

**Step 1:** Add tests for:
- `add_attribute`: after `add_class("Order")` + `add_attribute(class_name="Order", name="id", type="int")`, the class has an attribute.
- `add_association`: Customer-Order association with multiplicities.
- `add_generalization`: child extends parent; graph model contains a generalization edge.

**Step 2:** Run, expect FAIL (not yet implemented).

**Step 3:** Implement `execAddAttribute`, `execAddOperation`, `execAddAssociation`, `execAddGeneralization`, `execAddDependency`, `execRenameClass`, `execDeleteClass`, `execListClasses`. Reference: `ActionAddAllClassesFromModel.java:96-107` shows `DiagramInterface.addClass(element, withBounds)` — we use the lower-level `MutableGraphModel.addNode/addEdge` for finer control.

**Step 4:** Run, expect PASS.

**Step 5:** Verify.

---

## Task 9: Implement `AiClient`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/agent/AiClient.java`
- Create: `src/argouml-ai/tests/org/argouml/ai/agent/TestAiClient.java`

**Step 1:** Test using `com.sun.net.httpserver.HttpServer` (JDK built-in) to spin up a local mock at `127.0.0.1:0`:
- Mock returns a canned OpenAI response with one `tool_call` (add_class).
- `AiClient.send(AiRequest)` returns parsed `AiResponse` with that tool call.

**Step 2:** Run, expect compile fail.

**Step 3:** Implement `AiClient`:
- `send(AiRequest req)` opens `HttpURLConnection` to `SidecarConfig.getInstance().getEndpoint() + "/v1/chat/completions"`.
- POST with `Content-Type: application/json`, body = `req.toJson()`.
- `Authorization: Bearer <apiKey>` if apiKey non-empty.
- Read response, parse with `JsonMini` (re-use or extract a `JsonMini.parseObject(String)` to `Map<String,Object>`).
- Returns `AiResponse`. Throws `IOException` on non-2xx.

**Step 4:** Run, expect PASS.

**Step 5:** Verify.

---

## Task 10: Build `AiPanel` (right-side tab content)

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/ui/AiPanel.java`
- Create: `src/argouml-ai/src/org/argouml/ai/ui/ChatPane.java`
- Create: `src/argouml-ai/src/org/argouml/ai/ui/PreviewPane.java`
- Create: `src/argouml-ai/src/org/argouml/ai/org/argouml/ai/ui/AiPanel.properties` (ISO-8859-1)

**Step 1:** Implement `AiPanel extends JPanel` with `BorderLayout`:
- North: `TopBar` (JLabel showing current diagram name + buttons "清空" / "设置").
- Center: `JSplitPane` (vertical) with `ChatPane` (top, read-only `JTextArea` in `JScrollPane`) and `PreviewPane` (bottom, `JTable`).
- South: `InputBar` (JTextField + "发送" / "取消" JButtons).

**Step 2:** Implement `ChatPane`:
- `appendUser(String)`, `appendAssistant(String)`, `appendSystem(String)`, `appendError(String)`, `clear()`.
- Stores history; supports Chinese UTF-8 text via `JTextArea.setLineWrap(true)` + monospace font (JDK 8 has decent CJK font fallback).

**Step 3:** Implement `PreviewPane`:
- `setOps(List<PlannedOp>)` populates a `DefaultTableModel` with columns: # / 操作 / 对象 / 参数摘要 / 状态.
- `getOps()` returns the current list for execution.
- `markApplied()` / `markFailed(int row, String reason)` updates status column.

**Step 4:** Wire send flow: pressing "发送" calls `AiClient.send(buildRequest(userText))`, parses via `PlannedOpParser`, sets PreviewPane. Pressing "应用" calls `new OpExecutor(currentDiagram).apply(previewPane.getOps())`.

**Step 5:** Add `AiPanel.properties` (ISO-8859-1) with keys `ai.panel.title`, `ai.button.send`, `ai.button.cancel`, `ai.button.apply`, etc. All Chinese values. Use `Translator.localize("ai.button.send")` in Java.

**Step 6:** Smoke test `TestAiPanel.testPanelConstructs()`:
```java
public void testPanelConstructs() {
    AiPanel p = new AiPanel();
    assertNotNull(p.getComponent(0));  // top bar
}
```
Run, expect PASS.

**Step 7:** Verify.

---

## Task 11: Build `AiSettingsTab`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/ui/AiSettingsTab.java`
- Create: `src/argouml-ai/src/org/argouml/ai/ui/AiSettingsTab.properties` (ISO-8859-1)

**Step 1:** `AiSettingsTab extends JPanel implements GUISettingsTabInterface`:
- Fields: `JTextField endpointField`, `JPasswordField apiKeyField`, `JTextField modelField`, `JSpinner timeoutSpinner`.
- `getSettingsTabs()` not relevant; instance method is `getName()` + ArgoUML's settings framework calls `getSettingsPanel()` and applies config.
- On "保存" click, write back to `SidecarConfig.save()`.

**Step 2:** Add `.properties` with labels.

**Step 3:** Test `TestAiSettingsTab.testLoadsConfig()` — instantiate, assert fields populated from `SidecarConfig.getInstance()`.

**Step 4:** Verify.

---

## Task 12: `InitAiSubsystem` + wire into Main

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/InitAiSubsystem.java`
- Modify: `src/argouml-app/pom.xml` (add `argouml-ai` compile dependency after `argouml-diagrams-sequence`)
- Modify: `src/argouml-app/src/org/argouml/application/Main.java` (add import + init call)

**Step 1:** Implement `InitAiSubsystem`:
```java
public class InitAiSubsystem implements InitSubsystem {
    public void init() { SidecarConfig.getInstance(); /* load config */ }
    public List<AbstractArgoJPanel> getDetailsTabs() { return List.of(new AiPanel()); }
    public List<GUISettingsTabInterface> getSettingsTabs() { return List.of(new AiSettingsTab()); }
}
```

**Step 2:** Modify `src/argouml-app/pom.xml`:
- Add import: `import org.argouml.ai.InitAiSubsystem;` at top of `Main.java` matching the existing import style.
- After `SubsystemUtility.initSubsystem(new InitUseCaseDiagram());` (line 429) add `SubsystemUtility.initSubsystem(new InitAiSubsystem());`.

**Step 3:** Add `<dependency>` block for `argouml-ai` in `src/argouml-app/pom.xml` after the `argouml-diagrams-sequence` block (around line 78-82). Mirror exact same `<scope>compile</scope>` and `${project.version}`.

**Step 4:** Test `TestInitAiSubsystem` smoke:
```java
public void testSubsystemProvidesTabs() {
    InitAiSubsystem s = new InitAiSubsystem();
    assertEquals(1, s.getDetailsTabs().size());
    assertEquals(1, s.getSettingsTabs().size());
}
```

**Step 5:** Build the reactor:
```bash
mvn -pl src/argouml-ai install -Dmaven.test.skip=true -o
mvn -pl src/argouml-app -am package
```
Expected: `BUILD SUCCESS` for both.

**Step 6:** Run argouml-app tests:
```bash
mvn -pl src/argouml-ai test
mvn -pl src/argouml-app test -Dtest=TestProject
```
Expected: PASS.

**Step 7:** Verify.

---

## Task 13: Sidecar example script

**Files:**
- Create: `src/argouml-ai/examples/sidecar.py`
- Create: `src/argouml-ai/examples/README.md`

**Step 1:** Write `sidecar.py` per design §7. Include:
- `import os` to read `OPENAI_API_KEY` (fallback if `ai.apiKey` not set).
- Optional `OPENAI_BASE_URL` env var (DeepSeek / Moonshot / Ollama-compatible endpoints).
- `uvicorn.run(app, host="127.0.0.1", port=8765)`.

**Step 2:** Write `examples/README.md`:
- Install: `pip install openai fastapi uvicorn`
- Run: `OPENAI_API_KEY=sk-xxx python sidecar.py`
- Verify: `curl -X POST http://127.0.0.1:8765/v1/chat/completions -H 'Content-Type: application/json' -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"hi"}]}'`

**Step 3:** Verify (no JVM, but lint syntax):
```bash
python -m py_compile src/argouml-ai/examples/sidecar.py
```
Expected: no errors.

**Step 4:** Document in `src/argouml-ai/README.md` (already created in Task 1) with link to `examples/README.md`.

---

## Task 14: End-to-end manual smoke test

**Files:** none (manual run)

**Step 1:** Start sidecar:
```bash
OPENAI_API_KEY=sk-xxx python src/argouml-ai/examples/sidecar.py
```

**Step 2:** Build and run ArgoUML using the existing `run-argouml.sh`:
```bash
mvn -pl src/argouml-ai install -Dmaven.test.skip=true -o
mvn -pl src/argouml-app -am package
bash src/argouml-app/src/bin/run-argouml.sh
```

**Step 3:** In ArgoUML:
- Create a new project.
- Open a class diagram.
- Click right-side "AI 助手" tab.
- In settings, confirm endpoint is `http://127.0.0.1:8765`.
- Type: "加一个 Customer 类，包含 id:int 属性" → press 发送.
- Expect: PreviewPane shows 1 op; chat shows assistant confirmation.
- Click 应用.
- Expect: FigCustomer appears on canvas at AI-chosen coords; Ctrl+Z removes it.

**Step 4:** Multi-op test:
- Type: "加 Order 类，含 date:Date 属性；Customer 和 Order 建 1 对多关联"
- Expect: 3 ops previewed; after 应用, all 3 applied atomically; one Ctrl+Z reverts all 3.

**Step 5:** Error path test:
- Stop sidecar. Try sending.
- Expect: chat shows red error "边车不可达"; no crash.

**Step 6:** Verify (no commit per AGENTS.md).

---

## Self-Review Checklist

Before declaring done, verify:
- [ ] All 10 tool definitions in `ClassDiagramTools` match design §3.1.
- [ ] `OpExecutor` covers all 10 op types.
- [ ] Undo: pressing Ctrl+Z after applying AI edits removes all of them as one transaction.
- [ ] `InitializeModel.initializeDefault()` is called in every model-touching test's `setUp()`.
- [ ] No UTF-8 BOM in any Java source or `.properties` file (use ISO-8859-1).
- [ ] No new third-party deps introduced (only JDK + JUnit 3 + Python stdlib + `openai` + `fastapi` + `uvicorn` for the example sidecar).
- [ ] `mvn -pl src/argouml-ai install -Dmaven.test.skip=true -o && mvn -pl src/argouml-app -am package` succeeds.
- [ ] `mvn -pl src/argouml-ai test` passes.
- [ ] Manual smoke (Task 14) passes all 3 scenarios.
