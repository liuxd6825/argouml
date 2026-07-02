/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.ops;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

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
import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;
import org.tigris.gef.graph.MutableGraphModel;
import org.tigris.gef.presentation.Fig;

/**
 * Tests for {@link OpExecutor}.
 *
 * <p>These tests touch the live ArgoUML model subsystem, so
 * {@link InitializeModel#initializeDefault()} is called in
 * {@link #setUp()} per the project convention. The MDR implementation
 * is several seconds to bootstrap on first use; subsequent tests in
 * the same JVM reuse the initialized singleton.
 *
 * <p>The tests cover the MVP contract from Task 7:
 * <ul>
 *   <li>{@link OpExecutor#apply(java.util.List)} places a Fig for a
 *       freshly created {@code MClass} at the AI-supplied (x, y);</li>
 *   <li>same for {@code MInterface};</li>
 *   <li>multiple ops in one call all land;</li>
 *   <li>missing or empty {@code name} on an ADD_CLASS is rejected
 *       with {@link IllegalArgumentException};</li>
 *   <li>ops of types not yet implemented throw
 *       {@link UnsupportedOperationException};</li>
 *   <li>the executor's lookup helper
 *       {@link OpExecutor#findClassByName(String)} returns the
 *       matching model element (or {@code null}).</li>
 * </ul>
 */
public class TestOpExecutor extends TestCase {

    static {
        // The notation providers must be initialised before the diagram
        // is rendered. Done once per class-loader.
        new InitNotation().init();
        new InitNotationUml().init();
        new InitNotationJava().init();
    }

