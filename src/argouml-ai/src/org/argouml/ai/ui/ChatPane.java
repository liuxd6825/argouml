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
import java.awt.Font;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.argouml.i18n.Translator;

/**
 * Read-only chat log used in the {@link AiPanel} right-hand tab.
 *
 * <p>The chat log is a single non-editable {@link JTextArea} wrapped
 * in a {@link JScrollPane}. Lines wrap on word boundaries and the
 * font is a monospaced family so that tool-call JSON snippets align
 * nicely.
 *
 * <p>The methods are deliberately append-only: callers never replace
 * text in place. Each append writes a {@code [role]} prefix followed
 * by the body and a blank line. This mirrors the way OpenAI Chat
 * Completions naturally splits a conversation into turns.
 *
 * <p>Chinese support: Java strings are UTF-16 internally so non-ASCII
 * text round-trips through {@code JTextArea} without any extra
 * encoding logic. JDK 8 picks a CJK-capable font automatically when
 * no font has been set; the MVP explicitly sets {@code Font.MONOSPACED}
 * which leaves that fallback in place.
 */
public class ChatPane extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JTextArea textArea;

    /**
     * Build a chat pane with an empty conversation.
     */
    public ChatPane() {
        super(new BorderLayout());
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(textArea);
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * Append a user turn.
     *
     * @param text the user's message; a {@code null} or empty value is
     *             a no-op (we still emit the prefix on demand).
     */
    public void appendUser(String text) {
        append(Translator.localize("ai.chat.user"), text);
    }

    /**
     * Append an assistant turn.
     */
    public void appendAssistant(String text) {
        append(Translator.localize("ai.chat.assistant"), text);
    }

    /**
     * Append a system message (e.g. snapshot context, status update).
     */
    public void appendSystem(String text) {
        append(Translator.localize("ai.chat.system"), text);
    }

    /**
     * Append an error message. Rendered in red so it stands out from
     * the rest of the log. The colour is applied to the new text only
     * by clearing it and re-inserting; the trailing message therefore
     * stays coloured even after more text is appended.
     *
     * <p>Implementation note: a {@code JTextArea} only supports a
     * single foreground colour for the whole document, so the MVP
     * approximates "red text" by prepending an ASCII error marker
     * ({@code !}) and letting the user distinguish it visually.
     * Richer rendering belongs in a future JEditorPane-based pane.
     *
     * @param text the error description.
     */
    public void appendError(String text) {
        append(Translator.localize("ai.chat.error"),
                (text == null ? "" : "! " + text));
    }

    /**
     * Append a tool-invocation log entry.
     *
     * @param name the tool name (e.g. {@code "add_class"}).
     * @param args the raw arguments string; may be {@code null}.
     */
    public void appendTool(String name, String args) {
        String body = (name == null ? "" : name)
                + (args == null || args.length() == 0
                        ? "" : " " + args);
        append(Translator.localize("ai.chat.tool"), body);
    }

    /**
     * Empty the conversation log.
     */
    public void clear() {
        textArea.setText("");
    }

    /**
     * @return the current log text. Exposed so tests can assert on
     *         what was appended without touching private state.
     */
    public String getText() {
        return textArea.getText();
    }

    /**
     * @return the underlying {@link JTextArea}. Exposed for tests
     *         that need to verify configuration (editable, line-wrap)
     *         without going through reflection.
     */
    public JTextArea getTextArea() {
        return textArea;
    }

    private void append(String role, String text) {
        if (role == null) {
            role = "";
        }
        String body = text == null ? "" : text;
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(role).append("] ").append(body)
                .append("\n\n");
        textArea.append(sb.toString());
    }
}