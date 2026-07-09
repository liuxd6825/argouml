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
import java.awt.Frame;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
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

    public UseCaseManageRepresentedDiagramsDialog(Object useCase) {
        super((Frame) null, "Manage Represented Diagrams", true);
        this.useCase = useCase;
        setLayout(new BorderLayout(8, 8));

        tree = buildTree();
        add(new JScrollPane(tree), BorderLayout.CENTER);

        add(buildButtonPanel(), BorderLayout.SOUTH);
        setSize(420, 480);
        setLocationRelativeTo(null);
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
        t.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        return t;
    }

    private DefaultMutableTreeNode buildNamespaceNode(Object ns, Project project) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new NamespaceNode(ns));
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

    private JPanel buildButtonPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        JButton add = new JButton("Add");
        JButton remove = new JButton("Remove");
        JButton close = new JButton("Close");
        add.addActionListener(e -> onAdd());
        remove.addActionListener(e -> onRemove());
        close.addActionListener(e -> dispose());
        p.add(add);
        p.add(remove);
        p.add(close);
        return p;
    }

    private void onAdd() {
        List<String> toAdd = collectSelectedDiagramUuids();
        if (toAdd.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select diagrams in the tree.");
            return;
        }
        int n = 0;
        for (String uuid : toAdd) {
            if (UseCaseOperations.addRepresentedDiagram(useCase, uuid)) n++;
        }
        JOptionPane.showMessageDialog(this, "Added " + n + " diagram(s).");
    }

    private void onRemove() {
        TreePath[] sel = tree.getSelectionPaths();
        if (sel == null) return;
        int n = 0;
        for (TreePath p : sel) {
            Object o = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
            if (o instanceof DiagramNode) {
                String uuid = ((DiagramNode) o).uuid;
                if (UseCaseOperations.removeRepresentedDiagram(useCase, uuid)) n++;
            }
        }
        if (n > 0) JOptionPane.showMessageDialog(this, "Removed " + n + " diagram(s).");
    }

    private List<String> collectSelectedDiagramUuids() {
        List<String> out = new ArrayList<String>();
        TreePath[] sel = tree.getSelectionPaths();
        if (sel == null) return out;
        for (TreePath p : sel) {
            Object o = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
            if (o instanceof DiagramNode) out.add(((DiagramNode) o).uuid);
        }
        return out;
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