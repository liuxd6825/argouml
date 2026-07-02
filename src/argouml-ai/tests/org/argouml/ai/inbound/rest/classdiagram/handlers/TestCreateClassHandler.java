/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.application.common.DuplicateException;
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
 * Tests for {@link CreateClassHandler}. Hits a real
 * {@link ClassDiagramService} so the handler-level wiring (path
 * param lookup, body parsing, JSON envelope) is exercised end to
 * end against the same model surface used in production.
 */
public class TestCreateClassHandler extends TestCase {

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

    public void testReturns201OnHappyPath() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        ResponseEnvelope env = new CreateClassHandler(svc).handle(pp,
                new HashMap<String, String>(),
                "{\"name\":\"Order\",\"x\":120,\"y\":200}");
        assertEquals(201, env.status);
        assertTrue("body must contain ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must contain name Order: " + env.body,
                env.body.contains("Order"));
    }

    public void testNullServiceRejected() {
        try {
            new CreateClassHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testEmptyNameThrowsInvalidArgument() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        try {
            new CreateClassHandler(svc).handle(pp,
                    new HashMap<String, String>(),
                    "{\"name\":\"\"}");
            fail("expected InvalidArgumentException for empty name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testMissingNameThrowsInvalidArgument() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        try {
            new CreateClassHandler(svc).handle(pp,
                    new HashMap<String, String>(), "{}");
            fail("expected InvalidArgumentException for missing name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testDuplicateThrowsConflict() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        CreateClassHandler h = new CreateClassHandler(svc);
        h.handle(pp, new HashMap<String, String>(),
                "{\"name\":\"Order\"}");
        try {
            h.handle(pp, new HashMap<String, String>(),
                    "{\"name\":\"Order\"}");
            fail("expected DuplicateException for second create");
        } catch (DuplicateException expected) {
            assertEquals("DUPLICATE_CLASS", expected.code());
        }
    }

    public void testMissingDiagramThrowsNotFound() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "NoSuch");
        try {
            new CreateClassHandler(svc).handle(pp,
                    new HashMap<String, String>(),
                    "{\"name\":\"Order\"}");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testStereotypeAndAbstractRoundTrip() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        ResponseEnvelope env = new CreateClassHandler(svc).handle(pp,
                new HashMap<String, String>(),
                "{\"name\":\"Abs\",\"stereotype\":\"entity\","
                + "\"isAbstract\":true}");
        assertEquals(201, env.status);
        ClassDiagramService.ClassView v = svc.getClass(DIAGRAM, "Abs");
        assertTrue("class should be abstract", v.isAbstract);
        assertTrue("stereotype should be present: " + v.stereotypeNames,
                v.stereotypeNames.contains("entity"));
    }

    public void testMalformedBodyThrows() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        try {
            new CreateClassHandler(svc).handle(pp,
                    new HashMap<String, String>(), "not-json");
            fail("expected IllegalArgumentException for malformed body");
        } catch (IllegalArgumentException expected) {
        }
    }
}