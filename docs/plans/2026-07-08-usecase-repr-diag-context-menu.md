# UseCase 右键跳转关联图 — 实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 UseCase 图形和 Navigator 树的右键菜单中加 "跳转到关联图" 项，点击后通过 `TargetManager` 切换到 `representedDiagram` tag 指向的 ArgoDiagram。

**Architecture:** 实现一个 `ContextActionFactory`，在 UseCase 元素被右键时由 `FigNodeModelElement` 和 `ExplorerPopup` 共同调用。Factory 内部读 tagged value + 扫描 diagram 列表，只在 tag 存在时返一个 `ActionNavigateRepresentedDiagram` 单例 Action。Action 类继承 `AbstractActionNavigate`，复用其 TargetListener / enable-disable 同步逻辑。

**Tech Stack:** 纯 Java + ArgoUML Swing 框架；JUnit 3 测试惯例（本任务无单测，以手动 GUI 验证为主）。

---

### Task 1: 在 menu.properties 加 i18n key

**Files:**
- Modify: `src/argouml-app/src/org/argouml/i18n/menu.properties`

**Step 1: 在 `menu.popup.modifiers` 附近加新 key**

打开文件，找到 `menu.popup.modifiers` 一行（按字母序插入到该行之前或之后；推荐紧跟 `menu.popup.j` 字母段）：
```
menu.popup.jump-to-represented-diagram = Jump to Represented Diagram
```
中文 fallback 字符串。如果中文 i18n 包存在（`externals/argouml-i18n-zh/...`），后续 Task 加；若不存在，跳过。

**Step 2: 验证 key 可被 Translator 解析**

```bash
# 简单 syntax 检查（properties 文件无 quoting 风险）
grep "menu.popup.jump-to-represented-diagram" /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/org/argouml/i18n/menu.properties
```
Expected: 1 行输出，包含新 key。

**Step 3: Commit**

```bash
git add src/argouml-app/src/org/argouml/i18n/menu.properties
git commit -m "i18n: add menu.popup.jump-to-represented-diagram key"
```

---

### Task 2: 创建 ActionNavigateRepresentedDiagram 类

**Files:**
- Create: `src/argouml-app/src/org/argouml/uml/ui/ActionNavigateRepresentedDiagram.java`

**Step 1: 写完整 Java 文件**

```java
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.uml.ui;

import java.util.Iterator;

import javax.swing.Action;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Right-click / popup menu action: jump from a UseCase to the
 * ArgoDiagram referenced by its {@code representedDiagram}
 * tagged value. Visible only when the tag is set; the parent
 * {@link UseCaseContextPopupFactory} filters out null results
 * before they reach the menu.
 *
 * <p>Extends {@link AbstractActionNavigate} so the
 * TargetListener plumbing (auto enable/disable on selection
 * change) is inherited.</p>
 */
public final class ActionNavigateRepresentedDiagram
        extends AbstractActionNavigate {

    private static final long serialVersionUID = 1L;

    private static final String TAG_NAME = "representedDiagram";

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

    /**
     * Static helper shared with
     * {@link org.argouml.ui.UseCaseContextPopupFactory}.
     * Returns the matching ArgoDiagram or null when the tag is
     * missing, empty, or its UUID doesn't match any diagram in
     * the current project.
     */
    public static ArgoDiagram lookupRepresentedDiagram(Object useCase) {
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
            if (!(d instanceof ArgoDiagram)) {
                continue;
            }
            ArgoDiagram ad = (ArgoDiagram) d;
            if (uuid.equals(ad.getName())) {
                return ad;
            }
            Object ns = ad.getNamespace();
            if (ns != null && uuid.equals(facade.getUUID(ns))) {
                return ad;
            }
        }
        return null;
    }

    private static String readTag(Object useCase) {
        try {
            Facade facade = Model.getFacade();
            Iterator tvs = facade.getTaggedValues(useCase);
            if (tvs == null) {
                return "";
            }
            while (tvs.hasNext()) {
                Object tv = tvs.next();
                if (TAG_NAME.equals(facade.getName(tv))) {
                    Object v = facade.getValue(tv);
                    return v == null ? "" : v.toString();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }
}
```

