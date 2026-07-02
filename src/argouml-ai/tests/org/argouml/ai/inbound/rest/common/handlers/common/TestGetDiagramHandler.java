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
 * Tests for {@link GetDiagramHandler}.
 */
public class TestGetDiagramHandler extends TestCase {

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

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getModel();
        ArgoDiagram d = new UMLClassDiagram("Test", ns);
        project.addDiagram(d);
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

    public void testReturns200OnHit() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "Test");
        ResponseEnvelope env = new GetDiagramHandler().handle(pp,
                new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must wrap in ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must mention name 'Test': " + env.body,
                env.body.contains("Test"));
    }

    public void testReturnsKindField() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "Test");
        ResponseEnvelope env = new GetDiagramHandler().handle(pp,
                new HashMap<String, String>(), "");
        assertTrue("body must contain kind=class: " + env.body,
                env.body.contains("\"kind\":\"class\""));
    }

    public void testMissingDiagramThrows404() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "DoesNotExist");
        try {
            new GetDiagramHandler().handle(pp,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testNullPathParamThrows() {
        try {
            new GetDiagramHandler().handle(new HashMap<String, String>(),
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for null name");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testNoProjectThrows() {
        ProjectManager.getManager().removeProject(project);
        project = null;
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "Test");
        try {
            new GetDiagramHandler().handle(pp,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException when no project");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }
}