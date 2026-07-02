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

import java.net.URLEncoder;

import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;

/**
 * End-to-end workflow integration tests that exercise the full
 * lifecycle of a class diagram through the HTTP API: a single
 * class from POST through DELETE, a multi-class domain model
 * with associations, a generalization chain, two diagrams that
 * do not interfere, a bulk create / delete round-trip, and a
 * full request/response cycle that asserts the wire envelope on
 * every step. Each test runs against a real
 * {@link fi.iki.elonen.NanoHTTPD} instance wired to the
 * production router / dispatcher / service stack, so the
 * assertions cover the whole request path - HTTP framing, JSON
 * envelope, URL-decoding of path segments, exception mapping,
 * service-level validation, and model mutation - all the way
 * from a fresh {@link org.argouml.kernel.Project} through the
 * final read-back.
 *
 * <p>The lifecycle tests deliberately walk the model through
 * every public HTTP verb on a single class: POST, GET, POST
 * (attribute), POST (operation), PUT (rename), GET (verify),
 * DELETE, GET (404). The bulk test creates five classes and
 * deletes three to confirm the list handler re-derives from
 * the live graph model rather than a cache. The full-cycle
 * test inverts the per-handler test pattern: instead of
 * asserting one path at a time, it walks a representative
 * slice of the API and asserts the envelope shape on every
 * response, so any future handler that returns a 2xx without
 * the {@code ok:true} flag, or a 4xx without the
 * {@code ok:false,"error":{...}} shape, will fail this test
 * before it ships.</p>
 */
public class TestEndToEndWorkflow extends TestHttpServerIntegrationBase {

    // -----------------------------------------------------------------
    // 1. Full class lifecycle: POST -> GET -> POST attr -> POST op
    //    -> PUT rename -> GET verify -> DELETE -> GET 404
    // -----------------------------------------------------------------

    public void testFullClassLifecycle() throws Exception {
        // POST: create class A. Call the helper once and reuse
        // the response - calling it twice would fail the second
        // time with 409 (DUPLICATE_CLASS), which is correct
        // production behaviour but would mask any first-call
        // failure as a confusing second-call failure.
        Response createA = postCreate("A", 1, 1);
        assertEquals("create should be 201, got " + createA.status
                + " body: " + createA.body, 201, createA.status);
        // GET: confirm the create round-tripped.
        Response get = httpGet("/d/" + DIAGRAM + "/classes/A");
        assertEquals("GET should be 200, got " + get.status
                + " body: " + get.body, 200, get.status);
        assertTrue("GET must show class A: " + get.body,
                get.body.contains("\"name\":\"A\""));
        // POST attribute.
        Response addAttr = postAttr("A", "id", "long");
        assertEquals("add attribute should be 201, got " + addAttr.status
                + " body: " + addAttr.body, 201, addAttr.status);
        // POST operation.
        Response addOp = postOp("A", "save", "void");
        assertEquals("add operation should be 201, got " + addOp.status
                + " body: " + addOp.body, 201, addOp.status);
        // PUT rename.
        Response put = httpPut("/d/" + DIAGRAM + "/classes/A",
                "{\"newName\":\"A2\"}");
        assertEquals("rename should be 200, got " + put.status
                + " body: " + put.body, 200, put.status);
        assertTrue("PUT must show new name A2: " + put.body,
                put.body.contains("\"name\":\"A2\""));
        // GET verify the rename + the attribute + the operation
        // are all still there. We rely on substring matches
        // (not JSON parse) to keep the test surface small and
        // consistent with the rest of the suite.
        Response afterRename = httpGet("/d/" + DIAGRAM + "/classes/A2");
        assertEquals("GET after rename should be 200, got "
                + afterRename.status + " body: " + afterRename.body,
                200, afterRename.status);
        assertTrue("after rename class must show A2: " + afterRename.body,
                afterRename.body.contains("\"name\":\"A2\""));
        // DELETE.
        Response del = httpDelete("/d/" + DIAGRAM + "/classes/A2");
        assertEquals("delete should be 204, got " + del.status
                + " body: " + del.body, 204, del.status);
        // GET 404: the class is gone.
        Response get404 = httpGet("/d/" + DIAGRAM + "/classes/A2");
        assertEquals("GET on deleted class should be 404, got "
                + get404.status + " body: " + get404.body, 404,
                get404.status);
        assertTrue("404 body must mention CLASS_NOT_FOUND: "
                + get404.body, get404.body.contains("CLASS_NOT_FOUND"));
    }

