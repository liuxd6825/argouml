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
 * Tests for {@link AddAssociationHandler}.
 */
public class TestAddAssociationHandler extends TestCase {

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
            new AddAssociationHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testReturns201OnHappyPath() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        ResponseEnvelope env = new AddAssociationHandler(svc).handle(
                ppFor(), new HashMap<String, String>(),
                "{\"classA\":\"Customer\",\"classB\":\"Order\","
                + "\"multA\":\"1\",\"multB\":\"0..*\","
                + "\"labelA\":\"places\",\"labelB\":\"placedBy\"}");
        assertEquals(201, env.status);
        assertTrue("body must wrap ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must mention id: " + env.body,
                env.body.contains("\"id\":\"Customer|Order\""));
        assertTrue("body must mention a: " + env.body,
                env.body.contains("\"a\":\"Customer\""));
        assertTrue("body must mention b: " + env.body,
                env.body.contains("\"b\":\"Order\""));
    }

    public void testReturns201WithoutMultiplicityOrLabels() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        ResponseEnvelope env = new AddAssociationHandler(svc).handle(
                ppFor(), new HashMap<String, String>(),
                "{\"classA\":\"Customer\",\"classB\":\"Order\"}");
        assertEquals(201, env.status);
        assertTrue("body must mention Customer: " + env.body,
                env.body.contains("Customer"));
    }

    public void testMissingClassAThrowsInvalidArgument() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        try {
            new AddAssociationHandler(svc).handle(
                    ppFor(), new HashMap<String, String>(),
                    "{\"classB\":\"Order\"}");
            fail("expected InvalidArgumentException for missing classA");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testMissingClassBThrowsInvalidArgument() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        try {
            new AddAssociationHandler(svc).handle(
                    ppFor(), new HashMap<String, String>(),
                    "{\"classA\":\"Customer\"}");
            fail("expected InvalidArgumentException for missing classB");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testUnknownClassThrowsClassNotFound() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        try {
            new AddAssociationHandler(svc).handle(
                    ppFor(), new HashMap<String, String>(),
                    "{\"classA\":\"Customer\",\"classB\":\"NoSuch\"}");
            fail("expected NotFoundException for missing classB");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testMissingDiagramThrowsDiagramNotFound() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "NoSuch");
        try {
            new AddAssociationHandler(svc).handle(pp,
                    new HashMap<String, String>(),
                    "{\"classA\":\"Customer\",\"classB\":\"Order\"}");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testAssociationIsAttachedToDiagram() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        ArgoDiagram d = org.argouml.ai.domain.common.DiagramLocator.byName(DIAGRAM);
        int before = d.getGraphModel().getEdges().size();
        new AddAssociationHandler(svc).handle(
                ppFor(), new HashMap<String, String>(),
                "{\"classA\":\"Customer\",\"classB\":\"Order\"}");
        int after = d.getGraphModel().getEdges().size();
        assertEquals("edge should be appended to graph model",
                before + 1, after);
    }
}