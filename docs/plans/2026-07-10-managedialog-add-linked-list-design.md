# Manage Represented Diagrams Dialog — 添加 Linked List — 设计

**日期**：2026-07-10
**状态**：design（待 implementation）

## 背景

`UseCaseManageRepresentedDiagramsDialog`（位于 `src/argouml-app/src/org/argouml/ui/UseCaseManageRepresentedDiagramsDialog.java`）当前只有包树（左侧 `JTree` 浏览所有 ArgoDiagram）和底部按钮行（Add / Remove / Close）。**没有面板显示当前已关联图列表**——用户必须依靠属性面板表格或 REST GET 才能看到状态。本轮添加一个"Currently Linked"列表到对话框中。

## 目标

1. **添加 Linked List 面板**：显示 use case 当前已关联的所有图（含包路径）
2. **Add / Remove 按钮放在 Tree 与 Linked List 之间**：垂直排列
3. **Close 按钮单独放在最下方**

## 设计决策（已与用户确认）

| 决策 | 选择 |
|---|---|
| 布局 | 左右双面板（中间夹按钮） |
| 列内容 | 名称 + 类型 + 包路径 |
| 空状态 | 什么都不显示（纯空白） |
| Add / Remove 按钮位置 | Tree 与 Linked List 中间（垂直两按钮） |
| Close 按钮位置 | 最下方 |

## 架构

```
UseCaseManageRepresentedDiagramsDialog (BorderLayout 外层)
├── CENTER: BoxLayout(X_AXIS) 3-pane 容器
│     ├── WEST (~300px): Tree panel
│     │   ├── NORTH: label "Available Diagrams"
│     │   └── CENTER: JScrollPane → JTree
│     ├── CENTER (~80px): Buttons panel (BoxLayout Y_AXIS)
│     │   ├── [Add →]
│     │   └── [← Remove]
│     └── EAST (~340px): Linked list panel
│           ├── NORTH: label "Currently Linked (N)"
│           └── CENTER: JScrollPane → JList
└── SOUTH: [Close] 按钮 (单独面板)
```

总窗口尺寸：`720 × 480`。

## 组件清单

| # | 文件 | 状态 |
|---|---|---|
| 1 | `src/argouml-app/src/org/argouml/ui/UseCaseManageRepresentedDiagramsDialog.java` | 改：重构为 3-pane BoxLayout + 新 LinkedListModel + LinkedItem |

## 数据流

### 打开对话框
1. `buildTree()` 拉取 Project 的所有 ArgoDiagram，按包分组（已存在）
2. 新 `LinkedListModel.refresh()` 调 `UseCaseOperations.getRepresentedDiagrams(useCase)` 获取 UUID 列表，逐条解析到 ArgoDiagram，构造 LinkedItem 列表
3. Linked list 头部 label 显示 `Currently Linked (N)`

### Add → 按钮（左选 → 右增）
1. 收集 `tree.getSelectionPaths()` 中所有 `DiagramNode` 的 UUID
2. 若空：弹 `JOptionPane` "Please select diagrams in the tree."
3. 否则逐条调 `UseCaseOperations.addRepresentedDiagram(useCase, uuid)`
4. `linkedListModel.refresh()` 重新拉取
5. 弹 `JOptionPane` "Added N diagram(s)."（N 为成功数；add 内部已处理 duplicate 不计入）
6. 树不刷新（用户可能继续选图）

### ← Remove 按钮（右选 → 删）
1. 收集 `linkedList.getSelectedValues()` 中所有 `LinkedItem` 的 UUID
2. 若空：silent no-op
3. 否则逐条调 `UseCaseOperations.removeRepresentedDiagram(useCase, uuid)`
4. `linkedListModel.refresh()` 重新拉取
5. 弹 `JOptionPane` "Removed N diagram(s)."（仅当 N > 0）

### Close 按钮
1. `dispose()`

## LinkedListModel 设计

```java
private final class LinkedListModel extends AbstractListModel {
    private final List<LinkedItem> items = new ArrayList<LinkedItem>();

    void refresh() {
        items.clear();
        List<String> uuids = UseCaseOperations.getRepresentedDiagrams(useCase);
        for (String uuid : uuids) {
            ArgoDiagram ad = lookupDiagram(uuid);  // 与 tree 的 lookup 复用
            items.add(new LinkedItem(ad, uuid));
        }
        fireContentsChanged(this, 0, items.size());
    }

    @Override public int getSize() { return items.size(); }
    @Override public Object getElementAt(int i) {
        return items.get(i).toString();
    }
}

private final class LinkedItem {
    final ArgoDiagram diagram;  // 可能为 null（若 diagram 已删）
    final String uuid;

    LinkedItem(ArgoDiagram d, String u) {
        this.diagram = d;
        this.uuid = u;
    }

    @Override public String toString() {
        if (diagram == null) {
            return "(missing)  " + uuid;
        }
        String name = diagram.getName() == null ? "(unnamed)" : diagram.getName();
        String type = "[" + diagram.getClass().getSimpleName() + "]";
        String path = pathOf(diagram);
        return name + "  " + type + "  -  " + path;
    }
}

private static String pathOf(ArgoDiagram ad) {
    Object ns = ad.getNamespace();
    if (ns == null) return "";
    StringBuilder sb = new StringBuilder("/");
    Object current = ns;
    while (current != null) {
        String n = (String) Model.getFacade().getName(current);
        if (n == null) break;
        if (sb.length() > 1) sb.insert(1, "/");
        sb.insert(1, n);
        current = Model.getFacade().getNamespace(current);
        if (current == null) break;
    }
    return sb.toString();
}
```

> **注**：包路径从当前 namespace 递归向上拼装，例如 `/Model/main/LoginSeq`。depth 不深，递归简单。

