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
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import org.argouml.ai.agent.AiClient;
import org.argouml.ai.agent.AiRequest;
import org.argouml.ai.agent.AiResponse;
import org.argouml.ai.agent.SidecarConfig;
import org.argouml.ai.ops.OpExecutor;
import org.argouml.ai.ops.PlannedOp;
import org.argouml.ai.ops.PlannedOpParser;
import org.argouml.ai.tools.ClassDiagramTools;
import org.argouml.ai.tools.ProjectSnapshot;
import org.argouml.application.api.AbstractArgoJPanel;
import org.argouml.i18n.Translator;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * The AI assistant's right-hand tab.
 *
 * <p>Layout (BorderLayout):
 * <ul>
 *   <li>NORTH: a thin "top bar" with the current diagram name, a
 *       "clear" button and a "settings" button (settings is a no-op
 *       for the MVP).</li>
 *   <li>CENTER: a vertical {@link JSplitPane} with the
 *       {@link ChatPane} on top and the {@link PreviewPane} below.</li>
 *   <li>SOUTH: the input bar with a {@link JTextField}, a send button
 *       and a cancel button.</li>
 * </ul>
 *
 * <p>Threading: the {@link AiClient#send} call is blocking, so the
 * send flow offloads the request to a {@link SwingWorker} and updates
 * the UI on the EDT from {@code done()}. The cancel button is a
 * no-op for the MVP because the underlying HTTP client does not yet
 * support cancellation; it just flips the button state to reflect
 * that there is nothing to cancel.
 *
 * <p>Diagram binding: the active ArgoUML diagram is read from
 * {@link ProjectManager} every time the user presses "send" (and
 * each {@link #refreshDiagramLabel()} call). The MVP does not listen
 * for diagram-switch events; the next user-driven send will see the
 * change.
 */
public class AiPanel extends AbstractArgoJPanel {

    private static final long serialVersionUID = 1L;

    private static final String SYSTEM_PROMPT =
            "\u4f60\u662f ArgoUML \u7684 AI \u52a9\u624b\u3002"
            + "\u5f53\u524d\u4efb\u52a1\u662f\u6839\u636e\u7528\u6237"
            + "\u7684\u63cf\u8ff0\u5728\u7c7b\u56fe\u4e0a\u521b\u5efa"
            + "\u3001\u4fee\u6539\u6216\u67e5\u770b UML \u5143\u7d20\u3002"
            + "\u4f60\u53ea\u80fd\u901a\u8fc7\u5de5\u5177\u8c03\u7528"
            + "\u6765\u4fee\u6539\u6a21\u578b\uff0c\u4e0d\u8981\u76f4"
            + "\u63a5\u8fd4\u56de\u4ee3\u7801\u3002";

    private final ChatPane chatPane;
    private final PreviewPane previewPane;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton cancelButton;
    private final JButton applyButton;
    private final JButton clearButton;
    private final JButton settingsButton;
    private final JLabel diagramLabel;

    private final AiClient client;
    private final PlannedOpParser parser;

    private SwingWorker<AiResponse, Void> currentWorker;

    /**
     * Build a panel wired to the user-wide sidecar config and the
     * default toolset. The constructor never throws; if the model
     * subsystem has not been initialised yet, the diagram label
     * simply shows the "no diagram" placeholder.
     */
    public AiPanel() {
        this(SidecarConfig.getInstance(), new PlannedOpParser());
    }

    /**
     * Test-friendly constructor: callers can supply a custom
     * {@link SidecarConfig} (e.g. a temp-file backed one) and parser.
     *
     * @param config the sidecar settings; must not be {@code null}.
     * @param parser the op parser; must not be {@code null}.
     */
    public AiPanel(SidecarConfig config, PlannedOpParser parser) {
        super("ai.panel.title");
        setLayout(new BorderLayout());
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (parser == null) {
            throw new IllegalArgumentException("parser must not be null");
        }
        this.client = new AiClient(config);
        this.parser = parser;

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        diagramLabel = new JLabel(diagramLabelText(null));
        clearButton = new JButton(
                Translator.localize("ai.button.clear"));
        settingsButton = new JButton(
                Translator.localize("ai.button.settings"));
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                chatPane.clear();
                previewPane.setOps(null);
                applyButton.setEnabled(false);
            }
        });
        settingsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                // MVP: settings is a no-op. The future settings dialog
                // lives elsewhere; we just log to console for now.
                System.out.println("[argouml.ai] settings clicked (no-op)");
            }
        });
        topBar.add(diagramLabel);
        topBar.add(clearButton);
        topBar.add(settingsButton);
        add(topBar, BorderLayout.NORTH);

        chatPane = new ChatPane();
        previewPane = new PreviewPane();
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(chatPane), new JScrollPane(previewPane));
        split.setDividerLocation(0.6);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        JPanel inputBar = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton(
                Translator.localize("ai.button.send"));
        cancelButton = new JButton(
                Translator.localize("ai.button.cancel"));
        applyButton = new JButton(
                Translator.localize("ai.button.apply"));
        cancelButton.setEnabled(false);
        applyButton.setEnabled(false);
        sendButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sendUserInput();
            }
        });
        inputField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sendUserInput();
            }
        });
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                cancelInFlightRequest();
            }
        });
        applyButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                applyPreview();
            }
        });
        JPanel inputButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        inputButtons.add(applyButton);
        inputButtons.add(sendButton);
        inputButtons.add(cancelButton);
        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(inputButtons, BorderLayout.EAST);
        add(inputBar, BorderLayout.SOUTH);
        refreshDiagramLabel();
    }

    /**
     * @return the chat pane. Exposed for tests that want to inspect
     *         the conversation log.
     */
    public ChatPane getChatPane() {
        return chatPane;
    }

    /**
     * @return the preview pane. Exposed for tests that want to drive
     *         setOps / markApplied directly.
     */
    public PreviewPane getPreviewPane() {
        return previewPane;
    }

    /**
     * Refresh the diagram label from {@link ProjectManager}.
     * Call this when the user switches diagrams so the label stays
     * current without waiting for the next send.
     */
    public void refreshDiagramLabel() {
        ArgoDiagram d = currentDiagram();
        diagramLabel.setText(diagramLabelText(d));
        sendButton.setEnabled(d != null);
    }

    // -----------------------------------------------------------------
    // Send flow
    // -----------------------------------------------------------------

    private void sendUserInput() {
        if (currentWorker != null && !currentWorker.isDone()) {
            // already in flight
            return;
        }
        String text = inputField.getText();
        if (text == null || text.trim().length() == 0) {
            return;
        }
        ArgoDiagram diagram = currentDiagram();
        if (diagram == null) {
            chatPane.appendError(
                    Translator.localize("ai.error.no-active-diagram"));
            return;
        }
        chatPane.appendUser(text);
        inputField.setText("");

        final String userText = text;
        final ArgoDiagram diag = diagram;
        final ProjectSnapshot.Snapshot snapshot;
        try {
            snapshot = ProjectSnapshot.snapshot(diag);
        } catch (RuntimeException ex) {
            chatPane.appendError(
                    Translator.localize("ai.error.snapshot-failed")
                    + ": " + ex.getMessage());
            return;
        }

        sendButton.setEnabled(false);
        cancelButton.setEnabled(true);

        currentWorker = new SwingWorker<AiResponse, Void>() {
            @Override
            protected AiResponse doInBackground() throws IOException {
                AiRequest req = new AiRequest();
                req.setModel(SidecarConfig.getInstance().getModel());
                req.setTools(ClassDiagramTools.all());
                req.addMessage("system", SYSTEM_PROMPT);
                req.addMessage("system",
                        "snapshot: " + snapshot.toJson());
                req.addMessage("user", userText);
                return client.send(req);
            }
            @Override
            protected void done() {
                try {
                    AiResponse resp = get();
                    if (resp == null) {
                        chatPane.appendError(
                                Translator.localize("ai.error.empty-response"));
                    } else {
                        if (resp.getContent() != null
                                && resp.getContent().length() > 0) {
                            chatPane.appendAssistant(resp.getContent());
                        }
                        List<PlannedOp> ops;
                        try {
                            ops = parser.parse(resp);
                        } catch (IllegalArgumentException ex) {
                            chatPane.appendError(
                                    Translator.localize(
                                            "ai.error.parse-failed")
                                    + ": " + ex.getMessage());
                            ops = java.util.Collections.emptyList();
                        }
                        if (!ops.isEmpty()) {
                            previewPane.setOps(ops);
                            applyButton.setEnabled(true);
                            chatPane.appendTool("preview", ops.size()
                                    + " ops");
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    chatPane.appendError(
                            Translator.localize("ai.error.interrupted"));
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() == null
                            ? ex : ex.getCause();
                    chatPane.appendError(
                            Translator.localize("ai.error.request-failed")
                            + ": " + cause.getMessage());
                } finally {
                    sendButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                    currentWorker = null;
                }
            }
        };
        currentWorker.execute();
    }

    private void cancelInFlightRequest() {
        // The AiClient does not yet expose a cancel hook; the MVP
        // simply disables the cancel button. A real implementation
        // would call HttpURLConnection.disconnect() on a worker
        // thread (see Task 10 backlog item T10.4).
        cancelButton.setEnabled(false);
    }

    /**
     * Apply the ops currently shown in the preview pane to the
     * active diagram. The executor is called with per-op success and
     * failure callbacks so that rows are marked individually - an
     * exception thrown by op N does not falsely mark rows 0..N-1 as
     * failed.
     *
     * <p>When at least one op failed, the preview pane is left
     * visible so the user can fix inputs and retry. On a fully
     * successful apply the preview pane is cleared.
     */
    public void applyPreview() {
        ArgoDiagram diagram = currentDiagram();
        if (diagram == null) {
            chatPane.appendError(
                    Translator.localize("ai.error.no-active-diagram"));
            return;
        }
        final List<PlannedOp> ops = previewPane.getOps();
        if (ops.isEmpty()) {
            return;
        }
        final int[] successCount = { 0 };
        final int[] failCount = { 0 };
        final StringBuilder firstError = new StringBuilder();
        try {
            new OpExecutor(diagram).apply(ops,
                new IntConsumer() { public void accept(int row) {
                    previewPane.markApplied(row);
                    successCount[0]++;
                }},
                new BiConsumer<Integer, Throwable>() {
                    public void accept(Integer row, Throwable ex) {
                        String reason = ex.getMessage() == null
                            ? ex.getClass().getSimpleName()
                            : ex.getMessage();
                        previewPane.markFailed(row.intValue(), reason);
                        failCount[0]++;
                        if (firstError.length() == 0) {
                            firstError.append(reason);
                        }
                    }
                });
        } catch (RuntimeException ex) {
            // Defensive: if apply() itself throws (not via per-op
            // callback), surface the error. Per-row marking has
            // already happened for any ops that were attempted
            // before the throw.
            chatPane.appendError(
                    Translator.localize("ai.error.apply-failed")
                    + ": " + ex.getMessage());
        }
        String msg = Translator.localize("ai.chat.applied")
                + ": " + successCount[0] + "/" + ops.size();
        if (failCount[0] > 0) {
            msg = msg + " (" + failCount[0]
                    + " \u5931\u8d25" + ")";
            if (firstError.length() > 0) {
                msg = msg + " - " + firstError.toString();
            }
        }
        chatPane.appendSystem(msg);
        if (failCount[0] == 0) {
            previewPane.setOps(null);
        }
        // else: keep the preview visible so the user can see which
        // rows failed and fix inputs.
    }

    // -----------------------------------------------------------------
    // Diagram lookup
    // -----------------------------------------------------------------

    private static ArgoDiagram currentDiagram() {
        try {
            Project p = ProjectManager.getManager().getCurrentProject();
            if (p == null) {
                return null;
            }
            return p.getActiveDiagram();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String diagramLabelText(ArgoDiagram d) {
        String prefix = Translator.localize("ai.topbar.diagram");
        if (d == null) {
            return prefix + ": " + Translator.localize(
                    "ai.topbar.no-diagram");
        }
        String name = d.getName();
        if (name == null || name.length() == 0) {
            name = Translator.localize("ai.topbar.no-diagram");
        }
        return prefix + ": " + name;
    }
}