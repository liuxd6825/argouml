/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers.attribute;

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
 * Tests for {@link AddAttributeHandler}.
 */
public class TestAddAttributeHandler extends TestCase {

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

    private Map<String, String> ppFor(String className) {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", className);
        return pp;
    }

    public void testNullServiceRejected() {
        try {
            new AddAttributeHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testReturns201OnHappyPath() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        ResponseEnvelope env = new AddAttributeHandler(svc).handle(
                ppFor("Order"), new HashMap<String, String>(),
                "{\"name\":\"id\",\"type\":\"long\","
                + "\"visibility\":\"private\"}");
        assertEquals(201, env.status);
        assertTrue("body must wrap ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must mention id: " + env.body,
                env.body.contains("\"name\":\"id\""));
        assertTrue("body must echo type long: " + env.body,
                env.body.contains("\"type\":\"long\""));
    }

    public void testReturns201WithoutType() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        ResponseEnvelope env = new AddAttributeHandler(svc).handle(
                ppFor("Order"), new HashMap<String, String>(),
                "{\"name\":\"flag\"}");
        assertEquals(201, env.status);
        assertTrue("body must mention flag: " + env.body,
                env.body.contains("\"name\":\"flag\""));
    }

    public void testReturns201WithoutVisibility() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        ResponseEnvelope env = new AddAttributeHandler(svc).handle(
                ppFor("Order"), new HashMap<String, String>(),
                "{\"name\":\"id\",\"type\":\"long\"}");
        assertEquals(201, env.status);
    }

    public void testEmptyNameThrowsInvalidArgument() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        try {
            new AddAttributeHandler(svc).handle(
                    ppFor("Order"), new HashMap<String, String>(),
                    "{\"name\":\"\",\"type\":\"long\"}");
            fail("expected InvalidArgumentException for empty name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testMissingNameThrowsInvalidArgument() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        try {
            new AddAttributeHandler(svc).handle(
                    ppFor("Order"), new HashMap<String, String>(), "{}");
            fail("expected InvalidArgumentException for missing name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testMissingDiagramThrowsNotFound() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        Map<String, String> pathParams = new HashMap<String, String>();
        pathParams.put("d", "NoSuch");
        pathParams.put("c", "Order");
        try {
            new AddAttributeHandler(svc).handle(pathParams,
                    new HashMap<String, String>(),
                    "{\"name\":\"id\",\"type\":\"long\"}");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testMissingClassThrowsClassNotFound() {
        Map<String, String> pathParams = new HashMap<String, String>();
        pathParams.put("d", DIAGRAM);
        pathParams.put("c", "NoSuch");
        try {
            new AddAttributeHandler(svc).handle(pathParams,
                    new HashMap<String, String>(),
                    "{\"name\":\"id\",\"type\":\"long\"}");
            fail("expected NotFoundException for missing class");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testAttributeIsAttachedToClass() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        ClassDiagramService.ClassView before =
                svc.getClass(DIAGRAM, "Order");
        int beforeCount = before.attributes.size();
        new AddAttributeHandler(svc).handle(
                ppFor("Order"), new HashMap<String, String>(),
                "{\"name\":\"id\",\"type\":\"long\","
                + "\"visibility\":\"private\"}");
        ClassDiagramService.ClassView after =
                svc.getClass(DIAGRAM, "Order");
        assertEquals("attribute should be appended",
                beforeCount + 1, after.attributes.size());
        boolean found = false;
        for (String enc : after.attributes) {
            if (enc.startsWith("id:")) {
                found = true;
                break;
            }
        }
        assertTrue("added attribute should appear in ClassView: "
                + after.attributes, found);
    }
}