    // -----------------------------------------------------------------
    // 2. Domain model with associations: 2 classes + 1 assoc with
    //    multiplicity + label; snapshot reflects it; DELETE assoc;
    //    GET assoc list is empty.
    // -----------------------------------------------------------------

    public void testDomainModelWithAssociations() throws Exception {
        // Two-class domain: Customer -- Order (1 to 0..*).
        assertEquals(201, postCreate("Customer", 50, 50).status);
        assertEquals(201, postCreate("Order", 250, 50).status);
        Response post = httpPost("/d/" + DIAGRAM + "/associations",
                "{\"classA\":\"Customer\",\"classB\":\"Order\","
                + "\"multA\":\"1\",\"multB\":\"0..*\","
                + "\"labelA\":\"places\",\"labelB\":\"placedBy\"}");
        assertEquals("assoc POST should be 201, got " + post.status
                + " body: " + post.body, 201, post.status);
        // Snapshot must reflect both classes and the association.
        Response snap = httpGet("/project/diagrams/" + DIAGRAM + "/snapshot");
        assertEquals("snapshot should be 200, got " + snap.status
                + " body: " + snap.body, 200, snap.status);
        assertTrue("snapshot must list Customer: " + snap.body,
                snap.body.contains("\"name\":\"Customer\""));
        assertTrue("snapshot must list Order: " + snap.body,
                snap.body.contains("\"name\":\"Order\""));
        assertTrue("snapshot must list the association: " + snap.body,
                snap.body.contains("\"a\":\"Customer\""));
        assertTrue("snapshot must list the association endpoint: "
                + snap.body, snap.body.contains("\"b\":\"Order\""));
        // DELETE the association via the relationship endpoint.
        String id = URLEncoder.encode("Customer|Order", "UTF-8");
        Response del = httpDelete("/d/" + DIAGRAM
                + "/relationships/" + id + "?type=association");
        assertEquals("delete association should be 204, got " + del.status
                + " body: " + del.body, 204, del.status);
        // The list endpoint must be empty now.
        Response after = httpGet("/d/" + DIAGRAM + "/associations");
        assertEquals("list after delete should be 200, got " + after.status
                + " body: " + after.body, 200, after.status);
        assertTrue("deleted assoc must not appear: " + after.body,
                !after.body.contains("\"a\":\"Customer\""));
        // Snapshot must also reflect the removal: the association
        // entry that carried "a":"Customer" is gone, but the two
        // classes remain.
        Response snap2 = httpGet("/project/diagrams/" + DIAGRAM + "/snapshot");
        assertTrue("snapshot after delete must still list Customer: "
                + snap2.body, snap2.body.contains("\"name\":\"Customer\""));
        assertTrue("snapshot after delete must still list Order: "
                + snap2.body, snap2.body.contains("\"name\":\"Order\""));
    }

    // -----------------------------------------------------------------
    // 3. Generalization chain: 3 classes A, B, C with A->B, B->C.
    //    GET generalizations must report 2 entries.
    // -----------------------------------------------------------------

