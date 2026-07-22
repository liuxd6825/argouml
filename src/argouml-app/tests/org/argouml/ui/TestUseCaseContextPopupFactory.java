/* $Id$
 *****************************************************************************
 * Copyright (c) 2009-2012 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    test (2026-07-13)
 *****************************************************************************
 */
// Copyright (c) 1996-2009 The Regents of the University of California. All
// Rights Reserved. Permission to use, copy, modify, and distribute this
// software and its documentation without fee, and without a written
// agreement is hereby granted, provided that the above copyright notice
// and this paragraph appear in all copies.  This software program and
// documentation are copyrighted by The Regents of the University of
// California. The software program and documentation are supplied "AS
// IS", without any accompanying services from The Regents. The Regents
// does not warrant that the operation of the program will be
// uninterrupted or error-free.  The end-user understands that the program
// was developed for research purposes and is advised not to rely
// exclusively on the program for any reason.  IN NO EVENT SHALL THE
// UNIVERSITY OF CALIFORNIA BE LIABLE FOR ANY PARTY FOR DIRECT, INDIRECT,
// SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
// ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
// THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
// SUCH DAMAGE. THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE
// PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
// CALIFORNIA HAS NO OBLIGATIONS TO PROVIDE MAINTENANCE, SUPPORT,
// UPDATES, ENHANCEMENTS, OR MODIFICATIONS.

package org.argouml.ui;

import java.util.List;
import java.util.Locale;

import javax.swing.Action;

import junit.framework.TestCase;

import org.argouml.i18n.Translator;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram;
import org.argouml.uml.ui.ActionManageRepresentedDiagrams;
import org.argouml.uml.ui.ActionNavigateRepresentedDiagram;
import org.argouml.util.ItemUID;

/**
 * Tests for the Chinese (and any non-English) localisation of the
 * UseCase right-click popup.
 *
 * <p>Two regressions are guarded against:</p>
 * <ol>
 *   <li>{@link UseCaseContextPopupFactory} previously built its
 *       submenu header with a raw i18n KEY string
 *       ("menu.popup.related-diagrams") that the popup rendering
 *       displayed verbatim — users in every locale saw the key text
 *       instead of the localized value. The factory must now pass
 *       {@code Translator.localize("menu.popup.related-diagrams")}.</li>
 *   <li>{@link ActionManageRepresentedDiagrams} previously used
 *       a hardcoded English string ("Manage...") as its display
 *       label. It must now use an i18n key resolved at construction
 *       time.</li>
 * </ol>
 */
public class TestUseCaseContextPopupFactory extends TestCase {

    /** Original English keys we expect to see resolved. */
    private static final String KEY_RELATED_DIAGRAMS =
            "menu.popup.related-diagrams";
    private static final String KEY_MANAGE_REP =
            "button.manage-rep-diagrams";

    public TestUseCaseContextPopupFactory(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        ProjectManager.getManager().makeEmptyProject();
        new InitNotation().init();
        new InitNotationUml().init();
    }

