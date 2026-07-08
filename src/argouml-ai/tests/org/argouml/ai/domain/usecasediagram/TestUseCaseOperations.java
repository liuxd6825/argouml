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

public class TestUseCaseOperations extends TestCase {

    private static final String DIAGRAM = "TestUCOps";

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

    public void testBuildCreatesUseCase() {
        Object u = UseCaseOperations.build(diagram, "Login", null);
        assertTrue(Model.getFacade().isAUseCase(u));
        assertEquals("Login", Model.getFacade().getName(u));
    }

    public void testBuildRejectsEmptyName() {
        try {
            UseCaseOperations.build(diagram, "", null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testFindByNameReturnsBuilt() {
        Object u = UseCaseOperations.build(diagram, "Checkout", null);
        assertSame(u, new UseCaseOperations().findByName(diagram, "Checkout"));
    }

    public void testListReturnsAllBuilt() {
        UseCaseOperations.build(diagram, "A", null);
        UseCaseOperations.build(diagram, "B", null);
        UseCaseOperations.build(diagram, "C", null);
        assertEquals(3, UseCaseOperations.list(diagram).size());
    }

    public void testSetPositionNoFigIsNoOp() {
        // Fig creation is lazy; setPosition must not throw when fig
        // is not yet realized.
        Object u = UseCaseOperations.build(diagram, "MoveUC", null);
        new UseCaseOperations().setPosition(diagram, u, 400, 500);
    }

    public void testDeleteRemovesFromDiagram() {
        Object u = UseCaseOperations.build(diagram, "X", null);
        new UseCaseOperations().delete(diagram, u);
        assertNull(new UseCaseOperations().findByName(diagram, "X"));
    }
}