    public void testGeneralizationChain() throws Exception {
        assertEquals(201, postCreate("A", 1, 1).status);
        assertEquals(201, postCreate("B", 1, 1).status);
        assertEquals(201, postCreate("C", 1, 1).status);
        // A inherits from B.
        Response genAB = httpPost("/d/" + DIAGRAM + "/generalizations",
                "{\"subclass\":\"A\",\"superclass\":\"B\"}");
        assertEquals("gen A->B should be 201, got " + genAB.status
                + " body: " + genAB.body, 201, genAB.status);
        // B inherits from C.
        Response genBC = httpPost("/d/" + DIAGRAM + "/generalizations",
                "{\"subclass\":\"B\",\"superclass\":\"C\"}");
        assertEquals("gen B->C should be 201, got " + genBC.status
                + " body: " + genBC.body, 201, genBC.status);
        // The list endpoint must surface both edges.
        Response list = httpGet("/d/" + DIAGRAM + "/generalizations");
        assertEquals("list should be 200, got " + list.status
                + " body: " + list.body, 200, list.status);
        assertTrue("list must include A->B: " + list.body,
                list.body.contains("\"child\":\"A\"")
                && list.body.contains("\"parent\":\"B\""));
        assertTrue("list must include B->C: " + list.body,
                list.body.contains("\"child\":\"B\"")
                && list.body.contains("\"parent\":\"C\""));
        // Count the edges. The list handler emits one entry per
        // edge with shape {"child":"X","parent":"Y"} (no id key
        // is exposed on the read path - see
        // ListGeneralizationsHandler Javadoc). The two POSTs above
        // each add one edge, so the response must carry exactly
        // two child/parent pairs.
        int childACount = countOccurrences(list.body, "\"child\":\"A\"");
        int childBCount = countOccurrences(list.body, "\"child\":\"B\"");
        int parentBCount = countOccurrences(list.body, "\"parent\":\"B\"");
        int parentCCount = countOccurrences(list.body, "\"parent\":\"C\"");
        assertEquals("expected exactly one A->B entry, got body: "
                + list.body, 1, childACount);
        assertEquals("expected exactly one B->C entry, got body: "
                + list.body, 1, childBCount);
        assertEquals("expected exactly one parent=B entry, got body: "
                + list.body, 1, parentBCount);
        assertEquals("expected exactly one parent=C entry, got body: "
                + list.body, 1, parentCCount);
    }

    // -----------------------------------------------------------------
    // 4. Multiple diagrams: 2 diagrams side-by-side, CRUD on one
    //    must not affect the other. The base setUp() registers one
    //    diagram named "Test"; this test adds a second one named
    //    "Other" directly.
    // -----------------------------------------------------------------

    public void testMultipleDiagramsIsolation() throws Exception {
        // Add a second diagram. The base setUp() created "Test";
        // we add "Other" so the project carries exactly two.
        project.addDiagram(
                new UMLClassDiagram("Other", project.getModel()));
        // Confirm the project now sees two diagrams.
        Response list = httpGet("/project/diagrams");
        assertEquals("diagrams list should be 200, got " + list.status
                + " body: " + list.body, 200, list.status);
        assertTrue("list must include Test: " + list.body,
                list.body.contains("\"name\":\"Test\""));
        assertTrue("list must include Other: " + list.body,
                list.body.contains("\"name\":\"Other\""));
        // CRUD on Test must not pollute Other.
        assertEquals(201, postCreate("X", 1, 1).status);
        // CRUD on Other must not pollute Test.
        assertEquals(201, httpPost("/d/Other/classes",
                "{\"name\":\"Y\",\"x\":1,\"y\":1}").status);
        // Test's class list has only X.
        Response tList = httpGet("/d/Test/classes");
        assertEquals(200, tList.status);
        assertTrue("Test must list X: " + tList.body,
                tList.body.contains("\"name\":\"X\""));
        assertFalse("Test must NOT list Y: " + tList.body,
                tList.body.contains("\"name\":\"Y\""));
        // Other's class list has only Y.
        Response oList = httpGet("/d/Other/classes");
        assertEquals(200, oList.status);
        assertTrue("Other must list Y: " + oList.body,
                oList.body.contains("\"name\":\"Y\""));
        assertFalse("Other must NOT list X: " + oList.body,
                oList.body.contains("\"name\":\"X\""));
        // Delete on one must not affect the other.
        assertEquals(204, httpDelete("/d/Test/classes/X").status);
        assertEquals(200, httpGet("/d/Other/classes/Y").status);
        // After delete, Test's list is empty.
        Response tAfter = httpGet("/d/Test/classes");
        assertEquals(200, tAfter.status);
        assertFalse("Test must be empty after delete: " + tAfter.body,
                tAfter.body.contains("\"name\":\"X\""));
        // Other is untouched.
        Response oAfter = httpGet("/d/Other/classes");
        assertEquals(200, oAfter.status);
        assertTrue("Other must still list Y: " + oAfter.body,
                oAfter.body.contains("\"name\":\"Y\""));
    }

