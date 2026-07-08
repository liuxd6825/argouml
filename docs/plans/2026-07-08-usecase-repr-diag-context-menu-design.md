# UseCase 右键跳转关联图 — 设计

**日期**：2026-07-08
**状态**：design（待 implementation）

## 背景

ArgoUML 已有一条 UseCase → Sequence Diagram 的引用机制：通过 ArgoUML tagged
value `representedDiagram` 存目标 ArgoDiagram 的 namespace UUID（见
`src/argouml-ai/src/org/argouml/ai/domain/usecasediagram/UseCaseOperations.java:128`
起的 `setRepresentedDiagram` / `getRepresentedDiagram`）。前期工作已经实现：

- **REST API**：`PUT/GET /d/{d}/usecasediagram/usecases/{uuid}/representedDiagram`
- **属性面板**：`UseCaseRepresentedDiagramField`（Browse 按钮 + UUID 文本框 +
  in-memory `WeakHashMap` 缓存解决 MDR round-trip 问题）
- **图形双击跳转**：已实现的 `FigUseCase.mouseClicked` override 失败 — 双击事件
  触发 `FigNodeModelElement` 父类的名字编辑模式后被 `me.consume()`，导航代码
  永远跑不到
- **Explore tree**：`GoClassifierToSequenceDiagram` 规则已存在，但只能跳到
  SequenceDiagram，不能跳到任意 ArgoDiagram

## 目标

在 UseCase 的右键菜单里加 **"跳转到关联图"** 项：

- 出现位置：**UseCase 图形上 + Navigator 树节点上**
- 文案：**"跳转到关联图"**（中文，Translator fallback 到 key）
- 可见性：**仅当该 UseCase 已设置 `representedDiagram` tag** 时才出现
- 动作：调用 `TargetManager.setTarget(argoDiagram)` 切换视图

## 设计选择

| 决策 | 选项 | 选定 |
|---|---|---|
| 出现位置 | 图形 only / 图形 + 树 / 树 only | **图形 + 树**（双位置最有用） |
| 文案 | 英文 / 中文 / 描述意图 | **中文（"跳转到关联图"）** |
| 可见性 | disable / 隐藏 / 错误提示 | **隐藏（仅在有链接时插入）** |
| tag 读取 | 直读 / 复用 CACHE / fallback chain | **直读 tag + 实时扫图列表** |

实现策略：**ContextActionFactory + AbstractActionNavigate**。理由：
- 与 ArgoUML 既定模式一致（`InitUseCaseDiagram.init()` 已经注册其他 UseCase 相关
  factory）
- 单点注册，图形 + Navigator 树两条路径同时生效
- AbstractActionNavigate 已处理 TargetListener / enable-disable 同步逻辑
- 不需要改 `FigUseCase.java` 或 `ExplorerPopup.java`

## 架构

```
右键 UseCase（图形 or Navigator 树节点）
    ↓
FigNodeModelElement.getPopUpActions()  或  ExplorerPopup 构造器
    ↓
ContextActionFactoryManager.getContextPopupActions()
    ↓ 遍历所有已注册的 factory
UseCaseContextPopupFactory.createContextPopupActions(useCase)
    ↓ [if representedDiagram tag exists, value 非空]
    ↓   调 static helper lookupRepresentedDiagram(useCase)
    ↓   若命中 ArgoDiagram，返 List<Action> 单元素
    ↓ [else] 返 Collections.emptyList()
ActionNavigateRepresentedDiagram（extends AbstractActionNavigate）
    ↓ 用户点击
AbstractActionNavigate.actionPerformed → navigateTo(source)
    ↓ 读 tag + 扫描 Project.getDiagramList()
TargetManager.getInstance().setTarget(argoDiagram)
    ↓
GUI 切换到目标 ArgoDiagram
```

## 组件清单

