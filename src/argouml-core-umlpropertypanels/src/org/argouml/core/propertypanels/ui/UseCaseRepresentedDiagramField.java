/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.core.propertypanels.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import org.argouml.ai.domain.usecasediagram.UseCaseOperations;
import org.argouml.i18n.Translator;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Model;
import org.argouml.ui.UseCaseManageRepresentedDiagramsDialog;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.ui.UMLDiagram;
import org.argouml.util.ItemUID;

/**
 * Property-panel table of a Use Case's "represented diagrams" links.
 *
 * <p>Three columns: <b>Diagram</b> (the diagram's user-given name),
 * <b>Path</b> (the namespace path of the diagram, with the
 * "untitledModel" name localized via
 * {@code Translator.localize("misc.untitled-model")}),
 * <b>Type</b> (the diagram kind label such as "Use Case Diagram").
 * The UUID column is hidden — keeping UUIDs visible to end users was
 * the original bug we are fixing here.</p>
 */
public final class UseCaseRepresentedDiagramField extends JPanel {

    private final LinkTableModel tableModel = new LinkTableModel();
    private final JTable table = new JTable(tableModel);
    private final JButton manageButton = new JButton(
            Translator.localize("button.manage"));
    private Object useCase;

    public UseCaseRepresentedDiagramField() {
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JLabel header = new JLabel(
                Translator.localize("label.represented-diagrams"));
        add(header, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(false);
        table.setRowHeight(20);
        // Hide the legacy UUID column (index 2). Kept as a no-op guard
        // because the new model exposes Diagram, Path, Type — only Type
        // lives at index 2 now.
        // (Removing the column entirely would have required a column
        // model swap, so we just rebuild the table model below.)
        tableModel.setColumns(new String[] {"Diagram", "Path", "Type"});

        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(320, 60));
        add(sp, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.add(Box.createHorizontalGlue());
        buttonRow.add(manageButton);
        add(buttonRow, BorderLayout.SOUTH);

        manageButton.addActionListener(e -> {
            if (useCase == null) return;
            new UseCaseManageRepresentedDiagramsDialog(useCase).setVisible(true);
        });
    }

    public void setTarget(Object target) {
        this.useCase = target;
        tableModel.setRows(buildRows(target));
    }

    /**
     * Test-only accessor returning the current target. Exposed as
     * package-private to keep it out of the public API.
     */
    Object getUseCaseForTest() {
        return useCase;
    }

    private static List<Row> buildRows(Object useCase) {
        List<Row> rows = new ArrayList<Row>();
        if (useCase == null) return rows;
        List<String> uuids = UseCaseOperations.getRepresentedDiagrams(useCase);
        if (uuids.isEmpty()) return rows;
        Project p = ProjectManager.getManager().getCurrentProject();
        for (String uuid : uuids) {
            ArgoDiagram ad = lookupDiagram(p, uuid);
            if (ad == null) {
                rows.add(new Row(
                        "(missing diagram)", "", "", uuid));
                continue;
            }
            rows.add(new Row(
                    safeName(ad),
                    pathOf(ad),
                    diagramTypeLabel(ad),
                    uuid));
        }
        return rows;
    }

    /**
     * Resolve a stored UUID string to the matching ArgoDiagram.
     *
     * <p>Tries in order:
     * <ol>
     *   <li>ItemUID match (current canonical storage).</li>
     *   <li>Diagram name match (covers pre-fix links stored as name).</li>
     *   <li>Namespace UUID match (covers legacy links stored as the
     *       shared model namespace UUID). This may match more than
     *       one diagram when the user has multiple diagrams in one
     *       project — we return the first match which preserves the
     *       old fallback behaviour for un-migrated data.</li>
     * </ol></p>
     */
    private static ArgoDiagram lookupDiagram(Project p, String uuid) {
        if (p == null || uuid == null || uuid.isEmpty()) return null;
        // Collect candidates so we can pick the most specific match
        // (ItemUID > name > namespace UUID).
        ArgoDiagram byName = null;
        ArgoDiagram byNamespace = null;
        for (Object d : p.getDiagramList()) {
            if (!(d instanceof ArgoDiagram)) continue;
            ArgoDiagram ad = (ArgoDiagram) d;

            // (1) ItemUID match (current canonical).
            try {
                ItemUID uid = ad.getItemUID();
                if (uid != null && uuid.equals(uid.toString())) return ad;
            } catch (RuntimeException ignored) {
                // fall through to next strategy
            }

            // (2) Name match.
            try {
                if (uuid.equals(ad.getName())) {
                    if (byName == null) byName = ad;
                }
            } catch (RuntimeException ignored) {
                // fall through
            }

            // (3) Namespace UUID match (legacy data).
            try {
                Object ns = ad.getNamespace();
                if (ns != null
                        && uuid.equals(Model.getFacade().getUUID(ns))
                        && byNamespace == null) {
                    byNamespace = ad;
                }
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return byName != null ? byName : byNamespace;
    }

    private static String safeName(ArgoDiagram ad) {
        String n = ad.getName();
        return n == null ? "(" + Translator.localize("misc.unnamed") + ")" : n;
    }

    /**
     * Build a human-readable path for the diagram's namespace.
     *
     * <p>The default root model name stored in the model attribute is
     * the unlocalized source string {@code "untitledModel"}. We detect
     * that exact value and substitute the localized form via
     * {@code Translator.localize("misc.untitled-model")} so users in
     * non-English locales see a translated label.</p>
     */
    private static String pathOf(ArgoDiagram ad) {
        Object ns = ad.getNamespace();
        if (ns == null) return "";
        StringBuilder sb = new StringBuilder("/");
        Object current = ns;
        while (current != null) {
            String n = (String) Model.getFacade().getName(current);
            if (n == null) break;
            if ("untitledModel".equals(n)) {
                n = Translator.localize("misc.untitled-model");
            }
            sb.insert(1, n);
            current = Model.getFacade().getNamespace(current);
            if (current != null) sb.insert(1, "/");
        }
        return sb.toString();
    }

    /**
     * Localized diagram kind label like "Use Case Diagram". Falls back
     * to the simple class name for ArgoDiagram implementations that
     * don't extend {@link UMLDiagram}.
     */
    private static String diagramTypeLabel(ArgoDiagram ad) {
        if (ad instanceof UMLDiagram) {
            return ((UMLDiagram) ad).getLabelName();
        }
        // Fallback for non-UMLDiagram subclasses.
        String simple = ad.getClass().getSimpleName();
        // Strip a leading "UML" for the rare subclass that doesn't
        // already use getLabelName().
        if (simple.startsWith("UML") && simple.length() > 3) {
            simple = simple.substring(3);
        }
        return simple;
    }

    private static final class Row {
        final String name;
        final String path;
        final String type;
        final String uuid;
        Row(String name, String path, String type, String uuid) {
            this.name = name;
            this.path = path;
            this.type = type;
            this.uuid = uuid;
        }
        // Legacy 3-arg constructor kept in case someone subclasses Row.
        Row(String name, String path, String uuid) {
            this(name, path, "", uuid);
        }
        // Package-private accessors for tests and for the table model.
        String getName() { return name; }
        String getPath() { return path; }
        String getType() { return type; }
        String getUuid() { return uuid; }
    }

    private static final class LinkTableModel extends AbstractTableModel {
        private String[] cols = {"Diagram", "Path", "Type"};
        private List<Row> rows = new ArrayList<Row>();
        void setColumns(String[] c) {
            this.cols = c;
            fireTableStructureChanged();
        }
        void setRows(List<Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            if (c == 0) return row.name;
            if (c == 1) return row.path;
            return row.type;
        }
    }
}