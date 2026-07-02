/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.ui;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.argouml.ai.ops.PlannedOp;
import org.argouml.i18n.Translator;

/**
 * Read-only {@link JTable} that displays the {@link PlannedOp}s an
 * AI assistant returned for the current user turn.
 *
 * <p>The table has five columns: {@code #} (row number, 1-indexed),
 * {@code op} (the {@link PlannedOp.Type} name), {@code object} (the
 * model element the op touches, when one exists), {@code params}
 * (a compact summary of the field bag), and {@code status}
 * ({@code pending} / {@code applied} / {@code failed: reason}).
 *
 * <p>The status column starts as the localised "pending" string and
 * is rewritten in place by {@link #markApplied(int)} /
 * {@link #markFailed(int, String)} as the user clicks "apply" or
 * individual rows fail. The same op list is stored in {@link #ops}
 * so the apply handler can call {@link #getOps()} without
 * re-extracting from the table model.
 *
 * <p>The table model is rebuilt by every {@link #setOps(List)} call
 * because {@link DefaultTableModel#setRowCount(int)} followed by
 * {@code addRow} is not enough to also reset the column types after
 * a status change leaves Object-typed cells behind.
 */
public class PreviewPane extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int COL_NO = 0;
    private static final int COL_OP = 1;
    private static final int COL_OBJECT = 2;
    private static final int COL_PARAMS = 3;
    private static final int COL_STATUS = 4;

    private final JTable table;
    private List<PlannedOp> ops = Collections.emptyList();

    /**
     * Build a preview pane with five columns and no rows.
     */
    public PreviewPane() {
        super(new BorderLayout());
        String[] headers = new String[] {
                Translator.localize("ai.preview.column.no"),
                Translator.localize("ai.preview.column.op"),
                Translator.localize("ai.preview.column.object"),
                Translator.localize("ai.preview.column.params"),
                Translator.localize("ai.preview.column.status") };
        DefaultTableModel model = new DefaultTableModel(headers, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Replace the displayed ops with {@code ops}. A {@code null}
     * argument is normalised to an empty list.
     *
     * @param ops the new ops to display; may be {@code null}.
     */
    public void setOps(List<PlannedOp> ops) {
        if (ops == null) {
            ops = Collections.emptyList();
        }
        this.ops = new ArrayList<PlannedOp>(ops);
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        String pending = Translator.localize("ai.preview.status.pending");
        for (int i = 0; i < ops.size(); i++) {
            PlannedOp op = ops.get(i);
            model.addRow(new Object[] {
                    Integer.valueOf(i + 1),
                    op == null ? "" : op.getType().name(),
                    describeObject(op),
                    describeParams(op),
                    pending });
        }
    }

    /**
     * @return the ops currently displayed. Returns a defensive copy
     *         (defensive copy of an immutable view) so callers can
     *         iterate without worrying about concurrent mutation.
     *         Returns an empty list, never {@code null}.
     */
    public List<PlannedOp> getOps() {
        return Collections.unmodifiableList(ops);
    }

    /**
     * Set the status of row {@code row} to the localised "applied"
     * marker. Out-of-range indices are silently ignored; the caller
     * is responsible for not invoking this after {@link #setOps} has
     * shrunk the model.
     *
     * @param row the row to update, 0-indexed.
     */
    public void markApplied(int row) {
        setStatus(row, Translator.localize("ai.preview.status.applied"));
    }

    /**
     * Set the status of row {@code row} to {@code "failed: <reason>"}.
     * Out-of-range indices are silently ignored.
     *
     * @param row    the row to update, 0-indexed.
     * @param reason the failure reason; appended to the localised
     *               "failed" prefix. {@code null} or empty is
     *               normalised to the bare "failed" string.
     */
    public void markFailed(int row, String reason) {
        String failed = Translator.localize("ai.preview.status.failed");
        String suffix = (reason == null || reason.length() == 0)
                ? "" : ": " + reason;
        setStatus(row, failed + suffix);
    }

    /**
     * Set every row's status to the localised "applied" marker.
     * Useful after a successful {@code OpExecutor.apply(...)} call.
     */
    public void markAllApplied() {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        String applied = Translator.localize("ai.preview.status.applied");
        for (int i = 0; i < model.getRowCount(); i++) {
            model.setValueAt(applied, i, COL_STATUS);
        }
    }

    /**
     * @return the underlying {@link JTable}. Exposed for tests so
     *         they can read column counts and cell values without
     *         reflection.
     */
    public JTable getTable() {
        return table;
    }

    // -----------------------------------------------------------------
    // Cell formatters
    // -----------------------------------------------------------------

    private static String describeObject(PlannedOp op) {
        if (op == null) {
            return "";
        }
        switch (op.getType()) {
        case ADD_CLASS:
        case ADD_INTERFACE:
        case DELETE_CLASS:
            return orEmpty(op.getString("name"));
        case ADD_ATTRIBUTE:
        case ADD_OPERATION:
            return orEmpty(op.getString("className"));
        case ADD_ASSOCIATION:
            return orEmpty(op.getString("classA")) + " - "
                    + orEmpty(op.getString("classB"));
        case ADD_GENERALIZATION:
            return orEmpty(op.getString("subclass")) + " -> "
                    + orEmpty(op.getString("superclass"));
        case ADD_DEPENDENCY:
            return orEmpty(op.getString("client")) + " > "
                    + orEmpty(op.getString("supplier"));
        case RENAME_CLASS:
            return orEmpty(op.getString("oldName")) + " -> "
                    + orEmpty(op.getString("newName"));
        case LIST_CLASSES:
            return "";
        default:
            return "";
        }
    }

    private static String describeParams(PlannedOp op) {
        if (op == null) {
            return "";
        }
        switch (op.getType()) {
        case ADD_CLASS:
        case ADD_INTERFACE:
            return "x=" + op.getInt("x") + ",y=" + op.getInt("y");
        case ADD_ATTRIBUTE:
            return "name=" + orEmpty(op.getString("name"))
                    + ",type=" + orEmpty(op.getString("type"))
                    + ",visibility=" + orEmpty(op.getString("visibility"));
        case ADD_OPERATION:
            return "name=" + orEmpty(op.getString("name"))
                    + ",returnType=" + orEmpty(op.getString("returnType"))
                    + ",visibility=" + orEmpty(op.getString("visibility"));
        case ADD_ASSOCIATION:
            return "multA=" + orEmpty(op.getString("multA"))
                    + ",multB=" + orEmpty(op.getString("multB"))
                    + ",name=" + orEmpty(op.getString("name"));
        case ADD_GENERALIZATION:
            return "";
        case ADD_DEPENDENCY:
            return "name=" + orEmpty(op.getString("name"));
        case RENAME_CLASS:
            return "";
        case DELETE_CLASS:
            return "";
        case LIST_CLASSES:
            return "";
        default:
            return "";
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private void setStatus(int row, String status) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        if (row < 0 || row >= model.getRowCount()) {
            return;
        }
        model.setValueAt(status, row, COL_STATUS);
    }
}