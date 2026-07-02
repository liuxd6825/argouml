/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.common.handlers.common;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;

public class TestCreateDiagramHandler extends TestCase {

    private Project project;
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
    protected void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getModel();
        // Add one starting diagram ("Base") so the test exercises the
        // duplicate-pre-check path on subsequent creates.
        project.addDiagram(new UMLClassDiagram("Base", ns));
        svc = new ClassDiagramService();
    }

    @Override
    protected void tearDown() throws Exception {
        if (project != null) {
            try { ProjectManager.getManager().removeProject(project); }
            catch (RuntimeException ignored) {}
        }
        super.tearDown();
    }

    public void testNullServiceRejected() {
        try {
            new CreateDiagramHandler(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testCreateHappyPath() {
        ResponseEnvelope r = new CreateDiagramHandler(svc).handle(
                new HashMap<String, String>(), new HashMap<String, String>(),
                "{\"name\":\"NewDiagram\"}");
        assertEquals("expected 201, got " + r.status + " body=" + r.body,
                201, r.status);
        assertTrue("body must include name: " + r.body,
                r.body.contains("\"name\":\"NewDiagram\""));
        assertTrue("body must include kind: " + r.body,
                r.body.contains("\"kind\":\"class\""));
    }

    public void testCreateDuplicateThrowsConflict() {
        // The service throws DuplicateException for the second
        // create; the handler doesn't catch it — the Dispatcher is
        // responsible for mapping the exception to a 409 response.
        // This unit test exercises the service contract directly.
        CreateDiagramHandler h = new CreateDiagramHandler(svc);
        Map<String, String> pp = new HashMap<String, String>();
        try {
            h.handle(pp, new HashMap<String, String>(),
                    "{\"name\":\"Base\"}");
            fail("expected DuplicateException for second create");
        } catch (org.argouml.ai.application.common.DuplicateException expected) {
            assertEquals("DUPLICATE_DIAGRAM", expected.code());
            assertEquals(409, expected.httpStatus());
        }
    }

    public void testCreateEmptyNameReturns400() {
        ResponseEnvelope r = new CreateDiagramHandler(svc).handle(
                new HashMap<String, String>(), new HashMap<String, String>(),
                "{\"name\":\"\"}");
        assertEquals(400, r.status);
        assertTrue("body must include INVALID_NAME: " + r.body,
                r.body.contains("INVALID_NAME"));
    }

    public void testCreateMissingNameFieldReturns400() {
        ResponseEnvelope r = new CreateDiagramHandler(svc).handle(
                new HashMap<String, String>(), new HashMap<String, String>(),
                "{}");
        assertEquals(400, r.status);
        assertTrue("body must include INVALID_BODY: " + r.body,
                r.body.contains("INVALID_BODY"));
    }

    public void testCreateMalformedJsonReturns400() {
        ResponseEnvelope r = new CreateDiagramHandler(svc).handle(
                new HashMap<String, String>(), new HashMap<String, String>(),
                "{not json");
        assertEquals(400, r.status);
        assertTrue("body must include INVALID_BODY: " + r.body,
                r.body.contains("INVALID_BODY"));
    }

    public void testCreateNewDiagramIsInProjectList() {
        // After creating, the project's diagram list should include
        // the new name in addition to the seeded "Base".
        new CreateDiagramHandler(svc).handle(
                new HashMap<String, String>(), new HashMap<String, String>(),
                "{\"name\":\"NewlyCreated\"}");
        boolean found = false;
        for (ArgoDiagram d : project.getDiagramList()) {
            if ("NewlyCreated".equals(d.getName())) {
                found = true;
                break;
            }
        }
        assertTrue("newly-created diagram should appear in project list",
                found);
    }
}
