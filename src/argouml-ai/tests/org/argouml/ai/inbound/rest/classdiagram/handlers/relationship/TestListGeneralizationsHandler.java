/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers.relationship;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
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
 * Tests for {@link ListGeneralizationsHandler}.
 */
public class TestListGeneralizationsHandler extends TestCase {

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

    private Map<String, String> ppFor() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        return pp;
    }

    public void testNullServiceRejected() {
        try {
            new ListGeneralizationsHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testReturns200AndEmptyForFreshDiagram() {
        ResponseEnvelope env = new ListGeneralizationsHandler(svc).handle(
                ppFor(), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must wrap ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must contain empty data array: " + env.body,
                env.body.contains("\"data\":[]"));
    }

    public void testReturns200AndListsAddedGeneralization() {
        svc.createClass(DIAGRAM, "Document", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        svc.addGeneralization(DIAGRAM, "Order", "Document");
        ResponseEnvelope env = new ListGeneralizationsHandler(svc).handle(
                ppFor(), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must mention child Order: " + env.body,
                env.body.contains("\"child\":\"Order\""));
        assertTrue("body must mention parent Document: " + env.body,
                env.body.contains("\"parent\":\"Document\""));
    }

    public void testIgnoresAssociations() {
        svc.createClass(DIAGRAM, "Customer", 10, 20, null, false);
        svc.createClass(DIAGRAM, "Order", 30, 40, null, false);
        svc.addAssociation(DIAGRAM, "Customer", "Order",
                "1", "0..*", "places", "placedBy");
        ResponseEnvelope env = new ListGeneralizationsHandler(svc).handle(
                ppFor(), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must have empty data (assoc not gen): "
                + env.body, env.body.contains("\"data\":[]"));
    }

    public void testMissingDiagramThrowsNotFound() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "NoSuch");
        try {
            new ListGeneralizationsHandler(svc).handle(pp,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }
}