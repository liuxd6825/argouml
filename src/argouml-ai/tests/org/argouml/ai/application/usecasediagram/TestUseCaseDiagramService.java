/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.application.usecasediagram;

import junit.framework.TestCase;

import org.argouml.ai.application.common.DiagramServiceException;
import org.argouml.ai.application.common.DuplicateException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.domain.entity.UsecaseActorEntity;
import org.argouml.ai.domain.entity.UsecaseAssociationEntity;
import org.argouml.ai.domain.entity.UsecaseExtendEntity;
import org.argouml.ai.domain.entity.UsecaseIncludeEntity;
import org.argouml.ai.domain.entity.UsecaseUseCaseEntity;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram;

public class TestUseCaseDiagramService extends TestCase {

    private static final String DIAGRAM = "TestUCDS";

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

    public void testKindIsUSECASE() {
        assertEquals(org.argouml.ai.domain.common.ModelKind.USECASE,
                svc.kind());
    }

    public void testCreateActorAndList() {
        UsecaseActorEntity a = svc.createActor(DIAGRAM, "User", 100, 50);
        assertEquals("User", a.name());
        assertEquals("actor", a.kind());
        assertFalse("uuid must be non-empty", a.uuid().isEmpty());
        assertEquals(1, svc.listActors(DIAGRAM).size());
        assertEquals("User", svc.listActors(DIAGRAM).get(0).name());
    }

    public void testCreateActorDuplicateThrows() {
        svc.createActor(DIAGRAM, "User", 0, 0);
        try {
            svc.createActor(DIAGRAM, "User", 0, 0);
            fail("expected DuplicateException");
        } catch (DuplicateException expected) {
            assertEquals("DUPLICATE_ACTOR", expected.code());
        }
    }

    public void testCreateActorEmptyNameThrows() {
        try {
            svc.createActor(DIAGRAM, "", 0, 0);
            fail("expected DiagramServiceException");
        } catch (DiagramServiceException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testDeleteActorByName() {
        UsecaseActorEntity a = svc.createActor(DIAGRAM, "A", 0, 0);
        svc.deleteActorByName(DIAGRAM, "A");
        assertEquals(0, svc.listActors(DIAGRAM).size());
        // Delete by uuid works on the same element
        UsecaseActorEntity b = svc.createActor(DIAGRAM, "B", 0, 0);
        svc.deleteActorByUuid(DIAGRAM, b.uuid());
        assertEquals(0, svc.listActors(DIAGRAM).size());
        // suppress unused warning
        assertNotNull(a);
    }

    public void testDeleteMissingActorByNameThrows() {
        try {
            svc.deleteActorByName(DIAGRAM, "Nobody");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ACTOR_NOT_FOUND", expected.code());
        }
    }

    public void testFindActorByUuidRoundTrip() {
        UsecaseActorEntity created = svc.createActor(DIAGRAM, "F", 0, 0);
        UsecaseActorEntity found = svc.findActorByUuid(DIAGRAM, created.uuid());
        assertEquals(created.uuid(), found.uuid());
        assertEquals("F", found.name());
    }

    public void testFindActorByUuidMissingThrows() {
        try {
            svc.findActorByUuid(DIAGRAM, "no-such-uuid");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ACTOR_NOT_FOUND", expected.code());
        }
    }

    public void testCreateUseCaseAndList() {
        UsecaseUseCaseEntity u = svc.createUseCase(DIAGRAM, "Login", null, 200, 100);
        assertEquals("Login", u.name());
        assertEquals("usecase", u.kind());
        assertFalse("uuid must be non-empty", u.uuid().isEmpty());
        assertEquals(1, svc.listUseCases(DIAGRAM).size());
        assertEquals("Login", svc.listUseCases(DIAGRAM).get(0).name());
    }

    public void testGetUseCaseByNameAndByUuid() {
        UsecaseUseCaseEntity created = svc.createUseCase(DIAGRAM, "G", "desc", 1, 2);
        assertEquals("G", svc.getUseCaseByName(DIAGRAM, "G").name());
        assertEquals(created.uuid(),
                svc.findUseCaseByUuid(DIAGRAM, created.uuid()).uuid());
    }

    public void testCreateInclude() {
        svc.createUseCase(DIAGRAM, "Base", null, 100, 100);
        svc.createUseCase(DIAGRAM, "Sub", null, 200, 200);
        UsecaseIncludeEntity r = svc.createInclude(DIAGRAM, "Base", "Sub");
        assertEquals("Base|Sub", r.id());
        assertEquals("Base", r.baseName());
        assertEquals("Sub", r.inclusionName());
        assertEquals("include", r.kind());
        assertFalse("uuid must be non-empty", r.uuid().isEmpty());
    }

    public void testCreateExtend() {
        svc.createUseCase(DIAGRAM, "Base", null, 100, 100);
        svc.createUseCase(DIAGRAM, "Ext", null, 200, 200);
        UsecaseExtendEntity r = svc.createExtend(DIAGRAM, "Base", "Ext", "after-base");
        assertEquals("Base|Ext", r.id());
        assertEquals("after-base", r.extensionPoint());
        assertEquals("extend", r.kind());
        assertFalse("uuid must be non-empty", r.uuid().isEmpty());
    }

    public void testCreateAssociation() {
        svc.createActor(DIAGRAM, "User", 0, 0);
        svc.createUseCase(DIAGRAM, "Login", null, 0, 0);
        UsecaseAssociationEntity r = svc.createAssociation(DIAGRAM, "User", "Login");
        assertEquals("User|Login", r.id());
        assertEquals("User", r.actorName());
        assertEquals("Login", r.usecaseName());
        assertEquals("association", r.kind());
        assertFalse("uuid must be non-empty", r.uuid().isEmpty());
    }

    public void testCreateAssociationMissingActorThrows() {
        svc.createUseCase(DIAGRAM, "Login", null, 0, 0);
        try {
            svc.createAssociation(DIAGRAM, "NoOne", "Login");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ACTOR_NOT_FOUND", expected.code());
        }
    }
}