**Step 2: 编译验证**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml -am compile -DskipTests -o -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS，无错误。

**Step 3: Commit**

```bash
git add src/argouml-app/src/org/argouml/uml/ui/ActionNavigateRepresentedDiagram.java
git commit -m "feat: add ActionNavigateRepresentedDiagram (singleton)"
```

---

### Task 3: 创建 UseCaseContextPopupFactory 类

**Files:**
- Create: `src/argouml-app/src/org/argouml/ui/UseCaseContextPopupFactory.java`

**Step 1: 写完整 Java 文件**

```java
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ui;

import java.util.Collections;
import java.util.List;

import javax.swing.Action;

import org.argouml.model.Model;
import org.argouml.uml.ui.ActionNavigateRepresentedDiagram;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * {@link ContextActionFactory} that injects the
 * {@code "Jump to Represented Diagram"} right-click menu entry
 * on UseCase elements. Used by both
 * {@link org.argouml.uml.diagram.ui.FigNodeModelElement#getPopUpActions}
 * (figure popup) and
 * {@link org.argouml.ui.explorer.ExplorerPopup} (Navigator tree
 * popup).
 *
 * <p>Returns an empty list when the target is not a UseCase or
 * when the UseCase has no {@code representedDiagram} tagged
 * value, so the menu item only appears when navigation is
 * possible.</p>
 */
public final class UseCaseContextPopupFactory
        implements ContextActionFactory {

    @Override
    public List<Action> createContextPopupActions(Object context) {
        if (context == null) {
            return Collections.emptyList();
        }
        if (!Model.getFacade().isAUseCase(context)) {
            return Collections.emptyList();
        }
        ArgoDiagram target =
                ActionNavigateRepresentedDiagram.lookupRepresentedDiagram(
                        context);
        if (target == null) {
            return Collections.emptyList();
        }
        return Collections.<Action>singletonList(
                ActionNavigateRepresentedDiagram.singleton());
    }
}
```

**Step 2: 编译验证**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml -am compile -DskipTests -o -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS。

**Step 3: Commit**

```bash
git add src/argouml-app/src/org/argouml/ui/UseCaseContextPopupFactory.java
git commit -m "feat: add UseCaseContextPopupFactory (figure + tree popup)"
```

---

### Task 4: 在 InitUseCaseDiagram.init() 注册 factory

**Files:**
- Modify: `src/argouml-app/src/org/argouml/uml/diagram/use_case/ui/InitUseCaseDiagram.java`

**Step 1: 读当前 init() 实现**

```bash
sed -n '1,80p' /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/org/argouml/uml/diagram/use_case/ui/InitUseCaseDiagram.java
```
找到 `init()` 方法体末尾。

**Step 2: 加 import 和注册调用**

在文件顶部 import 区添加（按字母序插入）：
```java
import org.argouml.ui.ContextActionFactoryManager;
import org.argouml.ui.UseCaseContextPopupFactory;
```

在 `init()` 方法体的最后（`return` 之前或方法末尾）添加：
```java
        ContextActionFactoryManager.addContextPopupFactory(
                new UseCaseContextPopupFactory());
```

**Step 3: 编译验证**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml -am compile -DskipTests -o -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS。

**Step 4: Commit**

```bash
git add src/argouml-app/src/org/argouml/uml/diagram/use_case/ui/InitUseCaseDiagram.java
git commit -m "feat: register UseCaseContextPopupFactory in InitUseCaseDiagram"
```

---

### Task 5: 删除 FigUseCase.mouseClicked override 和 lookupRepresentedDiagram helper

**Files:**
- Modify: `src/argouml-app/src/org/argouml/uml/diagram/use_case/ui/FigUseCase.java`

**Step 1: 删除 mouseClicked override（lines 605-621）和 lookupRepresentedDiagram helper（lines 623-660）**

打开文件，找到这两段代码。删除从 `@Override public void mouseClicked(MouseEvent me)` 开始到 `lookupRepresentedDiagram` 方法最后一个 `}` 之间的所有内容，连同两个方法之间的空行。

**Step 2: 删除冗余 import**

