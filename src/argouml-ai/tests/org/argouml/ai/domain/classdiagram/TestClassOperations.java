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

import java.util.Collection;

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
 * Tests for {@link ClassOperations}.
 *
 * <p>These tests touch the live ArgoUML model subsystem, so
 * {@link InitializeModel#initializeDefault()} is called in
 * {@link #setUp()} per the project convention. The MDR implementation
 * is several seconds to bootstrap on first use; subsequent tests in
 * the same JVM reuse the initialized singleton.
 *
 * <p>Setup builds a project containing a single class diagram named
 * {@code "Test"} (registered via {@link Project#addDiagram}) so each
 * test starts with a known diagram to mutate.
 *
 * <p>The API corrections in {@link org.argouml.ai.domain.classdiagram}
 * compared to the plan as written: ArgoUML's {@link Facade} exposes
 * {@code getStereotypes(Object)} (a {@link Collection}) rather than
 * {@code getStereotype(Object)} (singular). The test asserts on the
 * collection as recommended by
 * {@code StereotypeUtility.getStereotypes} in the production code
 * (see {@code src/argouml-app/src/org/argouml/uml/StereotypeUtility.java}).
 */
public class TestClassOperations extends TestCase {

    private Project project;
    private ArgoDiagram diagram;
    @SuppressWarnings("unused")
    private Object ns;

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
        ns = project.getModel();
        diagram = new UMLClassDiagram("Test", ns);
        project.addDiagram(diagram);
    }

    @Override
    protected void tearDown() throws Exception {
        ProjectManager.getManager().removeProject(project);
        super.tearDown();
    }

    public void testBuildClass() {
        Object c = ClassOperations.build(diagram, "Order");
        assertNotNull(c);
        assertEquals("Order", Model.getFacade().getName(c));
    }

    public void testBuildClassWithNullNameThrows() {
        try {
            ClassOperations.build(diagram, null);
            fail("expected IllegalArgumentException for null name");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testBuildClassWithEmptyNameThrows() {
        try {
            ClassOperations.build(diagram, "");
            fail("expected IllegalArgumentException for empty name");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testFindByNameFindsExisting() {
        ClassOperations.build(diagram, "Customer");
        Object found = ClassOperations.findByName(diagram, "Customer");
        assertNotNull(found);
        assertEquals("Customer", Model.getFacade().getName(found));
    }

    public void testFindByNameMissingReturnsNull() {
        ClassOperations.build(diagram, "Order");
        assertNull(ClassOperations.findByName(diagram, "Missing"));
    }

    public void testFindByNameNullOrEmptyReturnsNull() {
        ClassOperations.build(diagram, "Order");
        assertNull(ClassOperations.findByName(diagram, null));
        assertNull(ClassOperations.findByName(diagram, ""));
    }

    public void testDeleteRemovesFromGraphAndModel() {
        Object c = ClassOperations.build(diagram, "Temp");
        ClassOperations.delete(diagram, c);
        assertNull(ClassOperations.findByName(diagram, "Temp"));
    }

    public void testRenameChangesName() {
        Object c = ClassOperations.build(diagram, "Old");
        ClassOperations.rename(c, "New");
        assertEquals("New", Model.getFacade().getName(c));
    }

    public void testSetAbstractTogglesFlag() {
        Object c = ClassOperations.build(diagram, "Abs");
        ClassOperations.setAbstract(c, true);
        assertTrue("isAbstract true", Model.getFacade().isAbstract(c));
        ClassOperations.setAbstract(c, false);
        assertFalse("isAbstract false", Model.getFacade().isAbstract(c));
    }

    public void testAddStereotypeAttaches() {
        Object c = ClassOperations.build(diagram, "Sty");
        ClassOperations.addStereotype(c, "entity");
        Collection stereos = Model.getFacade().getStereotypes(c);
        assertNotNull("stereotypes collection should not be null", stereos);
        assertFalse("stereotype should be attached", stereos.isEmpty());
        boolean foundEntity = false;
        for (Object st : stereos) {
            if ("entity".equals(Model.getFacade().getName(st))) {
                foundEntity = true;
                break;
            }
        }
        assertTrue("stereotype named 'entity' should be attached",
                foundEntity);
    }
}