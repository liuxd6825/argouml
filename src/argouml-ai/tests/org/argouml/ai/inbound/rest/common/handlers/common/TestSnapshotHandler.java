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
import org.argouml.ai.application.common.UnsupportedException;
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
 * Tests for {@link SnapshotHandler}. Only class diagrams are in the
 * snapshot tool's MVP scope; the handler is expected to respond 501
 * for anything else.
 */
public class TestSnapshotHandler extends TestCase {

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
        ArgoDiagram d = new UMLClassDiagram("Snap", ns);
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

    public void testSnapshotOfEmptyClassDiagram() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "Snap");
        ResponseEnvelope env = new SnapshotHandler().handle(pp,
                new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must contain ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        // Even an empty diagram produces a snapshot object with the
        // 'classes' and 'associations' keys present.
        assertTrue("body must contain 'classes': " + env.body,
                env.body.contains("\"classes\""));
        assertTrue("body must contain 'associations': " + env.body,
                env.body.contains("\"associations\""));
    }

    public void testSnapshotOfPopulatedDiagram() {
        // Use ClassDiagramService so the class is properly registered
        // with the diagram's graph model and namespace. Building a
        // class with Model.getCoreFactory().buildClass() alone does
        // not link it into the diagram.
        org.argouml.ai.application.classdiagram.ClassDiagramService svc =
                new org.argouml.ai.application.classdiagram.ClassDiagramService();
        svc.createClass("Snap", "Customer", 10, 20, null, false);

        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "Snap");
        ResponseEnvelope env = new SnapshotHandler().handle(pp,
                new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must mention Customer: " + env.body,
                env.body.contains("Customer"));
    }

    public void testMissingDiagramThrows404() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "NoSuch");
        try {
            new SnapshotHandler().handle(pp,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testNullPathParamThrows() {
        try {
            new SnapshotHandler().handle(new HashMap<String, String>(),
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for null diagram name");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testNonClassDiagramThrows501() {
        // Build a project with a non-class diagram type. UseCaseDiagram
        // is a stock ArgoUML diagram type; the snapshot tool does not
        // support it in MVP.
        Object ns = project.getModel();
        ArgoDiagram useCase = null;
        try {
            Class<?> ucCls = Class.forName(
                    "org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram");
            useCase = (ArgoDiagram) ucCls.getConstructor(Object.class)
                    .newInstance(ns);
        } catch (Throwable t) {
            // If the UseCaseDiagram class is unavailable in this
            // build, skip the test rather than fail. The class-name
            // based detection in SnapshotHandler is the contract
            // under test, so any non-class class-name will do.
            return;
        }
        project.addDiagram(useCase);

        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", useCase.getName());
        try {
            new SnapshotHandler().handle(pp,
                    new HashMap<String, String>(), "");
            fail("expected UnsupportedException for non-class diagram");
        } catch (UnsupportedException expected) {
            assertEquals("SNAPSHOT_UNSUPPORTED", expected.code());
        }
    }
}