| # | 文件 | 状态 | 行为 |
|---|---|---|---|
| 1 | `src/argouml-app/src/org/argouml/uml/ui/ActionNavigateRepresentedDiagram.java` | 新建 | ~50 行。`extends AbstractActionNavigate(String key, boolean hasIcon)`；`navigateTo(Object source)` 读 tag、找 ArgoDiagram、返 null（找不到时）。 |
| 2 | `src/argouml-app/src/org/argouml/ui/UseCaseContextPopupFactory.java` | 新建 | ~35 行。`implements ContextActionFactory`；`createContextPopupActions(Object)`：isAUseCase && tag 非空 → 返包含 `ActionNavigateRepresentedDiagram` 的单元素 list；否则空 list。 |
| 3 | `src/argouml-app/src/org/argouml/uml/diagram/use_case/ui/InitUseCaseDiagram.java` | 修改 | `init()` 末尾调 `ContextActionFactoryManager.addContextPopupFactory(new UseCaseContextPopupFactory())`。 |
| 4 | `src/argouml-app/src/org/argouml/uml/diagram/use_case/ui/FigUseCase.java` | 修改 | **删除** `mouseClicked(MouseEvent)` override（lines 605-621）和 `lookupRepresentedDiagram(Object)` helper（lines 623-660）。删除对应 import (`Project`, `ProjectManager`, `ArgoDiagram`, `Facade`)。`MouseEvent` 仍被其他用途引用，保留。 |
| 5 | `src/argouml-app/src/org/argouml/i18n/menu.properties` | 修改 | 加 `menu.popup.jump-to-represented-diagram = 跳转到关联图`（在 `menu.popup.modifiers` 附近，按字母序）。 |
| 6 | `externals/argouml-i18n-zh/src/main/resources/org/argouml/i18n/menu_zh_CN.properties`（若已 checkout） | 修改（可选） | 同步加中文映射。当前未确认该目录存在；fallback 到 key 字符串也可接受。 |

## 数据流详解

### lookupRepresentedDiagram(Object useCase) — 共享 helper

```java
private static ArgoDiagram lookupRepresentedDiagram(Object useCase) {
    if (useCase == null || !Model.getFacade().isAUseCase(useCase)) {
        return null;
    }
    String uuid = readTag(useCase);
    if (uuid == null || uuid.isEmpty()) {
        return null;
    }
    Project project = ProjectManager.getManager().getCurrentProject();
    if (project == null) {
        return null;
    }
    Facade facade = Model.getFacade();
    for (Object d : project.getDiagramList()) {
        if (!(d instanceof ArgoDiagram)) continue;
        ArgoDiagram ad = (ArgoDiagram) d;
        if (uuid.equals(ad.getName())) return ad;
        Object ns = ad.getNamespace();
        if (ns != null && uuid.equals(facade.getUUID(ns))) return ad;
    }
    return null;
}
```

> 注：与既有 `FigUseCase.lookupRepresentedDiagram` 算法相同；从 FigUseCase 移
> 到 factory 内部 static helper 即可，避免代码重复。

### readTag(Object useCase) — 读 tagged value

```java
private static String readTag(Object useCase) {
    try {
        Facade facade = Model.getFacade();
        Iterator tvs = facade.getTaggedValues(useCase);
        if (tvs == null) return "";
        while (tvs.hasNext()) {
            Object tv = tvs.next();
            if ("representedDiagram".equals(facade.getName(tv))) {
                Object v = facade.getValue(tv);
                return v == null ? "" : v.toString();
            }
        }
    } catch (RuntimeException ignored) { }
    return "";
}
```

### UseCaseContextPopupFactory

```java
public final class UseCaseContextPopupFactory implements ContextActionFactory {
    @Override
    public List<Action> createContextPopupActions(Object context) {
        if (context == null) return Collections.emptyList();
        if (!Model.getFacade().isAUseCase(context)) {
            return Collections.emptyList();
        }
        if (ActionNavigateRepresentedDiagram.lookupRepresentedDiagram(context)
                == null) {
            return Collections.emptyList();
        }
        return Collections.<Action>singletonList(
                ActionNavigateRepresentedDiagram.singleton());
    }
}
```

### ActionNavigateRepresentedDiagram

```java
public final class ActionNavigateRepresentedDiagram
        extends AbstractActionNavigate {

    private static final ActionNavigateRepresentedDiagram INSTANCE =
            new ActionNavigateRepresentedDiagram();

    public static ActionNavigateRepresentedDiagram singleton() {
        return INSTANCE;
    }

    private ActionNavigateRepresentedDiagram() {
        super("menu.popup.jump-to-represented-diagram", true);
    }

    @Override
    protected Object navigateTo(Object source) {
        return lookupRepresentedDiagram(source);
    }

    static ArgoDiagram lookupRepresentedDiagram(Object source) { /* as above */ }
}
```

