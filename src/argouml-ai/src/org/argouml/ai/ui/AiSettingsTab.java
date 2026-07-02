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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.argouml.ai.agent.SidecarConfig;
import org.argouml.application.api.GUISettingsTabInterface;
import org.argouml.i18n.Translator;

/**
 * Settings tab for the AI sidecar ({@link SidecarConfig}).
 *
 * <p>Lets the user edit the four sidecar settings - endpoint URL,
 * API key, model name and HTTP timeout (seconds) - and persist them
 * back to {@code ~/.argouml/ai-config.properties}. The tab is
 * wired both as a self-contained panel (with its own Save / Reset
 * buttons) and as a {@link GUISettingsTabInterface} so the
 * ArgoUML settings dialog can drive it through the standard
 * {@code handleSettingsTabSave} / {@code handleSettingsTabRefresh} /
 * {@code handleSettingsTabCancel} hooks.
 *
 * <p>Encoding: the panel is plain ASCII; all user-facing labels are
 * routed through {@link Translator#localize} against the
 * {@code org/argouml/i18n/ai.properties} bundle.
 */
public class AiSettingsTab extends JPanel implements GUISettingsTabInterface {

    private static final long serialVersionUID = 1L;

    private static final int MIN_TIMEOUT = 10;
    private static final int MAX_TIMEOUT = 300;
    private static final int STEP_TIMEOUT = 10;

    private final SidecarConfig config;

    private JTextField endpointField;
    private JPasswordField apiKeyField;
    private JTextField modelField;
    private JSpinner timeoutSpinner;
    private JButton saveButton;
    private JButton resetButton;

    /**
     * Build a tab backed by the user-wide {@link SidecarConfig} singleton.
     */
    public AiSettingsTab() {
        this(SidecarConfig.getInstance());
    }

    /**
     * Test-friendly constructor: callers can supply a custom
     * {@link SidecarConfig} (typically a temp-file backed one) so
     * tests do not touch the real user config.
     *
     * @param config the sidecar settings; must not be {@code null}.
     */
    public AiSettingsTab(SidecarConfig config) {
        super(new BorderLayout());
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        buildPanel();
        handleSettingsTabRefresh();
    }

    private void buildPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(javax.swing.BorderFactory.createEmptyBorder(
                10, 10, 10, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = 0;

        c.gridx = 0;
        form.add(new JLabel(Translator.localize("ai.settings.label.endpoint")),
                c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        endpointField = new JTextField(30);
        form.add(endpointField, c);
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;

        c.gridy++;
        c.gridx = 0;
        form.add(new JLabel(Translator.localize("ai.settings.label.apiKey")),
                c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        apiKeyField = new JPasswordField(30);
        form.add(apiKeyField, c);
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;

        c.gridy++;
        c.gridx = 0;
        form.add(new JLabel(Translator.localize("ai.settings.label.model")),
                c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        modelField = new JTextField(30);
        form.add(modelField, c);
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;

        c.gridy++;
        c.gridx = 0;
        form.add(new JLabel(Translator.localize("ai.settings.label.timeout")),
                c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        int initial = config.getTimeoutSec();
        if (initial < MIN_TIMEOUT) {
            initial = MIN_TIMEOUT;
        } else if (initial > MAX_TIMEOUT) {
            initial = MAX_TIMEOUT;
        }
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(
                initial, MIN_TIMEOUT, MAX_TIMEOUT, STEP_TIMEOUT));
        form.add(timeoutSpinner, c);
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;

        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveButton = new JButton(Translator.localize("ai.settings.button.save"));
        resetButton = new JButton(Translator.localize("ai.settings.button.reset"));
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                handleSettingsTabSave();
            }
        });
        resetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                handleSettingsTabRefresh();
            }
        });
        buttons.add(saveButton);
        buttons.add(resetButton);
        add(buttons, BorderLayout.SOUTH);
    }

    /**
     * @return the endpoint URL text field. Exposed for tests.
     */
    public JTextField getEndpointField() {
        return endpointField;
    }

    /**
     * @return the API key password field. Exposed for tests.
     */
    public JPasswordField getApiKeyField() {
        return apiKeyField;
    }

    /**
     * @return the model name text field. Exposed for tests.
     */
    public JTextField getModelField() {
        return modelField;
    }

    /**
     * @return the timeout spinner. Exposed for tests.
     */
    public JSpinner getTimeoutSpinner() {
        return timeoutSpinner;
    }

    /*
     * @see GUISettingsTabInterface#handleSettingsTabRefresh()
     */
    public void handleSettingsTabRefresh() {
        String endpoint = config.getEndpoint();
        endpointField.setText(endpoint == null ? "" : endpoint);
        String apiKey = config.getApiKey();
        apiKeyField.setText(apiKey == null ? "" : apiKey);
        String model = config.getModel();
        modelField.setText(model == null ? "" : model);
        int t = config.getTimeoutSec();
        if (t < MIN_TIMEOUT) {
            t = MIN_TIMEOUT;
        } else if (t > MAX_TIMEOUT) {
            t = MAX_TIMEOUT;
        }
        timeoutSpinner.setValue(Integer.valueOf(t));
    }

    /*
     * @see GUISettingsTabInterface#handleSettingsTabSave()
     */
    public void handleSettingsTabSave() {
        config.setEndpoint(endpointField.getText());
        config.setApiKey(new String(apiKeyField.getPassword()));
        config.setModel(modelField.getText());
        config.setTimeoutSec(
                ((Integer) timeoutSpinner.getValue()).intValue());
        try {
            config.save();
        } catch (IOException ex) {
            // The settings dialog cannot meaningfully recover from a
            // failed save; surface the error via stderr so the
            // application log captures it without crashing the EDT.
            System.err.println("[argouml.ai] failed to save sidecar config: "
                    + ex.getMessage());
        }
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
        // The sidecar config has no notion of "factory defaults beyond
        // the file" - the file IS the source of truth, so reverting
        // to it is the same operation as refreshing from disk.
        handleSettingsTabRefresh();
    }

    /*
     * @see GUISettingsTabInterface#getTabKey()
     */
    public String getTabKey() {
        return "ai.settings.tab.title";
    }

    /*
     * @see GUISettingsTabInterface#getTabPanel()
     */
    public JPanel getTabPanel() {
        return this;
    }
}
