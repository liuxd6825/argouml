# UseCase → 多图关联 升级 — 设计

**日期**：2026-07-09
**状态**：design（待 implementation）

## 背景

`UseCase.representedDiagram` 当前支持 1:1 关联。已有实现：

- `RepresentedDiagramLinkCache`（argouml-core-model）：`Map<Object, String>` 单 UUID
- `UseCaseRepresentedDiagramField`（argouml-core-umlpropertypanels）：单文本框 + 平铺 JList 浏览
- `ActionNavigateRepresentedDiagram`（argouml-app）：单跳转 action，extends `AbstractActionNavigate`
- `UseCaseContextPopupFactory`（argouml-app）：单静态 action
- `UseCaseOperations`（argouml-ai）：单 tagged value 读写
- `UseCaseDiagramService`（argouml-ai）：单 REST 端点 `PUT/GET /representedDiagram`

## 目标

升级到 1:N 关联，并提供：

1. **一个元素关联多个图**（共享 tag，多 value 数据）
2. **右键子菜单**"关联图"，包含：
   - "关联管理"（打开包树对话框，支持多选 + Add / Remove）
   - 当前关联图列表（点击跳转）
3. **元素视觉符号** ∞（右上角），关联数 > 0 时显示

## 设计决策（已与用户确认）

| 决策 | 选择 |
|---|---|
| 存储策略 | A. 复用现有 tag，多 value 存（`dataValues: String[]`） |
| 属性面板 | WinForms 风格表格（UUID 隐藏 / 名称 / 路径 / 移除按钮）+ [Manage...] |
| ∞ 位置 | FigUseCase 右上角 |
| 子菜单出现 | 总是出现（即使 0 关联） |
| 关联管理对话框 | 多选 + Add / Remove / Close |
| REST API | 同步升级（List<String> 入出 + 单条 POST/DELETE 端点） |

## 架构

### 数据流

```
用户操作                          后端
────────────────────────────────────────────────────────────
属性面板表格行 Remove  ──→  UseCaseOperations.removeRepresentedDiagram
右键 → 关联管理 → Add     ──→  UseCaseOperations.addRepresentedDiagram
                                  ↓
                            RepresentedDiagramLinkCache.add/remove (权威)
                                  ↓ best-effort
                            Model tagged value (dataValues[])
                                  ↑
右键 → 跳转到 X          ──  ActionJumpToRepresentedDiagram(uuid)
   ← ProjectActions.jumpToDiagramShowing  ──  read from cache
属性面板 setTarget      ──  read from cache, refresh 表格
FigUseCase 渲染          ──  cache.size() > 0 → 显示 ∞
```

### 子菜单动态生成

```
右键 UseCase
    ↓
UseCaseContextPopupFactory.createContextPopupActions(useCase)
    ↓
ArgoJMenu("menu.popup.related-diagrams")
    ├── ActionManageRepresentedDiagrams(useCase)  ← 永远存在
    ├── JSeparator                                  ← 仅当有 ≥1 关联时
    ├── ActionJumpToRepresentedDiagram(useCase, d1)  ← 动态生成, 一图一项
    ├── ActionJumpToRepresentedDiagram(useCase, d2)
    └── ...
```

### 包树对话框结构

```
UseCaseManageRepresentedDiagramsDialog (JDialog, OK_CANCEL_OPTION)
├── JTree (DisplayTextTree + UMLTreeCellRenderer)
│   └── Project
│       ├── Package A
│       │   ├── Diagram A1   ← 可多选
│       │   ├── Diagram A2
│       │   └── Sub-package A.1
│       │       └── Diagram A1.1
│       └── Package B
│           └── Diagram B1
├── [Add]    ← 把 JTree 选中项加到 UseCase 关联
├── [Remove] ← 把关联里的选中 UUID 移除
└── [Close]
```

### 视觉符号渲染

`FigUseCase.initialize()` 在 `addFig(getStereotypeFig())` 之后追加：

```
FigText(∞)
├── 默认 setVisible(false)
├── setEditable(false), setLineWidth(0), setFilled(false)
├── 位置 (bigPort.x + bigPort.width - 12, bigPort.y - 6)
└── 监听 "representedDiagram" tag → 数量变更时 setVisible(count > 0)
```

## 组件清单（17 个文件）

### 数据层（4 文件改）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `argouml-core-model/.../model/RepresentedDiagramLinkCache.java` | value `String` → `List<String>`；加 `addUuid/removeUuid/getAll/clear` |
| 2 | `argouml-ai/.../domain/usecasediagram/UseCaseOperations.java` | `setRepresentedDiagram(String)` → `setRepresentedDiagrams(List<String>)` + `add/removeRepresentedDiagram(String)` + `getRepresentedDiagrams(): List<String>`；用 `Facade.getDataValues(tv)` 读数组 |
| 3 | `argouml-ai/.../application/usecasediagram/UseCaseDiagramService.java` | 现有 setter/getter 改为 List；新增 `add/remove/list` 单条操作 |
| 4 | `argouml-ai/.../domain/entity/UsecaseUseCaseEntity.java` | `representedDiagramUuid: String` → `representedDiagramUuids: List<String>`（实体构造器签名变） |

