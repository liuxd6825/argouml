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
 * Tests for {@link AddDependencyHandler}.
 */
public class TestAddDependencyHandler extends TestCase {

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

    private Map<String, String> ppFor() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        return pp;
    }

    public void testNullServiceRejected() {
        try {
            new AddDependencyHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testReturns201OnHappyPath() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Inventory", 30, 40, null, false);
        ResponseEnvelope env = new AddDependencyHandler(svc).handle(
                ppFor(), new HashMap<String, String>(),
                "{\"client\":\"Order\","
                + "\"supplier\":\"Inventory\"}");
        assertEquals(201, env.status);
        assertTrue("body must wrap ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must mention id Order|Inventory: " + env.body,
                env.body.contains("\"id\":\"Order|Inventory\""));
        assertTrue("body must mention client Order: " + env.body,
                env.body.contains("\"client\":\"Order\""));
        assertTrue("body must mention supplier Inventory: " + env.body,
                env.body.contains("\"supplier\":\"Inventory\""));
    }

    public void testMissingClientThrowsInvalidArgument() {
        svc.createClass(DIAGRAM, "Inventory", 10, 20, null, false);
        try {
            new AddDependencyHandler(svc).handle(
                    ppFor(), new HashMap<String, String>(),
                    "{\"supplier\":\"Inventory\"}");
            fail("expected InvalidArgumentException for missing client");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testMissingSupplierThrowsInvalidArgument() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        try {
            new AddDependencyHandler(svc).handle(
                    ppFor(), new HashMap<String, String>(),
                    "{\"client\":\"Order\"}");
            fail("expected InvalidArgumentException for missing supplier");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testUnknownClientThrowsClassNotFound() {
        svc.createClass(DIAGRAM, "Inventory", 10, 20, null, false);
        try {
            new AddDependencyHandler(svc).handle(
                    ppFor(), new HashMap<String, String>(),
                    "{\"client\":\"NoSuch\","
                    + "\"supplier\":\"Inventory\"}");
            fail("expected NotFoundException for missing client");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testMissingDiagramThrowsDiagramNotFound() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Inventory", 30, 40, null, false);
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "NoSuch");
        try {
            new AddDependencyHandler(svc).handle(pp,
                    new HashMap<String, String>(),
                    "{\"client\":\"Order\","
                    + "\"supplier\":\"Inventory\"}");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testDependencyIsAttachedToDiagram() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Inventory", 30, 40, null, false);
        ArgoDiagram d = org.argouml.ai.domain.common.DiagramLocator.byName(DIAGRAM);
        int before = d.getGraphModel().getEdges().size();
        new AddDependencyHandler(svc).handle(
                ppFor(), new HashMap<String, String>(),
                "{\"client\":\"Order\","
                + "\"supplier\":\"Inventory\"}");
        int after = d.getGraphModel().getEdges().size();
        assertEquals("edge should be appended to graph model",
                before + 1, after);
    }
}