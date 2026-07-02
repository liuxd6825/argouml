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
 * Tests for {@link DeleteOperationHandler}.
 */
public class TestDeleteOperationHandler extends TestCase {

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

    private Map<String, String> ppFor(String className, String opName) {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", className);
        pp.put("op", opName);
        return pp;
    }

    public void testNullServiceRejected() {
        try {
            new DeleteOperationHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testReturns204OnHappyPath() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        svc.addOperation(DIAGRAM, "Order", "save", "void", "public");
        ResponseEnvelope env = new DeleteOperationHandler(svc).handle(
                ppFor("Order", "save"),
                new HashMap<String, String>(), "");
        assertEquals(204, env.status);
        assertEquals("body should be empty for 204", "", env.body);
    }

    public void testOperationIsGoneAfter() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        svc.addOperation(DIAGRAM, "Order", "save", "void", "public");
        new DeleteOperationHandler(svc).handle(
                ppFor("Order", "save"),
                new HashMap<String, String>(), "");
        ClassDiagramService.ClassView after =
                svc.getClass(DIAGRAM, "Order");
        for (String enc : after.operations) {
            assertFalse("operation should be gone: " + enc,
                    enc.startsWith("save"));
        }
    }

    public void testMissingOpThrowsNotFound() {
        svc.createClass(DIAGRAM, "Order", 10, 20, null, false);
        try {
            new DeleteOperationHandler(svc).handle(
                    ppFor("Order", "noSuchOp"),
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing operation");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testMissingClassThrowsClassNotFound() {
        Map<String, String> pathParams = new HashMap<String, String>();
        pathParams.put("d", DIAGRAM);
        pathParams.put("c", "NoSuch");
        pathParams.put("op", "save");
        try {
            new DeleteOperationHandler(svc).handle(pathParams,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing class");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testMissingDiagramThrowsDiagramNotFound() {
        Map<String, String> pathParams = new HashMap<String, String>();
        pathParams.put("d", "NoSuch");
        pathParams.put("c", "Order");
        pathParams.put("op", "save");
        try {
            new DeleteOperationHandler(svc).handle(pathParams,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testEmptyNameThrowsInvalidName() {
        Map<String, String> pathParams = new HashMap<String, String>();
        pathParams.put("d", DIAGRAM);
        pathParams.put("c", "Order");
        pathParams.put("op", "");
        try {
            new DeleteOperationHandler(svc).handle(pathParams,
                    new HashMap<String, String>(), "");
            fail("expected InvalidArgumentException for empty op name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testSlashInNameThrowsInvalidName() {
        Map<String, String> pathParams = new HashMap<String, String>();
        pathParams.put("d", DIAGRAM);
        pathParams.put("c", "Order");
        pathParams.put("op", "foo/bar");
        try {
            new DeleteOperationHandler(svc).handle(pathParams,
                    new HashMap<String, String>(), "");
            fail("expected InvalidArgumentException for '/' in name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }
}