确认以下 import 不再被使用，删除之：
```java
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.uml.diagram.ArgoDiagram;
```
（`MouseEvent` 仍被 `buildShowPopUp()` 等使用，保留。）

**Step 3: 编译验证**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml -am compile -DskipTests -o -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS（删除 method 后无残留引用）。

**Step 4: Commit**

```bash
git add src/argouml-app/src/org/argouml/uml/diagram/use_case/ui/FigUseCase.java
git commit -m "refactor: drop FigUseCase.mouseClicked override (replaced by context menu)"
```

---

### Task 6: 重建 argouml-app 并打包 fat jar

**Step 1: install argouml-app 到 ~/.m2**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml -am install -DskipTests -o 2>&1 | tail -5
```
Expected: BUILD SUCCESS。

**Step 2: 重打 fat jar**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-build/pom.xml -am package -DskipTests -o 2>&1 | tail -5
```
Expected: BUILD SUCCESS。

**Step 3: 验证新 class 在 fat jar 里**

```bash
unzip -l /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-build/target/argouml-jar-with-dependencies.jar | grep -E "ActionNavigateRepresentedDiagram|UseCaseContextPopupFactory"
```
Expected: 两个 .class 文件都在。

---

### Task 7: 重启 GUI 并手动验证

**Step 1: 关闭现有 GUI 进程（如果有）**

```bash
if [ -f /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke/.gui.pid ]; then
  PID=$(cat /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke/.gui.pid)
  if ps -p "$PID" > /dev/null 2>&1; then kill "$PID"; fi
  rm -f /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke/.gui.pid
fi
```

**Step 2: 启动 GUI**

```bash
nohup /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/bin/run-argouml.sh \
  > /tmp/argouml-logs/argouml-gui.log 2>&1 &
sleep 8
ps -p $! -o pid,etime,command | head -2
```
Expected: ArgoUML 运行中。

**Step 3: 手动验证 6 个场景**

| # | 操作 | 期望 |
|---|---|---|
| 1 | 新建项目 → 用例图 → 添加 UseCase `UC1`（无链接）→ 右键 `UC1` 椭圆 | 菜单**无**"Jump to Represented Diagram"项 |
| 2 | 通过属性面板 Browse 设链接 → 右键 `UC1` 椭圆 | 菜单出现该项 |
| 3 | 点该项 | 视图切换到目标 ArgoDiagram |
| 4 | Navigator 树右键 `UC1` 节点 | 同样出现该项 |
| 5 | 删除目标图 → 重启 GUI → 右键 `UC1` | 该项消失 |
| 6 | 中文界面（`ARGO_LANG=zh ARGO_COUNTRY=CN`）重启 | 菜单显示"Jump to Represented Diagram"（i18n fallback）或中文 key 字符串 |

如发现 bug，回到对应 Task 修复。

---

### Task 8: 更新 AGENTS.md

**Files:**
- Modify: `AGENTS.md`

**Step 1: 在 Extension points 表格新增一行**

找到 `| Add a custom Swing field to a property panel | ...` 行，在其后插入：
```
| Add a right-click menu entry to a model element | (1) implement `ContextActionFactory` that returns `List<Action>` (empty when inapplicable), (2) create `extends AbstractActionNavigate` action (handles TargetListener / enable-disable plumbing), (3) register the factory in the relevant `InitXxxDiagram.init()` via `ContextActionFactoryManager.addContextPopupFactory(...)`. The factory is invoked by both `FigNodeModelElement.getPopUpActions` (figure popup) and `ExplorerPopup` (Navigator tree popup) — single registration, two locations. |
```

**Step 2: Commit**

```bash
git add AGENTS.md
git commit -m "docs: AGENTS.md extension-point for context-menu ContextActionFactory"
```

---

## 执行选项

**Plan complete and saved to `docs/plans/2026-07-08-usecase-repr-diag-context-menu.md`. Two execution options:**

1. **Subagent-Driven (this session)** — dispatch fresh subagent per task, review between tasks, fast iteration
2. **Parallel Session (separate)** — open new session with executing-plans, batch execution with checkpoints

**Which approach?**