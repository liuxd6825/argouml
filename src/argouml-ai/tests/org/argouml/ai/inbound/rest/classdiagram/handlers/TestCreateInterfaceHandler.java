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
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;

/**
 * Tests for {@link CreateInterfaceHandler}.
 */
public class TestCreateInterfaceHandler extends TestCase {

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
            new CreateInterfaceHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testReturns201OnHappyPath() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        ResponseEnvelope env = new CreateInterfaceHandler(svc).handle(pp,
                new HashMap<String, String>(),
                "{\"name\":\"ICloneable\",\"x\":100,\"y\":100}");
        assertEquals(201, env.status);
        assertTrue("body must wrap ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must mention name ICloneable: " + env.body,
                env.body.contains("ICloneable"));
    }

    public void testInterfaceIsAttachedToDiagram() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        new CreateInterfaceHandler(svc).handle(pp,
                new HashMap<String, String>(),
                "{\"name\":\"ICloneable\"}");
        ArgoDiagram d = org.argouml.ai.domain.common.DiagramLocator.byName(DIAGRAM);
        Object found = null;
        for (Object node : d.getGraphModel().getNodes()) {
            if ("ICloneable".equals(Model.getFacade().getName(node))) {
                found = node;
                break;
            }
        }
        assertNotNull("interface should be present on diagram", found);
        assertTrue("result must be an Interface",
                Model.getFacade().isAInterface(found));
    }

    public void testEmptyNameThrowsInvalidArgument() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        try {
            new CreateInterfaceHandler(svc).handle(pp,
                    new HashMap<String, String>(), "{\"name\":\"\"}");
            fail("expected InvalidArgumentException for empty name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testMissingDiagramThrowsNotFound() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "NoSuch");
        try {
            new CreateInterfaceHandler(svc).handle(pp,
                    new HashMap<String, String>(), "{\"name\":\"IFoo\"}");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testWithStereotype() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", DIAGRAM);
        ResponseEnvelope env = new CreateInterfaceHandler(svc).handle(pp,
                new HashMap<String, String>(),
                "{\"name\":\"ISer\",\"stereotype\":\"role\"}");
        assertEquals(201, env.status);
        assertTrue("body must mention name: " + env.body,
                env.body.contains("ISer"));
    }
}