    // -----------------------------------------------------------------
    // 5. Bulk project state: create 5 classes, delete 3 of them,
    //    confirm the list endpoint reports exactly the 2 that
    //    remain. Exercises the live-graph-model re-derivation
    //    path on the list handler under repeated mutation.
    //    (Spec: this replaced a "testUndoRedoNotViaHTTP" that
    //    was not meaningful over the HTTP surface.)
    // -----------------------------------------------------------------

    public void testProjectStateAfterBulkOperations() throws Exception {
        // Create 5 classes. Each POST is its own request; calling
        // postCreate once per name keeps the failure messages
        // aligned with the call site.
        for (String n : new String[]{"A", "B", "C", "D", "E"}) {
            Response r = postCreate(n, 1, 1);
            assertEquals("create " + n + " should be 201, got "
                    + r.status + " body: " + r.body, 201, r.status);
        }
        // List has all 5.
        Response all5 = httpGet("/d/" + DIAGRAM + "/classes");
        assertEquals(200, all5.status);
        for (String n : new String[]{"A", "B", "C", "D", "E"}) {
            assertTrue("list must include " + n + ": " + all5.body,
                    all5.body.contains("\"name\":\"" + n + "\""));
        }
        // Delete 3 of them (A, C, E).
        for (String n : new String[]{"A", "C", "E"}) {
            Response del = httpDelete("/d/" + DIAGRAM + "/classes/" + n);
            assertEquals("delete " + n + " should be 204, got "
                    + del.status + " body: " + del.body, 204, del.status);
        }
        // List now has exactly 2: B and D.
        Response after = httpGet("/d/" + DIAGRAM + "/classes");
        assertEquals(200, after.status);
        for (String n : new String[]{"B", "D"}) {
            assertTrue("after bulk delete, list must include " + n + ": "
                    + after.body, after.body.contains("\"name\":\""
                    + n + "\""));
        }
        for (String n : new String[]{"A", "C", "E"}) {
            assertFalse("after bulk delete, list must NOT include " + n
                    + ": " + after.body,
                    after.body.contains("\"name\":\"" + n + "\""));
        }
    }

    // -----------------------------------------------------------------
    // 6. Full request/response envelope cycle: a representative
    //    slice of the API; every 2xx body must carry the
    //    {"ok":true,...} flag, every 4xx body must carry the
    //    {"ok":false,"error":{"code":...,"message":...}} shape.
    //    This guards the wire contract for any future handler
    //    that forgets to wrap a response.
    // -----------------------------------------------------------------

