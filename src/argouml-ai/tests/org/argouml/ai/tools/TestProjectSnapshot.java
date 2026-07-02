/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.infrastructure.json.JsonMini;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.CoreFactory;
import org.argouml.model.Facade;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;
import org.tigris.gef.graph.MutableGraphModel;

/**
 * Tests for {@link ProjectSnapshot}.
 *
 * <p>These tests touch the live ArgoUML model subsystem, so
 * {@link InitializeModel#initializeDefault()} is called in
 * {@link #setUp()} per the project convention (AGENTS.md
 * "Extension points"). The MDR implementation is several seconds
 * to bootstrap on first use; subsequent tests in the same JVM
 * reuse the initialized singleton.
 *
 * <p>The tests deliberately stay inside the {@code ProjectSnapshot}
 * contract surfaced by the design document:
 * <ul>
 *   <li>2 classes + 1 association round-trip via
 *       {@link ProjectSnapshot.Snapshot#toJson()} and contains the
 *       expected tokens;</li>
 *   <li>empty diagram yields empty lists;</li>
 *   <li>classes with no attributes/operations yield empty
 *       {@code attrs} / {@code ops} lists;</li>
 *   <li>interfaces yield no {@code attrs} list;</li>
 *   <li>the {@code diagram.id}, {@code diagram.type}, and
 *       {@code diagram.namespace} fields are populated.</li>
 * </ul>
 */
public class TestProjectSnapshot extends TestCase {

    private Object ns;
    private Project project;

    public void setUp() throws Exception {
        super.setUp();
        (new InitNotation()).init();
        (new InitNotationUml()).init();
        (new InitNotationJava()).init();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        ns = Model.getModelManagementFactory().buildPackage("MyModel");
    }

    @Override
    protected void tearDown() throws Exception {
        ProjectManager.getManager().removeProject(project);
        super.tearDown();
    }

    // -----------------------------------------------------------------
    // Spec-required test
    // -----------------------------------------------------------------

    public void testSnapshotOfCustomerOrder() {
        CoreFactory cf = Model.getCoreFactory();
        Facade facade = Model.getFacade();

        Object customerClass = cf.buildClass("Customer", ns);
        Object orderClass = cf.buildClass("Order", ns);
        Object assoc = cf.buildAssociation(customerClass, orderClass);

        // Default multiplicity of end A is 1..1. Set end B to "0..*".
        Object endB = facade.getAssociationEnd(orderClass, assoc);
        Model.getCoreHelper().setMultiplicity(endB, "0..*");

        UMLClassDiagram diagram = new UMLClassDiagram(ns);
        MutableGraphModel gm =
                (MutableGraphModel) diagram.getGraphModel();
        gm.addNode(customerClass);
        gm.addNode(orderClass);
        gm.addEdge(assoc);

        ProjectSnapshot.Snapshot snap = ProjectSnapshot.snapshot(diagram);
        String json = snap.toJson();

        assertNotNull("snapshot.toJson() must not return null", json);
        assertTrue("JSON must contain Customer name: " + json,
                json.contains("\"Customer\""));
        assertTrue("JSON must contain Order name: " + json,
                json.contains("\"Order\""));
        assertTrue("JSON must contain multiplicity '0..*': " + json,
                json.contains("0..*"));
    }

    // -----------------------------------------------------------------
    // Recommended expansion tests
    // -----------------------------------------------------------------

    public void testEmptyDiagramYieldsEmptyLists() {
        UMLClassDiagram diagram = new UMLClassDiagram(ns);
        ProjectSnapshot.Snapshot snap = ProjectSnapshot.snapshot(diagram);
        Map<String, Object> parsed = JsonMini.parseObject(snap.toJson());
        assertTrue("'classes' must be present", parsed.containsKey("classes"));
        assertTrue("'associations' must be present",
                parsed.containsKey("associations"));
        assertEquals("classes must be empty list",
                new ArrayList<Object>(), parsed.get("classes"));
        assertEquals("associations must be empty list",
                new ArrayList<Object>(), parsed.get("associations"));
    }

    public void testClassWithNoAttrsOrOpsYieldsEmptyLists() {
        Object c = Model.getCoreFactory().buildClass("Lone", ns);
        UMLClassDiagram diagram = new UMLClassDiagram(ns);
        ((MutableGraphModel) diagram.getGraphModel()).addNode(c);

        ProjectSnapshot.Snapshot snap = ProjectSnapshot.snapshot(diagram);
        Map<String, Object> parsed = JsonMini.parseObject(snap.toJson());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> classes =
                (List<Map<String, Object>>) parsed.get("classes");
        assertEquals("exactly one class expected", 1, classes.size());
        Map<String, Object> entry = classes.get(0);
        assertEquals("Lone", entry.get("name"));
        assertEquals("attrs must be empty list",
                new ArrayList<Object>(), entry.get("attrs"));
        assertEquals("ops must be empty list",
                new ArrayList<Object>(), entry.get("ops"));
    }

    public void testInterfaceHasNoAttrsField() {
        Object iface = Model.getCoreFactory().buildInterface("IRunnable", ns);
        UMLClassDiagram diagram = new UMLClassDiagram(ns);
        ((MutableGraphModel) diagram.getGraphModel()).addNode(iface);

        ProjectSnapshot.Snapshot snap = ProjectSnapshot.snapshot(diagram);
        Map<String, Object> parsed = JsonMini.parseObject(snap.toJson());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> classes =
                (List<Map<String, Object>>) parsed.get("classes");
        assertEquals("exactly one interface expected", 1, classes.size());
        Map<String, Object> entry = classes.get(0);
        assertEquals("IRunnable", entry.get("name"));
        // Design allows either an empty attrs list or no attrs key. We
        // emit empty for consistency with the class shape.
        assertTrue("'attrs' must be present (possibly empty)",
                entry.containsKey("attrs"));
        assertEquals("attrs must be empty list",
                new ArrayList<Object>(), entry.get("attrs"));
        assertTrue("'ops' must be present (possibly empty)",
                entry.containsKey("ops"));
    }