    private Object ns;
    private Project project;
    private UMLClassDiagram diagram;
    private OpExecutor exec;

    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        // DefaultUndoManager starts with undoMax == 0, which makes
        // addCommand() a no-op (and thus no "undoAdded" events fire).
        // The production UI does not call setUndoMax either, so undo
        // is effectively disabled out of the box, but the AI tests
        // want to observe the stack growing under apply(), so we
        // explicitly enable undo here.
        project.getUndoManager().setUndoMax(100);
        ns = Model.getModelManagementFactory().buildPackage("OpModel");
        diagram = new UMLClassDiagram(ns);
        exec = new OpExecutor(diagram);
    }

    @Override
    protected void tearDown() throws Exception {
        ProjectManager.getManager().removeProject(project);
        super.tearDown();
    }

    // -----------------------------------------------------------------
    // Spec-required test
    // -----------------------------------------------------------------

    public void testAddClassPlacesFig() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", "Order");
        op.setInt("x", 200);
        op.setInt("y", 100);

        exec.apply(Collections.singletonList(op));

        Object found = findNodeByName("Order");
        assertNotNull("Order class should exist in the diagram", found);
        Fig fig = diagram.presentationFor(found);
        assertNotNull("Order should have a Fig presentation", fig);
        assertEquals("Fig x must match AI-supplied x", 200, fig.getX());
        assertEquals("Fig y must match AI-supplied y", 100, fig.getY());
    }

    // -----------------------------------------------------------------
    // Recommended expansion tests
    // -----------------------------------------------------------------

    public void testAddInterfacePlacesFig() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_INTERFACE);
        op.setString("name", "IRunnable");
        op.setInt("x", 50);
        op.setInt("y", 75);

        exec.apply(Collections.singletonList(op));

        Object found = findNodeByName("IRunnable");
        assertNotNull("IRunnable interface should exist", found);
        Fig fig = diagram.presentationFor(found);
        assertNotNull("IRunnable should have a Fig presentation", fig);
        assertEquals(50, fig.getX());
        assertEquals(75, fig.getY());
    }

    public void testApplyMultipleOpsPlacesBothFigs() {
        PlannedOp a = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        a.setString("name", "Alpha");
        a.setInt("x", 10);
        a.setInt("y", 20);

        PlannedOp b = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        b.setString("name", "Beta");
        b.setInt("x", 110);
        b.setInt("y", 220);

        exec.apply(Arrays.asList(a, b));

        Object alpha = findNodeByName("Alpha");
        Object beta = findNodeByName("Beta");
        assertNotNull("Alpha should exist", alpha);
        assertNotNull("Beta should exist", beta);

        Fig fa = diagram.presentationFor(alpha);
        Fig fb = diagram.presentationFor(beta);
        assertNotNull("Alpha Fig should exist", fa);
        assertNotNull("Beta Fig should exist", fb);
        assertEquals(10, fa.getX());
        assertEquals(20, fa.getY());
        assertEquals(110, fb.getX());
        assertEquals(220, fb.getY());
    }

    public void testAddClassRejectsNullName() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        // name not set -> getString returns null
        op.setInt("x", 0);
        op.setInt("y", 0);

        try {
            exec.apply(Collections.singletonList(op));
            fail("apply() must reject ADD_CLASS with null name");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    public void testAddClassRejectsEmptyName() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", "");
        op.setInt("x", 0);
        op.setInt("y", 0);

        try {
            exec.apply(Collections.singletonList(op));
            fail("apply() must reject ADD_CLASS with empty name");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    public void testAddInterfaceRejectsNullName() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_INTERFACE);
        // no name set

        try {
            exec.apply(Collections.singletonList(op));
            fail("apply() must reject ADD_INTERFACE with null name");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    public void testAddAttributeIsUnsupported() {
        // Kept as a regression: ADD_ATTRIBUTE without a className throws IAE
        // because the className lookup happens before reaching the model.
        // The body of the test was tightened in Task 8 to no longer rely on
        // the global "all op types must throw UnsupportedOperationException"
        // precondition -- ADD_ATTRIBUTE is now supported for valid args.
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_ATTRIBUTE);
        op.setString("name", "id");
        try {
            exec.apply(Collections.singletonList(op));
            fail("ADD_ATTRIBUTE with no className must be rejected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @SuppressWarnings("deprecation")
    public void testApplyRegistersUndoableCommand() {
        org.argouml.kernel.Project project =
            org.argouml.kernel.ProjectManager.getManager().getCurrentProject();
        assertNotNull("test setup must provide a current project", project);
        final org.argouml.kernel.UndoManager undo = project.getUndoManager();
        assertNotNull("project must have an UndoManager", undo);
        final List<PropertyChangeEvent> undoAddedEvents =
            new java.util.ArrayList<PropertyChangeEvent>();
        PropertyChangeListener listener = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("undoAdded".equals(evt.getPropertyName())) {
                    undoAddedEvents.add(evt);
                }
            }
        };
        undo.addPropertyChangeListener(listener);
        try {
            PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
            op.setString("name", "UndoProbe1");
            op.setInt("x", 50);
            op.setInt("y", 50);
            exec.apply(Collections.singletonList(op));
        } finally {
            undo.removePropertyChangeListener(listener);
        }
        assertTrue("apply() must push an undoAdded event",
                undoAddedEvents.size() >= 1);
        undo.undo();
        assertNull("class should be gone after undo",
                exec.findClassByName("UndoProbe1"));
        undo.redo();
        assertNotNull("class should be back after redo",
                exec.findClassByName("UndoProbe1"));
    }

    public void testApplyIsUndoableBatch() {
        PlannedOp a = makeAddClass("BatchA", 50, 50);
        PlannedOp b = makeAddClass("BatchB", 200, 100);
        PlannedOp c = makeAddClass("BatchC", 350, 150);
        exec.apply(Arrays.asList(a, b, c));
        assertNotNull(exec.findClassByName("BatchA"));
        assertNotNull(exec.findClassByName("BatchB"));
        assertNotNull(exec.findClassByName("BatchC"));
        org.argouml.kernel.ProjectManager.getManager()
            .getCurrentProject().getUndoManager().undo();
        assertNull("BatchA gone after batch undo",
                exec.findClassByName("BatchA"));
        assertNull("BatchB gone after batch undo",
                exec.findClassByName("BatchB"));
        assertNull("BatchC gone after batch undo",
                exec.findClassByName("BatchC"));
    }

    public void testFindClassByNameReturnsMatch() {
        Object c = Model.getCoreFactory().buildClass("Widget", ns);
        ((MutableGraphModel) diagram.getGraphModel()).addNode(c);

        Object found = exec.findClassByName("Widget");
        assertNotNull("findClassByName should return the matching node",
                found);
        assertSame("must be the same model element", c, found);
    }

    public void testFindClassByNameReturnsNullWhenAbsent() {
        // diagram has no nodes
        assertNull("findClassByName should return null when nothing matches",
                exec.findClassByName("Ghost"));
    }

    public void testFindClassByNameIgnoresInterfaces() {
        // Add an interface. findClassByName must filter by isAClass
        // and therefore must NOT return this interface even though
        // the name matches.
        Object iface = Model.getCoreFactory().buildInterface("Callable", ns);
        ((MutableGraphModel) diagram.getGraphModel()).addNode(iface);

        assertNull("findClassByName must not return an interface",
                exec.findClassByName("Callable"));
    }

    // -----------------------------------------------------------------
    // Task 8 - remaining op kinds (attribute, operation, association,
    // generalization, dependency, rename, delete, list)
    // -----------------------------------------------------------------

    public void testAddAttribute() {
        // First add the class.
        PlannedOp addClass = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addClass.setString("name", "Order");
        addClass.setInt("x", 100);
        addClass.setInt("y", 100);
        exec.apply(Collections.singletonList(addClass));

        // Then add the attribute.
        PlannedOp addAttr = new PlannedOp(PlannedOp.Type.ADD_ATTRIBUTE);
        addAttr.setString("className", "Order");
        addAttr.setString("name", "id");
        addAttr.setString("type", "int");
        exec.apply(Collections.singletonList(addAttr));

        // Verify the class has exactly one attribute named "id".
        Object orderClass = exec.findClassByName("Order");
        assertNotNull("Order should exist", orderClass);
        java.util.Collection attrs =
                Model.getFacade().getAttributes(orderClass);
        assertEquals("Order should have 1 attribute", 1, attrs.size());
        Object attr = attrs.iterator().next();
        assertEquals("id", Model.getFacade().getName(attr));
    }

    public void testAddOperation() {
        PlannedOp addClass = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addClass.setString("name", "Order");
        addClass.setInt("x", 100);
        addClass.setInt("y", 100);
        exec.apply(Collections.singletonList(addClass));

        PlannedOp addOp = new PlannedOp(PlannedOp.Type.ADD_OPERATION);
        addOp.setString("className", "Order");
        addOp.setString("name", "save");
        addOp.setString("returnType", "void");
        exec.apply(Collections.singletonList(addOp));

        Object orderClass = exec.findClassByName("Order");
        assertNotNull("Order should exist", orderClass);
        java.util.List ops = Model.getFacade().getOperations(orderClass);
        assertEquals("Order should have 1 operation", 1, ops.size());
        Object op = ops.get(0);
        assertEquals("save", Model.getFacade().getName(op));
    }

    public void testAddAssociation() {
        PlannedOp addCustomer = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addCustomer.setString("name", "Customer");
        addCustomer.setInt("x", 100);
        addCustomer.setInt("y", 100);
        exec.apply(Collections.singletonList(addCustomer));

        PlannedOp addOrder = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addOrder.setString("name", "Order");
        addOrder.setInt("x", 300);
        addOrder.setInt("y", 100);
        exec.apply(Collections.singletonList(addOrder));

        PlannedOp addAssoc = new PlannedOp(PlannedOp.Type.ADD_ASSOCIATION);
        addAssoc.setString("classA", "Customer");
        addAssoc.setString("classB", "Order");
        addAssoc.setString("multA", "1");
        addAssoc.setString("multB", "0..*");
        exec.apply(Collections.singletonList(addAssoc));

        // Exactly one association edge on the diagram.
        int edgeCount = 0;
        Object assocEdge = null;
        for (Iterator it = diagram.getGraphModel().getEdges().iterator();
                it.hasNext();) {
            Object edge = it.next();
            if (Model.getFacade().isAAssociation(edge)) {
                edgeCount++;
                assocEdge = edge;
            }
        }
        assertEquals("Should have 1 association edge", 1, edgeCount);

        // And the multiplicities should be applied to the two ends.
        java.util.Collection ends = Model.getFacade().getConnections(assocEdge);
        assertEquals("association should have 2 ends", 2, ends.size());
        Object[] endArr = ends.toArray();
        // First end corresponds to classA (Customer), second to classB (Order)
        // as built by buildAssociation(c1, c2); MDR returns connections
        // in MOF declaration order matching the build argument order.
        Object endA = endArr[0];
        Object endB = endArr[1];
        // "1"  -> lower 1, upper 1.
        assertEquals("end A lower should be 1",
                1, Model.getFacade().getLower(endA));
        assertEquals("end A upper should be 1",
                1, Model.getFacade().getUpper(endA));
        // "0..*" -> lower 0, upper -1 (unbounded).
        assertEquals("end B lower should be 0",
                0, Model.getFacade().getLower(endB));
        assertEquals("end B upper should be -1 (unbounded)",
                -1, Model.getFacade().getUpper(endB));
    }

    public void testAddGeneralization() {
        // Parent first, then child. Order does not really matter for the
        // buildGeneralization call, but we mimic the AI planner pattern.
        PlannedOp addParent = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addParent.setString("name", "Animal");
        addParent.setInt("x", 50);
        addParent.setInt("y", 50);
        exec.apply(Collections.singletonList(addParent));

        PlannedOp addChild = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addChild.setString("name", "Dog");
        addChild.setInt("x", 250);
        addChild.setInt("y", 50);
        exec.apply(Collections.singletonList(addChild));

        PlannedOp addGen = new PlannedOp(PlannedOp.Type.ADD_GENERALIZATION);
        addGen.setString("subclass", "Dog");
        addGen.setString("superclass", "Animal");
        exec.apply(Collections.singletonList(addGen));

        // There must be a generalization edge in the diagram's graph model.
        boolean foundGen = false;
        for (Iterator it = diagram.getGraphModel().getEdges().iterator();
                it.hasNext();) {
            Object edge = it.next();
            if (Model.getFacade().isAGeneralization(edge)) {
                foundGen = true;
                break;
            }
        }
        assertTrue("diagram should contain a generalization edge", foundGen);
    }

    public void testAddDependency() {
        PlannedOp addClient = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addClient.setString("name", "Report");
        addClient.setInt("x", 50);
        addClient.setInt("y", 50);
        exec.apply(Collections.singletonList(addClient));

        PlannedOp addSupplier = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addSupplier.setString("name", "Database");
        addSupplier.setInt("x", 250);
        addSupplier.setInt("y", 50);
        exec.apply(Collections.singletonList(addSupplier));

        PlannedOp addDep = new PlannedOp(PlannedOp.Type.ADD_DEPENDENCY);
        addDep.setString("client", "Report");
        addDep.setString("supplier", "Database");
        exec.apply(Collections.singletonList(addDep));

        boolean foundDep = false;
        for (Iterator it = diagram.getGraphModel().getEdges().iterator();
                it.hasNext();) {
            Object edge = it.next();
            if (Model.getFacade().isADependency(edge)) {
                foundDep = true;
                break;
            }
        }
        assertTrue("diagram should contain a dependency edge", foundDep);
    }

    public void testRenameClass() {
        PlannedOp addClass = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addClass.setString("name", "Order");
        addClass.setInt("x", 100);
        addClass.setInt("y", 100);
        exec.apply(Collections.singletonList(addClass));
        assertNotNull("Order should exist before rename",
                exec.findClassByName("Order"));

        PlannedOp rename = new PlannedOp(PlannedOp.Type.RENAME_CLASS);
        rename.setString("oldName", "Order");
        rename.setString("newName", "Invoice");
        exec.apply(Collections.singletonList(rename));

        assertNull("old name Order must no longer be findable",
                exec.findClassByName("Order"));
        assertNotNull("new name Invoice should now be findable",
                exec.findClassByName("Invoice"));
    }

    public void testDeleteClass() {
        PlannedOp addClass = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addClass.setString("name", "Order");
        addClass.setInt("x", 100);
        addClass.setInt("y", 100);
        exec.apply(Collections.singletonList(addClass));
        assertNotNull("Order should exist before delete",
                exec.findClassByName("Order"));

        PlannedOp del = new PlannedOp(PlannedOp.Type.DELETE_CLASS);
        del.setString("name", "Order");
        exec.apply(Collections.singletonList(del));

        // The class must be gone from the graph model.
        boolean found = false;
        for (Iterator it = diagram.getGraphModel().getNodes().iterator();
                it.hasNext();) {
            Object node = it.next();
            if ("Order".equals(Model.getFacade().getName(node))) {
                found = true;
                break;
            }
        }
        assertFalse("Order should be deleted from the graph model", found);
        assertNull("findClassByName should return null after delete",
                exec.findClassByName("Order"));
    }

    public void testListClassesDoesNotThrow() {
        PlannedOp addA = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addA.setString("name", "Alpha");
        addA.setInt("x", 10);
        addA.setInt("y", 20);
        PlannedOp addB = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addB.setString("name", "Beta");
        addB.setInt("x", 110);
        addB.setInt("y", 120);
        exec.apply(Arrays.asList(addA, addB));

        // list_classes has no required fields; calling apply with just
        // the op kind must complete without exception.
        PlannedOp list = new PlannedOp(PlannedOp.Type.LIST_CLASSES);
        try {
            exec.apply(Collections.singletonList(list));
        } catch (RuntimeException e) {
            fail("LIST_CLASSES must not throw: " + e.getMessage());
        }
    }

    public void testAddAttributeRejectsMissingClassName() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_ATTRIBUTE);
        op.setString("name", "id");
        // className deliberately not set
        try {
            exec.apply(Collections.singletonList(op));
            fail("ADD_ATTRIBUTE with no className must throw IAE");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    public void testAddOperationRejectsMissingClassName() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_OPERATION);
        op.setString("name", "save");
        // className deliberately not set
        try {
            exec.apply(Collections.singletonList(op));
            fail("ADD_OPERATION with no className must throw IAE");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // -----------------------------------------------------------------
    // C2/C3/C4 regressions: labelA/labelB, stereotype, isAbstract
    // -----------------------------------------------------------------

    public void testAddAssociationWithLabels() {
        // Two endpoints + association + role names.
        PlannedOp addCustomer = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addCustomer.setString("name", "Customer");
        addCustomer.setInt("x", 100);
        addCustomer.setInt("y", 100);
        exec.apply(Collections.singletonList(addCustomer));

        PlannedOp addOrder = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        addOrder.setString("name", "Order");
        addOrder.setInt("x", 300);
        addOrder.setInt("y", 100);
        exec.apply(Collections.singletonList(addOrder));

        PlannedOp addAssoc = new PlannedOp(PlannedOp.Type.ADD_ASSOCIATION);
        addAssoc.setString("classA", "Customer");
        addAssoc.setString("classB", "Order");
        addAssoc.setString("labelA", "places");
        addAssoc.setString("labelB", "placedBy");
        exec.apply(Collections.singletonList(addAssoc));

        Object assocEdge = null;
        for (Iterator it = diagram.getGraphModel().getEdges().iterator();
                it.hasNext();) {
            Object edge = it.next();
            if (Model.getFacade().isAAssociation(edge)) {
                assocEdge = edge;
                break;
            }
        }
        assertNotNull("association edge should exist", assocEdge);

        java.util.Collection ends = Model.getFacade().getConnections(assocEdge);
        Object[] endArr = ends.toArray();
        assertEquals("end A name should be 'places'", "places",
                Model.getFacade().getName(endArr[0]));
        assertEquals("end B name should be 'placedBy'", "placedBy",
                Model.getFacade().getName(endArr[1]));
    }

    public void testAddClassWithStereotype() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", "Widget");
        op.setInt("x", 0);
        op.setInt("y", 0);
        op.setString("stereotype", "ui");
        exec.apply(Collections.singletonList(op));

        Object cls = exec.findClassByName("Widget");
        assertNotNull("Widget should exist", cls);
        java.util.Collection stereotypes = Model.getFacade().getStereotypes(cls);
        assertEquals("Widget should have 1 stereotype", 1, stereotypes.size());
        Object st = stereotypes.iterator().next();
        assertEquals("stereotype name should be 'ui'", "ui",
                Model.getFacade().getName(st));
    }

    public void testAddInterfaceWithStereotype() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_INTERFACE);
        op.setString("name", "Runnable");
        op.setInt("x", 0);
        op.setInt("y", 0);
        op.setString("stereotype", "facade");
        exec.apply(Collections.singletonList(op));

        Object iface = null;
        for (Iterator it = diagram.getGraphModel().getNodes().iterator();
                it.hasNext();) {
            Object node = it.next();
            if (Model.getFacade().isAInterface(node)
                    && "Runnable".equals(Model.getFacade().getName(node))) {
                iface = node;
                break;
            }
        }
        assertNotNull("Runnable interface should exist", iface);
        java.util.Collection stereotypes = Model.getFacade().getStereotypes(iface);
        assertEquals("Runnable should have 1 stereotype", 1, stereotypes.size());
        assertEquals("stereotype name should be 'facade'", "facade",
                Model.getFacade().getName(stereotypes.iterator().next()));
    }

    public void testAddClassAbstract() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", "Shape");
        op.setInt("x", 0);
        op.setInt("y", 0);
        op.setBoolean("isAbstract", true);
        exec.apply(Collections.singletonList(op));

        Object cls = exec.findClassByName("Shape");
        assertNotNull("Shape should exist", cls);
        assertTrue("Shape should be abstract",
                Model.getFacade().isAbstract(cls));
    }

    public void testAddClassNotAbstractByDefault() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", "Concrete");
        op.setInt("x", 0);
        op.setInt("y", 0);
        // isAbstract deliberately not set
        exec.apply(Collections.singletonList(op));

        Object cls = exec.findClassByName("Concrete");
        assertNotNull("Concrete should exist", cls);
        assertFalse("Concrete should NOT be abstract by default",
                Model.getFacade().isAbstract(cls));
    }

    // -----------------------------------------------------------------
    // Per-row callback contract
    // -----------------------------------------------------------------

    public void testApplyPerRowCallbacks() {
        // 3 ops: a valid ADD_CLASS, a failing ADD_GENERALIZATION
        // that references a non-existent superclass, and another
        // valid ADD_CLASS. The middle one must throw without
        // affecting the success callbacks for the other two.
        addClass("GenSub", 50, 50);
        addClass("GenSibling", 200, 50);
        PlannedOp validA = makeAddClass("ValidA", 350, 50);
        PlannedOp failingB = new PlannedOp(
                PlannedOp.Type.ADD_GENERALIZATION);
        failingB.setString("subclass", "GenSub");
        failingB.setString("superclass", "NonExistent");
        PlannedOp validC = makeAddClass("ValidC", 500, 50);

        final int[] success = { 0 };
        final int[] fail = { 0 };
        final List<Integer> failedRows = new ArrayList<Integer>();
        final List<Integer> succeededRows = new ArrayList<Integer>();
        new OpExecutor(diagram).apply(
                Arrays.asList(validA, failingB, validC),
                new IntConsumer() {
                    public void accept(int row) {
                        success[0]++;
                        succeededRows.add(Integer.valueOf(row));
                    }
                },
                new BiConsumer<Integer, Throwable>() {
                    public void accept(Integer row, Throwable ex) {
                        fail[0]++;
                        failedRows.add(row);
                    }
                });

        assertEquals("expected 2 successes", 2, success[0]);
        assertEquals("expected 1 failure", 1, fail[0]);
        assertEquals("success rows must be indices 0 and 2",
                Arrays.asList(0, 2), succeededRows);
        assertEquals("failure row must be index 1",
                Arrays.asList(1), failedRows);

        // Model state: the two valid ADD_CLASSes must have landed,
        // and the failing ADD_GENERALIZATION must have left no edge.
        assertNotNull("ValidA should be on diagram",
                exec.findClassByName("ValidA"));
        assertNotNull("ValidC should be on diagram",
                exec.findClassByName("ValidC"));
        boolean foundGenEdge = false;
        for (Iterator it = diagram.getGraphModel().getEdges().iterator();
                it.hasNext();) {
            Object edge = it.next();
            if (Model.getFacade().isAGeneralization(edge)) {
                foundGenEdge = true;
                break;
            }
        }
        assertFalse("no generalization edge should have been added",
                foundGenEdge);
    }

    public void testApplyPerRowCallbacksAllSucceed() {
        // Sanity: per-row success callbacks fire for every op when
        // none throws, and the failure callback is never invoked.
        PlannedOp a = makeAddClass("AllOKA", 50, 50);
        PlannedOp b = makeAddClass("AllOKB", 200, 50);
        final int[] success = { 0 };
        final int[] fail = { 0 };
        final List<Integer> succeededRows = new ArrayList<Integer>();
        new OpExecutor(diagram).apply(
                Arrays.asList(a, b),
                new IntConsumer() {
                    public void accept(int row) {
                        success[0]++;
                        succeededRows.add(Integer.valueOf(row));
                    }
                },
                new BiConsumer<Integer, Throwable>() {
                    public void accept(Integer row, Throwable ex) {
                        fail[0]++;
                    }
                });
        assertEquals(2, success[0]);
        assertEquals(0, fail[0]);
        assertEquals(Arrays.asList(0, 1), succeededRows);
    }

    public void testApplyRejectsNullSuccessCallback() {
        try {
            new OpExecutor(diagram).apply(Collections.<PlannedOp>emptyList(),
                    null,
                    new BiConsumer<Integer, Throwable>() {
                        public void accept(Integer row, Throwable ex) { }
                    });
            fail("null onSuccess callback must be rejected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    public void testApplyRejectsNullFailureCallback() {
        try {
            new OpExecutor(diagram).apply(Collections.<PlannedOp>emptyList(),
                    new IntConsumer() { public void accept(int row) { } },
                    null);
            fail("null onFailure callback must be rejected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static PlannedOp makeAddClass(String name, int x, int y) {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", name);
        op.setInt("x", x); op.setInt("y", y);
        return op;
    }

    private void addClass(String name, int x, int y) {
        exec.apply(Collections.singletonList(
                makeAddClass(name, x, y)));
    }

    private Object findEdgeBetween(String a, String b) {
        for (Object e : diagram.getGraphModel().getEdges()) {
            java.util.Collection ends = Model.getFacade().getConnections(e);
            Object[] arr = ends.toArray();
            if (arr.length == 2) {
                String n0 = Model.getFacade().getName(
                        Model.getFacade().getType(arr[0]));
                String n1 = Model.getFacade().getName(
                        Model.getFacade().getType(arr[1]));
                if ((a.equals(n0) && b.equals(n1))
                        || (a.equals(n1) && b.equals(n0))) {
                    return e;
                }
            }
        }
        return null;
    }

    /**
     * Walk the diagram's nodes and return the one whose
     * {@link org.argouml.model.Facade#getName(Object)} equals the
     * supplied value, or {@code null} if none.
     */
    private Object findNodeByName(String name) {
        Facade facade = Model.getFacade();
        for (Object node : diagram.getGraphModel().getNodes()) {
            if (name.equals(facade.getName(node))) {
                return node;
            }
        }
        return null;
    }
}