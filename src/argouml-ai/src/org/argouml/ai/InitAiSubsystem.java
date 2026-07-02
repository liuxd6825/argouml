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

import java.util.Collections;
import java.util.List;

import org.argouml.ai.agent.SidecarConfig;
import org.argouml.ai.ui.AiPanel;
import org.argouml.ai.ui.AiSettingsTab;
import org.argouml.application.api.AbstractArgoJPanel;
import org.argouml.application.api.GUISettingsTabInterface;
import org.argouml.application.api.InitSubsystem;

/**
 * Initializer for the AI assistant subsystem.
 *
 * <p>Wired into the ArgoUML startup chain from
 * {@code org.argouml.application.Main#initializeSubsystems()}
 * (the same spot as {@code InitUseCaseDiagram} etc.). The class
 * is intentionally minimal:
 * <ul>
 *   <li>{@link #init()} primes {@link SidecarConfig#getInstance()}
 *       so the user's persisted endpoint / API key / model /
 *       timeout are loaded before any UI panel is built.</li>
 *   <li>{@link #getDetailsTabs()} returns the chat/preview panel
 *       that lives in the right-hand details pane.</li>
 *   <li>{@link #getSettingsTabs()} returns the sidecar settings
 *       tab that is added to the global settings dialog.</li>
 *   <li>{@link #getProjectSettingsTabs()} is intentionally empty:
 *       the AI assistant is global, not per-project.</li>
 * </ul>
 *
 * <p>Encoding: ASCII. No i18n strings live here directly; the
 * tab titles and labels are owned by {@link AiPanel} and
 * {@link AiSettingsTab}, both of which route user-facing strings
 * through {@code org.argouml.i18n.Translator}.
 */
public class InitAiSubsystem implements InitSubsystem {

    public void init() {
        SidecarConfig.getInstance();
    }

    public List<AbstractArgoJPanel> getDetailsTabs() {
        return Collections.<AbstractArgoJPanel>singletonList(new AiPanel());
    }

    public List<GUISettingsTabInterface> getSettingsTabs() {
        return Collections.<GUISettingsTabInterface>singletonList(
                new AiSettingsTab());
    }

    public List<GUISettingsTabInterface> getProjectSettingsTabs() {
        return Collections.emptyList();
    }

}