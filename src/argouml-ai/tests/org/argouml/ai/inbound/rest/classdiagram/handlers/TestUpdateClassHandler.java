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
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;

/**
 * Tests for {@link UpdateClassHandler}. Covers the partial-update
 * semantics (every field optional) and the rename / abstract /
 * stereotype paths.
 */
public class TestUpdateClassHandler extends TestCase {

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
            new UpdateClassHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testRenamesClass() {
        svc.createClass(DIAGRAM, "Old", 10, 20, null, false);
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", "Old");
        ResponseEnvelope env = new UpdateClassHandler(svc).handle(pp,
                new HashMap<String, String>(),
                "{\"newName\":\"New\"}");
        assertEquals(200, env.status);
        assertTrue("body must mention new name: " + env.body,
                env.body.contains("\"name\":\"New\""));
    }

    public void testToggleAbstract() {
        svc.createClass(DIAGRAM, "Abs", 10, 20, null, false);
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", "Abs");
        new UpdateClassHandler(svc).handle(pp,
                new HashMap<String, String>(),
                "{\"isAbstract\":true}");
        Facade f = Model.getFacade();
        Object cls = org.argouml.ai.domain.classdiagram.ClassOperations
                .findByName(diagram(), "Abs");
        assertNotNull("class should still exist", cls);
        assertTrue("class should now be abstract", f.isAbstract(cls));
    }

    public void testAddStereotype() {
        svc.createClass(DIAGRAM, "Foo", 10, 20, null, false);
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", "Foo");
        new UpdateClassHandler(svc).handle(pp,
                new HashMap<String, String>(),
                "{\"stereotype\":\"entity\"}");
        ClassDiagramService.ClassView v = svc.getClass(DIAGRAM, "Foo");
        assertTrue("stereotype should be present: " + v.stereotypeNames,
                v.stereotypeNames.contains("entity"));
    }

    public void testRenameToExistingThrowsDuplicate() {
        svc.createClass(DIAGRAM, "First", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Second", 30, 40, null, false);
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", "First");
        try {
            new UpdateClassHandler(svc).handle(pp,
                    new HashMap<String, String>(),
                    "{\"newName\":\"Second\"}");
            fail("expected DuplicateException for rename collision");
        } catch (DuplicateException expected) {
            assertEquals("DUPLICATE_CLASS", expected.code());
        }
    }

    public void testMissingClassThrowsClassNotFound() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", "NoSuch");
        try {
            new UpdateClassHandler(svc).handle(pp,
                    new HashMap<String, String>(),
                    "{\"newName\":\"X\"}");
            fail("expected NotFoundException for missing class");
        } catch (NotFoundException expected) {
            assertEquals("CLASS_NOT_FOUND", expected.code());
        }
    }

    public void testMissingDiagramThrowsDiagramNotFound() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "NoSuch");
        pp.put("c", "Order");
        try {
            new UpdateClassHandler(svc).handle(pp,
                    new HashMap<String, String>(), "{}");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testEmptyBodyIsNoop() {
        svc.createClass(DIAGRAM, "Foo", 10, 20, null, false);
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        pp.put("c", "Foo");
        ResponseEnvelope env = new UpdateClassHandler(svc).handle(pp,
                new HashMap<String, String>(), "{}");
        assertEquals(200, env.status);
        assertTrue("body must mention retained name: " + env.body,
                env.body.contains("\"name\":\"Foo\""));
    }

    private ArgoDiagram diagram() {
        // makeEmptyProject(true) adds Class + UseCase default
        // diagrams ahead of our test's "Test" diagram; use the
        // name-based locator to find the diagram the handler used,
        // not the project-list head.
        return org.argouml.ai.domain.common.DiagramLocator.byName(DIAGRAM);
    }
}