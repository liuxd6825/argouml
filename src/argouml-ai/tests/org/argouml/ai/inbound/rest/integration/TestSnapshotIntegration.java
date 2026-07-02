/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.integration;

import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;

/**
 * Integration tests for the snapshot endpoint
 * ({@code GET /project/diagrams/{d}/snapshot}). Each test runs
 * against a real {@link fi.iki.elonen.NanoHTTPD} instance wired
 * to the production router / dispatcher / service stack, so the
 * assertions cover the whole request path - HTTP framing, JSON
 * envelope, the {@link org.argouml.ai.tools.ProjectSnapshot}
 * serializer, and (for the 404 case) the diagram-locator
 * not-found path.
 *
 * <p>The snapshot endpoint re-uses the route registered by the
 * base class as {@code /project/diagrams/{d}/snapshot} (not
 * {@code /d/{d}/snapshot} as the {@code SnapshotHandler} Javadoc
 * older draft suggested). The handler wraps the
 * {@link org.argouml.ai.tools.ProjectSnapshot.Snapshot#toJson()}
 * payload inside the standard
 * {@code {"ok":true,"data":<snapshot>}} envelope.</p>
 *
 * <p>Snapshot shape: the inner {@code data} object is the
 * snapshot JSON, with three top-level keys
 * ({@code diagram}, {@code classes}, {@code associations}). Each
 * class entry carries {@code name}, {@code attrs}, {@code ops};
 * an empty diagram has an empty array for both
 * {@code classes} and {@code associations}. The tests assert on
 * these keys via substring match (no JSON parser dependency)
 * because the wire format is byte-stable - see
 * {@link org.argouml.ai.tools.ProjectSnapshot.Snapshot#toJson()}.</p>
 */
public class TestSnapshotIntegration extends TestHttpServerIntegrationBase {

    // -----------------------------------------------------------------
    // GET /project/diagrams/{d}/snapshot
    // -----------------------------------------------------------------

    public void testSnapshotOfEmptyDiagram() throws Exception {
        // No classes, no associations, no attributes - the snapshot
        // handler must still return 200 with a well-formed envelope
        // and the three top-level keys, plus the empty arrays for
        // classes / associations.
        Response r = httpGet("/project/diagrams/" + DIAGRAM + "/snapshot");
        assertEquals("status should be 200, got " + r.status
                + " body: " + r.body, 200, r.status);
        assertTrue("body must wrap ok:true: " + r.body,
                r.body.contains("\"ok\":true"));
        assertTrue("body must contain 'diagram' key: " + r.body,
                r.body.contains("\"diagram\""));
        assertTrue("body must contain empty classes array: " + r.body,
                r.body.contains("\"classes\":[]"));
        assertTrue("body must contain empty associations array: "
                + r.body, r.body.contains("\"associations\":[]"));
    }

    public void testSnapshotAfterCreate() throws Exception {
        // Two classes back-to-back; the second snapshot must
        // surface both names. We deliberately do not assert on
        // attribute / operation details here - that is covered
        // by testSnapshotJsonShape below.
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":1,\"y\":1}").status);
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"B\",\"x\":2,\"y\":2}").status);
        Response r = httpGet("/project/diagrams/" + DIAGRAM + "/snapshot");
        assertEquals("status should be 200, got " + r.status
                + " body: " + r.body, 200, r.status);
        assertTrue("body must wrap ok:true: " + r.body,
                r.body.contains("\"ok\":true"));
        assertTrue("snapshot must include class A: " + r.body,
                r.body.contains("\"name\":\"A\""));
        assertTrue("snapshot must include class B: " + r.body,
                r.body.contains("\"name\":\"B\""));
    }

    public void testSnapshotUnknownDiagram() throws Exception {
        // Unknown diagram name - the snapshot handler delegates to
        // DiagramLocator.byName which throws a not-found; the
        // dispatcher maps that to 404 with code DIAGRAM_NOT_FOUND.
        Response r = httpGet("/project/diagrams/NoSuch/snapshot");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must wrap ok:false: " + r.body,
                r.body.contains("\"ok\":false"));
        assertTrue("body must mention DIAGRAM_NOT_FOUND: " + r.body,
                r.body.contains("DIAGRAM_NOT_FOUND"));
    }

    public void testSnapshotJsonShape() throws Exception {
        // Create one class with one attribute; the snapshot must
        // contain the three top-level keys (diagram, classes,
        // associations) and each class entry must include the
        // name / attrs / ops sub-keys.
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"Widget\",\"x\":50,\"y\":50}").status);
        assertEquals(201, httpPost("/d/" + DIAGRAM
                + "/classes/Widget/attributes",
                "{\"name\":\"id\",\"type\":\"long\"}").status);
        Response r = httpGet("/project/diagrams/" + DIAGRAM + "/snapshot");
        assertEquals("status should be 200, got " + r.status
                + " body: " + r.body, 200, r.status);
        // Top-level keys: 'diagram', 'classes', 'associations'.
        assertTrue("snapshot must have diagram key: " + r.body,
                r.body.contains("\"diagram\""));
        assertTrue("snapshot must have classes key: " + r.body,
                r.body.contains("\"classes\""));
        assertTrue("snapshot must have associations key: " + r.body,
                r.body.contains("\"associations\""));
        // Per-class shape: name / attrs / ops.
        assertTrue("class entry must have name: " + r.body,
                r.body.contains("\"name\":\"Widget\""));
        assertTrue("class entry must have attrs array: " + r.body,
                r.body.contains("\"attrs\""));
        assertTrue("class entry must have ops array: " + r.body,
                r.body.contains("\"ops\""));
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    /**
     * Add a second class diagram named {@code Other} to the
     * current project. Snapshot-isolation tests in
     * {@link TestEndToEndWorkflow#testMultipleDiagramsIsolation}
     * reuse this; declared here for proximity to the rest of the
     * snapshot shape assertions.
     */
    protected void addOtherDiagram() {
        project.addDiagram(new UMLClassDiagram("Other", project.getModel()));
    }
}
