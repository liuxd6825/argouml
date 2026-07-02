/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.application.classdiagram;

import java.util.List;

import junit.framework.TestCase;

import org.argouml.ai.application.common.DuplicateException;
import org.argouml.ai.application.common.InvalidArgumentException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.domain.common.ModelKind;
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
import org.tigris.gef.presentation.Fig;

/**
 * Tests for {@link ClassDiagramService}.
 *
 * <p>These tests touch the live ArgoUML model subsystem, so
 * {@link InitializeModel#initializeDefault()} is called in
 * {@link #setUp()} per the project convention. The MDR implementation
 * is several seconds to bootstrap on first use; subsequent tests in
 * the same JVM reuse the initialized singleton.</p>
 *
 * <p>Setup builds a project containing a single class diagram named
 * {@code "Test"} (registered via {@link Project#addDiagram}) so each
 * test starts with a known diagram to mutate.</p>
 */
public class TestClassDiagramService extends TestCase {

    private static final String DIAGRAM = "Test";

    private Project project;
    private ArgoDiagram diagram;
    private ClassDiagramService svc;

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
        diagram = new UMLClassDiagram(DIAGRAM, ns);
        project.addDiagram(diagram);
        svc = new ClassDiagramService();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            ProjectManager.getManager().removeProject(project);
        } catch (RuntimeException ignored) {
            // ConcurrentModificationException from NotationProvider /
            // GEF listener cleanup races against model deletes applied
            // mid-test. Best-effort cleanup; the JVM exits the test JVM
            // soon after.
        }
        super.tearDown();
    }

    // -----------------------------------------------------------------
    // kind()
    // -----------------------------------------------------------------

    public void testKind() {
        assertEquals(ModelKind.CLASS, svc.kind());
    }

    // -----------------------------------------------------------------
    // createClass
    // -----------------------------------------------------------------

    public void testCreateClassHappyPath() {
        ClassDiagramService.ClassElement elt =
                svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        assertNotNull("ClassElement must not be null", elt);
        assertNotNull("underlying element must not be null", elt.element);
        assertEquals("Order", elt.name);
        assertEquals("Order", Model.getFacade().getName(elt.element));
        assertTrue("result must be a Class",
                Model.getFacade().isAClass(elt.element));
    }

    public void testCreateClassEmptyNameThrows() {
        try {
            svc.createClass(DIAGRAM, "", 10, 20, null, false);
            fail("expected InvalidArgumentException for empty name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testCreateClassNullNameThrows() {
        try {
            svc.createClass(DIAGRAM, null, 10, 20, null, false);
            fail("expected InvalidArgumentException for null name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testCreateClassNullDiagramThrows() {
        try {
            svc.createClass("NoSuchDiagram", "Order", 10, 20, null, false);
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testCreateClassDuplicateThrows() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        try {
            svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
            fail("expected DuplicateException for repeated class name");
        } catch (DuplicateException expected) {
            assertEquals("DUPLICATE_CLASS", expected.code());
        }
    }

    public void testCreateClassPlacesFig() {
        ClassDiagramService.ClassElement elt =
                svc.createClass(DIAGRAM, "Order", 200, 100, null, false);
        Fig fig = diagram.presentationFor(elt.element);
        assertNotNull("Fig must be registered on the diagram", fig);
        assertEquals("Fig x must match requested x", 200, fig.getX());
        assertEquals("Fig y must match requested y", 100, fig.getY());
    }

    public void testCreateClassWithStereotype() {
        ClassDiagramService.ClassElement elt =
                svc.createClass(DIAGRAM, "Order", 10, 20, "entity", false);
        java.util.Collection stereos =
                Model.getFacade().getStereotypes(elt.element);
        assertNotNull("stereotypes collection must not be null", stereos);
        assertFalse("stereotype should be attached", stereos.isEmpty());
        boolean found = false;
        for (Object st : stereos) {
            if ("entity".equals(Model.getFacade().getName(st))) {
                found = true;
                break;
            }
        }
        assertTrue("stereotype named 'entity' should be attached", found);
    }

    public void testCreateClassWithAbstract() {
        ClassDiagramService.ClassElement elt =
                svc.createClass(DIAGRAM, "Abs", 10, 20, null, true);
        assertTrue("class should be abstract",
                Model.getFacade().isAbstract(elt.element));
    }

    // -----------------------------------------------------------------
    // updateClass
    // -----------------------------------------------------------------

    public void testUpdateClassRenames() {
        svc.createClass(DIAGRAM, "Old", 10, 20, null, false);
        ClassDiagramService.ClassElement updated =
                svc.updateClass(DIAGRAM, "Old", "New",
                        null, null, null, null);
        assertNotNull(updated);
        assertEquals("New", updated.name);
        // ClassOperations.rename mutates in place; the underlying
        // model element should now carry the new name.
        assertEquals("New", Model.getFacade().getName(updated.element));
        assertNull("old name should no longer resolve",
                org.argouml.ai.domain.classdiagram.ClassOperations.findByName(
                        diagram, "Old"));
        assertNotNull("new name should resolve",
                org.argouml.ai.domain.classdiagram.ClassOperations.findByName(
                        diagram, "New"));
    }

    public void testUpdateClassEmptyClassNameThrows() {
        try {
            svc.updateClass(DIAGRAM, "", "New",
                    null, null, null, null);
            fail("expected InvalidArgumentException for empty className");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testUpdateClassMissingDiagramThrows() {
        try {
            svc.updateClass("NoSuchDiagram", "Order", "New",
                    null, null, null, null);
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testUpdateClassMissingClassThrows() {
        try {
            svc.updateClass(DIAGRAM, "DoesNotExist", "New",
                    null, null, null, null);
            fail("expected NotFoundException for missing class");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    // -----------------------------------------------------------------
    // deleteClass
    // -----------------------------------------------------------------

    public void testDeleteClass() {
        svc.createClass(DIAGRAM, "Temp", 10, 20, null, false);
        svc.deleteClass(DIAGRAM, "Temp");
        assertNull("class should be gone",
                org.argouml.ai.domain.classdiagram.ClassOperations.findByName(
                        diagram, "Temp"));
    }

    public void testDeleteClassMissingDiagramThrows() {
        try {
            svc.deleteClass("NoSuchDiagram", "Order");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    // -----------------------------------------------------------------
    // listClasses
    // -----------------------------------------------------------------

    public void testListClassesEmpty() {
        List<ClassDiagramService.ClassView> list = svc.listClasses(DIAGRAM);
        assertNotNull(list);
        assertTrue("empty diagram should yield empty list", list.isEmpty());
    }

    public void testListClassesPopulated() {
        svc.createClass(DIAGRAM, "Alpha", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Beta", 30, 40, null, true);
        List<ClassDiagramService.ClassView> list = svc.listClasses(DIAGRAM);
        assertNotNull(list);
        assertEquals("two classes were created", 2, list.size());
        boolean foundAlpha = false;
        boolean foundBeta = false;
        for (ClassDiagramService.ClassView v : list) {
            if ("Alpha".equals(v.name)) {
                foundAlpha = true;
                assertFalse("Alpha is not abstract", v.isAbstract);
            } else if ("Beta".equals(v.name)) {
                foundBeta = true;
                assertTrue("Beta is abstract", v.isAbstract);
            }
        }
        assertTrue("Alpha should appear", foundAlpha);
        assertTrue("Beta should appear", foundBeta);
    }

    public void testListClassesMissingDiagramThrows() {
        try {
            svc.listClasses("NoSuchDiagram");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    // -----------------------------------------------------------------
    // getClass
    // -----------------------------------------------------------------

    public void testGetClassHappyPath() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        ClassDiagramService.ClassView v = svc.getClass(DIAGRAM, "Order");
        assertNotNull(v);
        assertEquals("Order", v.name);
        assertNotNull("attributes list must not be null", v.attributes);
        assertNotNull("operations list must not be null", v.operations);
        assertNotNull("stereotypes list must not be null",
                v.stereotypeNames);
    }

    public void testGetClassWithAttributeAndOperation() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        svc.addAttribute(DIAGRAM, "Order", "id", "long", "private");
        svc.addOperation(DIAGRAM, "Order", "cancel", null, null);
        ClassDiagramService.ClassView v = svc.getClass(DIAGRAM, "Order");
        assertNotNull(v);
        assertEquals("one attribute present", 1, v.attributes.size());
        // Convention from ProjectSnapshot: "name:type"
        assertEquals("id:long", v.attributes.get(0));
        assertEquals("one operation present", 1, v.operations.size());
        // Convention from ProjectSnapshot: "name(params):returnType"
        // No params + no explicit returnType -> "cancel():void"
        assertEquals("cancel():void", v.operations.get(0));
    }

    public void testGetClassMissingThrows() {
        try {
            svc.getClass(DIAGRAM, "DoesNotExist");
            fail("expected NotFoundException for missing class");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testGetClassMissingDiagramThrows() {
        try {
            svc.getClass("NoSuchDiagram", "Order");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    // -----------------------------------------------------------------
    // Interfaces
    // -----------------------------------------------------------------

    public void testCreateInterface() {
        Object iface =
                svc.createInterface(DIAGRAM, "ICloneable", 10, 20, null);
        assertNotNull(iface);
        assertEquals("ICloneable", Model.getFacade().getName(iface));
        assertTrue("result must be an Interface",
                Model.getFacade().isAInterface(iface));
        Fig fig = diagram.presentationFor(iface);
        assertNotNull("interface Fig should be registered", fig);
        assertEquals(10, fig.getX());
        assertEquals(20, fig.getY());
    }

    public void testCreateInterfaceEmptyNameThrows() {
        try {
            svc.createInterface(DIAGRAM, "", 10, 20, null);
            fail("expected InvalidArgumentException for empty name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testCreateInterfaceNullDiagramThrows() {
        try {
            svc.createInterface("NoSuchDiagram", "I", 10, 20, null);
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    // -----------------------------------------------------------------
    // Attributes
    // -----------------------------------------------------------------

    public void testAddAttribute() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        Object attr = svc.addAttribute(
                DIAGRAM, "Order", "id", "long", "private");
        assertNotNull(attr);
        assertEquals("id", Model.getFacade().getName(attr));
        assertTrue("must be an attribute",
                Model.getFacade().isAAttribute(attr));
    }

    public void testAddAttributeEmptyAttrNameThrows() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        try {
            svc.addAttribute(DIAGRAM, "Order", "", "long", null);
            fail("expected InvalidArgumentException for empty name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testAddAttributeEmptyClassNameThrows() {
        try {
            svc.addAttribute(DIAGRAM, "", "id", "long", null);
            fail("expected InvalidArgumentException for empty className");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testAddAttributeClassMissingThrows() {
        try {
            svc.addAttribute(DIAGRAM, "DoesNotExist", "id", null, null);
            fail("expected NotFoundException for missing class");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testAddAttributeDiagramMissingThrows() {
        try {
            svc.addAttribute("NoSuchDiagram", "Order", "id", null, null);
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteAttribute() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        svc.addAttribute(DIAGRAM, "Order", "id", "long", null);
        svc.deleteAttribute(DIAGRAM, "Order", "id");
        assertNull("attribute should be gone",
                org.argouml.ai.domain.classdiagram.AttributeOperations.findByName(
                        lookupClassByName("Order"), "id"));
    }

    // -----------------------------------------------------------------
    // Operations
    // -----------------------------------------------------------------

    public void testAddOperation() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        Object op = svc.addOperation(
                DIAGRAM, "Order", "cancel", null, null);
        assertNotNull(op);
        assertEquals("cancel", Model.getFacade().getName(op));
        assertTrue("must be an operation",
                Model.getFacade().isAOperation(op));
    }

    public void testAddOperationWithReturnType() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        Object op = svc.addOperation(
                DIAGRAM, "Order", "total", "Money", null);
        assertNotNull(op);
        assertEquals("total", Model.getFacade().getName(op));
    }

    public void testAddOperationEmptyOpNameThrows() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        try {
            svc.addOperation(DIAGRAM, "Order", "", null, null);
            fail("expected InvalidArgumentException for empty name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testAddOperationClassMissingThrows() {
        try {
            svc.addOperation(DIAGRAM, "DoesNotExist", "cancel", null, null);
            fail("expected NotFoundException for missing class");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteOperation() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        svc.addOperation(DIAGRAM, "Order", "cancel", null, null);
        svc.deleteOperation(DIAGRAM, "Order", "cancel");
        assertNull("operation should be gone",
                org.argouml.ai.domain.classdiagram.OperationOperations.findByName(
                        lookupClassByName("Order"), "cancel"));
    }

    // -----------------------------------------------------------------
    // Relationships
    // -----------------------------------------------------------------

    public void testAddAssociation() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        Object assoc = svc.addAssociation(
                DIAGRAM, "Customer", "Order",
                "1", "0..*", "owner", "items");
        assertNotNull(assoc);
        assertTrue("must be an association",
                Model.getFacade().isAAssociation(assoc));
        Object found = org.argouml.ai.domain.classdiagram.RelationshipOperations
                .findAssociationBetween(diagram,
                        lookupClassByName("Customer"),
                        lookupClassByName("Order"));
        assertNotNull("association should be discoverable by endpoint name",
                found);
        assertEquals(assoc, found);
    }

    public void testAddAssociationClassMissingThrows() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        try {
            svc.addAssociation(DIAGRAM, "Customer", "NoSuch",
                    null, null, null, null);
            fail("expected NotFoundException for missing endpoint");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testAddAssociationDiagramMissingThrows() {
        try {
            svc.addAssociation("NoSuchDiagram", "Customer", "Order",
                    null, null, null, null);
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testAddGeneralization() {
        svc.createClass(DIAGRAM, "Child", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Parent", 30, 40, null, false);
        Object gen = svc.addGeneralization(DIAGRAM, "Child", "Parent");
        assertNotNull(gen);
        assertTrue("must be a generalization",
                Model.getFacade().isAGeneralization(gen));
        assertEquals(lookupClassByName("Child"),
                Model.getFacade().getSpecific(gen));
        assertEquals(lookupClassByName("Parent"),
                Model.getFacade().getGeneral(gen));
    }

    public void testAddGeneralizationClassMissingThrows() {
        svc.createClass(DIAGRAM, "Child", 10, 20, null, false);
        try {
            svc.addGeneralization(DIAGRAM, "Child", "NoSuch");
            fail("expected NotFoundException for missing parent");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testAddDependency() {
        svc.createClass(DIAGRAM, "Client", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Supplier", 30, 40, null, false);
        Object dep = svc.addDependency(DIAGRAM, "Client", "Supplier");
        assertNotNull(dep);
        assertTrue("must be a dependency",
                Model.getFacade().isADependency(dep));
    }

    public void testAddDependencyClassMissingThrows() {
        svc.createClass(DIAGRAM, "Client", 10, 20, null, false);
        try {
            svc.addDependency(DIAGRAM, "Client", "NoSuch");
            fail("expected NotFoundException for missing supplier");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteRelationshipAssociation() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        svc.addAssociation(DIAGRAM, "Customer", "Order",
                null, null, null, null);
        svc.deleteRelationship(DIAGRAM, "association", "Customer|Order");
        Object found = org.argouml.ai.domain.classdiagram.RelationshipOperations
                .findAssociationBetween(diagram,
                        lookupClassByName("Customer"),
                        lookupClassByName("Order"));
        assertNull("association should be gone", found);
    }

    public void testDeleteRelationshipGeneralization() {
        svc.createClass(DIAGRAM, "Child", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Parent", 30, 40, null, false);
        svc.addGeneralization(DIAGRAM, "Child", "Parent");
        svc.deleteRelationship(DIAGRAM, "generalization", "Child|Parent");
        Object found = org.argouml.ai.domain.classdiagram.RelationshipOperations
                .findGeneralizationBetween(diagram,
                        lookupClassByName("Child"),
                        lookupClassByName("Parent"));
        assertNull("generalization should be gone", found);
    }

    public void testDeleteRelationshipDependency() {
        svc.createClass(DIAGRAM, "Client", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Supplier", 30, 40, null, false);
        svc.addDependency(DIAGRAM, "Client", "Supplier");
        svc.deleteRelationship(DIAGRAM, "dependency", "Client|Supplier");
        Object found = org.argouml.ai.domain.classdiagram.RelationshipOperations
                .findDependencyBetween(diagram,
                        lookupClassByName("Client"),
                        lookupClassByName("Supplier"));
        assertNull("dependency should be gone", found);
    }

    public void testDeleteRelationshipUnknownTypeThrows() {
        try {
            svc.deleteRelationship(DIAGRAM, "unknown", "x|y");
            fail("expected InvalidArgumentException for unknown type");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_RELATIONSHIP_TYPE", expected.code());
        }
    }

    public void testDeleteRelationshipMissingDiagramThrows() {
        try {
            svc.deleteRelationship("NoSuchDiagram", "association", "x|y");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private Object lookupClassByName(String name) {
        return org.argouml.ai.domain.classdiagram.ClassOperations.findByName(
                diagram, name);
    }
}