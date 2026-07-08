/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.usecasediagram.handlers;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.common.DiagramServiceException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.CreateAssociationHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.CreateExtendHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.CreateIncludeHandler;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram;

public class TestRelationshipHandlers extends TestCase {

    private static final String DIAGRAM = "TestRelH";

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
    private UseCaseDiagramService svc;

    @Override
    @SuppressWarnings("deprecation")
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getUserDefinedModelList().iterator().next();
        ArgoDiagram d = new UMLUseCaseDiagram(DIAGRAM, ns);
        project.addDiagram(d);
        svc = new UseCaseDiagramService();
        // Pre-create one actor and two use cases for relationship tests
        svc.createActor(DIAGRAM, "User", 0, 0);
        svc.createUseCase(DIAGRAM, "Login", null, 100, 100);
        svc.createUseCase(DIAGRAM, "Sub", null, 200, 200);
        svc.createUseCase(DIAGRAM, "Ext", null, 300, 300);
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

    private Map<String, String> pp() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("d", DIAGRAM);
        return m;
    }

    public void testCreateAssociationReturns201() {
        ResponseEnvelope env = new CreateAssociationHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"actor\":\"User\",\"usecase\":\"Login\"}");
        assertEquals(201, env.status);
        assertTrue("body must mention User|Login: " + env.body,
                env.body.contains("User|Login"));
    }

    public void testCreateAssociationMissingActorReturns404() {
        try {
            new CreateAssociationHandler(svc).handle(
                    pp(), new HashMap<String, String>(),
                    "{\"actor\":\"Nobody\",\"usecase\":\"Login\"}");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ACTOR_NOT_FOUND", expected.code());
        }
    }

    public void testCreateAssociationMissingUseCaseReturns404() {
        try {
            new CreateAssociationHandler(svc).handle(
                    pp(), new HashMap<String, String>(),
                    "{\"actor\":\"User\",\"usecase\":\"Nobody\"}");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("USECASE_NOT_FOUND", expected.code());
        }
    }

    public void testCreateIncludeReturns201() {
        ResponseEnvelope env = new CreateIncludeHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"base\":\"Login\",\"inclusion\":\"Sub\"}");
        assertEquals(201, env.status);
        assertTrue("body must mention Login|Sub: " + env.body,
                env.body.contains("Login|Sub"));
    }

    public void testCreateIncludeMissingBaseReturns404() {
        try {
            new CreateIncludeHandler(svc).handle(
                    pp(), new HashMap<String, String>(),
                    "{\"base\":\"Nobody\",\"inclusion\":\"Sub\"}");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("USECASE_NOT_FOUND", expected.code());
        }
    }

    public void testCreateExtendReturns201() {
        ResponseEnvelope env = new CreateExtendHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"base\":\"Login\",\"extension\":\"Ext\","
                + "\"extensionPoint\":\"after-login\"}");
        assertEquals(201, env.status);
        assertTrue("body must mention Login|Ext: " + env.body,
                env.body.contains("Login|Ext"));
    }

    public void testCreateExtendWithoutExtensionPoint() {
        ResponseEnvelope env = new CreateExtendHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"base\":\"Sub\",\"extension\":\"Ext\"}");
        assertEquals(201, env.status);
    }

    public void testRejectsNullService() {
        try {
            new CreateAssociationHandler(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new CreateIncludeHandler(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new CreateExtendHandler(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
