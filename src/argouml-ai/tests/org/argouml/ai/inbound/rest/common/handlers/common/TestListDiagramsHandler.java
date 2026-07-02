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
 * Tests for {@link ListDiagramsHandler}.
 */
public class TestListDiagramsHandler extends TestCase {

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
    private ArgoDiagram diagramA;
    private ArgoDiagram diagramB;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getModel();
        diagramA = new UMLClassDiagram("Alpha", ns);
        diagramB = new UMLClassDiagram("Beta", ns);
        project.addDiagram(diagramA);
        project.addDiagram(diagramB);
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

    public void testReturns200WithOkEnvelope() {
        ResponseEnvelope env = new ListDiagramsHandler().handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(),
                "");
        assertEquals(200, env.status);
        assertTrue("body must wrap data in ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
    }

    public void testListsAllDiagrams() {
        ResponseEnvelope env = new ListDiagramsHandler().handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(),
                "");
        assertTrue("body must mention Alpha: " + env.body,
                env.body.contains("Alpha"));
        assertTrue("body must mention Beta: " + env.body,
                env.body.contains("Beta"));
    }

    public void testEntriesIncludeKindField() {
        ResponseEnvelope env = new ListDiagramsHandler().handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(),
                "");
        // For UMLClassDiagram, kind should be lowercased to "class".
        assertTrue("body must mention kind=class: " + env.body,
                env.body.contains("\"kind\":\"class\""));
    }

    public void testEntriesIncludeNamespaceField() {
        ResponseEnvelope env = new ListDiagramsHandler().handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(),
                "");
        assertTrue("body must mention namespace key: " + env.body,
                env.body.contains("\"namespace\""));
    }

    public void testSortedByName() {
        // Add a diagram that should sort first alphabetically.
        Object ns = project.getModel();
        ArgoDiagram zeta = new UMLClassDiagram("Zeta", ns);
        project.addDiagram(zeta);
        ResponseEnvelope env = new ListDiagramsHandler().handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(),
                "");
        int idxAlpha = env.body.indexOf("Alpha");
        int idxBeta = env.body.indexOf("Beta");
        int idxZeta = env.body.indexOf("Zeta");
        assertTrue("Alpha must appear before Beta (idx " + idxAlpha
                + " vs " + idxBeta + ")", idxAlpha > 0 && idxAlpha < idxBeta);
        assertTrue("Beta must appear before Zeta", idxBeta < idxZeta);
    }

    public void testThrowsWhenNoProject() {
        ProjectManager.getManager().removeProject(project);
        project = null;
        try {
            new ListDiagramsHandler().handle(
                    new HashMap<String, String>(),
                    new HashMap<String, String>(),
                    "");
            fail("expected NotFoundException when no project is open");
        } catch (NotFoundException expected) {
            assertEquals("PROJECT_NOT_FOUND", expected.code());
        }
    }
}