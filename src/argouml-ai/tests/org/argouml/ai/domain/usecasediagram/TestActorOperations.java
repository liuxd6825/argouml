/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.usecasediagram;

import junit.framework.TestCase;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram;

/**
 * Tests for {@link ActorOperations} on a freshly built
 * use-case diagram.
 */
public class TestActorOperations extends TestCase {

    private static final String DIAGRAM = "TestActorOps";

    static {
        try {
            new InitNotation().init();
            new InitNotationUml().init();
            new InitNotationJava().init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Project project;
    private ArgoDiagram diagram;

    @Override
    @SuppressWarnings("deprecation")
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getUserDefinedModelList().iterator().next();
        diagram = new UMLUseCaseDiagram(DIAGRAM, ns);
        project.addDiagram(diagram);
    }

    @Override
    protected void tearDown() throws Exception {
        if (project != null) {
            try {
                ProjectManager.getManager().removeProject(project);
            } catch (RuntimeException ignored) {
            }
            project = null;
        }
        super.tearDown();
    }

    public void testBuildCreatesActor() {
        Object a = new org.argouml.ai.domain.usecasediagram.ActorOperations().build(diagram, "User");
        assertNotNull("build() must return a model element", a);
        assertTrue("returned element must be an actor",
                Model.getFacade().isAActor(a));
        assertEquals("User", Model.getFacade().getName(a));
    }

    public void testBuildRejectsEmptyName() {
        try {
            new org.argouml.ai.domain.usecasediagram.ActorOperations().build(diagram, "");
            fail("expected IllegalArgumentException for empty name");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testBuildRejectsNullName() {
        try {
            new org.argouml.ai.domain.usecasediagram.ActorOperations().build(diagram, null);
            fail("expected IllegalArgumentException for null name");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testFindByNameReturnsNullOnMiss() {
        assertNull(new org.argouml.ai.domain.usecasediagram.ActorOperations().findByName(diagram, "Nobody"));
    }

    public void testFindByNameReturnsBuiltActor() {
        Object a = new org.argouml.ai.domain.usecasediagram.ActorOperations().build(diagram, "Admin");
        assertSame("findByName must return the same model element",
                a, new org.argouml.ai.domain.usecasediagram.ActorOperations().findByName(diagram, "Admin"));
    }

    public void testSetPositionNoFigIsNoOp() {
        // Fig creation is lazy for use case diagrams; setPosition
        // must not throw when the fig is not yet realized.
        Object a = new org.argouml.ai.domain.usecasediagram.ActorOperations().build(diagram, "Mover");
        new org.argouml.ai.domain.usecasediagram.ActorOperations().setPosition(diagram, a, 200, 300);
        // no assertion on fig (it is null at this point in unit tests)
    }

    public void testSetPositionNullActorIsNoOp() {
        // Defensive: should not throw on null.
        new org.argouml.ai.domain.usecasediagram.ActorOperations().setPosition(diagram, null, 0, 0);
    }

    public void testDeleteRemovesFromDiagram() {
        Object a = new org.argouml.ai.domain.usecasediagram.ActorOperations().build(diagram, "Doomed");
        new org.argouml.ai.domain.usecasediagram.ActorOperations().delete(diagram, a);
        assertNull("findByName must return null after delete",
                new org.argouml.ai.domain.usecasediagram.ActorOperations().findByName(diagram, "Doomed"));
    }
}
