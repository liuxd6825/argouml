/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai;

import java.util.List;

import junit.framework.TestCase;

import org.argouml.ai.ui.AiPanel;
import org.argouml.ai.ui.AiSettingsTab;
import org.argouml.application.api.AbstractArgoJPanel;
import org.argouml.application.api.GUISettingsTabInterface;

/**
 * Smoke tests for {@link InitAiSubsystem}.
 *
 * <p>Verifies the {@link org.argouml.application.api.InitSubsystem}
 * contract: {@code init()} must not throw, both tab lists must be
 * non-empty and contain the expected panel / tab types. The test
 * deliberately avoids touching {@code ProjectManager} or any GEF
 * type, so it can run without the ArgoUML model subsystem being
 * initialised first (unlike the GUI subsystem test in
 * argouml-app/tests/.../GUITestInitSubsystem).
 *
 * <p>Encoding: ASCII.
 */
public class TestInitAiSubsystem extends TestCase {

    /**
     * Spec smoke: the subsystem must expose exactly one details
     * tab and one settings tab.
     */
    public void testSubsystemProvidesTabs() {
        InitAiSubsystem s = new InitAiSubsystem();
        assertEquals(1, s.getDetailsTabs().size());
        assertEquals(1, s.getSettingsTabs().size());
    }

    /**
     * {@code init()} must complete without throwing. Side effects
     * (loading the config file) are exercised by the
     * {@code TestSidecarConfig} suite; here we only assert the
     * subsystem entry point is safe to call.
     */
    public void testSubsystemInitDoesNotThrow() {
        InitAiSubsystem s = new InitAiSubsystem();
        s.init();
    }

    /**
     * The details tab must be an {@link AiPanel} instance, so the
     * caller (and tests) can cast it back to the concrete type.
     */
    public void testGetDetailsTabsReturnsAiPanel() {
        InitAiSubsystem s = new InitAiSubsystem();
        List<AbstractArgoJPanel> tabs = s.getDetailsTabs();
        assertTrue("details tab must be an AiPanel instance",
                tabs.get(0) instanceof AiPanel);
    }

    /**
     * The settings tab must be an {@link AiSettingsTab} instance.
     */
    public void testGetSettingsTabsReturnsAiSettingsTab() {
        InitAiSubsystem s = new InitAiSubsystem();
        List<GUISettingsTabInterface> tabs = s.getSettingsTabs();
        assertTrue("settings tab must be an AiSettingsTab instance",
                tabs.get(0) instanceof AiSettingsTab);
    }

}