## 错误处理

| 情况 | 表现 |
|---|---|
| 已关联列表为空 | 右面板纯空白（per 用户决定） |
| 关联的 UUID 对应 ArgoDiagram 已删除 | 列表项显示 `"(missing) <uuid>"`，仍可移除 |
| Add 同名 UUID 已存在 | `addRepresentedDiagram` 内部去重返 false，弹 "+0" |
| 树选 0 项点 Add | `JOptionPane` "Please select diagrams in the tree." |
| Linked 选 0 项点 Remove | silent no-op |
| Add/Remove 时 MDR 异常（极少） | `UseCaseOperations` 已 catch RuntimeException，列表不变 |

## 完整文件结构

`UseCaseManageRepresentedDiagramsDialog.java` 大致结构：

```java
public final class UseCaseManageRepresentedDiagramsDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final Object useCase;
    private final JTree tree;
    private final JList linkedList;       // 新
    private final LinkedListModel linkedListModel;  // 新
    private final JLabel linkedListLabel;  // 新 (显示 N)

    public UseCaseManageRepresentedDiagramsDialog(Object useCase) {
        super((Frame) null, "Manage Represented Diagrams", true);
        this.useCase = useCase;
        setLayout(new BorderLayout(8, 8));
        setSize(720, 480);
        setLocationRelativeTo(null);

        // 构建 3-pane 中心
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.X_AXIS));

        // 左：Tree
        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.add(new JLabel("Available Diagrams"), BorderLayout.NORTH);
        tree = buildTree();
        treePanel.add(new JScrollPane(tree), BorderLayout.CENTER);
        centerPanel.add(treePanel);

        // 中：Buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        JButton addBtn = new JButton("Add \u2192");
        JButton removeBtn = new JButton("\u2190 Remove");
        addBtn.addActionListener(e -> onAdd());
        removeBtn.addActionListener(e -> onRemove());
        buttonPanel.add(Box.createVerticalGlue());
        buttonPanel.add(addBtn);
        buttonPanel.add(Box.createVerticalStrut(8));
        buttonPanel.add(removeBtn);
        buttonPanel.add(Box.createVerticalGlue());
        centerPanel.add(buttonPanel);

        // 右：Linked list
        JPanel linkedPanel = new JPanel(new BorderLayout());
        linkedListLabel = new JLabel("Currently Linked (0)");
        linkedPanel.add(linkedListLabel, BorderLayout.NORTH);
        linkedListModel = new LinkedListModel();
        linkedList = new JList(linkedListModel);
        linkedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        linkedListModel.refresh();
        linkedPanel.add(new JScrollPane(linkedList), BorderLayout.CENTER);
        centerPanel.add(linkedPanel);

        add(centerPanel, BorderLayout.CENTER);

        // 底部：Close (单独)
        JPanel closePanel = new JPanel();
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        closePanel.add(closeBtn);
        add(closePanel, BorderLayout.SOUTH);
    }

    private void onAdd() { /* 同上 */ }
    private void onRemove() { /* 从 linkedList.getSelectedValues() 收集 UUID */ }
    // ... buildTree, buildNamespaceNode 不变
    // ... DiagramNode, LinkedItem, LinkedListModel, pathOf
}
```

## 测试

无新单元测试（GUI 验证为主）：

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml

# 编译 + 重打 fat jar
touch src/argouml-app/src/org/argouml/ui/UseCaseManageRepresentedDiagramsDialog.java
mvn -f src/argouml-app/pom.xml compile -DskipTests -o -q
mvn -f src/argouml-app/pom.xml install -DskipTests -o
mvn -pl src/argouml-build -am package -DskipTests -o

# 重启 GUI
kill $(cat tests/smoke/.gui.pid 2>/dev/null) 2>/dev/null
sleep 3
nohup src/argouml-app/src/bin/run-argouml.sh > /tmp/argouml-logs/argouml-gui.log 2>&1 &
echo $! > tests/smoke/.gui.pid

# 手动验证：
# 1. 新 UseCase，0 关联 → 右面板空白，"Currently Linked (0)"
# 2. 树中多选 2 个图 → Add → 右面板显示 2 行 "Name [Type] - /path"
# 3. 树中已添加的 UUID → 再 Add → 无变化（duplicate 静默）
# 4. 右面板选 1 行 → Remove → 右面板剩 1 行
# 5. 删除图本身 → 右面板显示 "(missing) <uuid>"
# 6. 选 missing → Remove → 列表空，右面板空白
```

## 风险

| 风险 | 缓解 |
|---|---|
| 窗口宽度变化用户不适 | 720px 已含两列；可在主框架集成时调整 |
| 列表条目的 UUID 缺失（diagram 已删）| 显式显示 `(missing) <uuid>`，可移除 |
| Add 重复 UUID | `addRepresentedDiagram` 内 idempotent，UI 弹 "+0" |
| 包路径递归深度（极端深层嵌套）| ArgoUML 模型深度一般 < 10 层，递归足够 |
| ASCII 兼容 | 箭头用 Unicode 转义 `\u2192` / `\u2190`，不污染源码 |

## 不在本轮范围

- Linked Item 的右键菜单（如 "Jump to" / "View package"）
- 列表条目的拖拽排序
- 列表条目的编辑（如改 UUID）
- 包路径的本地化
- 持久化 divider 位置
- 与属性面板表格的实时双向同步（属性面板仅在 `setTarget` 时刷新，足够）

## 待用户确认的隐含假设

1. Close 按钮保持右下角（不贴左右）
2. 窗口最小尺寸 ~720×480（不做 `setMinimumSize`，Swing 默认允许缩）
3. 列表默认无排序（按 `UseCaseOperations.getRepresentedDiagrams` 的插入顺序）