### REST 层（6 文件，1 改 + 4 新 + 1 旧 wrapper）

| # | 文件 | 状态 |
|---|---|---|
| 5 | `argouml-ai/.../inbound/rest/usecasediagram/handlers/usecase/SetUseCaseRepresentedDiagramsHandler.java` | 新（PUT 替换全集） |
| 6 | `argouml-ai/.../inbound/rest/usecasediagram/handlers/usecase/AddUseCaseRepresentedDiagramHandler.java` | 新（POST 单条添加） |
| 7 | `argouml-ai/.../inbound/rest/usecasediagram/handlers/usecase/RemoveUseCaseRepresentedDiagramHandler.java` | 新（DELETE 单条移除） |
| 8 | `argouml-ai/.../inbound/rest/usecasediagram/handlers/usecase/ListUseCaseRepresentedDiagramsHandler.java` | 新（GET 列表） |
| 9 | `argouml-ai/InitHttpServerSubsystem.java` + `StandaloneHttpServer.java` | 改（注册 4 条新路由） |
| 10 | `argouml-ai/.../infrastructure/json/EntityJson.java` | 改（`UsecaseUseCaseEntity` 序列化 `representedDiagramUuids`） |

### GUI 层（4 文件，1 改 + 3 新）

| # | 文件 | 状态 |
|---|---|---|
| 11 | `argouml-app/.../ui/ActionJumpToRepresentedDiagram.java` | 新建（extends `AbstractActionNavigate`，每实例绑定一个 diagram） |
| 12 | `argouml-app/.../ui/ActionManageRepresentedDiagrams.java` | 新建（extends `AbstractAction`，打开 ManageDialog） |
| 13 | `argouml-app/.../ui/UseCaseManageRepresentedDiagramsDialog.java` | 新建（包树 JTree + 多选 + Add/Remove） |
| 14 | `argouml-app/.../ui/UseCaseContextPopupFactory.java` | 改（返回 ArgoJMenu 含 Manage + 动态 jump items） |

### 图渲染层（1 文件改）

| # | 文件 | 改动 |
|---|---|---|
| 15 | `argouml-app/.../uml/diagram/use_case/ui/FigUseCase.java` | 加 ∞ `FigText` 子组件 + 监听 + 定位 |

### 属性面板层（1 文件改）

| # | 文件 | 改动 |
|---|---|---|
| 16 | `argouml-core-umlpropertypanels/.../ui/UseCaseRepresentedDiagramField.java` | 改：用 `JTable`（UUID 隐藏 / name / path / Remove 列）；加 [Manage...] 按钮；删除旧 JList Browse 对话框；改调新 ManageDialog |

### 兼容性 + 文档（3 文件）

| # | 文件 | 改动 |
|---|---|---|
| 17 | `argouml-app/.../ui/ActionNavigateRepresentedDiagram.java` | 保留为 legacy，方法改为 `lookupAllRepresentedDiagrams(useCase)`；旧 `lookupRepresentedDiagram(...)` 委派给 `.get(0)` |
| 18 | `argouml-app/src/org/argouml/i18n/menu.properties` | 加：`menu.popup.related-diagrams = 关联图`、`menu.popup.manage-related-diagrams = 关联管理...` |
| 19 | `AGENTS.md` | Extension points 加一行 |

## REST API

| Method | Path | Body | Response |
|---|---|---|---|
| `GET` | `/d/{d}/usecasediagram/usecases/{uuid}/representedDiagrams` | — | `{"data":{"diagramUuids":["u1","u2"]}, "ok":true}` |
| `PUT` | `/d/{d}/usecasediagram/usecases/by-name/{u}/representedDiagrams` | `{"diagramUuids":["u1","u2"]}` | `{"data":{...UseCase with diagramUuids...}, "ok":true}` |
| `POST` | `/d/{d}/usecasediagram/usecases/by-name/{u}/representedDiagram` | `{"diagramUuid":"u1"}` | 200 |
| `DELETE` | `/d/{d}/usecasediagram/usecases/by-name/{u}/representedDiagram/{uuid}` | — | 204 |
| (legacy) `GET` | `.../{uuid}/representedDiagram` | — | `{"data":{"diagramUuid":"u1"}, "ok":true}`（返第一个） |
| (legacy) `PUT` | `.../by-name/{u}/representedDiagram` | `{"diagramUuid":"u1"}` | 200（替换为单元素数组） |

## 数据结构

### Cache
```java
// RepresentedDiagramLinkCache.java
private static final Map<Object, List<String>> CACHE = new WeakHashMap<>();
```

### 实体
```java
// UsecaseUseCaseEntity.java
public final class UsecaseUseCaseEntity {
    private final String uuid;
    private final String name;
    private final String description;
    private final List<String> representedDiagramUuids;  // NEW: replaces single String
    private final String diagramUuid;
    private final int x, y;
}
```

