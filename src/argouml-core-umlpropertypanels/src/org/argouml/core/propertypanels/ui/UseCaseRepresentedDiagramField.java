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
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.ui.UseCaseManageRepresentedDiagramsDialog;
import org.argouml.uml.diagram.ArgoDiagram;

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
        // Hide the UUID column (index 2)
        try {
            table.removeColumn(table.getColumnModel().getColumn(2));
        } catch (Exception ignored) {}

        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(280, 60));
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

    private static List<Row> buildRows(Object useCase) {
        List<Row> rows = new ArrayList<Row>();
        if (useCase == null) return rows;
        List<String> uuids = UseCaseOperations.getRepresentedDiagrams(useCase);
        if (uuids.isEmpty()) return rows;
        Project p = ProjectManager.getManager().getCurrentProject();
        Facade facade = Model.getFacade();
        for (String uuid : uuids) {
            ArgoDiagram ad = lookupDiagram(p, uuid, facade);
            if (ad == null) {
                rows.add(new Row("(missing diagram)", uuid, uuid));
                continue;
            }
            rows.add(new Row(
                    safeName(ad),
                    pathOf(ad),
                    uuid));
        }
        return rows;
    }

    private static ArgoDiagram lookupDiagram(Project p, String uuid, Facade facade) {
        if (p == null) return null;
        for (Object d : p.getDiagramList()) {
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

    private static String safeName(ArgoDiagram ad) {
        return ad.getName() == null ? "(unnamed)" : ad.getName();
    }

    private static String pathOf(ArgoDiagram ad) {
        Object ns = ad.getNamespace();
        if (ns == null) return "";
        String n = (String) Model.getFacade().getName(ns);
        return n == null ? "" : n;
    }

    private static final class Row {
        final String name;
        final String path;
        final String uuid;
        Row(String name, String path, String uuid) {
            this.name = name;
            this.path = path;
            this.uuid = uuid;
        }
    }

    private static final class LinkTableModel extends AbstractTableModel {
        private static final String[] COLS = {"Diagram", "Path", "UUID"};
        private List<Row> rows = new ArrayList<Row>();
        void setRows(List<Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            if (c == 0) return row.name;
            if (c == 1) return row.path;
            return row.uuid;
        }
    }
}
