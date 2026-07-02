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
import java.io.File;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JPanel;

import junit.framework.TestCase;

import org.argouml.ai.agent.SidecarConfig;

/**
 * Tests {@link AiSettingsTab}, the settings UI for the AI sidecar.
 *
 * <p>Each test builds a temp-file backed {@link SidecarConfig} so the
 * real user config is never touched, then constructs the panel against
 * that config and drives its fields and buttons directly. Tests MUST
 * NOT call {@link SidecarConfig#getInstance()}.
 */
public class TestAiSettingsTab extends TestCase {

    private File tmp;

    protected void setUp() throws IOException {
        tmp = File.createTempFile("argouml-ai-settings-", ".properties");
        tmp.delete();
    }

    protected void tearDown() {
        if (tmp != null && tmp.exists()) {
            tmp.delete();
        }
    }

    /**
     * Construction must read every field from the supplied
     * {@link SidecarConfig} and populate the corresponding widget.
     * The test pre-fills the file so the in-memory config has
     * recognisably non-default values.
     */
    public void testLoadsConfig() throws IOException {
        SidecarConfig cfg = new SidecarConfig(tmp);
        cfg.setEndpoint("http://example.org:9000");
        cfg.setApiKey("sk-initial");
        cfg.setModel("claude-3");
        cfg.setTimeoutSec(90);
        cfg.save();

        SidecarConfig loaded = new SidecarConfig(tmp);
        AiSettingsTab tab = new AiSettingsTab(loaded);

        assertEquals("http://example.org:9000", tab.getEndpointField().getText());
        assertEquals("sk-initial",
                new String(tab.getApiKeyField().getPassword()));
        assertEquals("claude-3", tab.getModelField().getText());
        assertEquals(90, ((Integer) tab.getTimeoutSpinner().getValue()).intValue());
    }

    /**
     * Editing the fields and clicking the internal "save" button must
     * call every {@link SidecarConfig} setter and persist the new
     * values to the underlying file.
     */
    public void testSaveButtonPersistsFields() throws IOException {
        SidecarConfig cfg = new SidecarConfig(tmp);
        cfg.setEndpoint("http://old:1000");
        cfg.setApiKey("old-key");
        cfg.setModel("old-model");
        cfg.setTimeoutSec(30);
        cfg.save();

        AiSettingsTab tab = new AiSettingsTab(cfg);

        tab.getEndpointField().setText("http://new:2000");
        tab.getApiKeyField().setText("new-key");
        tab.getModelField().setText("new-model");
        tab.getTimeoutSpinner().setValue(Integer.valueOf(120));

        JButton save = findButton(tab, "\u4fdd\u5b58");
        assertNotNull("save button must exist", save);
        save.doClick();

        // The in-memory config used by the panel must reflect the
        // new values.
        assertEquals("http://new:2000", cfg.getEndpoint());
        assertEquals("new-key", cfg.getApiKey());
        assertEquals("new-model", cfg.getModel());
        assertEquals(120, cfg.getTimeoutSec());

        // And a fresh load from disk must see the same values.
        SidecarConfig reloaded = new SidecarConfig(tmp);
        assertEquals("http://new:2000", reloaded.getEndpoint());
        assertEquals("new-key", reloaded.getApiKey());
        assertEquals("new-model", reloaded.getModel());
        assertEquals(120, reloaded.getTimeoutSec());
    }

    /**
     * Editing the fields and clicking the internal "reset" button must
     * throw away the edits and restore the values last loaded from the
     * {@link SidecarConfig} (i.e. the values on disk before the panel
     * was constructed).
     */
    public void testResetButtonRestoresFields() throws IOException {
        SidecarConfig cfg = new SidecarConfig(tmp);
        cfg.setEndpoint("http://original:3000");
        cfg.setApiKey("orig-key");
        cfg.setModel("orig-model");
        cfg.setTimeoutSec(45);
        cfg.save();

        AiSettingsTab tab = new AiSettingsTab(new SidecarConfig(tmp));

        // User edits the fields.
        tab.getEndpointField().setText("http://scratch:4000");
        tab.getApiKeyField().setText("scratch-key");
        tab.getModelField().setText("scratch-model");
        tab.getTimeoutSpinner().setValue(Integer.valueOf(15));

        JButton reset = findButton(tab, "\u91cd\u7f6e");
        assertNotNull("reset button must exist", reset);
        reset.doClick();

        // Fields must be back to the originally-loaded values.
        assertEquals("http://original:3000", tab.getEndpointField().getText());
        assertEquals("orig-key",
                new String(tab.getApiKeyField().getPassword()));
        assertEquals("orig-model", tab.getModelField().getText());
        assertEquals(45, ((Integer) tab.getTimeoutSpinner().getValue()).intValue());
    }

    /**
     * {@link AiSettingsTab} must identify itself to the ArgoUML
     * settings framework via a non-empty tab key. The test is
     * deliberately tolerant about the exact value: only "non-null,
     * non-empty" is asserted so the bundle key can evolve without
     * breaking the contract.
     */
    public void testTabKeyIsNonEmpty() {
        AiSettingsTab tab = new AiSettingsTab(new SidecarConfig(tmp));
        String key = tab.getTabKey();
        assertNotNull("tab key must not be null", key);
        assertTrue("tab key must not be empty", key.length() > 0);
    }

    /**
     * {@link AiSettingsTab} must also be usable as a plain
     * {@link JPanel} (it {@code extends JPanel}) so the framework can
     * drop it into the settings dialog. {@link AiSettingsTab#getTabPanel()}
     * must return the same instance the framework can show.
     */
    public void testGetTabPanelReturnsSelf() {
        AiSettingsTab tab = new AiSettingsTab(new SidecarConfig(tmp));
        assertSame(tab, tab.getTabPanel());
    }

    /**
     * No-config constructor must use the user-wide singleton and not
     * throw. We cannot assert field values (the real user config is
     * unobservable in tests), but the panel must at least construct.
     */
    public void testNoArgConstructorBuildsPanel() {
        AiSettingsTab tab = new AiSettingsTab();
        assertNotNull(tab.getEndpointField());
        assertNotNull(tab.getApiKeyField());
        assertNotNull(tab.getModelField());
        assertNotNull(tab.getTimeoutSpinner());
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private static JButton findButton(JPanel root, String textFragment) {
        return findButtonRecursive(root, textFragment);
    }

    private static JButton findButtonRecursive(java.awt.Container root,
            String textFragment) {
        Component[] kids = root.getComponents();
        for (int i = 0; i < kids.length; i++) {
            Component c = kids[i];
            if (c instanceof JButton) {
                JButton b = (JButton) c;
                String t = b.getText();
                if (t != null && t.contains(textFragment)) {
                    return b;
                }
            }
            if (c instanceof java.awt.Container) {
                JButton hit = findButtonRecursive(
                        (java.awt.Container) c, textFragment);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}
