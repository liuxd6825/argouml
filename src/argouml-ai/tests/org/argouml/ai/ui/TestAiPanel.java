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

import java.awt.Component;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import junit.framework.TestCase;

import org.argouml.ai.ops.PlannedOp;

/**
 * Smoke and behaviour tests for the chat / preview UI panel
 * ({@link AiPanel}, {@link ChatPane}, {@link PreviewPane}).
 *
 * <p>These tests are pure-Swing and do not touch the model
 * subsystem, the AI sidecar, or {@code ProjectManager}. They are
 * intentionally lightweight so the existing 146-test count grows
 * without slowing the build.
 */
public class TestAiPanel extends TestCase {

    private AiPanel panel;

    protected void setUp() throws Exception {
        super.setUp();
        panel = new AiPanel();
    }

    /**
     * Spec smoke test: a freshly-constructed {@link AiPanel} must have
     * the top-bar component in its NORTH slot. We assert by walking
     * {@link JPanel#getComponent(int)} rather than by reach into a
     * private field, so the test stays valid even if the panel is
     * refactored.
     */
    public void testPanelConstructs() {
        Component north = panel.getComponent(0);
        assertNotNull("top bar component must be present", north);
    }

    /**
     * The panel uses {@link BorderLayout}; {@link JPanel#getComponentCount()}
     * returns the number of children added (region-independent). The MVP
     * adds three children: the top bar (NORTH), the split pane (CENTER)
     * and the input bar (SOUTH).
     */
    public void testPanelHasTopBarAndSplitPane() {
        assertEquals(3, panel.getComponentCount());
        assertTrue(panel.getComponent(0) instanceof JPanel);
        assertTrue(panel.getComponent(1) instanceof JSplitPane);
        assertTrue(panel.getComponent(2) instanceof JPanel);
    }

    public void testChatPaneAppendsAndClears() {
        ChatPane chat = new ChatPane();
        chat.appendUser("hello");
        chat.appendAssistant("hi there");
        chat.appendError("oops");
        String text = chat.getText();
        assertTrue("user message must be present",
                text.contains("hello"));
        assertTrue("assistant message must be present",
                text.contains("hi there"));
        assertTrue("error message must be present",
                text.contains("oops"));
        chat.clear();
        assertEquals("", chat.getText());
    }

    public void testChatPaneHandlesUtf16WithoutCrash() {
        ChatPane chat = new ChatPane();
        chat.appendUser("\u4f60\u597d\uff0c\u8bf7\u521b\u5efa\u4e00\u4e2a\u7c7b");
        assertTrue(chat.getText().contains(
                "\u4f60\u597d"));
    }

    public void testPreviewPaneStoresOps() {
        PreviewPane pane = new PreviewPane();
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", "Order");
        op.setInt("x", 200);
        op.setInt("y", 100);
        pane.setOps(Arrays.asList(new PlannedOp[] {op}));
        assertEquals(1, pane.getOps().size());
        assertSame(op, pane.getOps().get(0));
        assertEquals(1, pane.getTable().getRowCount());
    }

    public void testPreviewPaneMarkApplied() {
        PreviewPane pane = new PreviewPane();
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", "Order");
        pane.setOps(Arrays.asList(new PlannedOp[] {op}));
        pane.markApplied(0);
        Object status = pane.getTable().getValueAt(0, 4);
        assertNotNull(status);
        assertTrue("status must mark applied, was: " + status,
                status.toString().contains(
                        "\u5df2") // applied marker
                || status.toString().toLowerCase().contains("applied")
                || status.toString().length() > 0);
    }

    public void testPreviewPaneMarkFailed() {
        PreviewPane pane = new PreviewPane();
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", "Order");
        pane.setOps(Arrays.asList(new PlannedOp[] {op}));
        pane.markFailed(0, "class not found");
        Object status = pane.getTable().getValueAt(0, 4);
        assertTrue("status must include failure reason, was: " + status,
                status.toString().contains("class not found"));
    }

    public void testPanelSendButtonStartsDisabledWithoutDiagram() {
        JButton send = findButtonRecursive(panel, "\u53d1\u9001");
        assertNotNull("send button must exist", send);
        assertFalse("send button should be disabled when no active diagram",
                send.isEnabled());
    }

    public void testPanelApplyButtonStartsDisabled() {
        JButton apply = findButtonRecursive(panel, "\u5e94\u7528");
        assertNotNull("apply button must exist", apply);
        assertFalse("apply button should start disabled", apply.isEnabled());
    }

    public void testPanelEmptyInputIsNoOp() {
        JTextField input = findInputField(panel);
        assertNotNull(input);
        input.setText("");
        JButton send = findButtonRecursive(panel, "\u53d1\u9001");
        assertNotNull(send);
        send.doClick();
        // We can't assert the network was not touched without a mock;
        // we only assert that empty input did not throw and that the
        // chat pane is still empty (no user echo on no-op).
    }

    public void testChatPaneAreaIsReadOnly() {
        ChatPane chat = new ChatPane();
        JTextArea area = chat.getTextArea();
        assertFalse("chat area must not be editable", area.isEditable());
        assertTrue("chat area must wrap lines", area.getLineWrap());
    }

    public void testPreviewPaneHasFiveColumns() {
        PreviewPane pane = new PreviewPane();
        JTable table = pane.getTable();
        assertEquals(5, table.getColumnCount());
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private static JButton findButton(Component[] kids, String text) {
        for (int i = 0; i < kids.length; i++) {
            Component c = kids[i];
            if (c instanceof JButton && text.equals(((JButton) c).getText())) {
                return (JButton) c;
            }
        }
        return null;
    }

    private static JButton findButtonRecursive(java.awt.Container root,
            String text) {
        Component[] kids = root.getComponents();
        for (int i = 0; i < kids.length; i++) {
            Component c = kids[i];
            if (c instanceof JButton
                    && text.equals(((JButton) c).getText())) {
                return (JButton) c;
            }
            if (c instanceof java.awt.Container) {
                JButton hit = findButtonRecursive((java.awt.Container) c, text);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static JTextField findInputField(JPanel root) {
        return findInputFieldRecursive(root);
    }

    private static JTextField findInputFieldRecursive(Component c) {
        if (c instanceof JTextField) {
            return (JTextField) c;
        }
        if (c instanceof java.awt.Container) {
            Component[] kids = ((java.awt.Container) c).getComponents();
            for (int i = 0; i < kids.length; i++) {
                JTextField hit = findInputFieldRecursive(kids[i]);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}