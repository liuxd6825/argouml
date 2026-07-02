/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers.operation;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
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
 * Tests for {@link ListOperationsHandler}.
 */
public class TestListOperationsHandler extends TestCase {

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
            new ListOperationsHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testReturns200AndEmptyForFreshClass() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        ResponseEnvelope env = new ListOperationsHandler(svc).handle(
                ppFor("Order"), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must wrap ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must contain empty data array: " + env.body,
                env.body.contains("\"data\":[]"));
    }

    public void testReturns200AndListsAddedOperations() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        svc.addOperation(DIAGRAM, "Order", "save", "void", "public");
        svc.addOperation(DIAGRAM, "Order", "cancel", null, null);
        ResponseEnvelope env = new ListOperationsHandler(svc).handle(
                ppFor("Order"), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must mention save: " + env.body,
                env.body.contains("save"));
        assertTrue("body must mention cancel: " + env.body,
                env.body.contains("cancel"));
        assertTrue("body must mention void returnType: " + env.body,
                env.body.contains("void"));
        assertTrue("body must mention public visibility: " + env.body,
                env.body.contains("public"));
    }

    public void testReturns200AndEchoesReturnType() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        svc.addOperation(DIAGRAM, "Order", "getTotal", "double", "public");
        ResponseEnvelope env = new ListOperationsHandler(svc).handle(
                ppFor("Order"), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must mention getTotal: " + env.body,
                env.body.contains("getTotal"));
        assertTrue("body must mention double returnType: " + env.body,
                env.body.contains("double"));
    }

    public void testMissingDiagramThrowsNotFound() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        Map<String, String> pathParams = new HashMap<String, String>();
        pathParams.put("d", "NoSuch");
        pathParams.put("c", "Order");
        try {
            new ListOperationsHandler(svc).handle(pathParams,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing diagram");
        } catch (org.argouml.ai.application.common.NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testMissingClassThrowsClassNotFound() {
        Map<String, String> pathParams = new HashMap<String, String>();
        pathParams.put("d", DIAGRAM);
        pathParams.put("c", "NoSuch");
        try {
            new ListOperationsHandler(svc).handle(pathParams,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing class");
        } catch (org.argouml.ai.application.common.NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }
}