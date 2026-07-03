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
 * Tests for {@link InterfaceOperations}. Mirrors {@link TestClassOperations}
 * for the {@code classdiagram} domain layer.
 */
public class TestInterfaceOperations extends TestCase {

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

    public void testBuildInterface() {
        Object iface = InterfaceOperations.build(diagram, "IPersistable");
        assertNotNull(iface);
        assertEquals("IPersistable", Model.getFacade().getName(iface));
        Facade facade = Model.getFacade();
        assertTrue("result must be an Interface", facade.isAInterface(iface));
    }

    public void testBuildInterfaceWithNullNameThrows() {
        try {
            InterfaceOperations.build(diagram, null);
            fail("expected IllegalArgumentException for null name");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testBuildInterfaceWithEmptyNameThrows() {
        try {
            InterfaceOperations.build(diagram, "");
            fail("expected IllegalArgumentException for empty name");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testFindByNameFindsExisting() {
        InterfaceOperations.build(diagram, "ICloneable");
        Object found = InterfaceOperations.findByName(diagram, "ICloneable");
        assertNotNull(found);
        assertEquals("ICloneable", Model.getFacade().getName(found));
    }

    public void testFindByNameMissingReturnsNull() {
        InterfaceOperations.build(diagram, "IOne");
        assertNull(InterfaceOperations.findByName(diagram, "IMissing"));
    }

    public void testFindByNameNullOrEmptyReturnsNull() {
        InterfaceOperations.build(diagram, "IOne");
        assertNull(InterfaceOperations.findByName(diagram, null));
        assertNull(InterfaceOperations.findByName(diagram, ""));
    }

    public void testFindByNameSkipsClasses() {
        new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "NotInterface");
        assertNull("must not return a class", InterfaceOperations.findByName(
                diagram, "NotInterface"));
    }

    public void testDeleteRemovesFromGraph() {
        Object iface = InterfaceOperations.build(diagram, "IDrop");
        InterfaceOperations.delete(diagram, iface);
        assertNull(InterfaceOperations.findByName(diagram, "IDrop"));
    }

    public void testDeleteNullIsNoop() {
        InterfaceOperations.delete(diagram, null);
        // nothing thrown
    }

    public void testRenameChangesName() {
        Object iface = InterfaceOperations.build(diagram, "IOld");
        InterfaceOperations.rename(iface, "INew");
        assertEquals("INew", Model.getFacade().getName(iface));
    }

    public void testRenameWithEmptyThrows() {
        Object iface = InterfaceOperations.build(diagram, "IOld");
        try {
            InterfaceOperations.rename(iface, "");
            fail("expected IllegalArgumentException for empty name");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testAddStereotypeAttaches() {
        Object iface = InterfaceOperations.build(diagram, "ISty");
        InterfaceOperations.addStereotype(iface, "marker");
        java.util.Collection stereos = Model.getFacade().getStereotypes(iface);
        assertNotNull("stereotypes collection should not be null", stereos);
        assertFalse("stereotype should be attached", stereos.isEmpty());
        boolean found = false;
        for (Object st : stereos) {
            if ("marker".equals(Model.getFacade().getName(st))) {
                found = true;
                break;
            }
        }
        assertTrue("stereotype named 'marker' should be attached", found);
    }

    public void testAddStereotypeNullOrEmptyIsNoop() {
        Object iface = InterfaceOperations.build(diagram, "ISty");
        InterfaceOperations.addStereotype(iface, null);
        InterfaceOperations.addStereotype(iface, "");
        java.util.Collection stereos = Model.getFacade().getStereotypes(iface);
        assertTrue("no stereotype should be attached", stereos.isEmpty());
    }
}