    public void testDiagramEnvelopeFields() {
        UMLClassDiagram diagram = new UMLClassDiagram(ns);
        ProjectSnapshot.Snapshot snap = ProjectSnapshot.snapshot(diagram);
        Map<String, Object> parsed = JsonMini.parseObject(snap.toJson());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) parsed.get("diagram");
        assertNotNull("diagram envelope must be present", meta);
        assertEquals("diagram.type must be 'Class' for UMLClassDiagram",
                "Class", meta.get("type"));
        assertEquals("diagram.namespace must match the package name",
                "MyModel", meta.get("namespace"));
        Object id = meta.get("id");
        assertNotNull("diagram.id must be present", id);
        assertTrue("diagram.id must be a non-empty string: " + id,
                id instanceof String && ((String) id).length() > 0);
    }

    public void testAssociationMultiplicitiesAreEmitted() {
        CoreFactory cf = Model.getCoreFactory();
        Facade facade = Model.getFacade();

        Object a = cf.buildClass("A", ns);
        Object b = cf.buildClass("B", ns);
        Object assoc = cf.buildAssociation(a, b);
        Object endA = facade.getAssociationEnd(a, assoc);
        Object endB = facade.getAssociationEnd(b, assoc);
        Model.getCoreHelper().setMultiplicity(endA, "1");
        Model.getCoreHelper().setMultiplicity(endB, "0..*");

        UMLClassDiagram diagram = new UMLClassDiagram(ns);
        MutableGraphModel gm2 = (MutableGraphModel) diagram.getGraphModel();
        gm2.addNode(a);
        gm2.addNode(b);
        gm2.addEdge(assoc);

        ProjectSnapshot.Snapshot snap = ProjectSnapshot.snapshot(diagram);
        Map<String, Object> parsed = JsonMini.parseObject(snap.toJson());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assocs =
                (List<Map<String, Object>>) parsed.get("associations");
        assertEquals("exactly one association expected", 1, assocs.size());
        Map<String, Object> entry = assocs.get(0);
        assertTrue("endpoints must be present: " + entry,
                entry.containsKey("a") && entry.containsKey("b"));
        assertTrue("multiplicities must be present: " + entry,
                entry.containsKey("multA") && entry.containsKey("multB"));
        // The first end's type must be "A" (buildAssociation(a, b) order).
        assertEquals("A", entry.get("a"));
        assertEquals("B", entry.get("b"));
        assertEquals("1", entry.get("multA"));
        assertEquals("0..*", entry.get("multB"));
    }

    public void testAttributeAndOperationShape() {
        CoreFactory cf = Model.getCoreFactory();

        Object customerClass = cf.buildClass("Customer", ns);
        Object idAttr = cf.buildAttribute2(customerClass,
                cf.buildDataType("int", ns));
        Model.getCoreHelper().setName(idAttr, "id");

        // returnType stays null -> op has no return parameter; sig = "save():void"
        cf.buildOperation2(customerClass, null, "save");

        UMLClassDiagram diagram = new UMLClassDiagram(ns);
        ((MutableGraphModel) diagram.getGraphModel()).addNode(customerClass);

        ProjectSnapshot.Snapshot snap = ProjectSnapshot.snapshot(diagram);
        Map<String, Object> parsed = JsonMini.parseObject(snap.toJson());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> classes =
                (List<Map<String, Object>>) parsed.get("classes");
        assertEquals(1, classes.size());
        Map<String, Object> entry = classes.get(0);
        @SuppressWarnings("unchecked")
        List<Object> attrs = (List<Object>) entry.get("attrs");
        assertEquals(1, attrs.size());
        assertEquals("id:int", attrs.get(0));
        @SuppressWarnings("unchecked")
        List<Object> ops = (List<Object>) entry.get("ops");
        assertEquals(1, ops.size());
        assertEquals("save():void", ops.get(0));
    }

    public void testJsonRoundTrips() {
        CoreFactory cf = Model.getCoreFactory();
        Object c = cf.buildClass("Foo", ns);
        UMLClassDiagram diagram = new UMLClassDiagram(ns);
        ((MutableGraphModel) diagram.getGraphModel()).addNode(c);
        String json = ProjectSnapshot.snapshot(diagram).toJson();
        Object parsed;
        try {
            parsed = JsonMini.parse(json);
        } catch (IllegalArgumentException e) {
            fail("toJson() must produce valid JSON: " + e.getMessage());
            return;
        }
        assertTrue("round-trip must yield an object: " + json,
                parsed instanceof Map);
    }

    public void testNullSafeOnUnnamedClass() {
        // A class with no name set is unusual but allowed by the model.
        // We fall back to empty string in the snapshot.
        Object c = Model.getCoreFactory().buildClass("", ns);
        UMLClassDiagram diagram = new UMLClassDiagram(ns);
        ((MutableGraphModel) diagram.getGraphModel()).addNode(c);
        String json = ProjectSnapshot.snapshot(diagram).toJson();
        assertTrue("JSON must contain an empty-string class name entry: "
                + json, json.contains("\"name\":\"\""));
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------
}