## 错误处理

| 情况 | 表现 |
|---|---|
| UseCase 没有 tag | factory 返空 → 菜单项不出现 |
| tag 值为空字符串 | 同上 |
| UUID 对应 ArgoDiagram 已删除 | `lookupRepresentedDiagram` 返 null → 菜单项不出现 |
| `ProjectManager.getCurrentProject()` 返 null | 返 null → 菜单项不出现 |
| MDR 后端 tag 读取异常 | `try/catch (RuntimeException)` 吞掉，返空字符串 → 菜单项不出现 |
| `navigateTo` 返 null 时被点中（边界场景） | `AbstractActionNavigate.isEnabled()` 已返 false → 点击不触发 |

## 测试策略

本仓库无 GUI 单元测试惯例（JUnit 3 仅覆盖 model 层）。验证步骤：

1. **编译**：`mvn -pl src/argouml-app -am compile -DskipTests`
2. **打包**：`mvn -f src/argouml-core-umlpropertypanels/pom.xml install -DskipTests && mvn -pl src/argouml-build -am package -DskipTests`
3. **重启 GUI**：`bash src/argouml-app/src/bin/run-argouml.sh`
4. **场景 1**：新建 UseCase，无 `representedDiagram` tag → 右键 UseCase 椭圆 → 菜单**无**"跳转到关联图"项
5. **场景 2**：通过属性面板 Browse 设链接 → 右键 UseCase 椭圆 → 出现该项
6. **场景 3**：点该项 → 视图切换到目标 ArgoDiagram ✓
7. **场景 4**：Navigator 树右键 UseCase 节点 → 同样出现该项 ✓
8. **场景 5**：删除目标图 → 右键 UseCase → 该项消失 ✓
9. **场景 6**：i18n — 中文界面下显示"跳转到关联图"（或 key fallback）

## 风险与限制

| 风险 | 缓解 |
|---|---|
| MDR 后端 tag 回读不稳定（已知） | factory 在每次 popup 时实时读；同样问题在 Browse 对话框已经遇到过，行为一致 |
| 双击跳转已删除，仅靠右键 / 树右键 / Go 按钮 | 用户有三种入口；属性面板"Go"按钮也可保留（如前期已实现） |
| 中文 i18n key 在 zh_CN properties 里没有镜像 | 第一次 fallback 到 key 字符串本身（"menu.popup.jump-to-..."），用户可见但不影响功能 |
| `ActionNavigateRepresentedDiagram.singleton()` 单例 vs factory 创建多个实例 | 单例确保内存中只有一个 Action，所有 popup 复用 |
| 删除 FigUseCase.mouseClicked 后"双击"恢复默认行为 | 这是**有意的**改进：双击进入名字编辑符合用户预期 |

## 与现有功能的关系

- **REST API**：`PUT /d/{d}/usecasediagram/usecases/{u}/representedDiagram` 仍是
  设置链接的程序化入口；右键菜单只是 GUI 入口
- **属性面板**：仍负责"设置链接"；右键菜单负责"跳转到已设链接的图"
- **Explorer 树的 GoClassifierToSequenceDiagram**：仍存在，但只覆盖 Sequence 图；
  本设计扩展到任意 ArgoDiagram
- **AGENTS.md "Adding data types" 章节**：不相关，不更新
- **AGENTS.md "Extension points" 表格**：可加一行
  "Add a UseCase → Diagram context-menu entry" 指向本文档

## 待用户确认的隐含假设

1. **保留现有的属性面板"Go"按钮吗？**（前期实现过，但用户没明确要求）
   - **建议保留**：属性面板 Go 是另一种入口（不依赖右键位置），与右键菜单互不冲突
2. **删除 FigUseCase.mouseClicked 后，是否还需要"shape 双击 = 跳图"功能？**
   - **本设计删除**：用户已选择"右键菜单"作为新入口，双击恢复默认（编辑名字）
3. **i18n key 是否需要其他语言同步？**
   - **本设计只加英文 i18n key**：en_US.properties 是 fallback；中文界面 fallback 到 key 字符串