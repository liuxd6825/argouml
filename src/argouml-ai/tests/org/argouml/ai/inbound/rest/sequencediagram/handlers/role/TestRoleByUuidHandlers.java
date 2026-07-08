/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.sequencediagram.handlers.role;

import java.beans.PropertyVetoException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.domain.entity.SequenceClassifierRoleEntity;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.sequence2.diagram.UMLSequenceDiagram;

/**
 * Edge-case tests for the two role-by-uuid handlers:
 * {@link GetRoleByUuidHandler} and {@link DeleteRoleByUuidHandler}.
 *
 * <p>The happy paths and 404-by-missing-uuid paths are exercised
 * by {@link TestRoleHandlers}; this file focuses on
 * round-trip-after-create and bad-input edge cases (empty uuid
 * segment, non-existent uuid on delete).</p>
 */
public class TestRoleByUuidHandlers extends TestCase {

    private static final String DIAGRAM = "TestRoleUuidHandlers";

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
    private SequenceDiagramService svc;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object modelRoot = project.getUserDefinedModelList().iterator().next();
        Object collaboration = Model.getCollaborationsFactory()
                .buildCollaboration(modelRoot);
        UMLSequenceDiagram d = new UMLSequenceDiagram(collaboration);
        try {
            d.setName(DIAGRAM);
        } catch (PropertyVetoException e) {
            throw new RuntimeException(e);
        }
        project.addDiagram(d);
        svc = new SequenceDiagramService();
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

    private SequenceClassifierRoleEntity createRole(String name) {
        return svc.createRole(DIAGRAM, name, "", 0, 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(String body) {
        Map<String, Object> env = JsonBodyReader.readMap(body);
        Object data = env == null ? null : env.get("data");
        return data instanceof Map ? (Map<String, Object>) data : null;
    }

    public void testGetRoleByUuidAfterCreate() {
        SequenceClassifierRoleEntity created = createRole("RoundTrip");
        Map<String, String> m = pp();
        m.put("uuid", created.uuid());
        ResponseEnvelope env = new GetRoleByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals(created.uuid(), data.get("uuid"));
        assertEquals("RoundTrip", data.get("name"));
        assertEquals("classifierRole", data.get("kind"));
    }

    public void testGetRoleByUuidInvalidFormatReturns400() {
        Map<String, String> m = pp();
        m.put("uuid", "");
        ResponseEnvelope env = new GetRoleByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(400, env.status);
        assertTrue("body must mention INVALID_NAME: " + env.body,
                env.body.contains("INVALID_NAME"));
    }

    public void testDeleteRoleByUuidWithNonExistentUuidReturns404() {
        Map<String, String> m = pp();
        m.put("uuid", "no-such-uuid");
        try {
            new DeleteRoleByUuidHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }
}