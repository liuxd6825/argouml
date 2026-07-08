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

/**
 * Tests for {@link ClassifierRoleOperations} against a freshly built
 * UML sequence diagram. The diagram is built with a real
 * {@code MCollaboration} as its namespace (mandatory for
 * {@code SequenceDiagramGraphModel.canAddNode}).
 *
 * <p>Tests bypass the graph-model listener chain because the
 * sequence diagram's {@code SequenceDiagramRenderer} NPEs when a
 * node is added via the standard {@code gm.addNode(elem)} path in
 * headless (no-Editor) mode — see {@link ClassifierRoleOperations}
 * Javadoc for the rationale. The model element is registered
 * directly in the graph model's nodes list, which is what the
 * operations class uses internally as well.</p>
 */
public class TestClassifierRoleOperations extends TestCase {

    private static final String DIAGRAM = "TestSDRole";

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
    private ClassifierRoleOperations ops;
    private Object collaboration;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object modelRoot = project.getUserDefinedModelList().iterator().next();
        collaboration = Model.getCollaborationsFactory()
                .buildCollaboration(modelRoot);
        diagram = new UMLSequenceDiagram(collaboration);
        try {
            diagram.setName(DIAGRAM);
        } catch (PropertyVetoException e) {
            throw new RuntimeException(e);
        }
        project.addDiagram(diagram);
        ops = new ClassifierRoleOperations();
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

    public void testBuildCreatesRole() {
        Object role = ops.build(diagram, "Customer");
        assertNotNull("build should return a role", role);
        assertTrue("built element must be ClassifierRole: "
                + role.getClass(),
                Model.getFacade().isAClassifierRole(role));
        assertEquals("Customer", Model.getFacade().getName(role));
    }

    public void testBuildRejectsEmptyName() {
        try {
            ops.build(diagram, "");
            fail("expected IllegalArgumentException for empty name");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testBuildRejectsNullName() {
        try {
            ops.build(diagram, null);
            fail("expected IllegalArgumentException for null name");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testFindByNameAndUuid() {
        Object role = ops.build(diagram, "Customer");
        assertSame("findByName must return the built element",
                role, ops.findByName(diagram, "Customer"));
        String uuid = Model.getFacade().getUUID(role);
        assertNotNull("uuid must be assigned", uuid);
        assertSame("findByUuid must return the built element",
                role, ops.findByUuid(diagram, uuid));
    }

    public void testFindByNameReturnsNullOnMiss() {
        assertNull(ops.findByName(diagram, "Nobody"));
    }

    public void testIsTargetTypeFilters() {
        Object role = ops.build(diagram, "Customer");
        assertTrue("isTargetType(role) must be true", ops.isTargetType(role));
        assertFalse("isTargetType(string) must be false",
                ops.isTargetType("not a model element"));
    }

    public void testDeleteRemovesFromDiagram() {
        Object role = ops.build(diagram, "Doomed");
        ops.delete(diagram, role);
        assertNull("findByName must return null after delete",
                ops.findByName(diagram, "Doomed"));
    }
}
