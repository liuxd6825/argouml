/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.infrastructure.config;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.argouml.application.api.GUISettingsTabInterface;
import org.argouml.i18n.Translator;

/**
 * Settings tab for the embedded HTTP server ({@link ServerConfig}).
 *
 * <p>Lets the user edit {@code enabled}, {@code port}, and
 * {@code bind} and persist them to
 * {@link ServerConfigStore#defaultFile()}, which lives at
 * {@code ~/.argouml/http-server.properties}. Apply saves and pops
 * an info dialog reminding the user to restart ArgoUML (the server
 * lifecycle is bound at startup, not on every settings change).</p>
 *
 * <p>Wired as a {@link GUISettingsTabInterface} so the standard
 * ArgoUML settings dialog can host this tab. The "Apply" button is
 * a convenience for users who reach this tab outside the dialog
 * (e.g. via a top-bar button).</p>
 *
 * <p>Encoding: ASCII. All user-facing labels route through
 * {@link Translator#localize} against the {@code http.*} keys in
 * {@code org/argouml/i18n/http.properties}.</p>
 */
public class HttpServerSettingsTab extends JPanel
        implements GUISettingsTabInterface {

    private static final long serialVersionUID = 1L;

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int STEP_PORT = 1;
    private static final int DEFAULT_PORT = 8766;
    private static final String DEFAULT_BIND = "127.0.0.1";

    private final ServerConfigStore store;
    private final boolean headless;

    private JCheckBox enabledCheck;
    private JSpinner portSpinner;
    private JTextField bindField;
    private JButton applyButton;

    /**
     * Build a tab backed by the user-wide default
     * {@link ServerConfigStore#defaultFile()}.
     */
    public HttpServerSettingsTab() {
        this(new ServerConfigStore(ServerConfigStore.defaultFile()), false);
    }

    /**
     * Test-friendly constructor: callers can supply a custom
     * {@link ServerConfigStore} (typically a temp-file backed one)
     * and set {@code headless=true} to suppress the post-save
     * {@link JOptionPane} dialog (otherwise the test will block
     * waiting for user input).
     *
     * @param store the persistence target; must not be null.
     * @param headless if true, suppress modal dialogs after save.
     */
    public HttpServerSettingsTab(ServerConfigStore store, boolean headless) {
        super(new GridBagLayout());
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
        this.headless = headless;
        buildPanel();
        handleSettingsTabRefresh();
    }

    private void buildPanel() {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = 0;

        c.gridx = 0;
        c.gridwidth = 2;
        enabledCheck = new JCheckBox(
            Translator.localize("http.settings.enable"));
        add(enabledCheck, c);
        c.gridwidth = 1;

        c.gridy++;
        c.gridx = 0;
        add(new JLabel(Translator.localize("http.settings.port")), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        portSpinner = new JSpinner(new SpinnerNumberModel(
            DEFAULT_PORT, MIN_PORT, MAX_PORT, STEP_PORT));
        add(portSpinner, c);
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;

        c.gridy++;
        c.gridx = 0;
        add(new JLabel(Translator.localize("http.settings.bind")), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        bindField = new JTextField(20);
        add(bindField, c);
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;

        c.gridy++;
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_END;
        applyButton = new JButton(
            Translator.localize("http.settings.apply"));
        applyButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                applyFromForm(true);
            }
        });
        add(applyButton, c);
    }

    /*
     * @see GUISettingsTabInterface#handleSettingsTabRefresh()
     */
    public void handleSettingsTabRefresh() {
        ServerConfig c = store.load();
        enabledCheck.setSelected(c.enabled);
        int p = c.port;
        if (p < MIN_PORT) {
            p = MIN_PORT;
        } else if (p > MAX_PORT) {
            p = MAX_PORT;
        }
        portSpinner.setValue(Integer.valueOf(p));
        bindField.setText(c.bind == null ? "" : c.bind);
    }

    /*
     * @see GUISettingsTabInterface#handleSettingsTabSave()
     */
    public void handleSettingsTabSave() {
        applyFromForm(false);
    }

    /*
     * @see GUISettingsTabInterface#handleSettingsTabCancel()
     */
    public void handleSettingsTabCancel() {
        handleSettingsTabRefresh();
    }

    /*
     * @see GUISettingsTabInterface#handleResetToDefault()
     */
    public void handleResetToDefault() {
        ServerConfig d = new ServerConfig();
        enabledCheck.setSelected(d.enabled);
        portSpinner.setValue(Integer.valueOf(d.port));
        bindField.setText(d.bind);
    }

    /*
     * @see GUISettingsTabInterface#getTabKey()
     */
    public String getTabKey() {
        return "http.settings.tab";
    }

    /*
     * @see GUISettingsTabInterface#getTabPanel()
     */
    public JPanel getTabPanel() {
        return this;
    }

    /**
     * Read the form fields, validate, and persist via
     * {@link ServerConfigStore#save(ServerConfig)}.
     *
     * <p>If validation fails, no save happens and (in non-headless
     * mode) an error dialog is shown. On successful save the optional
     * "restart ArgoUML" reminder is shown when
     * {@code showRestartPrompt} is true.</p>
     *
     * @param showRestartPrompt if true, show the post-save restart
     *     reminder dialog. The SPI {@link #handleSettingsTabSave()}
     *     path passes false because the dialog's own OK button is
     *     the user signal that they want to keep their edits.
     */
    void applyFromForm(boolean showRestartPrompt) {
        ServerConfig c = store.load();
        c.enabled = enabledCheck.isSelected();
        Integer pv = (Integer) portSpinner.getValue();
        c.port = pv.intValue();
        c.bind = bindField.getText();
        if (c.port < MIN_PORT || c.port > MAX_PORT) {
            showError(Translator.localize("http.settings.invalid.port"));
            return;
        }
        if (c.bind == null || c.bind.trim().isEmpty()) {
            showError(Translator.localize("http.settings.invalid.bind"));
            return;
        }
        try {
            store.save(c);
        } catch (IOException ex) {
            showError("Failed to save: " + ex.getMessage());
            return;
        }
        if (showRestartPrompt) {
            JOptionPane.showMessageDialog(
                this,
                Translator.localize("http.settings.applied"),
                getTabKey(),
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showError(String msg) {
        if (headless) {
            return;
        }
        JOptionPane.showMessageDialog(
            this, msg, getTabKey(), JOptionPane.WARNING_MESSAGE);
    }
}