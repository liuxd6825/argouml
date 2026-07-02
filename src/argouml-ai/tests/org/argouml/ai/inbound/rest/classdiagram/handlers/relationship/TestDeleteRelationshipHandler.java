/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers.relationship;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.application.common.InvalidArgumentException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;

/**
 * Tests for {@link DeleteRelationshipHandler}.
 */
public class TestDeleteRelationshipHandler extends TestCase {

    private static final String DIAGRAM = "Test";

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
    private ClassDiagramService svc;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getModel();
        ArgoDiagram d = new UMLClassDiagram(DIAGRAM, ns);
        project.addDiagram(d);
        svc = new ClassDiagramService();
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

    private Map<String, String> ppFor(String id) {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("id", id);
        return pp;
    }

    private Map<String, String> qpFor(String type) {
        Map<String, String> qp = new HashMap<String, String>();
        qp.put("type", type);
        return qp;
    }

    public void testNullServiceRejected() {
        try {
            new DeleteRelationshipHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testReturns204OnHappyPathAssociation() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        svc.addAssociation(DIAGRAM, "Customer", "Order",
                "1", "0..*", "places", "placedBy");
        ResponseEnvelope env = new DeleteRelationshipHandler(svc).handle(
                ppFor("Customer|Order"),
                qpFor("association"), "");
        assertEquals(204, env.status);
        assertEquals("body should be empty for 204", "", env.body);
    }

    public void testReturns204OnHappyPathGeneralization() {
        svc.createClass(DIAGRAM, "Document", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        svc.addGeneralization(DIAGRAM, "Order", "Document");
        ResponseEnvelope env = new DeleteRelationshipHandler(svc).handle(
                ppFor("Order|Document"),
                qpFor("generalization"), "");
        assertEquals(204, env.status);
    }

    public void testReturns204OnHappyPathDependency() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Inventory", 30, 40, null, false);
        svc.addDependency(DIAGRAM, "Order", "Inventory");
        ResponseEnvelope env = new DeleteRelationshipHandler(svc).handle(
                ppFor("Order|Inventory"),
                qpFor("dependency"), "");
        assertEquals(204, env.status);
    }

    public void testAssociationIsGoneAfter() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        svc.addAssociation(DIAGRAM, "Customer", "Order",
                "1", "0..*", "places", "placedBy");
        ArgoDiagram d = org.argouml.ai.domain.common.DiagramLocator.byName(DIAGRAM);
        int before = d.getGraphModel().getEdges().size();
        new DeleteRelationshipHandler(svc).handle(
                ppFor("Customer|Order"),
                qpFor("association"), "");
        int after = d.getGraphModel().getEdges().size();
        assertEquals("edge should be removed from graph model",
                before - 1, after);
    }

    public void testUnknownTypeThrowsInvalidArgument() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        svc.addAssociation(DIAGRAM, "Customer", "Order",
                "1", "0..*", "places", "placedBy");
        try {
            new DeleteRelationshipHandler(svc).handle(
                    ppFor("Customer|Order"),
                    qpFor("bogus"), "");
            fail("expected InvalidArgumentException for unknown type");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_RELATIONSHIP_TYPE", expected.code());
        }
    }

    public void testMissingTypeThrowsInvalidArgument() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        svc.addAssociation(DIAGRAM, "Customer", "Order",
                "1", "0..*", "places", "placedBy");
        try {
            new DeleteRelationshipHandler(svc).handle(
                    ppFor("Customer|Order"),
                    new HashMap<String, String>(), "");
            fail("expected InvalidArgumentException for missing type");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_RELATIONSHIP_TYPE", expected.code());
        }
    }

    public void testMissingIdThrowsInvalidArgument() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        try {
            new DeleteRelationshipHandler(svc).handle(pp,
                    qpFor("association"), "");
            fail("expected InvalidArgumentException for missing id");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testMalformedIdThrowsInvalidArgument() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        svc.addAssociation(DIAGRAM, "Customer", "Order",
                "1", "0..*", "places", "placedBy");
        try {
            new DeleteRelationshipHandler(svc).handle(
                    ppFor("CustomerOrder"),
                    qpFor("association"), "");
            fail("expected InvalidArgumentException for malformed id");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testUnknownRelationshipThrowsNotFound() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        try {
            new DeleteRelationshipHandler(svc).handle(
                    ppFor("Customer|Order"),
                    qpFor("association"), "");
            fail("expected NotFoundException for missing relationship");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testMissingDiagramThrowsDiagramNotFound() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "NoSuch");
        pp.put("id", "Customer|Order");
        try {
            new DeleteRelationshipHandler(svc).handle(pp,
                    qpFor("association"), "");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }
}