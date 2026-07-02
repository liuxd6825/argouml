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
import org.argouml.model.CoreHelper;
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
 * Tests for {@link AttributeOperations}.
 *
 * <p>Mirrors {@link TestClassOperations} for the {@code classdiagram}
 * domain layer. The MDR implementation is several seconds to bootstrap
 * on first use; subsequent tests in the same JVM reuse the
 * initialized singleton.
 */
public class TestAttributeOperations extends TestCase {

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
        try {
            ProjectManager.getManager().removeProject(project);
        } catch (RuntimeException ignored) {
            // ConcurrentModificationException from NotationProvider /
            // GEF listener cleanup races against model deletes applied
            // mid-test (e.g. testDeleteRemovesAttribute). Best-effort
            // cleanup; the JVM exits the test JVM soon after.
        }
        super.tearDown();
    }

    public void testBuildAttribute() {
        Object cls = ClassOperations.build(diagram, "Order");
        Object attr = AttributeOperations.build(cls, "id", null, null);
        assertNotNull(attr);
        assertEquals("id", Model.getFacade().getName(attr));
        Facade facade = Model.getFacade();
        assertTrue("should be an attribute", facade.isAAttribute(attr));
    }

    public void testBuildAttributeWithType() {
        Object cls = ClassOperations.build(diagram, "Order");
        Object attr =
                AttributeOperations.build(cls, "total", "Money", null);
        assertNotNull(attr);
        assertEquals("total", Model.getFacade().getName(attr));
        Object type = Model.getFacade().getType(attr);
        assertNotNull("type should be set", type);
        assertEquals("Money", Model.getFacade().getName(type));
    }

    public void testBuildAttributeWithVisibility() {
        Object cls = ClassOperations.build(diagram, "C");
        Object priv =
                AttributeOperations.build(cls, "secret", null, "private");
        Object vis = Model.getFacade().getVisibility(priv);
        assertNotNull("visibility should be set", vis);
        Object expected = Model.getVisibilityKind().getPrivate();
        assertEquals("should be private visibility", expected, vis);
    }

    public void testBuildAttributeWithNullNameThrows() {
        Object cls = ClassOperations.build(diagram, "C");
        try {
            AttributeOperations.build(cls, null, null, null);
            fail("expected IllegalArgumentException for null name");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testBuildAttributeWithEmptyNameThrows() {
        Object cls = ClassOperations.build(diagram, "C");
        try {
            AttributeOperations.build(cls, "", null, null);
            fail("expected IllegalArgumentException for empty name");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testBuildAttributeAttachedToClass() {
        Object cls = ClassOperations.build(diagram, "Order");
        Object attr = AttributeOperations.build(cls, "id", null, null);
        List attrs = Model.getFacade().getAttributes(cls);
        assertNotNull("attributes list should not be null", attrs);
        assertTrue("new attribute should appear in class attributes",
                attrs.contains(attr));
    }

    public void testFindByNameFindsExisting() {
        Object cls = ClassOperations.build(diagram, "Order");
        AttributeOperations.build(cls, "id", null, null);
        Object found = AttributeOperations.findByName(cls, "id");
        assertNotNull(found);
        assertEquals("id", Model.getFacade().getName(found));
    }

    public void testFindByNameMissingReturnsNull() {
        Object cls = ClassOperations.build(diagram, "Order");
        AttributeOperations.build(cls, "id", null, null);
        assertNull(AttributeOperations.findByName(cls, "missing"));
    }

    public void testFindByNameNullOrEmptyReturnsNull() {
        Object cls = ClassOperations.build(diagram, "Order");
        AttributeOperations.build(cls, "id", null, null);
        assertNull(AttributeOperations.findByName(cls, null));
        assertNull(AttributeOperations.findByName(cls, ""));
    }

    public void testFindByNameWrongClassReturnsNull() {
        Object c1 = ClassOperations.build(diagram, "C1");
        Object c2 = ClassOperations.build(diagram, "C2");
        AttributeOperations.build(c1, "id", null, null);
        assertNull("should not find attribute on a different class",
                AttributeOperations.findByName(c2, "id"));
    }

    public void testDeleteRemovesAttribute() {
        Object cls = ClassOperations.build(diagram, "Order");
        Object attr = AttributeOperations.build(cls, "temp", null, null);
        AttributeOperations.delete(cls, attr);
        assertNull(AttributeOperations.findByName(cls, "temp"));
        List attrs = Model.getFacade().getAttributes(cls);
        assertFalse("attribute should no longer be on class",
                attrs.contains(attr));
    }

    public void testDeleteNullIsNoop() {
        Object cls = ClassOperations.build(diagram, "C");
        CoreHelper h = Model.getCoreHelper();
        int before = h == null ? 0 : 0;
        AttributeOperations.delete(cls, null);
        int after = Model.getFacade().getAttributes(cls).size();
        assertEquals("delete(null) must not change class attributes",
                before, after);
    }
}
