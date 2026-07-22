/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ui.cmd;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import org.argouml.ui.TabVisibilityConfig;
import org.argouml.ui.TabVisibilityRegistry;

/**
 * Action bound to one of the CheckboxMenuItems under
 * "View &rarr; Window". On click, flips the persisted visibility for
 * the given tab key and tells {@link TabVisibilityRegistry} to show
 * or hide the corresponding EAST-pane tab immediately.
 *
 * <p>One shared instance per menu item &mdash; state lives in
 * {@link TabVisibilityConfig}. {@link javax.swing.JCheckBoxMenuItem}
 * synchronises its check-state from the same source.</p>
 */
public final class ToggleTabVisibilityAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final String tabKey;

    /**
     * @param tabKey one of {@link TabVisibilityConfig#ALL_KEYS}
     */
    public ToggleTabVisibilityAction(String tabKey) {
        super("tab." + tabKey);
        this.tabKey = tabKey;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        boolean nowVisible = !TabVisibilityConfig.isVisible(tabKey);
        TabVisibilityConfig.setVisible(tabKey, nowVisible);
        // Pass the new visibility to the registry rather than letting
        // it re-read the config: an earlier version of this code
        // called toggle(key) which re-read isVisible() and flipped the
        // state a second time, so every click was a no-op (config
        // net-effect: false → true → false on each click).  We hand the
        // already-computed newVisible to the registry so the panel
        // state and config stay in lockstep.
        TabVisibilityRegistry.toggle(tabKey, nowVisible);
        // Keep the menu item's check-mark icon in sync with the new
        // visibility.  We don't rely on JCheckBoxMenuItem here (its
        // model-based state sync misbehaves on the macOS Aqua LAF) —
        // we use a plain JMenuItem + this explicit setIcon call instead.
        Object src = e.getSource();
        if (src instanceof javax.swing.JMenuItem) {
            ((javax.swing.JMenuItem) src).setIcon(
                    nowVisible ? CheckIcon.INSTANCE : null);
        }
    }

    /** Exposed so the menu item can keep its check-state in sync. */
    public boolean isCurrentlyVisible() {
        return TabVisibilityConfig.isVisible(tabKey);
    }
}
