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
 * Tests for the duplicate-DataType cleanup path. Exercises both
 * {@link ClassDiagramService#cleanupDuplicateDataTypes()} (the
 * underlying service) and {@link CleanupDatatypesHandler} (the
 * REST surface).
 */
public class TestCleanupDatatypes extends TestCase {

    private static final String DIAGRAM = "TestCleanup";

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

    /**
     * Verifies that {@code resolveType} now deduplicates: adding
     * two attributes with the same type results in a single
     * DataType in the namespace.
     */
    public void testAddingAttributesWithSameTypeDeduplicates() {
        svc.createClass(DIAGRAM, "A", 10, 20, null, false);
        svc.createClass(DIAGRAM, "B", 200, 20, null, false);

        svc.addAttribute(DIAGRAM, "A", "name",  "String", "private");
        svc.addAttribute(DIAGRAM, "A", "code",  "String", "private");
        svc.addAttribute(DIAGRAM, "B", "label", "String", "private");
        svc.addAttribute(DIAGRAM, "B", "hint",  "String", "private");

        // Exactly one DataType named "String" should now exist
        Facade facade = Model.getFacade();
        Object model = project.getUserDefinedModelList().iterator().next();
        int stringCount = countDataTypesByName(facade, model, "String");
        assertEquals("expected 1 String DataType after dedup, got "
                + stringCount, 1, stringCount);
    }

    /**
     * Verifies that {@code cleanupDuplicateDataTypes} removes the
     * legacy duplicates that were created before the dedup fix.
     */
    public void testCleanupRemovesPreExistingDuplicates() {
        Facade facade = Model.getFacade();
        Object model = project.getUserDefinedModelList().iterator().next();

        // Simulate the legacy bug by hand-crafting 5 String DataTypes
        // in the model's own namespace.
        for (int i = 0; i < 5; i++) {
            Model.getCoreFactory().buildDataType("String", model);
        }
        int before = countDataTypesByName(facade, model, "String");
        assertEquals("expected 5 String DataTypes before cleanup",
                5, before);

        ClassDiagramService.CleanupReport r =
                svc.cleanupDuplicateDataTypes();
        assertEquals(5, r.scanned);
        assertEquals(4, r.removed);
        assertEquals(1, r.kept.size());
        assertEquals("String", r.kept.get(0));

        int after = countDataTypesByName(facade, model, "String");
        assertEquals("expected 1 String DataType after cleanup",
                1, after);
    }

    public void testCleanupIsIdempotent() {
        Object model = project.getUserDefinedModelList().iterator().next();
        for (int i = 0; i < 3; i++) {
            Model.getCoreFactory().buildDataType("int", model);
        }
        // First pass removes duplicates
        ClassDiagramService.CleanupReport first = svc.cleanupDuplicateDataTypes();
        assertEquals(3, first.scanned);
        assertEquals(2, first.removed);
        // Second pass should find no duplicates
        ClassDiagramService.CleanupReport second = svc.cleanupDuplicateDataTypes();
        assertEquals("no duplicates expected on second pass", 0, second.removed);
    }

    public void testHandlerReturns200() {
        ResponseEnvelope env = new CleanupDatatypesHandler(svc).handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must wrap ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must include scanned field: " + env.body,
                env.body.contains("\"scanned\":"));
        assertTrue("body must include removed field: " + env.body,
                env.body.contains("\"removed\":"));
        assertTrue("body must include kept array: " + env.body,
                env.body.contains("\"kept\":"));
    }

    public void testHandlerRejectsNullService() {
        try {
            new CleanupDatatypesHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    // ---- helpers ----

    private static int countDataTypesByName(Facade facade, Object root,
                                            String name) {
        int count = 0;
        java.util.Deque<Object> queue = new java.util.ArrayDeque<Object>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Object ns = queue.removeFirst();
            java.util.Collection owned = facade.getOwnedElements(ns);
            if (owned == null) {
                continue;
            }
            for (Object e : owned) {
                if (facade.isADataType(e)
                        && name.equals(facade.getName(e))) {
                    count++;
                } else if (facade.isANamespace(e)) {
                    queue.addLast(e);
                }
            }
        }
        return count;
    }
}
