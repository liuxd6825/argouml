/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.classdiagram.handlers.layout;

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
 * Tests for {@link GetLayoutHandler} and {@link PostLayoutHandler}.
 *
 * <p>Boots the same model subsystem as
 * {@code TestGetClassHandler} so the tests can construct a real
 * {@link UMLClassDiagram} and exercise the layout endpoints without
 * an HTTP layer.</p>
 */
public class TestLayoutHandler extends TestCase {

    private static final String DIAGRAM = "TestLayout";

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
    @SuppressWarnings("deprecation")
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getUserDefinedModelList().iterator().next();
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

    // ---- null-service rejection (constructor validation) ----

    public void testGetRejectsNullService() {
        try {
            new GetLayoutHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testPostRejectsNullService() {
        try {
            new PostLayoutHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    // ---- GET /d/{d}/layout ----

    public void testGetReturns200AndEmptyListForFreshDiagram() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        ResponseEnvelope env = new GetLayoutHandler(svc).handle(pp,
                new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must wrap ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must report diagram: " + env.body,
                env.body.contains("\"diagram\":\"" + DIAGRAM + "\""));
        assertTrue("body must contain count:0: " + env.body,
                env.body.contains("\"count\":0"));
        assertTrue("body must contain empty classes array: " + env.body,
                env.body.contains("\"classes\":[]"));
    }

    public void testGetReturnsPositionsForExistingClasses() {
        svc.createClass(DIAGRAM, "A", 100, 50, null, false);
        svc.createClass(DIAGRAM, "B", 300, 200, null, false);
        svc.createClass(DIAGRAM, "C", 50, 400, null, false);

        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        ResponseEnvelope env = new GetLayoutHandler(svc).handle(pp,
                new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must mention A: " + env.body,
                env.body.contains("\"name\":\"A\""));
        assertTrue("body must mention B: " + env.body,
                env.body.contains("\"name\":\"B\""));
        assertTrue("body must mention C: " + env.body,
                env.body.contains("\"name\":\"C\""));
        // x and y must appear as numeric values
        assertTrue("body must contain x:100: " + env.body,
                env.body.contains("\"x\":100"));
        assertTrue("body must contain y:50: " + env.body,
                env.body.contains("\"y\":50"));
        assertTrue("body must contain x:300: " + env.body,
                env.body.contains("\"x\":300"));
    }

    public void testGetLayoutMissingDiagramThrowsDiagramNotFound() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "NoSuchDiagram");
        try {
            new GetLayoutHandler(svc).handle(pp,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    // ---- POST /d/{d}/layout ----

    public void testPostLayoutReturns200AndMovesClasses() {
        // Scatter 4 classes at overlapping positions
        svc.createClass(DIAGRAM, "A", 50, 50, null, false);
        svc.createClass(DIAGRAM, "B", 50, 50, null, false);
        svc.createClass(DIAGRAM, "C", 50, 50, null, false);
        svc.createClass(DIAGRAM, "D", 50, 50, null, false);

        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        ResponseEnvelope env = new PostLayoutHandler(svc).handle(pp,
                new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must wrap ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must report moved=4: " + env.body,
                env.body.contains("\"moved\":4"));

        // After layout, the 4 figs should no longer all share (50,50).
        // The layouter's contract guarantees non-overlapping placement,
        // so at least one class x or y must differ from 50.
        org.argouml.uml.diagram.ArgoDiagram d = org.argouml.ai.domain.common.DiagramLocator.byName(DIAGRAM);
        org.tigris.gef.presentation.Fig figA = d.presentationFor(
                new org.argouml.ai.domain.classdiagram.ClassOperations().findByName(d, "A"));
        org.tigris.gef.presentation.Fig figD = d.presentationFor(
                new org.argouml.ai.domain.classdiagram.ClassOperations().findByName(d, "D"));
        assertNotNull("Fig A must exist", figA);
        assertNotNull("Fig D must exist", figD);
        boolean moved = (figA.getX() != 50 || figA.getY() != 50)
                || (figD.getX() != 50 || figD.getY() != 50);
        assertTrue("at least one class must have moved away from (50,50)",
                moved);
    }

    public void testPostLayoutOnEmptyDiagramStillSucceeds() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        ResponseEnvelope env = new PostLayoutHandler(svc).handle(pp,
                new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must report moved=0: " + env.body,
                env.body.contains("\"moved\":0"));
    }

    public void testPostLayoutMissingDiagramThrowsDiagramNotFound() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "NoSuchDiagram");
        try {
            new PostLayoutHandler(svc).handle(pp,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testPostLayoutRejectsNonClassDiagram() {
        // Create a UseCase diagram (not a class diagram)
        org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram uc =
                new org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram(
                        "UseCaseForTest",
                        project.getUserDefinedModelList().iterator().next());
        project.addDiagram(uc);

        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "UseCaseForTest");
        try {
            new PostLayoutHandler(svc).handle(pp,
                    new HashMap<String, String>(), "");
            fail("expected InvalidArgumentException for non-class diagram");
        } catch (InvalidArgumentException expected) {
            assertEquals("UNSUPPORTED_DIAGRAM_TYPE", expected.code());
        }
    }
}
