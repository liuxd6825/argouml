/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.domain.classdiagram;

import java.util.List;

import junit.framework.TestCase;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;

/**
 * Tests for {@link OperationOperations}. Mirrors
 * {@link TestAttributeOperations} but for UML Operations.
 */
public class TestOperationOperations extends TestCase {

    private Project project;
    private ArgoDiagram diagram;

    static {
        try {
            new InitNotation().init();
            new InitNotationUml().init();
            new InitNotationJava().init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getModel();
        diagram = new UMLClassDiagram("Test", ns);
        project.addDiagram(diagram);
    }

    @Override
    protected void tearDown() throws Exception {
        ProjectManager.getManager().removeProject(project);
        super.tearDown();
    }

    public void testBuildOperation() {
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "Order");
        Object op = OperationOperations.build(cls, "cancel", null, null);
        assertNotNull(op);
        assertEquals("cancel", Model.getFacade().getName(op));
        Facade facade = Model.getFacade();
        assertTrue("should be an operation", facade.isAOperation(op));
    }

    public void testBuildOperationWithReturnType() {
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "Order");
        Object op = OperationOperations.build(
                cls, "total", "Money", null);
        assertNotNull(op);
        assertEquals("total", Model.getFacade().getName(op));
        java.util.List retParams =
                Model.getCoreHelper().getReturnParameters(op);
        assertNotNull("return params should not be null", retParams);
        assertFalse("operation should have a return parameter",
                retParams.isEmpty());
        Object returnParam = retParams.get(0);
        Object returnType = Model.getFacade().getType(returnParam);
        assertNotNull("return type should be set", returnType);
        assertEquals("Money", Model.getFacade().getName(returnType));
    }

    public void testBuildOperationWithVisibility() {
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "C");
        Object priv = OperationOperations.build(
                cls, "secret", null, "private");
        assertNotNull(priv);
        Object expected = Model.getVisibilityKind().getPrivate();
        assertEquals("should be private visibility",
                expected, Model.getFacade().getVisibility(priv));
    }

    public void testBuildOperationWithNullNameThrows() {
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "C");
        try {
            OperationOperations.build(cls, null, null, null);
            fail("expected IllegalArgumentException for null name");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testBuildOperationWithEmptyNameThrows() {
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "C");
        try {
            OperationOperations.build(cls, "", null, null);
            fail("expected IllegalArgumentException for empty name");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testBuildOperationAttachedToClass() {
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "Order");
        Object op = OperationOperations.build(cls, "submit", null, null);
        List ops = Model.getFacade().getOperations(cls);
        assertNotNull("operations list should not be null", ops);
        assertTrue("new operation should appear in class operations",
                ops.contains(op));
    }

    public void testFindByNameFindsExisting() {
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "Order");
        OperationOperations.build(cls, "submit", null, null);
        Object found = OperationOperations.findByName(cls, "submit");
        assertNotNull(found);
        assertEquals("submit", Model.getFacade().getName(found));
    }

    public void testFindByNameMissingReturnsNull() {
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "Order");
        OperationOperations.build(cls, "submit", null, null);
        assertNull(OperationOperations.findByName(cls, "missing"));
    }

    public void testFindByNameNullOrEmptyReturnsNull() {
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "Order");
        OperationOperations.build(cls, "submit", null, null);
        assertNull(OperationOperations.findByName(cls, null));
        assertNull(OperationOperations.findByName(cls, ""));
    }

    public void testFindByNameWrongClassReturnsNull() {
        Object c1 = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "C1");
        Object c2 = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "C2");
        OperationOperations.build(c1, "submit", null, null);
        assertNull("should not find operation on a different class",
                OperationOperations.findByName(c2, "submit"));
    }

    public void testDeleteRemovesOperation() {
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "Order");
        Object op = OperationOperations.build(cls, "temp", null, null);
        OperationOperations.delete(cls, op);
        assertNull(OperationOperations.findByName(cls, "temp"));
        List ops = Model.getFacade().getOperations(cls);
        assertFalse("operation should no longer be on class",
                ops.contains(op));
    }

    public void testDeleteNullIsNoop() {
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "C");
        OperationOperations.build(cls, "submit", null, null);
        int before = Model.getFacade().getOperations(cls).size();
        OperationOperations.delete(cls, null);
        int after = Model.getFacade().getOperations(cls).size();
        assertEquals("delete(null) must not change class operations",
                before, after);
    }
}
