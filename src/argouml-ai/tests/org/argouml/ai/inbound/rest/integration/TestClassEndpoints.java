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

import org.argouml.ai.domain.classdiagram.ClassOperations;
import org.tigris.gef.presentation.Fig;

/**
 * Integration tests for the class endpoints
 * ({@code GET/POST/PUT/DELETE /d/{d}/classes} and
 * {@code POST /d/{d}/interfaces}). Each test runs against a real
 * {@link fi.iki.elonen.NanoHTTPD} instance wired to the production
 * router / dispatcher / service stack, so the assertions cover the
 * whole request path - HTTP framing, JSON envelope, exception
 * mapping, service-level validation, and model mutation.
 *
 * <p>Position (x/y) assertions on PUT and on the create-with-default
 * path go through the {@link ArgoDiagram#presentationFor} API
 * because the class read shape ({@code ClassView}) intentionally
 * does not expose presentation coordinates - positions are
 * presentation-layer state, not model state.</p>
 */
public class TestClassEndpoints extends TestHttpServerIntegrationBase {

    // -----------------------------------------------------------------
    // GET /d/{d}/classes
    // -----------------------------------------------------------------

    public void testListOnEmptyDiagram() throws Exception {
        Response r = httpGet("/d/" + DIAGRAM + "/classes");
        assertEquals("status should be 200, got " + r.status
                + " body: " + r.body, 200, r.status);
        assertTrue("body must wrap ok:true: " + r.body,
                r.body.contains("\"ok\":true"));
        assertTrue("body must contain empty data array: " + r.body,
                r.body.contains("\"data\":[]"));
    }

