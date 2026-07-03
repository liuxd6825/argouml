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
 * Tests for {@link DeleteClassHandler}.
 */
public class TestDeleteClassHandler extends TestCase {

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

    public void testNullServiceRejected() {
        try {
            new DeleteClassHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testReturns204OnHappyPath() {
        svc.createClass(DIAGRAM, "Temp", 10, 20, null, false);
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", "Temp");
        ResponseEnvelope env = new DeleteClassHandler(svc).handle(pp,
                new HashMap<String, String>(), "");
        assertEquals(204, env.status);
        assertEquals("body should be empty for 204", "", env.body);
    }

    public void testClassIsRemovedFromModel() {
        svc.createClass(DIAGRAM, "Temp", 10, 20, null, false);
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", "Temp");
        new DeleteClassHandler(svc).handle(pp,
                new HashMap<String, String>(), "");
        assertNull("class should be gone",
                new org.argouml.ai.domain.classdiagram.ClassOperations().findByName(
                        org.argouml.ai.domain.common.DiagramLocator.byName(DIAGRAM), "Temp"));
    }

    public void testMissingClassThrowsClassNotFound() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", "NoSuch");
        try {
            new DeleteClassHandler(svc).handle(pp,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing class");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testMissingDiagramThrowsDiagramNotFound() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "NoSuch");
        pp.put("c", "Temp");
        try {
            new DeleteClassHandler(svc).handle(pp,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testEmptyClassNameReturns400() {
        // The Delete handler now returns a 400 INVALID_NAME JSON
        // envelope rather than throwing — the dispatcher still
        // surfaces the envelope to the client. We assert the
        // status and code directly.
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", "");
        ResponseEnvelope env = new DeleteClassHandler(svc).handle(
                pp, new HashMap<String, String>(), "");
        assertEquals(400, env.status);
        assertTrue("body must mention INVALID_NAME: " + env.body,
                env.body.contains("INVALID_NAME"));
    }
}