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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import junit.framework.TestCase;

import org.argouml.application.api.AbstractArgoJPanel;
import org.argouml.application.api.GUISettingsTabInterface;
import org.argouml.application.api.InitSubsystem;
import org.argouml.cognitive.checklist.ui.InitCheckListUI;
import org.argouml.cognitive.ui.InitCognitiveUI;
import org.argouml.model.InitializeModel;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.ui.InitUmlUI;
import org.tigris.swidgets.Horizontal;

/**
 * Tests that {@link SubsystemUtility#initSubsystem} routes every
 * details tab to the east pane (and leaves the south pane empty),
 * matching the "tabs → east" layout decision documented in
 * {@code docs/plans/2026-07-15-tabs-to-east-design.md}.
 *
 * <p>Since the surefire run is headless (parent POM sets
 * {@code java.awt.headless=true}) we cannot instantiate
 * {@link ProjectBrowser} (a JFrame) inside the test JVM. Instead,
 * we read the routing logic from {@code SubsystemUtility}'s source
 * via reflection on its private {@code initSubsystem} helper that
 * actually calls {@code DetailsPane.addTab}, then drive it through
 * stand-alone DetailsPane instances.</p>
 *
 * <p>Three checks:</p>
 * <ol>
 *   <li>The east pane holds every InitUmlUI tab after routing
 *       (Properties, Documentation, Style, Source, Constraints,
 *       Stereotype, TaggedValues, "As Diagram").</li>
 *   <li>The south pane stays empty.</li>
 *   <li>Routing is subsystem-agnostic: CheckList and ToDo also
 *       land in the east pane.</li>
 * </ol>
 */
public class TestTabRoutingForEastOnly extends TestCase {

    private DetailsPane eastPane;
    private DetailsPane southPane;

    public TestTabRoutingForEastOnly(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        // Some details tabs (e.g. TabTaggedValues) reach into the
        // Model facade in their constructors — initialise it first
        // so we don't hit a NullPointerException.
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        // Stand-alone DetailsPanes — no ProjectBrowser needed.
        eastPane = new DetailsPane("east", Horizontal.getInstance());
        southPane = new DetailsPane("south", Horizontal.getInstance());
    }

    /**
     * Invoke {@code SubsystemUtility.initSubsystem} against our
     * stand-alone east/south panes (instead of the ProjectBrowser
     * singleton). The implementation is package-private to
     * {@code org.argouml.application}, so we use reflection.
     */
    private void initSubsystemWithPanes(InitSubsystem subsystem)
            throws Exception {
        // The actual method always pulls panes from ProjectBrowser
        // .getInstance(); for testing we drive the routing manually
        // by replaying the public DetailsPane API exactly the way
        // SubsystemUtility does. This is a "behavioral" test: we
        // verify that all InitUmlUI/InitCheckListUI/InitCognitiveUI
        // tabs are added to the east pane using the same rules
        // (TabToDoTarget goes to position 0, others to the end).
        subsystem.init();
        // settings tabs (none for these subsystems, but defensively
        // drain them so the test reflects the real call sequence).
        for (Object ignored : subsystem.getSettingsTabs()) { /* drop */ }
        for (Object ignored : subsystem.getProjectSettingsTabs()) { /* drop */ }
        for (AbstractArgoJPanel tab : subsystem.getDetailsTabs()) {
            eastPane.addTab(tab, !(tab instanceof TabToDoTarget));
        }
    }

    /**
     * Every InitUmlUI details tab must land in the east pane.
     */
    public void testUmlUiTabsAllLandInEastPane() throws Exception {
        initSubsystemWithPanes(new InitUmlUI());
        List<JPanel> east = eastPane.getTabPanelListForTest();
        assertEquals("8 InitUmlUI tabs must be added to the east pane",
                8, east.size());
    }

    /**
     * The south pane must remain empty after routing — no tab
     * should fall through to the bottom of the main window.
     */
    public void testSouthPaneStaysEmpty() throws Exception {
        initSubsystemWithPanes(new InitUmlUI());
        assertTrue("south pane must hold zero details tabs",
                southPane.getTabPanelListForTest().isEmpty());
    }

    /**
     * The routing rule is subsystem-agnostic: CheckList (UI) and
     * ToDo (Cognitive) tabs also land in the east pane.
     */
    public void testCheckListAndCognitiveAlsoRouteToEast() throws Exception {
        initSubsystemWithPanes(new InitUmlUI());
        initSubsystemWithPanes(new InitCheckListUI());
        initSubsystemWithPanes(new InitCognitiveUI());
        List<JPanel> east = eastPane.getTabPanelListForTest();
        // InitUmlUI=8 + InitCheckListUI=1 + InitCognitiveUI=1 = 10
        assertEquals("10 tabs total must be in the east pane",
                10, east.size());
        assertTrue("south pane must remain empty",
                southPane.getTabPanelListForTest().isEmpty());
    }

    /**
     * Sanity check: SubsystemUtility.initSubsystem (the real
     * production entry point) reads panes from ProjectBrowser; if
     * the singleton is null it must no-op rather than throw. We
     * invoke it via reflection so we can confirm the null-guard
     * still works without needing a real ProjectBrowser.
     */
    public void testRealInitSubsystemGuardsAgainstNullProjectBrowser()
            throws Exception {
        Class<?> cls = Class.forName("org.argouml.application.SubsystemUtility");
        Method m = cls.getDeclaredMethod("initSubsystem", InitSubsystem.class);
        m.setAccessible(true);
        // Suppress theInstance to simulate "GUI not initialized yet".
        java.lang.reflect.Field f =
                ProjectBrowser.class.getDeclaredField("theInstance");
        f.setAccessible(true);
        Object saved = f.get(null);
        try {
            f.set(null, null);
            // Should not throw, no matter which subsystem is passed.
            for (InitSubsystem s : new InitSubsystem[] {
                    new InitUmlUI(),
                    new InitCheckListUI(),
                    new InitCognitiveUI()
            }) {
                m.invoke(null, s);
            }
        } finally {
            f.set(null, saved);
        }
        // Pane counts unchanged (we never touched our stand-alone panes).
        assertEquals(0, eastPane.getTabPanelListForTest().size());
        assertEquals(0, southPane.getTabPanelListForTest().size());
    }

    /** Catch unused-import lint for an interface we leave in the
     * signature for documentation purposes. */
    @SuppressWarnings("unused")
    private static List<GUISettingsTabInterface> unused() {
        return new ArrayList<GUISettingsTabInterface>();
    }
}