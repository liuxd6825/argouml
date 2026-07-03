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
 * Tests for {@link RelationshipOperations}. Mirrors
 * {@link TestClassOperations} but exercises the create / find / delete
 * triple for association, generalization, and dependency.
 */
public class TestRelationshipOperations extends TestCase {

    private Project project;
    private ArgoDiagram diagram;
    private Object classA;
    private Object classB;

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
        classA = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "A");
        classB = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "B");
    }

    @Override
    protected void tearDown() throws Exception {
        ProjectManager.getManager().removeProject(project);
        super.tearDown();
    }

    public void testBuildAssociation() {
        Object assoc = RelationshipOperations.buildAssociation(
                diagram, classA, classB, null, null, null, null);
        assertNotNull(assoc);
        Facade facade = Model.getFacade();
        assertTrue("should be an association", facade.isAAssociation(assoc));
    }

    public void testBuildAssociationWithMultiplicitiesAndRoles() {
        Object assoc = RelationshipOperations.buildAssociation(
                diagram, classA, classB,
                "1", "0..*", "owner", "items");
        assertNotNull(assoc);
        Object[] ends = Model.getFacade().getConnections(assoc).toArray();
        assertEquals(2, ends.length);
        // Endpoint order matches the (classA, classB) call order to
        // CoreFactory.buildAssociation(c1, c2), per OpExecutor's
        // applyMultiplicities / applyRoleNames.
        assertEquals("1", multiplicityAsString(ends[0]));
        assertEquals("0..*", multiplicityAsString(ends[1]));
        assertEquals("owner", Model.getFacade().getName(ends[0]));
        assertEquals("items", Model.getFacade().getName(ends[1]));
    }

    /**
     * Render an association-end multiplicity as a String for
     * equality asserts. The MDR backend returns a
     * {@code Multiplicity} model element rather than a literal
     * string, so we ask the facade to render it.
     */
    private static String multiplicityAsString(Object end) {
        Object mult = Model.getFacade().getMultiplicity(end);
        if (mult == null) {
            return null;
        }
        return Model.getFacade().toString(mult);
    }

    public void testBuildGeneralization() {
        Object gen = RelationshipOperations.buildGeneralization(
                diagram, classA, classB);
        assertNotNull(gen);
        Facade facade = Model.getFacade();
        assertTrue("should be a generalization", facade.isAGeneralization(gen));
        assertEquals(classA, facade.getSpecific(gen));
        assertEquals(classB, facade.getGeneral(gen));
    }

    public void testBuildDependency() {
        Object dep = RelationshipOperations.buildDependency(
                diagram, classA, classB);
        assertNotNull(dep);
        Facade facade = Model.getFacade();
        assertTrue("should be a dependency", facade.isADependency(dep));
    }

    public void testFindAssociationBetween() {
        Object assoc = RelationshipOperations.buildAssociation(
                diagram, classA, classB, null, null, null, null);
        Object found = RelationshipOperations.findAssociationBetween(
                diagram, classA, classB);
        assertEquals(assoc, found);
    }

    public void testFindAssociationBetweenOrderInsensitive() {
        Object assoc = RelationshipOperations.buildAssociation(
                diagram, classA, classB, null, null, null, null);
        Object found = RelationshipOperations.findAssociationBetween(
                diagram, classB, classA);
        assertEquals(assoc, found);
    }

    public void testFindAssociationBetweenMissing() {
        RelationshipOperations.buildAssociation(
                diagram, classA, classB, null, null, null, null);
        Object c3 = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "C");
        assertNull(RelationshipOperations.findAssociationBetween(
                diagram, classA, c3));
    }

    public void testFindGeneralizationBetween() {
        Object gen = RelationshipOperations.buildGeneralization(
                diagram, classA, classB);
        Object found = RelationshipOperations.findGeneralizationBetween(
                diagram, classA, classB);
        assertEquals(gen, found);
    }

    public void testFindGeneralizationBetweenWrongOrderReturnsNull() {
        RelationshipOperations.buildGeneralization(diagram, classA, classB);
        // Generalization is directional: parent/child are not symmetric,
        // so asking (parent, child) should miss.
        assertNull(RelationshipOperations.findGeneralizationBetween(
                diagram, classB, classA));
    }

    public void testFindDependencyBetween() {
        Object dep = RelationshipOperations.buildDependency(
                diagram, classA, classB);
        Object found = RelationshipOperations.findDependencyBetween(
                diagram, classA, classB);
        assertEquals(dep, found);
    }

    public void testFindDependencyBetweenMissing() {
        RelationshipOperations.buildDependency(diagram, classA, classB);
        Object c3 = new org.argouml.ai.domain.classdiagram.ClassOperations().build(diagram, "C");
        assertNull(RelationshipOperations.findDependencyBetween(
                diagram, classA, c3));
    }

    public void testDeleteRemovesAssociation() {
        Object assoc = RelationshipOperations.buildAssociation(
                diagram, classA, classB, null, null, null, null);
        RelationshipOperations.delete(diagram, assoc);
        assertNull(RelationshipOperations.findAssociationBetween(
                diagram, classA, classB));
    }

    public void testDeleteRemovesGeneralization() {
        Object gen = RelationshipOperations.buildGeneralization(
                diagram, classA, classB);
        RelationshipOperations.delete(diagram, gen);
        assertNull(RelationshipOperations.findGeneralizationBetween(
                diagram, classA, classB));
    }

    public void testDeleteNullIsNoop() {
        RelationshipOperations.delete(diagram, null);
    }
}
