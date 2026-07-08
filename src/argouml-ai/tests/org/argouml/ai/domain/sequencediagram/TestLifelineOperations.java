/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.sequencediagram;

import java.beans.PropertyVetoException;

import junit.framework.TestCase;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.sequence2.diagram.UMLSequenceDiagram;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.MutableGraphModel;

/**
 * Tests for {@link LifelineOperations}. The diagram is built with a
 * real {@code MCollaboration} namespace. ClassifierRoles are added
 * via direct {@code gm.getNodes().add(...)} to bypass the
 * {@code SequenceDiagramRenderer}'s NPE in headless (no-Editor)
 * mode — see {@link ClassifierRoleOperations} Javadoc.
 */
public class TestLifelineOperations extends TestCase {

    private static final String DIAGRAM = "TestSDLifeline";

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
    private LifelineOperations ops;
    private Object customerRole;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object modelRoot = project.getUserDefinedModelList().iterator().next();
        Object collaboration = Model.getCollaborationsFactory()
                .buildCollaboration(modelRoot);
        diagram = new UMLSequenceDiagram(collaboration);
        try {
            diagram.setName(DIAGRAM);
        } catch (PropertyVetoException e) {
            throw new RuntimeException(e);
        }
        project.addDiagram(diagram);
        ops = new LifelineOperations();

        MutableGraphModel gm =
                (MutableGraphModel) diagram.getGraphModel();
        customerRole = Model.getCollaborationsFactory()
                .buildClassifierRole(collaboration);
        Model.getCoreHelper().setName(customerRole, "Customer");
        gm.getNodes().add(customerRole);
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

    public void testBuildCreatesLifeline() {
        Object lifeline = ops.build(diagram, "Customer");
        assertNotNull("build must return a lifeline", lifeline);
        assertTrue("must be Lifeline: " + lifeline.getClass(),
                Model.getFacade().isALifeline(lifeline));
        assertEquals("Customer", Model.getFacade().getName(lifeline));
    }

    public void testBuildRejectsEmptyName() {
        try {
            ops.build(diagram, "");
            fail("expected IllegalArgumentException for empty name");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testIsTargetType() {
        Object lifeline = ops.build(diagram, "Anonymous");
        assertTrue("isTargetType(lifeline) must be true",
                ops.isTargetType(lifeline));
        assertFalse("isTargetType(string) must be false",
                ops.isTargetType("not a model element"));
    }
}