### 表格 Model
```java
class LinkTableModel extends AbstractTableModel {
    // Columns: 0=name (always shown), 1=path (always shown), 2=uuid (hidden)
    private final List<ArgoDiagram> rows;  // may contain nulls when diagram deleted
    String[] columnNames = {"Diagram", "Path"};
    Class[] columnTypes = {String.class, String.class};
    public int getColumnCount() { return 2; }  // visible only; uuid is hidden col idx 2
    public boolean isCellEditable(int r, int c) { return c == -1; }  // no inline edit
}
```

## 数据流详解

### 写入路径
1. `addRepresentedDiagram(useCase, uuid)` 调 `UseCaseOperations.addRepresentedDiagram`
2. 读 cache → 补 uuid → `setRepresentedDiagrams` 覆盖
3. `RepresentedDiagramLinkCache.put(useCase, list)` （authoritative）
4. best-effort 写 model tagged value（`dataValues: String[]`）
5. 返回 → entity 构建带 `representedDiagramUuids`

### 读取路径
1. `getRepresentedDiagrams(useCase)`
2. 查 cache → 命中返
3. miss → `Facade.getDataValues(tv)` 读数组 → 回填 cache → 返

### 视觉符号触发
1. FigUseCase `updateLinkIndicator()` 调 `lookupAllRepresentedDiagrams(owner).size()`
2. > 0 → setVisible(true) + damage() 触发重绘
3. = 0 → setVisible(false) + damage()
4. 监听 `"representedDiagram"` model event，cache 写入后触发

## 错误处理

| 情况 | 表现 |
|---|---|
| UUID 对应 ArgoDiagram 已删除 | 表格行显示 "(missing)"，Remove 仍可清掉该 UUID |
| 包树对话框点 Add 时图不在当前 project | 抛 `NotFoundException` → handler 返 404 |
| 包树对话框遍历命名空间抛异常 | silently skip 节点（与现有 `facade.getAllNamespaces` 异常模式一致） |
| MDR `setDataValues` 抛 `RuntimeException` | cache 已写入成功；model 写入 swallowed |
| ManageDialog 选中 0 项时点 Add | 弹 toast/label 提示 "请先选择图" |

## 测试

- 单元测试增量（`TestUseCaseHandlers` 新增 8 个测试方法）：
  - `testListReturnsMultipleUuids`
  - `testSetReplacesAllUuids`
  - `testAddAppendsUuid`
  - `testRemoveDeletesUuid`
  - `testRemoveNonexistentUuidReturns404`
  - `testAddNonexistentDiagramReturns404`
  - `testListEmptyReturnsEmpty`
  - `testLegacySingleUuidEndpointStillWorks`
- 手动 GUI 验证：
  - 属性面板表格显示 / Remove 行触发更新
  - 包树对话框展开 / 多选 / Add / Remove / Close
  - 右键 "关联图" 子菜单动态生成（0 关联 → 仅 Manage；≥1 关联 → + jump items）
  - ∞ 符号在 0 关联时隐 / ≥1 关联时显
  - 删除图后 ∞ 符号自动更新

## 风险

| 风险 | 缓解 |
|---|---|
| `UsecaseUseCaseEntity` 字段改名 `representedDiagramUuids` 是破坏性 | REST 响应同步增加新字段；旧字段移除；既有 AI client 需更新 |
| MDR `setDataValues` 接受 String[] 但 `setType(String)` 仍拒绝 | cache 是 authoritative；model 写入 best-effort |
| `getDataValues` 在 EUML 后端可能返 `Collection` 而不是 `String[]` | 兼容处理：instanceof 两种都支持 |
| ∞ 文字渲染占 layout space 影响其他 Fig 元素 | 位置在 `bigPort` 外（y - 6），不挤压名字或 stereotype compartment；use `setFilled(false)` 不影响 hit-test |
| `DisplayTextTree` + `UMLTreeCellRenderer` 显示 ArgoDiagram 时是否显示节点图标 | 已有 precedent（`UMLTreeCellRenderer.java:115` 处理 UMLDiagram）—— 直接 setCellRenderer 即可 |
| ManageDialog 使用 JTree 但 ArgoUML 没用 JTree 于任何 modal | 第一个此类 UI，但 Swing 标准组件，无外部依赖 |

## 不在本轮范围（明确边界）

- Actor / Class 等其他元素类型的关联（仅 UseCase）
- EUML 后端的 storage（UML 2.x）兼容（MDR 是默认，EUML 是 best-effort）
- 把关联功能 export 到 XMI（重启仍丢失，因 cache 不是 model 层）
- 关联图本身的反向链接（图 → UseCase）
- 主程序的菜单 / 工具栏扩展

## 待用户二次确认的隐含假设

1. 缓存是否同时清空 `description` 等关联？→ 不变；只清 `representedDiagram` 相关
2. 是否需要双击 ManageDialog 中已关联的图直接跳转？→ **不进本轮**，简化实现
3. ManageDialog 是否需要"已关联" tab 显示当前状态？→ **不进本轮**，通过子菜单"图菜单项"已可见
4. 旧 `representedDiagram` (单数) REST 端点是否立刻删除？→ 保留为 deprecated wrapper（向后兼容 6 个月）
