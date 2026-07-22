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

import javax.swing.JPanel;

import org.argouml.ai.ui.AiPanel;
import org.argouml.cognitive.checklist.ui.TabChecklist;
import org.argouml.cognitive.ui.TabToDo;
import org.argouml.ui.TabUseCaseRepresentedDiagrams;
import org.argouml.uml.ui.TabConstraints;
import org.argouml.uml.ui.TabDocumentation;
import org.argouml.uml.ui.TabProps;
import org.argouml.uml.ui.TabSrc;
import org.argouml.uml.ui.TabStereotype;
import org.argouml.uml.ui.TabStyle;
import org.argouml.uml.ui.TabTaggedValues;

/**
 * Plugin point for the "View &rarr; Window" tab-visibility feature.
 *
 * <p>Maps every EAST-pane tab class to its {@link TabVisibilityConfig}
 * key, holds the singleton panel instance for each key after the
 * subsystems initialize, and is consulted both at startup (to hide
 * tabs that the user has disabled in Configuration) and at runtime
 * (when the user toggles a CheckboxMenuItem in the Window submenu).</p>
 *
 * <p>The registry keeps an instance reference for each tab even after
 * {@link DetailsPane#removeTab} so we can re-add the same instance
 * when the user re-enables the tab without paying factory cost.</p>
 *
 * @author ArgoUML contributors
 */
public final class TabVisibilityRegistry {

    /**
     * Hard-coded class &rarr; visibility-key table. Adding a new
     * toggleable tab here is the only step needed to expose it in
     * the "Window" submenu.
     */
    private static final Map<Class<?>, String> CLASS_TO_KEY =
            new HashMap<Class<?>, String>();
    static {
        CLASS_TO_KEY.put(TabToDo.class,                     "todo-item");
        CLASS_TO_KEY.put(TabDocumentation.class,            "documentation");
        CLASS_TO_KEY.put(TabSrc.class,                      "source");
        CLASS_TO_KEY.put(TabConstraints.class,              "constraints");
        CLASS_TO_KEY.put(TabUseCaseRepresentedDiagrams.class, "represented-diagrams");
        CLASS_TO_KEY.put(TabProps.class,                    "properties");
        CLASS_TO_KEY.put(TabChecklist.class,                "checklist");
        CLASS_TO_KEY.put(AiPanel.class,                     "ai-panel");
        CLASS_TO_KEY.put(TabStyle.class,                    "style");
        CLASS_TO_KEY.put(TabStereotype.class,               "stereotype");
        CLASS_TO_KEY.put(TabTaggedValues.class,             "tagged-values");
    }

    /** Cached tab instances, keyed by visibility-key. */
    private static final Map<String, JPanel> PANELS =
            new HashMap<String, JPanel>();

    private TabVisibilityRegistry() {
        /* utility class */
    }

    /**
     * Reverse-lookup the visibility key for a tab instance by
     * walking the class-to-key table.
     *
     * @param tab an AbstractArgoJPanel (or any JPanel) that should
     *          be matched against the registered class types.
     * @return the matching key, or {@code null} if the tab's class
     *         is not in {@link #CLASS_TO_KEY}.
     */
    public static String keyOf(JPanel tab) {
        if (tab == null) {
            return null;
        }
        for (Map.Entry<Class<?>, String> e : CLASS_TO_KEY.entrySet()) {
            if (e.getKey().isInstance(tab)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Hook called from {@link org.argouml.application.SubsystemUtility}
     * immediately after {@code DetailsPane.addTab(...)} — caches the
     * panel and removes it from the EAST pane if the user has
     * configured the corresponding tab to be hidden.
     *
     * @param tab the freshly-added tab panel
     */
    public static void onTabAdded(JPanel tab) {
        String key = keyOf(tab);
        if (key == null) {
            return; /* tab outside the user-controllable set */
        }
        PANELS.put(key, tab);
        if (!TabVisibilityConfig.isVisible(key)) {
            DetailsPane dp =
                (DetailsPane) ProjectBrowser.getInstance()
                                             .getEastDetailsPane();
            if (dp != null) {
                dp.removeTab(tab);
            }
        }
    }

    /**
     * Toggle visibility for a given key, driven from the View /
     * Window submenu. Persists the new state and immediately shows
     * or hides the corresponding tab via the {@link DetailsPane}.
     *
     * @param key one of {@link TabVisibilityConfig#ALL_KEYS}
     */
    public static void toggle(String key, boolean newVisible) {
        if (key == null) {
            return;
        }
        JPanel panel = PANELS.get(key);
        if (panel == null) {
            // Tab not yet initialized (very early in startup, or
            // subsystem not registered) — nothing to do; the choice
            // will apply on next addTab via onTabAdded().
            return;
        }
        DetailsPane dp =
            (DetailsPane) ProjectBrowser.getInstance()
                                         .getEastDetailsPane();
        if (dp == null) {
            return;
        }
        if (newVisible) {
            dp.addTab(panel, true);
        } else {
            dp.removeTab(panel);
        }
        // Force the nested BorderSplitPane → JTabbedPane → DetailsPane
        // tree to re-layout & repaint.  Without this, some LAFs (and
        // our own previous "tab added but invisible" regression) won't
        // update the displayed tab list until the next mouse event.
        //
        // We deliberately do NOT call setSelectedComponent — that would
        // switch the user's currently-active tab, which is not what
        // "show" / "hide" should do.  The "View > Window" submenu is a
        // pure visibility toggle.  JTabbedPane's own auto-select-
        // neighbour logic takes over when a tab is removed and the
        // removed tab happened to be the active one, so removing the
        // currently-active tab still produces a sensible selection
        // without us having to intervene.
        dp.revalidate();
        dp.repaint();
    }

    /**
     * Direct show/hide without persisting. Used to apply the user's
     * saved choices at startup (called once after every subsystem
     * has registered its tab &mdash; see
     * {@link org.argouml.application.SubsystemUtility}).
     *
     * @param key visibility key
     * @param visible desired state
     */
    public static void applyVisibility(String key, boolean visible) {
        JPanel panel = PANELS.get(key);
        if (panel == null) {
            return;
        }
        DetailsPane dp =
            (DetailsPane) ProjectBrowser.getInstance()
                                         .getEastDetailsPane();
        if (dp == null) {
            return;
        }
        // Only add when visible=true; leave removal to onTabAdded's
        // initial-render branch which fires during subsystem init.
        if (visible) {
            dp.addTab(panel, true);
        }
    }

    /** Test-only accessor for {@link #PANELS}. */
    static Map<String, JPanel> getPanelsForTest() {
        return PANELS;
    }

    /** Test-only accessor for {@link #CLASS_TO_KEY}. */
    static Map<Class<?>, String> getClassToKeyForTest() {
        return CLASS_TO_KEY;
    }
}
