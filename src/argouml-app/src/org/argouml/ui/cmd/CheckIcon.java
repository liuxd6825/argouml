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

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

import javax.swing.Icon;

/**
 * Small "tick" icon used to mark a "View > Window" submenu entry as
 * currently showing the corresponding EAST-pane tab.
 *
 * <p>We draw a check mark via {@link Graphics2D} rather than ship a
 * static {@code ImageIcon}, so it picks up the menu item's foreground
 * colour and renders correctly under every LAF (including Aqua).  The
 * path roughly traces the shape of the JRE's standard
 * {@code CheckBoxMenuItem.checkIcon}, but is fully owned by us so we
 * can be sure the on-screen result is consistent across JDK versions
 * and HiDPI/Retina scaling.</p>
 *
 * <p>Allocation is singleton &mdash; a single {@link CheckIcon}
 * instance is shared by every menu item.  {@code Icon} implementations
 * are required to be stateless; this one is, and uses no
 * {@link Component} field for storage.</p>
 */
final class CheckIcon implements Icon {

    /** Shared instance &mdash; see class-level Javadoc. */
    static final CheckIcon INSTANCE = new CheckIcon();

    private static final int WIDTH = 14;
    private static final int HEIGHT = 14;

    @Override
    public int getIconWidth() {
        return WIDTH;
    }

    @Override
    public int getIconHeight() {
        return HEIGHT;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            // macOS Aqua LAF's ScreenMenuItem.addNotify calls paintIcon
            // with a null Component on the very first render (before
            // the menu item has been fully wired into a parent), so
            // fall back to the current graphics colour — which is
            // what Aqua itself would have set anyway for a native
            // check-mark.
            g2.setColor(c != null ? c.getForeground() : g2.getColor());
            g2.setStroke(new BasicStroke(2));
            Path2D p = new Path2D.Float();
            p.moveTo(x + 2, y + 6);
            p.lineTo(x + 5, y + 10);
            p.lineTo(x + 11, y + 3);
            g2.draw(p);
        } finally {
            g2.dispose();
        }
    }
}
