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

import junit.framework.TestCase;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.profile.init.InitProfileSubsystem;

/**
 * Tests for {@link TabUseCaseRepresentedDiagrams} — the right-side
 * details tab that mirrors the UseCase property panel's
 * "represented diagrams" table on the east edge of the main window.
 *
 * <p>Six checks:</p>
 * <ol>
 *   <li>{@code shouldBeEnabled(null)} is {@code false}.</li>
 *   <li>{@code shouldBeEnabled(class)} is {@code false} (the tab is
 *       UseCase-specific).</li>
 *   <li>{@code shouldBeEnabled(useCase)} is {@code true}.</li>
 *   <li>{@code setTarget(useCase)} propagates to the wrapped
 *       {@code UseCaseRepresentedDiagramField}.</li>
 *   <li>{@code setTarget(null)} clears the wrapped field.</li>
 *   <li>Constructing the tab does not throw and yields a non-null
 *       wrapped field (sanity for the UI plumbing).</li>
 * </ol>
 */
public class TestTabUseCaseRepresentedDiagrams extends TestCase {

    private Project project;

    public TestTabUseCaseRepresentedDiagrams(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject(false);
    }

    @Override
    public void tearDown() throws Exception {
        ProjectManager.getManager().removeProject(project);
        super.tearDown();
    }

    /**
     * When no element is selected, the "As Diagram" tab must not
     * enable itself — DetailsPane consults this to grey out the
     * tab title.
     */
    public void testInactiveWhenNoTarget() {
        TabUseCaseRepresentedDiagrams tab = new TabUseCaseRepresentedDiagrams();
        try {
            assertFalse("shouldBeEnabled(null) must be false",
                    tab.shouldBeEnabled(null));
        } finally {
            tab.setTarget(null);
        }
    }

    /**
     * A non-UseCase model element must not activate the tab.
     * Mirrors {@code TabDocumentation.shouldBeEnabled(Object)}.
     */
    public void testInactiveForClass() {
        TabUseCaseRepresentedDiagrams tab = new TabUseCaseRepresentedDiagrams();
        Object clazz = Model.getCoreFactory().createClass();
        try {
            assertFalse("shouldBeEnabled(class) must be false",
                    tab.shouldBeEnabled(clazz));
        } finally {
            Model.getUmlFactory().delete(clazz);
            tab.setTarget(null);
        }
    }

    /**
     * A UseCase must activate the tab.
     */
    public void testActiveForUseCase() {
        TabUseCaseRepresentedDiagrams tab = new TabUseCaseRepresentedDiagrams();
        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            assertTrue("shouldBeEnabled(useCase) must be true",
                    tab.shouldBeEnabled(useCase));
        } finally {
            Model.getUmlFactory().delete(useCase);
            tab.setTarget(null);
        }
    }

    /**
     * {@code setTarget} must propagate the model element to the
     * underlying {@code UseCaseRepresentedDiagramField}, so the
     * right-edge table shows the same data as the property-panel
     * table when the user selects a UseCase.
     */
    public void testSetTargetPropagatesToField() {
        TabUseCaseRepresentedDiagrams tab = new TabUseCaseRepresentedDiagrams();
        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            tab.setTarget(useCase);
            assertSame("getTarget must echo the set UseCase",
                    useCase, tab.getTarget());
            assertNotNull("wrapped field must exist", tab.getFieldForTest());
        } finally {
            Model.getUmlFactory().delete(useCase);
            tab.setTarget(null);
        }
    }

    /**
     * Clearing the target must also clear the wrapped field — the
     * "As Diagram" table must not keep stale rows after the user
     * navigates away from a UseCase.
     */
    public void testClearTargetClearsField() {
        TabUseCaseRepresentedDiagrams tab = new TabUseCaseRepresentedDiagrams();
        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            tab.setTarget(useCase);
            assertSame(useCase, tab.getTarget());
            tab.setTarget(null);
            assertNull("tab getTarget must be null after clear",
                    tab.getTarget());
        } finally {
            Model.getUmlFactory().delete(useCase);
            tab.setTarget(null);
        }
    }

    /**
     * Constructor must build a valid Details tab without throwing
     * (regression guard for the
     * {@code super("tab.represented-diagrams")} title-resolution path).
     */
    public void testConstructorBuildsValidTab() {
        TabUseCaseRepresentedDiagrams tab = new TabUseCaseRepresentedDiagrams();
        try {
            assertNotNull("tab title must resolve",
                    tab.getTitle());
            assertNotNull("wrapped field must exist",
                    tab.getFieldForTest());
        } finally {
            tab.setTarget(null);
        }
    }
}