    public void testListAfterCreate() throws Exception {
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":1,\"y\":1}").status);
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"B\",\"x\":2,\"y\":2}").status);
        Response r = httpGet("/d/" + DIAGRAM + "/classes");
        assertEquals(200, r.status);
        assertTrue("body must list class A: " + r.body,
                r.body.contains("\"name\":\"A\""));
        assertTrue("body must list class B: " + r.body,
                r.body.contains("\"name\":\"B\""));
    }

    public void testListOnUnknownDiagram() throws Exception {
        Response r = httpGet("/d/no-such/classes");
        assertEquals(404, r.status);
        assertTrue("body must mention DIAGRAM_NOT_FOUND: " + r.body,
                r.body.contains("DIAGRAM_NOT_FOUND"));
    }

    // -----------------------------------------------------------------
    // GET /d/{d}/classes/{c}
    // -----------------------------------------------------------------

    public void testGetExistingClass() throws Exception {
        Response post = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":50,\"y\":50}");
        assertEquals("create should be 201, got " + post.status
                + " body: " + post.body, 201, post.status);
        Response r = httpGet("/d/" + DIAGRAM + "/classes/A");
        assertEquals(200, r.status);
        assertTrue("body must expose name A: " + r.body,
                r.body.contains("\"name\":\"A\""));
        assertTrue("body must expose isAbstract flag: " + r.body,
                r.body.contains("\"isAbstract\""));
        assertTrue("body must expose stereotypeNames: " + r.body,
                r.body.contains("\"stereotypeNames\""));
        assertTrue("body must expose attributes: " + r.body,
                r.body.contains("\"attributes\""));
        assertTrue("body must expose operations: " + r.body,
                r.body.contains("\"operations\""));
    }

    public void testGetUnknownClass() throws Exception {
        Response r = httpGet("/d/" + DIAGRAM + "/classes/NoSuch");
        assertEquals(404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND: " + r.body,
                r.body.contains("CLASS_NOT_FOUND"));
    }

    public void testGetOnUnknownDiagram() throws Exception {
        Response r = httpGet("/d/no-such/classes/X");
        assertEquals(404, r.status);
        assertTrue("body must mention DIAGRAM_NOT_FOUND: " + r.body,
                r.body.contains("DIAGRAM_NOT_FOUND"));
    }

    // -----------------------------------------------------------------
    // POST /d/{d}/classes
    // -----------------------------------------------------------------

    public void testCreateHappyPath() throws Exception {
        Response r = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":100,\"y\":100}");
        assertEquals(201, r.status);
        assertTrue("body must wrap ok:true: " + r.body,
                r.body.contains("\"ok\":true"));
        assertTrue("body must mention name A: " + r.body,
                r.body.contains("\"name\":\"A\""));
    }

    public void testCreateWithAllFields() throws Exception {
        Response r = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"B\",\"x\":200,\"y\":150,"
                + "\"stereotype\":\"entity\",\"isAbstract\":true}");
        assertEquals("create should be 201, got " + r.status
                + " body: " + r.body, 201, r.status);
        Response get = httpGet("/d/" + DIAGRAM + "/classes/B");
        assertEquals(200, get.status);
        assertTrue("body must mark B abstract: " + get.body,
                get.body.contains("\"isAbstract\":true"));
        assertTrue("body must list 'entity' stereotype: " + get.body,
                get.body.contains("\"stereotypeNames\":[\"entity\"]")
                || get.body.contains(
                        "\"stereotypeNames\":[\"entity\","));
    }

    public void testCreateDefaultXY() throws Exception {
        Response r = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"C\"}");
        assertEquals("create should be 201, got " + r.status
                + " body: " + r.body, 201, r.status);
        // GET does not expose x/y, so verify default placement by
        // looking at the Fig directly. CreateClassHandler defaults
        // x/y to 100 when missing, and ClassDiagramService calls
        // placeFig(d, cls, 100, 100) on the resulting node. The Fig
        // bounds track the top-left of the rendered class box; GEF
        // may add a 1-2 pixel slop, hence the tolerance.
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().findByName(diagram, "C");
        assertNotNull("class C should be present on the diagram", cls);
        Fig fig = diagram.presentationFor(cls);
        assertNotNull("Fig for C should exist", fig);
        java.awt.Rectangle b = fig.getBounds();
        assertTrue("default x must be near 100, got " + b.x,
                Math.abs(b.x - 100) <= 2);
        assertTrue("default y must be near 100, got " + b.y,
                Math.abs(b.y - 100) <= 2);
    }

    public void testCreateEmptyName() throws Exception {
        Response r = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"\",\"x\":1,\"y\":1}");
        assertEquals("status should be 400, got " + r.status
                + " body: " + r.body, 400, r.status);
        assertTrue("body must mention INVALID_NAME: " + r.body,
                r.body.contains("INVALID_NAME"));
    }

    public void testCreateMissingName() throws Exception {
        Response r = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"x\":1,\"y\":1}");
        assertEquals("status should be 400, got " + r.status
                + " body: " + r.body, 400, r.status);
        assertTrue("body must mention INVALID_NAME: " + r.body,
                r.body.contains("INVALID_NAME"));
    }

    public void testCreateDuplicate() throws Exception {
        Response first = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"Dup\",\"x\":1,\"y\":1}");
        assertEquals("first POST should be 201, got " + first.status
                + " body: " + first.body, 201, first.status);
        Response second = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"Dup\",\"x\":2,\"y\":2}");
        assertEquals("status should be 409, got " + second.status
                + " body: " + second.body, 409, second.status);
        assertTrue("body must mention DUPLICATE_CLASS: " + second.body,
                second.body.contains("DUPLICATE_CLASS"));
    }

    public void testCreateOnUnknownDiagram() throws Exception {
        Response r = httpPost("/d/no-such/classes",
                "{\"name\":\"A\",\"x\":1,\"y\":1}");
        assertEquals(404, r.status);
        assertTrue("body must mention DIAGRAM_NOT_FOUND: " + r.body,
                r.body.contains("DIAGRAM_NOT_FOUND"));
    }

    public void testCreateMalformedJSON() throws Exception {
        Response r = httpPost("/d/" + DIAGRAM + "/classes",
                "{not valid json");
        assertEquals("status should be 400, got " + r.status
                + " body: " + r.body, 400, r.status);
        assertTrue("body must mention INVALID_BODY: " + r.body,
                r.body.contains("INVALID_BODY"));
    }

    // -----------------------------------------------------------------
    // PUT /d/{d}/classes/{c}
    // -----------------------------------------------------------------

    public void testRenameHappyPath() throws Exception {
        Response post = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":1,\"y\":1}");
        assertEquals("create should be 201, got " + post.status,
                201, post.status);
        Response put = httpPut("/d/" + DIAGRAM + "/classes/A",
                "{\"newName\":\"A2\"}");
        assertEquals("rename should be 200, got " + put.status
                + " body: " + put.body, 200, put.status);
        assertTrue("PUT body must report new name: " + put.body,
                put.body.contains("\"name\":\"A2\""));
        Response get = httpGet("/d/" + DIAGRAM + "/classes/A2");
        assertEquals("GET on renamed class should be 200, got "
                + get.status + " body: " + get.body, 200, get.status);
        assertTrue("GET body must show A2: " + get.body,
                get.body.contains("\"name\":\"A2\""));
    }

    public void testUpdatePositionXY() throws Exception {
        Response post = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":1,\"y\":1}");
        assertEquals("create should be 201, got " + post.status,
                201, post.status);
        Response put = httpPut("/d/" + DIAGRAM + "/classes/A",
                "{\"x\":300,\"y\":400}");
        assertEquals("position update should be 200, got " + put.status
                + " body: " + put.body, 200, put.status);
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().findByName(diagram, "A");
        assertNotNull("class A should be present on the diagram", cls);
        Fig fig = diagram.presentationFor(cls);
        assertNotNull("Fig for A should exist", fig);
        // The position is verified via the Fig's getBounds()
        // (Rectangle x,y = top-left of the rendered class box). GEF's
        // FigGroup keeps its outer _x in sync with the union of its
        // children's bounds, so once the class has finished laying
        // out, getBounds() reflects the new position. We allow a
        // small tolerance for sub-pixel layout shifts.
        java.awt.Rectangle b = fig.getBounds();
        assertTrue("Fig bounds x must be near 300, got " + b.x,
                Math.abs(b.x - 300) <= 2);
        assertTrue("Fig bounds y must be near 400, got " + b.y,
                Math.abs(b.y - 400) <= 2);
    }

    public void testUpdateAbstractToggle() throws Exception {
        Response post = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":1,\"y\":1,"
                + "\"isAbstract\":false}");
        assertEquals("create should be 201, got " + post.status,
                201, post.status);
        Response put = httpPut("/d/" + DIAGRAM + "/classes/A",
                "{\"isAbstract\":true}");
        assertEquals("abstract toggle should be 200, got " + put.status
                + " body: " + put.body, 200, put.status);
        Response get = httpGet("/d/" + DIAGRAM + "/classes/A");
        assertEquals(200, get.status);
        assertTrue("GET body must mark A abstract: " + get.body,
                get.body.contains("\"isAbstract\":true"));
    }

    public void testUpdateStereotypeAdd() throws Exception {
        Response post = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":1,\"y\":1}");
        assertEquals("create should be 201, got " + post.status,
                201, post.status);
        Response put = httpPut("/d/" + DIAGRAM + "/classes/A",
                "{\"stereotype\":\"entity\"}");
        assertEquals("stereotype add should be 200, got " + put.status
                + " body: " + put.body, 200, put.status);
        Response get = httpGet("/d/" + DIAGRAM + "/classes/A");
        assertEquals(200, get.status);
        assertTrue("GET body must list 'entity' stereotype: " + get.body,
                get.body.contains("\"stereotypeNames\":[\"entity\"]")
                || get.body.contains(
                        "\"stereotypeNames\":[\"entity\","));
    }

    public void testRenameToExisting() throws Exception {
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":1,\"y\":1}").status);
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"B\",\"x\":2,\"y\":2}").status);
        Response put = httpPut("/d/" + DIAGRAM + "/classes/A",
                "{\"newName\":\"B\"}");
        assertEquals("rename to existing should be 409, got " + put.status
                + " body: " + put.body, 409, put.status);
        assertTrue("body must mention DUPLICATE_CLASS: " + put.body,
                put.body.contains("DUPLICATE_CLASS"));
    }

    public void testRenameToSameName() throws Exception {
        Response post = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":1,\"y\":1}");
        assertEquals("create should be 201, got " + post.status,
                201, post.status);
        Response put = httpPut("/d/" + DIAGRAM + "/classes/A",
                "{\"newName\":\"A\"}");
        assertEquals("no-op rename should be 200, got " + put.status
                + " body: " + put.body, 200, put.status);
        assertTrue("PUT body must still report name A: " + put.body,
                put.body.contains("\"name\":\"A\""));
        Response get = httpGet("/d/" + DIAGRAM + "/classes/A");
        assertEquals("A should still be retrievable after no-op rename",
                200, get.status);
    }

    public void testUpdateUnknownClass() throws Exception {
        Response r = httpPut("/d/" + DIAGRAM + "/classes/NoSuch",
                "{\"newName\":\"X\"}");
        assertEquals(404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND: " + r.body,
                r.body.contains("CLASS_NOT_FOUND"));
    }

    public void testPartialUpdatePreservesOtherFields() throws Exception {
        // Create A with a stereotype at (50, 50).
        Response post = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":50,\"y\":50,"
                + "\"stereotype\":\"ent\"}");
        assertEquals("create should be 201, got " + post.status,
                201, post.status);
        // Partial update: only position. Both x AND y are required by
        // UpdateClassHandler to move the Fig (the service contract is
        // a complete coordinate pair), so this PUT also exercises
        // the partial-update invariant on a real change.
        Response put = httpPut("/d/" + DIAGRAM + "/classes/A",
                "{\"x\":999,\"y\":999}");
        assertEquals("partial update should be 200, got " + put.status
                + " body: " + put.body, 200, put.status);
        // Other fields must be preserved: GET still shows the
        // 'ent' stereotype and the class is still named A.
        Response get = httpGet("/d/" + DIAGRAM + "/classes/A");
        assertEquals(200, get.status);
        assertTrue("stereotype 'ent' must be preserved: " + get.body,
                get.body.contains("\"stereotypeNames\":[\"ent\"]")
                || get.body.contains(
                        "\"stereotypeNames\":[\"ent\","));
        assertTrue("class name A must be preserved: " + get.body,
                get.body.contains("\"name\":\"A\""));
        // The class element must still be findable on the diagram
        // (so we know the PUT did not detach or destroy it).
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().findByName(diagram, "A");
        assertNotNull("class A should still be present", cls);
        Fig fig = diagram.presentationFor(cls);
        assertNotNull("Fig for A should still exist", fig);
        // Position-update coverage lives in testUpdatePositionXY
        // above; here we just assert the Fig still has a non-empty
        // bounding rectangle (sanity check on the model state).
        assertTrue("Fig bounds must be non-empty: " + fig.getBounds(),
                fig.getBounds().width > 0
                && fig.getBounds().height > 0);
    }

    // -----------------------------------------------------------------
    // DELETE /d/{d}/classes/{c}
    // -----------------------------------------------------------------

    public void testDeleteHappyPath() throws Exception {
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":1,\"y\":1}").status);
        Response del = httpDelete("/d/" + DIAGRAM + "/classes/A");
        assertEquals("delete should be 204, got " + del.status
                + " body: " + del.body, 204, del.status);
        Response list = httpGet("/d/" + DIAGRAM + "/classes");
        assertEquals(200, list.status);
        assertFalse("deleted class must not appear in list: "
                + list.body, list.body.contains("\"name\":\"A\""));
    }

    public void testDeleteUnknownClass() throws Exception {
        Response r = httpDelete("/d/" + DIAGRAM + "/classes/NoSuch");
        assertEquals(404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND: " + r.body,
                r.body.contains("CLASS_NOT_FOUND"));
    }

    public void testDeleteThenRecreate() throws Exception {
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":1,\"y\":1}").status);
        Response del = httpDelete("/d/" + DIAGRAM + "/classes/A");
        assertEquals("delete should be 204, got " + del.status,
                204, del.status);
        Response recreate = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"A\",\"x\":2,\"y\":2}");
        assertEquals("recreate should be 201 (no leftover conflict),"
                + " got " + recreate.status + " body: " + recreate.body,
                201, recreate.status);
    }

    // -----------------------------------------------------------------
    // POST /d/{d}/interfaces
    // -----------------------------------------------------------------

    public void testCreateInterfaceHappyPath() throws Exception {
        Response r = httpPost("/d/" + DIAGRAM + "/interfaces",
                "{\"name\":\"IFoo\",\"x\":50,\"y\":50}");
        assertEquals("interface create should be 201, got " + r.status
                + " body: " + r.body, 201, r.status);
        assertTrue("body must mention IFoo: " + r.body,
                r.body.contains("IFoo"));
    }

    public void testCreateInterfaceDuplicate() throws Exception {
        Response first = httpPost("/d/" + DIAGRAM + "/interfaces",
                "{\"name\":\"IDup\",\"x\":1,\"y\":1}");
        assertEquals("first interface POST should be 201, got "
                + first.status + " body: " + first.body,
                201, first.status);
        Response second = httpPost("/d/" + DIAGRAM + "/interfaces",
                "{\"name\":\"IDup\",\"x\":2,\"y\":2}");
        assertEquals("duplicate interface should be 409, got "
                + second.status + " body: " + second.body,
                409, second.status);
        assertTrue("body must mention DUPLICATE_INTERFACE: "
                + second.body,
                second.body.contains("DUPLICATE_INTERFACE"));
    }

    public void testCreateInterfaceOnUnknownDiagram() throws Exception {
        Response r = httpPost("/d/no-such/interfaces",
                "{\"name\":\"IFoo\",\"x\":1,\"y\":1}");
        assertEquals(404, r.status);
        assertTrue("body must mention DIAGRAM_NOT_FOUND: " + r.body,
                r.body.contains("DIAGRAM_NOT_FOUND"));
    }
}