    public void testFullRequestResponseCycle() throws Exception {
        // 2xx: GET /health.
        assertOk(httpGet("/health"));
        // 2xx: GET /project/diagrams.
        assertOk(httpGet("/project/diagrams"));
        // 2xx: GET snapshot on an empty diagram.
        assertOk(httpGet("/project/diagrams/" + DIAGRAM + "/snapshot"));
        // 2xx: POST a class.
        assertEquals(201, postCreate("E", 1, 1).status);
        // 2xx: GET the class we just created.
        assertOk(httpGet("/d/" + DIAGRAM + "/classes/E"));
        // 2xx: PUT a rename.
        Response put = httpPut("/d/" + DIAGRAM + "/classes/E",
                "{\"newName\":\"E2\"}");
        assertOk(put);
        // 2xx: GET the renamed class.
        assertOk(httpGet("/d/" + DIAGRAM + "/classes/E2"));
        // 2xx: POST an attribute.
        assertEquals(201, postAttr("E2", "id", "long").status);
        // 2xx: GET the attribute.
        assertOk(httpGet("/d/" + DIAGRAM + "/classes/E2/attributes/id"));
        // 2xx: POST an operation.
        assertEquals(201, postOp("E2", "save", "void").status);
        // 2xx: GET the operation.
        assertOk(httpGet("/d/" + DIAGRAM + "/classes/E2/operations/save"));
        // 2xx: GET the (empty) associations list.
        assertOk(httpGet("/d/" + DIAGRAM + "/associations"));
        // 4xx: GET a class that does not exist.
        assertErr(httpGet("/d/" + DIAGRAM + "/classes/Nope"), 404);
        // 4xx: GET a diagram that does not exist.
        assertErr(httpGet("/d/no-such/classes"), 404);
        // 4xx: GET an unknown route.
        assertErr(httpGet("/no-such-route"), 404);
        // 4xx: POST malformed JSON to a real endpoint.
        assertErr(httpPost("/d/" + DIAGRAM + "/classes",
                "{not valid json"), 400);
        // 4xx: PUT to a class that does not exist.
        assertErr(httpPut("/d/" + DIAGRAM + "/classes/Nope",
                "{\"newName\":\"X\"}"), 404);
        // 4xx: DELETE a class that does not exist.
        assertErr(httpDelete("/d/" + DIAGRAM + "/classes/Nope"), 404);
        // 2xx: DELETE the class we created.
        assertEquals(204, httpDelete("/d/" + DIAGRAM + "/classes/E2").status);
        // Final 2xx sanity check: snapshot is still 200 after all
        // the mutations, and still wraps ok:true.
        assertOk(httpGet("/project/diagrams/" + DIAGRAM + "/snapshot"));
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    /**
     * Create a class on the test diagram at (x, y). Returns the
     * response so callers can chain assertions on the body.
     */
    private Response postCreate(String name, int x, int y) throws Exception {
        return httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"" + name + "\",\"x\":" + x + ",\"y\":" + y
                + "}");
    }

    /**
     * POST an attribute on {@code cls}. Returns the response so
     * callers can chain assertions on the body.
     */
    private Response postAttr(String cls, String name, String type)
            throws Exception {
        return httpPost("/d/" + DIAGRAM + "/classes/" + cls + "/attributes",
                "{\"name\":\"" + name + "\",\"type\":\"" + type + "\"}");
    }

    /**
     * POST an operation on {@code cls}. Returns the response so
     * callers can chain assertions on the body.
     */
    private Response postOp(String cls, String name, String returnType)
            throws Exception {
        return httpPost("/d/" + DIAGRAM + "/classes/" + cls + "/operations",
                "{\"name\":\"" + name + "\",\"returnType\":\"" + returnType
                + "\"}");
    }

    /**
     * Assert that {@code r} is a 2xx response and that the body
     * carries the standard {@code "ok":true} envelope. We assert
     * the body content (not the status alone) because the
     * dispatcher can return a 2xx with a malformed body if a
     * handler forgets to wrap - that would be a silent contract
     * break, and this test exists to catch it.
     */
    private void assertOk(Response r) {
        assertTrue("expected 2xx, got " + r.status + " body=" + r.body,
                r.status >= 200 && r.status < 300);
        assertTrue("ok:true missing in body: " + r.body,
                r.body.contains("\"ok\":true"));
    }

    /**
     * Assert that {@code r} is an error response with the
     * standard {@code {"ok":false,"error":{"code":...}} shape.
     * The {@code expectedStatus} is exact (not a range) because
     * 4xx vs 5xx conveys different things to the client (client
     * error vs server bug), and we want the test to fail
     * loudly if a handler escalates or downgrades a status.
     */
    private void assertErr(Response r, int expectedStatus) {
        assertEquals("expected status " + expectedStatus + ", got "
                + r.status + " body=" + r.body, expectedStatus, r.status);
        assertTrue("ok:false missing in body: " + r.body,
                r.body.contains("\"ok\":false"));
        assertTrue("error.code missing in body: " + r.body,
                r.body.contains("\"code\""));
    }

    /**
     * Count non-overlapping occurrences of {@code needle} in
     * {@code haystack}. Used by the generalization-chain test to
     * assert the read path returned the right number of edges
     * (the list handler emits one entry per edge, so a count of
     * 1 for each expected child/parent pair is exactly what we
     * want - any other number means either an edge was dropped
     * on the round-trip or the same edge got counted twice).
     */
    private static int countOccurrences(String haystack, String needle) {
        if (needle == null || needle.length() == 0) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