    /**
     * The factory must return a single Action whose NAME is the
     * RESOLVED English label "Related Diagrams" — not the raw key.
     * Before the fix the popup displayed literally
     * "menu.popup.related-diagrams".
     */
    public void testPopupHeaderIsLocalizedNotRawKey() {
        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            Locale prev = Locale.getDefault();
            Translator.setLocale("en");
            try {
                List<Action> actions =
                        new UseCaseContextPopupFactory()
                                .createContextPopupActions(useCase);
                assertEquals("Factory must return exactly one top-level action",
                        1, actions.size());
                String name = (String) actions.get(0).getValue(Action.NAME);
                assertEquals("Popup header must be the RESOLVED label, not the raw key",
                        Translator.localize(KEY_RELATED_DIAGRAMS), name);
                assertEquals("In en locale the resolved label is 'Related Diagrams'",
                        "Related Diagrams", name);
                assertFalse("Resolved name must not equal the raw i18n key",
                        KEY_RELATED_DIAGRAMS.equals(name));
            } finally {
                Translator.setLocale(prev);
            }
        } finally {
            Model.getUmlFactory().delete(useCase);
        }
    }

    /**
     * {@link ActionManageRepresentedDiagrams} must also be localized —
     * its NAME is the resolved label of {@code button.manage-rep-diagrams}.
     * Before the fix the label was a hardcoded "Manage...".
     */
    public void testManageActionIsLocalized() {
        Locale prev = Locale.getDefault();
        Translator.setLocale("en");
        try {
            Action a = new ActionManageRepresentedDiagrams();
            String name = (String) a.getValue(Action.NAME);
            assertEquals("Manage action label must be the RESOLVED value, not raw key",
                    Translator.localize(KEY_MANAGE_REP), name);
            assertEquals("In en locale the resolved label is 'Manage Represented Diagrams...'",
                    "Manage Represented Diagrams...", name);
            assertFalse("Resolved name must not equal the raw i18n key",
                    KEY_MANAGE_REP.equals(name));
            // Tooltip must also be localized.
            String tooltip = (String) a.getValue(Action.SHORT_DESCRIPTION);
            assertEquals("Tooltip must match the localized NAME",
                    name, tooltip);
        } finally {
            Translator.setLocale(prev);
        }
    }

    /**
     * In zh_CN locale the popup must surface Chinese strings (assuming
     * the zh_CN bundle is on the test classpath via
     * {@code argouml-i18n-zh}). Falls back to English if the bundle
     * is absent — in that case we still require the resolved label, not
     * the raw key.
     */
    public void testPopupHeaderResolvesUnderNonEnglishLocale() {
        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            // Hermetic — independent of the JVM default locale.
            Locale prev = Locale.getDefault();
            Translator.setLocale("zh");
            Translator.setLocale("zh_CN");
            try {
                List<Action> actions =
                        new UseCaseContextPopupFactory()
                                .createContextPopupActions(useCase);
                String name = (String) actions.get(0).getValue(Action.NAME);
                // Whatever the locale, the popup must NOT show the raw key.
                assertFalse("Raw i18n key must not appear as popup label",
                        KEY_RELATED_DIAGRAMS.equals(name));
                // And the label must be the Translator-resolved string
                // for this locale.
                assertEquals("Label must equal the localized value",
                        Translator.localize(KEY_RELATED_DIAGRAMS), name);
            } finally {
                Translator.setLocale(prev);
            }
        } finally {
            Model.getUmlFactory().delete(useCase);
        }
    }

    /**
     * When the use case has no represented-diagram links, the popup
     * must still return the (localized) ActionList with just the
     * "Manage..." entry — no jump-to entries, no NullPointerException.
     */
    public void testPopupWithoutLinksHasManageActionOnly() throws Exception {
        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            Locale prev = Locale.getDefault();
            Translator.setLocale("en");
            try {
                List<Action> actions =
                        new UseCaseContextPopupFactory()
                                .createContextPopupActions(useCase);
                assertEquals(1, actions.size());
                // The single action is the ActionList containing one entry.
                assertEquals(1, ((java.util.List<?>) actions.get(0)).size());
                Object manageAction = ((java.util.List<?>) actions.get(0)).get(0);
                assertTrue("Manage action must be the first entry",
                        manageAction instanceof ActionManageRepresentedDiagrams);
            } finally {
                Translator.setLocale(prev);
            }
        } finally {
            Model.getUmlFactory().delete(useCase);
        }
    }

    /**
     * When the use case has a represented-diagram link, the popup
     * contains the Manage action plus one jump-to entry per linked
     * diagram. Verify the size and that every action has a non-raw
     * name (i.e., a localized label).
     */
    public void testPopupWithLinksIncludesLocalizedJumpEntries() throws Exception {
        // Need a project so the diagram is reachable by the lookup.
        Object namespace = Model.getModelManagementFactory().createModel();
        UMLUseCaseDiagram diag = new UMLUseCaseDiagram("Login", namespace);
        if (diag.getItemUID() == null) diag.setItemUID(new ItemUID());
        ProjectManager.getManager().getCurrentProject().addDiagram(diag);

        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            Locale prev = Locale.getDefault();
            Translator.setLocale("en");
            try {
                org.argouml.ai.domain.usecasediagram.UseCaseOperations.addRepresentedDiagram(
                        useCase, diag.getItemUID().toString());

                List<Action> actions =
                        new UseCaseContextPopupFactory()
                                .createContextPopupActions(useCase);
                assertEquals(1, actions.size());
                java.util.List<?> sub = (java.util.List<?>) actions.get(0);
                // Manage + one jump-to = 2 entries.
                assertEquals(2, sub.size());
                for (Object entry : sub) {
                    Action a = (Action) entry;
                    String name = (String) a.getValue(Action.NAME);
                    assertNotNull("Every popup entry must have a NAME", name);
                    assertFalse("NAME must not be a raw i18n key (got " + name + ")",
                            name.equals("menu.popup.related-diagrams")
                                    || name.equals("menu.popup.jump-to-represented-diagram")
                                    || name.equals("button.manage-rep-diagrams"));
                }
            } finally {
                Translator.setLocale(prev);
            }
        } finally {
            Model.getUmlFactory().delete(useCase);
            org.argouml.uml.diagram.DiagramFactory.getInstance().removeDiagram(diag);
            Model.getUmlFactory().delete(namespace);
        }
    }
}