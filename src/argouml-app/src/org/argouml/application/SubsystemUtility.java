/* $Id$
 *****************************************************************************
 * Copyright (c) 2009 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    tfmorris
 *****************************************************************************
 *
 * Some portions of this file was previously release using the BSD License:
 */

// Copyright (c) 2007 The Regents of the University of California. All
// Rights Reserved. Permission to use, copy, modify, and distribute this
// software and its documentation without fee, and without a written
// agreement is hereby granted, provided that the above copyright notice
// and this paragraph appear in all copies. This software program and
// documentation are copyrighted by The Regents of the University of
// California. The software program and documentation are supplied "AS
// IS", without any accompanying services from The Regents. The Regents
// does not warrant that the operation of the program will be
// uninterrupted or error-free. The end-user understands that the program
// was developed for research purposes and is advised not to rely
// exclusively on the program for any reason. IN NO EVENT SHALL THE
// UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
// SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
// ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
// THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
// SUCH DAMAGE. THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
// PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
// CALIFORNIA HAS NO OBLIGATIONS TO PROVIDE MAINTENANCE, SUPPORT,
// UPDATES, ENHANCEMENTS, OR MODIFICATIONS.

package org.argouml.application;

import org.argouml.application.api.AbstractArgoJPanel;
import org.argouml.application.api.GUISettingsTabInterface;
import org.argouml.application.api.InitSubsystem;
import org.argouml.ui.DetailsPane;
import org.argouml.ui.GUI;
import org.argouml.ui.ProjectBrowser;
import org.argouml.ui.TabToDoTarget;

/**
 * Utility class for subsystem management.
 *
 * @author Michiel
 */
public class SubsystemUtility {

    /**
     * The use of this method in the top-level package 
     * prevents that the subsystem would depend on the GUI.
     * 
     * @param subsystem the subsystem to be initialised
     */
    static void initSubsystem(InitSubsystem subsystem) {
        subsystem.init();
        for (GUISettingsTabInterface tab : subsystem.getSettingsTabs()) {
            // TODO: This work should be deferred until actually
            // needed for display
            GUI.getInstance().addSettingsTab(tab);
        }
        for (GUISettingsTabInterface tab : subsystem.getProjectSettingsTabs()) {
            // TODO: This work should be deferred until actually
            // needed for display
            GUI.getInstance().addProjectSettingsTab(tab);
        }
        // ProjectBrowser.getInstance() throws an AssertionError in
        // test JVMs (where assertions are enabled) and returns null
        // in production (where they are not). Probe the singleton
        // field directly so the guard works in both modes —
        // InitHttpServerSubsystem initializes under headless mode
        // and must not crash if the GUI singleton is missing.
        if (projectBrowserInitialized()) {
            /*
             * All details tabs (Properties, ToDo, Documentation, Style,
             * Source, Constraints, Stereotype, TaggedValues, CheckList,
             * "As Diagram", ...) now land in the east pane. The south
             * pane field is kept (ProjectBrowser.getDetailsPane()) for
             * legacy callers like DeveloperModule, but it is no longer
             * attached to the BorderSplitPane — see ProjectBrowser
             * .assemblePanels() — so tabs added here never become
             * visible at the bottom of the main window.
             */
            DetailsPane eastPane =
                (DetailsPane) ProjectBrowser.getInstance().getEastDetailsPane();
            for (AbstractArgoJPanel tab : subsystem.getDetailsTabs()) {
                /* All tabs are added at the end, except a TabToDoTarget: */
                eastPane.addTab(tab, !(tab instanceof TabToDoTarget));
                /* Honor the user's "View > Window" choice —
                 * newly-added tabs default to hidden unless the
                 * configuration marks them visible. */
                org.argouml.ui.TabVisibilityRegistry.onTabAdded(tab);
            }
        }
    }

    /**
     * Returns {@code true} iff the {@link ProjectBrowser} singleton
     * has been initialised. We probe the private static
     * {@code theInstance} field via reflection so this check works
     * even when assertions are enabled (the test JVM has
     * {@code -ea}, and {@link ProjectBrowser#getInstance()} would
     * otherwise throw AssertionError instead of returning
     * {@code null}).
     */
    private static boolean projectBrowserInitialized() {
        try {
            java.lang.reflect.Field f =
                    ProjectBrowser.class.getDeclaredField("theInstance");
            f.setAccessible(true);
            return f.get(null) != null;
        } catch (java.lang.NoSuchFieldException e) {
            return false;
        } catch (java.lang.IllegalAccessException e) {
            return false;
        }
    }

}
