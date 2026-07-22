/* $Id$
 *****************************************************************************
 * Copyright (c) 2009-2012 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    test (2026-07-12)
 *****************************************************************************
 */
package org.argouml.uml.ui;

import java.util.List;

import junit.framework.TestCase;

import org.argouml.ai.domain.usecasediagram.UseCaseOperations;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.DiagramFactory;
import org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram;
import org.argouml.util.ItemUID;

/**
 * Regression test for the diagram-isolation bug where
 * {@link ActionNavigateRepresentedDiagram#lookupAllRepresentedDiagrams}
 * previously matched every diagram in the project because the stored
 * key was the namespace UUID shared by all ArgoDiagrams in a project.
 *
 * <p>Symptom: in a project with two diagrams A and B, right-clicking
 * a UseCase in A that has B in its represented-diagrams list showed
 * BOTH A and B in the popup, plus every other diagram in the
 * project.</p>
 *
 * <p>Fix: storage switched from namespace UUID to per-diagram
 * {@link ItemUID}. Only the linked diagram should match.</p>
 */
public class TestRepresentedDiagramIsolation extends TestCase {

    public TestRepresentedDiagramIsolation(String name) {
        super(name);
    }

    private Project project;
    private Object namespace;
    private List<ArgoDiagram> createdDiagrams = new java.util.ArrayList<ArgoDiagram>();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        new InitNotation().init();
        new InitNotationUml().init();
        namespace = Model.getModelManagementFactory().createModel();
        createdDiagrams.clear();
    }

    @Override
    public void tearDown() throws Exception {
        // Remove all diagrams we created via DiagramFactory
        // (preserves layer persistence cleanup that ProjectImpl relies on).
        for (ArgoDiagram d : createdDiagrams) {
            DiagramFactory.getInstance().removeDiagram(d);
        }
        createdDiagrams.clear();
        if (namespace != null) {
            Model.getUmlFactory().delete(namespace);
            namespace = null;
        }
        ProjectManager.getManager().removeProject(project);
        super.tearDown();
    }

    private ArgoDiagram addDiagram(String name) {
        ArgoDiagram d = new UMLUseCaseDiagram(name, namespace);
        // ArgoDiagramImpl does not auto-assign ItemUID at construction;
        // assign one so the diagram is linkable like in production
        // (where DiagramNode.uuid now auto-creates one too).
        if (d.getItemUID() == null) {
            d.setItemUID(new ItemUID());
        }
        project.addDiagram(d);
        createdDiagrams.add(d);
        return d;
    }

    /**
     * Link the use case to B only. {@code lookupAllRepresentedDiagrams}
     * must return exactly [B], not [A, B] or [every diagram in project].
     */
    public void testLinkIsolatesLinkedDiagramFromCurrentDiagram() {
        ArgoDiagram diagA = addDiagram("Diagram A");
        ArgoDiagram diagB = addDiagram("Diagram B");

        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            // Simulate the dialog "Add": store B's ItemUID.
            String uuidB = diagB.getItemUID().toString();
            assertTrue("preconditions: should be able to link B",
                    UseCaseOperations.addRepresentedDiagram(useCase, uuidB));

            // Lookup must return ONLY B (not A or any other diagram).
            List<ArgoDiagram> matches =
                    ActionNavigateRepresentedDiagram.lookupAllRepresentedDiagrams(
                            useCase);
            assertEquals("Only B should be in the link list — "
                    + "got " + matches.size() + ": "
                    + namesOf(matches),
                    1, matches.size());
            assertSame("Linked diagram must be B, not A",
                    diagB, matches.get(0));
        } finally {
            Model.getUmlFactory().delete(useCase);
        }
    }

    /**
     * With no link, lookup must return empty.
     */
    public void testLookupEmptyWhenNoLink() {
        ArgoDiagram diagA = addDiagram("Diagram A");
        ArgoDiagram diagB = addDiagram("Diagram B");

        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            List<ArgoDiagram> matches =
                    ActionNavigateRepresentedDiagram.lookupAllRepresentedDiagrams(
                            useCase);
            assertTrue("No links means no matches; got "
                    + namesOf(matches), matches.isEmpty());
        } finally {
            Model.getUmlFactory().delete(useCase);
        }
    }

    /**
     * Linking to multiple diagrams returns exactly those.
     */
    public void testMultipleLinks() {
        ArgoDiagram diagA = addDiagram("Diagram A");
        ArgoDiagram diagB = addDiagram("Diagram B");
        ArgoDiagram diagC = addDiagram("Diagram C");

        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            UseCaseOperations.addRepresentedDiagram(
                    useCase, diagB.getItemUID().toString());
            UseCaseOperations.addRepresentedDiagram(
                    useCase, diagC.getItemUID().toString());
            List<ArgoDiagram> matches =
                    ActionNavigateRepresentedDiagram.lookupAllRepresentedDiagrams(
                            useCase);
            assertEquals("Linking B and C must yield exactly 2 matches; got "
                    + namesOf(matches), 2, matches.size());
        } finally {
            Model.getUmlFactory().delete(useCase);
        }
    }

    private static String namesOf(List<ArgoDiagram> ds) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ds.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(ds.get(i).getName());
        }
        sb.append("]");
        return sb.toString();
    }
}