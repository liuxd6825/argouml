/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ui;

import java.util.HashMap;
import java.util.Map;

import org.argouml.configuration.Configuration;
import org.argouml.configuration.ConfigurationKey;

/**
 * Per-tab visibility state for the right-edge detail tabs.
 *
 * <p>Backs the "View &rarr; Window" submenu's check-state. Choices
 * persist across sessions via {@link Configuration}. Used by
 * {@link TabVisibilityRegistry} to decide whether a freshly-added
 * tab should stay in the EAST pane or be immediately removed.</p>
 *
 * <p>Default visibility (used only when no Configuration key is
 * present, i.e. a fresh install): <strong>only "Properties" and
 * "Smart Assistant" are visible</strong>; everything else is hidden.
 * The user's selections override this and are persisted.</p>
 */
public final class TabVisibilityConfig {

    /** Configuration key prefix shared by every entry below. */
    private static final String PREFIX = "ui.visible.tab.";

    /** Stable ordering of all toggleable tab keys (matches menu order). */
    public static final String[] ALL_KEYS = new String[] {
            "todo-item",
            "documentation",
            "source",
            "constraints",
            "represented-diagrams",
            "properties",
            "checklist",
            "ai-panel",
            "style",
            "stereotype",
            "tagged-values",
    };

    /**
     * Tab keys that are visible on first run. The user can override
     * each individually via the "View &rarr; Window" submenu.
     */
    private static final Map<String, Boolean> DEFAULTS =
            new HashMap<String, Boolean>();
    static {
        for (String key : ALL_KEYS) {
            DEFAULTS.put(key, Boolean.FALSE);
        }
        DEFAULTS.put("properties", Boolean.TRUE);
        DEFAULTS.put("ai-panel", Boolean.TRUE);
    }

    private TabVisibilityConfig() {
        /* utility class */
    }

    private static ConfigurationKey cfgKey(String suffix) {
        return Configuration.makeKey(PREFIX + suffix);
    }

    /**
     * @param tabKey one of {@link #ALL_KEYS}
     * @return true iff the tab should currently be shown in the EAST pane.
     */
    public static boolean isVisible(String tabKey) {
        ConfigurationKey k = cfgKey(tabKey);
        Boolean def = DEFAULTS.get(tabKey);
        boolean fallback = def != null && def;
        // Returns fallback when the key is not set, otherwise the
        // user-persisted value (true/false).
        return Configuration.getBoolean(k, fallback);
    }

    /**
     * Persist the user's selection. The actual add/remove on the
     * EAST pane is the registry's responsibility.
     */
    public static void setVisible(String tabKey, boolean visible) {
        Configuration.setBoolean(cfgKey(tabKey), visible);
    }
}
