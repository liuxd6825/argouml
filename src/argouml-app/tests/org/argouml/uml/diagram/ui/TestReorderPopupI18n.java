/* $Id$
 *****************************************************************************
 * Copyright (c) 2026 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    utf8-migration
 *****************************************************************************
 */
package org.argouml.uml.diagram.ui;

import java.util.Locale;
import java.util.Vector;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import junit.framework.TestCase;

import org.argouml.i18n.Translator;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.tigris.gef.base.CmdReorder;
import org.tigris.gef.base.ReorderAction;

/**
 * Verify that {@link ReorderMenuBuilder} produces a localized submenu
 * suitable for replacing GEF 0.13.7's English "Ordering" submenu.
 *
 * <p>The full popup-vector assertion would require a real Fig (and
 * thus a Diagram and a Layer), which is heavy for a unit test; we
 * instead exercise the two helpers directly:</p>
 * <ul>
 *   <li>{@link ReorderMenuBuilder#buildReorderSubmenu()} produces a
 *       {@code JMenu} carrying 4 {@link ReorderAction} entries with
 *       localized labels.</li>
 *   <li>{@link ReorderMenuBuilder#replaceGefOrderingSubmenu(Vector)}
 *       strips a {@code JMenu} whose 4 children wrap
 *       {@link CmdReorder} singletons and replaces it with the
 *       localized variant.</li>
 * </ul>
 */
public class TestReorderPopupI18n extends TestCase {

    @SuppressWarnings("unused")
    private Project project;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        new InitNotation().init();
        new InitNotationUml().init();
        project = ProjectManager.getManager().makeEmptyProject();
    }

    @Override
    protected void tearDown() throws Exception {
        if (project != null) {
            ProjectManager.getManager().removeProject(project);
            project = null;
        }
        super.tearDown();
    }

    /**
     * The localised submenu is a {@code JMenu} carrying 4
     * {@link ReorderAction} items.
     */
    public void testBuildProducesFourItems() {
        JMenu menu = ReorderMenuBuilder.buildReorderSubmenu();
        assertNotNull("builder must return a JMenu", menu);
        assertEquals("menu carries exactly 4 reorder entries",
                     4, menu.getItemCount());
        for (int i = 0; i < 4; i++) {
            JMenuItem item = menu.getItem(i);
            assertNotNull("item " + i + " must not be null", item);
            Object action = item.getAction();
            assertTrue("item " + i + " action must be ReorderAction",
                       action instanceof ReorderAction);
        }
    }

    /** Items carry localized labels in zh_CN locale (not raw key). */
    public void testItemsAreLocalizedInZhCn() {
        Locale prev = Locale.getDefault();
        Translator.setLocale(new Locale("zh", "CN"));
        try {
            JMenu menu = ReorderMenuBuilder.buildReorderSubmenu();
            String[] expectedKeys = {
                "action.bring-forward", "action.send-backward",
                "action.bring-to-front", "action.send-to-back",
            };
            for (int i = 0; i < 4; i++) {
                String actualLabel =
                    (String) menu.getItem(i).getAction()
                        .getValue(javax.swing.Action.NAME);
                String expected = Translator.localize(expectedKeys[i]);
                assertEquals("item " + i + " label must match locale",
                             expected, actualLabel);
            }
        } finally {
            Translator.setLocale(prev);
        }
    }

    /** The strip helper removes a GEF-shaped menu and adds a localized one. */
    @SuppressWarnings("unchecked")
    public void testStripReplacesGefMenu() {
        Vector<Object> actions = new Vector<Object>();
        actions.add(makeGefShapedMenu());                // GEF's "Ordering"
        actions.add(new Object());                       // unrelated item
        int before = actions.size();
        ReorderMenuBuilder.replaceGefOrderingSubmenu(actions);
        assertEquals("exactly one item must be removed then one added",
                     before, actions.size());
        JMenu replacement = (JMenu) actions.lastElement();
        assertEquals(4, replacement.getItemCount());
        for (int i = 0; i < 4; i++) {
            Object a = replacement.getItem(i).getAction();
            assertTrue("replacement items must be ReorderAction",
                       a instanceof ReorderAction);
            assertFalse("no CmdReorder singletons should remain",
                        a instanceof CmdReorder);
        }
    }

    /** Strip leaves non-GEF JMenus alone. */
    @SuppressWarnings("unchecked")
    public void testStripLeavesOtherJMenusAlone() {
        Vector<Object> actions = new Vector<Object>();
        JMenu keep = new JMenu("keep me");
        keep.add(new JMenuItem("a"));
        keep.add(new JMenuItem("b"));
        actions.add(keep);
        actions.add(makeGefShapedMenu());
        ReorderMenuBuilder.replaceGefOrderingSubmenu(actions);
        assertEquals("non-GEF JMenu must be preserved", keep, actions.get(0));
        assertEquals("replacement submenu must be appended",
                     4, ((JMenu) actions.lastElement()).getItemCount());
    }

    /**
     * Build a JMenu that mimics GEF's "Ordering" shape: 4 children
     * whose Action is a {@link CmdReorder}. Used by the strip test
     * in isolation from the full GUI stack.
     */
    private static JMenu makeGefShapedMenu() {
        JMenu m = new JMenu("Ordering");
        m.add(new CmdReorder(CmdReorder.SEND_BACKWARD));
        m.add(new CmdReorder(CmdReorder.BRING_FORWARD));
        m.add(new CmdReorder(CmdReorder.SEND_TO_BACK));
        m.add(new CmdReorder(CmdReorder.BRING_TO_FRONT));
        return m;
    }
}
