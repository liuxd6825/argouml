# ManageRepresentedDiagramsDialog 加 Linked List — 实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 `UseCaseManageRepresentedDiagramsDialog` 增加 "Currently Linked" 列表面板 + 调整按钮布局（Add/Remove 中间，Close 底部）。

**Architecture:** 1 个 JDialog 文件重写中心区，从单 JTree 改为 3-pane BoxLayout（Tree | Buttons | Linked List），新增 LinkedListModel + LinkedItem 内部类 + pathOf 工具。

**Tech Stack:** 纯 Java + Swing；JUnit 3 不适用（GUI-only 任务，依赖手动验证）。

---

### Task 1: 重构 UseCaseManageRepresentedDiagramsDialog

**Files:**
- Modify: `/Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/org/argouml/ui/UseCaseManageRepresentedDiagramsDialog.java`

**Step 1**: Read current state of the file to confirm baseline:
```bash
wc -l /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/org/argouml/ui/UseCaseManageRepresentedDiagramsDialog.java
```

**Step 2**: Replace the entire file with this verbatim text:

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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.AbstractListModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.argouml.ai.domain.usecasediagram.UseCaseOperations;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.ui.UMLTreeCellRenderer;

public final class UseCaseManageRepresentedDiagramsDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final Object useCase;
    private final JTree tree;
    private final JList linkedList;
    private final LinkedListModel linkedListModel;
    private final JLabel linkedListLabel;

    public UseCaseManageRepresentedDiagramsDialog(Object useCase) {
        super((Frame) null, "Manage Represented Diagrams", true);
        this.useCase = useCase;
        setLayout(new BorderLayout(8, 8));
        setSize(720, 480);
        setLocationRelativeTo(null);

        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildClosePanel(), BorderLayout.SOUTH);
    }

    private JPanel buildCenterPanel() {
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.X_AXIS));

        // LEFT: Tree
        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        JLabel treeLabel = new JLabel("Available Diagrams");
        treeLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        treePanel.add(treeLabel, BorderLayout.NORTH);
        tree = buildTree();
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setPreferredSize(new Dimension(300, 380));
        treePanel.add(treeScroll, BorderLayout.CENTER);
        center.add(treePanel);

        // CENTER: Add / Remove buttons (vertically)
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        buttonPanel.setPreferredSize(new Dimension(80, 380));
        buttonPanel.add(Box.createVerticalGlue());
        JButton addBtn = new JButton("Add >");
        addBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                addBtn.getPreferredSize().height));
        JButton removeBtn = new JButton("< Remove");
        removeBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                removeBtn.getPreferredSize().height));
        addBtn.addActionListener(e -> onAdd());
        removeBtn.addActionListener(e -> onRemove());
        buttonPanel.add(addBtn);
        buttonPanel.add(Box.createVerticalStrut(8));
        buttonPanel.add(removeBtn);
        buttonPanel.add(Box.createVerticalGlue());
        center.add(buttonPanel);

        // RIGHT: Linked list
        JPanel linkedPanel = new JPanel(new BorderLayout());
        linkedPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        linkedListLabel = new JLabel("Currently Linked (0)");
        linkedListLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        linkedPanel.add(linkedListLabel, BorderLayout.NORTH);
        linkedListModel = new LinkedListModel();
        linkedListModel.refresh();
        linkedList = new JList(linkedListModel);
        linkedList.setSelectionMode(
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane linkedScroll = new JScrollPane(linkedList);
        linkedScroll.setPreferredSize(new Dimension(320, 380));
        linkedPanel.add(linkedScroll, BorderLayout.CENTER);
        center.add(linkedPanel);

        return center;
    }

    private JPanel buildClosePanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.add(Box.createHorizontalGlue());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        p.add(close);
        p.add(Box.createHorizontalStrut(8));
        return p;
    }

    private JTree buildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Project");
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project != null) {
            for (Object ns : project.getUserDefinedModelList()) {
                DefaultMutableTreeNode node = buildNamespaceNode(ns, project);
                if (node != null) root.add(node);
            }
        }
        JTree t = new DisplayTextTree();
        t.setModel(new DefaultTreeModel(root));
        t.setCellRenderer(new UMLTreeCellRenderer());
        t.getSelectionModel().setSelectionMode(
                TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        return t;
    }

    private DefaultMutableTreeNode buildNamespaceNode(Object ns, Project project) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                new NamespaceNode(ns));
        Facade facade = Model.getFacade();
        try {
            Collection subs = facade.getOwnedElements(ns);
            if (subs != null) {
                for (Object sub : subs) {
                    if (!facade.isAPackage(sub)) continue;
                    DefaultMutableTreeNode child = buildNamespaceNode(sub, project);
                    if (child != null) node.add(child);
                }
            }
        } catch (RuntimeException ignored) {
            // skip unreachable namespace
        }
        for (Object d : project.getDiagramList()) {
            if (!(d instanceof ArgoDiagram)) continue;
            ArgoDiagram ad = (ArgoDiagram) d;
            if (ad.getNamespace() == ns) {
                node.add(new DefaultMutableTreeNode(new DiagramNode(ad), false));
            }
        }
        return node;
    }

    private void onAdd() {
        List<String> toAdd = collectSelectedDiagramUuids();
        if (toAdd.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select diagrams in the tree.");
            return;
        }
        int n = 0;
        for (String uuid : toAdd) {
            if (UseCaseOperations.addRepresentedDiagram(useCase, uuid)) n++;
        }
        linkedListModel.refresh();
        JOptionPane.showMessageDialog(this, "Added " + n + " diagram(s).");
    }

    private void onRemove() {
        Object[] sel = linkedList.getSelectedValues();
        if (sel == null || sel.length == 0) return;
        int n = 0;
        for (Object o : sel) {
            if (o instanceof LinkedItem) {
                String uuid = ((LinkedItem) o).uuid;
                if (UseCaseOperations.removeRepresentedDiagram(useCase, uuid)) n++;
            }
        }
        if (n > 0) {
            linkedListModel.refresh();
            JOptionPane.showMessageDialog(this,
                    "Removed " + n + " diagram(s).");
        }
    }

    private List<String> collectSelectedDiagramUuids() {
        List<String> out = new ArrayList<String>();
        TreePath[] sel = tree.getSelectionPaths();
        if (sel == null) return out;
        for (TreePath p : sel) {
            Object o = ((DefaultMutableTreeNode) p.getLastPathComponent())
                    .getUserObject();
            if (o instanceof DiagramNode) {
                out.add(((DiagramNode) o).uuid);
            }
        }
        return out;
    }

    private ArgoDiagram lookupDiagram(Project project, String uuid, Facade facade) {
        if (project == null) return null;
        for (Object d : project.getDiagramList()) {
            if (!(d instanceof ArgoDiagram)) continue;
            ArgoDiagram ad = (ArgoDiagram) d;
            if (uuid.equals(ad.getName())) return ad;
            try {
                Object ns = ad.getNamespace();
                if (ns != null && uuid.equals(facade.getUUID(ns))) return ad;
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    private String pathOf(ArgoDiagram ad) {
        Object ns = ad.getNamespace();
        if (ns == null) return "";
        StringBuilder sb = new StringBuilder("/");
        Object current = ns;
        while (current != null) {
            String n = (String) Model.getFacade().getName(current);
            if (n == null) break;
            sb.insert(1, n);
            current = Model.getFacade().getNamespace(current);
            if (current != null) sb.insert(1, "/");
        }
        return sb.toString();
    }

    private final class LinkedListModel extends AbstractListModel {
        private final List<LinkedItem> items = new ArrayList<LinkedItem>();

        void refresh() {
            items.clear();
            List<String> uuids =
                    UseCaseOperations.getRepresentedDiagrams(useCase);
            Project project =
                    ProjectManager.getManager().getCurrentProject();
            Facade facade = Model.getFacade();
            for (String uuid : uuids) {
                ArgoDiagram ad = lookupDiagram(project, uuid, facade);
                items.add(new LinkedItem(ad, uuid));
            }
            fireContentsChanged(this, 0, items.size());
            if (linkedListLabel != null) {
                linkedListLabel.setText(
                        "Currently Linked (" + items.size() + ")");
            }
        }

        @Override public int getSize() { return items.size(); }
        @Override public Object getElementAt(int i) {
            return items.get(i).toString();
        }
    }

    private final class LinkedItem {
        final ArgoDiagram diagram;
        final String uuid;

        LinkedItem(ArgoDiagram d, String u) {
            this.diagram = d;
            this.uuid = u;
        }

        @Override public String toString() {
            if (diagram == null) {
                return "(missing)  " + uuid;
            }
            String name = diagram.getName() == null
                    ? "(unnamed)" : diagram.getName();
            String type = "[" + diagram.getClass().getSimpleName() + "]";
            return name + "  " + type + "  -  " + pathOf(diagram);
        }
    }

    private static final class NamespaceNode {
        final Object ns;
        NamespaceNode(Object ns) { this.ns = ns; }
        @Override public String toString() {
            String n = (String) Model.getFacade().getName(ns);
            return n == null ? "(namespace)" : n;
        }
    }

    private static final class DiagramNode {
        final ArgoDiagram diagram;
        final String uuid;
        DiagramNode(ArgoDiagram ad) {
            this.diagram = ad;
            Object ns = ad.getNamespace();
            String u = "";
            try {
                String t = (String) Model.getFacade().getUUID(ns);
                if (t != null) u = t;
            } catch (RuntimeException ignored) {
            }
            this.uuid = u;
        }
        @Override public String toString() {
            return (diagram.getName() == null ? "(unnamed)" : diagram.getName())
                    + "  [" + diagram.getClass().getSimpleName() + "]";
        }
    }
}
```

**Step 3**: Compile
```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
touch src/argouml-app/src/org/argouml/ui/UseCaseManageRepresentedDiagramsDialog.java
mvn -f src/argouml-app/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS (no other code depends on the dialog API).

**Step 4**: Install + repackage
```bash
mvn -f src/argouml-app/pom.xml install -DskipTests -o 2>&1 | tail -3
mvn -pl src/argouml-build -am package -DskipTests -o 2>&1 | tail -3
```
Expected: BUILD SUCCESS.

**Step 5**: Restart GUI
```bash
# Kill old GUI
if [ -f /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke/.gui.pid ]; then
  PID=$(cat /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke/.gui.pid)
  if ps -p "$PID" > /dev/null 2>&1; then kill "$PID"; fi
  rm -f /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke/.gui.pid
fi
pkill -f "argouml.application.Main" 2>/dev/null
sleep 3

# Start new
mkdir -p /tmp/argouml-logs
nohup /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/bin/run-argouml.sh \
  > /tmp/argouml-logs/argouml-gui.log 2>&1 &
GUI_PID=$!
sleep 12
if ps -p "$GUI_PID" > /dev/null 2>&1; then
  echo $GUI_PID > /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke/.gui.pid
  echo "GUI started PID=$GUI_PID"
else
  echo "FAILED to start"
  tail -20 /tmp/argouml-logs/argouml-gui.log
fi
```
Expected: GUI starts on PID stored in `.gui.pid`.

**Step 6**: Commit
```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-app/src/org/argouml/ui/UseCaseManageRepresentedDiagramsDialog.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(dialog): add 'Currently Linked' panel to ManageRepresentedDiagramsDialog"
```

## Manual GUI Verification Checklist (no automated tests)

The user performs these after each restart:

1. **Empty state** — Open dialog on a UseCase with no links → right pane is blank, label reads "Currently Linked (0)"
2. **Add** — Select 2 diagrams in tree → Add → right pane shows 2 entries "Name [Type] - /path"
3. **Add duplicate** — Re-add the same UUID → no change, dialog says "Added 0 diagram(s)."
4. **Remove** — Select 1 entry on right → Remove → entry disappears, label updates to "(1)"
5. **Missing diagram** — Delete the underlying ArgoDiagram (e.g. via Explorer or REST) → reopen dialog → that entry shows "(missing) <uuid>"
6. **Remove missing** — Select missing entry → Remove → entry disappears
7. **Close** — Click Close → dialog disposes

After verifying, the user can close GUI via:
```bash
kill $(cat /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke/.gui.pid)
```

---

## Execution Options

**Plan complete and saved to `docs/plans/2026-07-10-managedialog-add-linked-list.md`. Two execution options:**

1. **Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration
2. **Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints
3. **Single session direct** - I just run it end-to-end in this session

**Which approach?**