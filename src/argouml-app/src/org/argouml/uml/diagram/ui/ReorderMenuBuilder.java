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

import java.util.Iterator;
import java.util.Vector;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.argouml.application.helpers.ResourceLoaderWrapper;
import org.argouml.i18n.Translator;
import org.argouml.ui.ArgoJMenu;
import org.argouml.ui.cmd.ShortcutMgr;
import org.tigris.gef.base.CmdReorder;
import org.tigris.gef.base.ReorderAction;

/**
 * Builds the per-element "Reorder" submenu used in right-click popups
 * (FigNodeModelElement, FigEdgeModelElement) and the menu-bar Arrange
 * submenu (GenericArgoMenuBar).
 *
 * <p>The labels and accelerators are routed through {@link Translator} so
 * the entire submenu is locale-aware. We deliberately do not use the
 * default {@code CmdReorder} singletons (whose names are baked-in
 * English byte-code constants in GEF 0.13.7) and we do not let
 * {@link org.tigris.gef.presentation.Fig#getPopUpActions} inject its
 * un-translated submenu; the callers strip or skip that and use this
 * helper instead.</p>
 */
public final class ReorderMenuBuilder {

    private ReorderMenuBuilder() { }

    /**
     * Build and return the localized reorder submenu. The caller is
     * responsible for adding it to its popup vector (or menu bar).
     *
     * @return a fully populated <code>JMenu</code> named by the
     *         <code>menu.reorder</code> key
     */
    static JMenu buildReorderSubmenu() {
        ArgoJMenu menu = new ArgoJMenu("menu.reorder");
        populate(menu);
        return menu;
    }

    /**
     * Append the four localized reorder entries to an existing
     * {@link JMenu}. Useful when the caller (e.g. the menu-bar
     * <em>Arrange &gt; Reorder</em>) wants to keep its own constructed
     * menu shell and only share the item-creation logic.
     *
     * @param menu the menu to append the four reorder entries to
     */
    public static void populate(JMenu menu) {
        append(menu, "action.bring-forward",
                "Forward", ReorderAction.BRING_FORWARD,
                ShortcutMgr.ACTION_REORDER_FORWARD);
        append(menu, "action.send-backward",
                "Backward", ReorderAction.SEND_BACKWARD,
                ShortcutMgr.ACTION_REORDER_BACKWARD);
        append(menu, "action.bring-to-front",
                "ToFront", ReorderAction.BRING_TO_FRONT,
                ShortcutMgr.ACTION_REORDER_TO_FRONT);
        append(menu, "action.send-to-back",
                "ToBack", ReorderAction.SEND_TO_BACK,
                ShortcutMgr.ACTION_REORDER_TO_BACK);
    }

    /**
     * Remove GEF 0.13.7's un-translated "Ordering" submenu from
     * {@code actions} and append our localized variant in its place.
     * <p>GEF always emits a single {@code JMenu} whose 4 children are
     * the {@link CmdReorder} singletons. We match on that pattern rather
     * than on the menu text so a future GEF translation that does not
     * use the literal "Ordering" string is still removed.</p>
     *
     * @param actions the popup vector returned by super.getPopUpActions
     */
    static void replaceGefOrderingSubmenu(Vector<?> actions) {
        for (Iterator<?> it = actions.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof JMenu && isGefOrderingMenu((JMenu) o)) {
                it.remove();
            }
        }
        @SuppressWarnings("unchecked")
        Vector<Object> raw = (Vector<Object>) actions;
        raw.add(buildReorderSubmenu());
    }

    private static boolean isGefOrderingMenu(JMenu m) {
        // GEF adds 4 menu items that wrap CmdReorder.* singletons via
        // AbstractAction. JMenuItem.getAction() returns the underlying
        // Command, so the type test should look at the action, not at
        // the JMenuItem itself. CmdReorder extends Cmd extends
        // AbstractAction; we name the exact class to avoid false
        // positives on unrelated AbstractAction derivatives.
        if (m.getMenuComponentCount() != 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            java.awt.Component c = m.getMenuComponent(i);
            if (!(c instanceof JMenuItem)) {
                return false;
            }
            Action action = ((JMenuItem) c).getAction();
            if (!CmdReorder.class.isInstance(action)) {
                return false;
            }
        }
        return true;
    }

    private static void append(JMenu menu, String actionKey,
                               String iconName, int actionType,
                               String acceleratorKey) {
        Icon icon = ResourceLoaderWrapper.lookupIcon(iconName);
        ReorderAction action = new ReorderAction(
                Translator.localize(actionKey), icon, actionType);
        JMenuItem item = menu.add(action);
        // Re-bind any accelerator the action may inherit. The key
        // constant is a ShortcutMgr internal id; passing it through
        // ShortcutMgr keeps the assignment consistent with the existing
        // menu-bar wiring. We tolerate a HeadlessException / Error
        // here so the build itself can run in surefire's headless mode
        // (ShortcutMgr's static initializer pulls
        // Toolkit.getMenuShortcutKeyMask() and ProjectBrowser, both of
        // which throw when no display is available).
        try {
            ShortcutMgr.assignAccelerator(item, acceleratorKey);
        } catch (java.awt.HeadlessException
                 | NoClassDefFoundError
                 | ExceptionInInitializerError
                 | AssertionError ignore) {
            // running headless; skip keyboard-shortcut registration
        }
    }
}
