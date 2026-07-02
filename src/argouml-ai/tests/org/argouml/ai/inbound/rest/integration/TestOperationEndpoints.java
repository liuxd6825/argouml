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

/**
 * Integration tests for the operation endpoints
 * ({@code GET/POST/DELETE /d/{d}/classes/{c}/operations[/{op}]}).
 * Each test runs against a real
 * {@link fi.iki.elonen.NanoHTTPD} instance wired to the production
 * router / dispatcher / service stack, so the assertions cover the
 * whole request path - HTTP framing, URL-decoding of path
 * segments, JSON envelope, exception mapping, service-level
 * validation, and model mutation.
 *
 * <p>The operation read shape ({@code ListOperationsHandler})
 * DOES surface visibility: each entry in the {@code data} array
 * carries {@code name}, {@code returnType}, and {@code visibility}.
 * The handler walks the model facade in parallel to
 * {@code ClassView.operations} (the latter does not encode
 * visibility) and pairs each op's name with the
 * {@code isPublic / isProtected / isPrivate / isPackage}
 * predicate result. The visibilities test (#26) therefore asserts
 * that all four expected visibility strings appear in the
 * response.</p>
 */
public class TestOperationEndpoints extends TestHttpServerIntegrationBase {

    // -----------------------------------------------------------------
    // GET /d/{d}/classes/{c}/operations
    // -----------------------------------------------------------------

    public void testListOnEmptyClass() throws Exception {
        createClass("C");
        Response r = httpGet("/d/" + DIAGRAM + "/classes/C/operations");
        assertEquals("status should be 200, got " + r.status
                + " body: " + r.body, 200, r.status);
        assertTrue("body must wrap ok:true: " + r.body,
                r.body.contains("\"ok\":true"));
        assertTrue("body must contain empty data array: " + r.body,
                r.body.contains("\"data\":[]"));
    }

    public void testListAfterAdd() throws Exception {
        createClass("C");
        Response first = httpPost("/d/" + DIAGRAM + "/classes/C/operations",
                "{\"name\":\"save\",\"returnType\":\"void\"}");
        assertEquals("first op POST should be 201, got " + first.status
                + " body: " + first.body, 201, first.status);
        Response second = httpPost("/d/" + DIAGRAM
                + "/classes/C/operations",
                "{\"name\":\"load\",\"returnType\":\"int\"}");
        assertEquals("second op POST should be 201, got " + second.status
                + " body: " + second.body, 201, second.status);
        Response r = httpGet("/d/" + DIAGRAM + "/classes/C/operations");
        assertEquals(200, r.status);
        // The read pipeline parses the "name(params):returnType"
        // string from ClassView.operations and exposes the simple
        // name as "name" - so we can match "name":"save" directly.
        assertTrue("body must list save: " + r.body,
                r.body.contains("\"name\":\"save\""));
        assertTrue("body must list load: " + r.body,
                r.body.contains("\"name\":\"load\""));
        assertTrue("body must list save's return type: " + r.body,
                r.body.contains("\"returnType\":\"void\""));
        assertTrue("body must list load's return type: " + r.body,
                r.body.contains("\"returnType\":\"int\""));
    }

    public void testListOnUnknownClass() throws Exception {
        Response r = httpGet("/d/" + DIAGRAM
                + "/classes/NoSuch/operations");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND: " + r.body,
                r.body.contains("CLASS_NOT_FOUND"));
    }

    // -----------------------------------------------------------------
    // GET /d/{d}/classes/{c}/operations/{op}
    // -----------------------------------------------------------------

    public void testGetExistingOperation() throws Exception {
        createClass("C");
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes/C/operations",
                "{\"name\":\"save\",\"returnType\":\"void\","
                + "\"visibility\":\"public\"}").status);
        Response r = httpGet("/d/" + DIAGRAM
                + "/classes/C/operations/save");
        assertEquals("status should be 200, got " + r.status
                + " body: " + r.body, 200, r.status);
        assertTrue("body must expose name: " + r.body,
                r.body.contains("\"name\":\"save\""));
        assertTrue("body must expose returnType: " + r.body,
                r.body.contains("\"returnType\":\"void\""));
    }

    public void testGetUnknownOperation() throws Exception {
        createClass("C");
        // Operation does not exist on an existing class - the GET
        // path returns the dedicated OPERATION_NOT_FOUND code.
        // The DELETE path on the same miss returns CLASS_NOT_FOUND
        // instead; that divergence is covered by
        // testDeleteUnknownOperation below.
        Response r = httpGet("/d/" + DIAGRAM
                + "/classes/C/operations/nope");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must mention OPERATION_NOT_FOUND: " + r.body,
                r.body.contains("OPERATION_NOT_FOUND"));
    }

    public void testGetOnUnknownClass() throws Exception {
        Response r = httpGet("/d/" + DIAGRAM
                + "/classes/NoSuch/operations/anything");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND: " + r.body,
                r.body.contains("CLASS_NOT_FOUND"));
    }

    public void testGetWithUrlEncodedName() throws Exception {
        createClass("C");
        // Operation name carries a space; the path segment must
        // be percent-encoded by the client. URLEncoder produces
        // form-style encoding (spaces become '+'); PathMatcher
        // URL-decodes with URLDecoder which decodes '+' as space
        // for UTF-8, so the round-trip resolves to the original
        // name on the server side.
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes/C/operations",
                "{\"name\":\"do it\",\"returnType\":\"void\"}").status);
        String encoded = URLEncoder.encode("do it", "UTF-8");
        Response r = httpGet("/d/" + DIAGRAM
                + "/classes/C/operations/" + encoded);
        assertEquals("status should be 200, got " + r.status
                + " body: " + r.body, 200, r.status);
        assertTrue("body must expose decoded name: " + r.body,
                r.body.contains("\"name\":\"do it\""));
    }

    // -----------------------------------------------------------------
    // POST /d/{d}/classes/{c}/operations
    // -----------------------------------------------------------------

    public void testAddOperationWithReturnType() throws Exception {
        createClass("C");
        Response r = httpPost("/d/" + DIAGRAM + "/classes/C/operations",
                "{\"name\":\"save\",\"returnType\":\"void\","
                + "\"visibility\":\"public\"}");
        assertEquals("status should be 201, got " + r.status
                + " body: " + r.body, 201, r.status);
        assertTrue("body must wrap ok:true: " + r.body,
                r.body.contains("\"ok\":true"));
        assertTrue("body must echo the new name: " + r.body,
                r.body.contains("\"name\":\"save\""));
        assertTrue("body must echo the returnType: " + r.body,
                r.body.contains("\"returnType\":\"void\""));
        // Round-trip via GET to confirm the operation is
        // addressable by name.
        Response get = httpGet("/d/" + DIAGRAM
                + "/classes/C/operations/save");
        assertEquals(200, get.status);
        assertTrue("GET must include name: " + get.body,
                get.body.contains("\"name\":\"save\""));
        assertTrue("GET must include returnType: " + get.body,
                get.body.contains("\"returnType\":\"void\""));
    }

    public void testAddOperationWithoutReturnType() throws Exception {
        createClass("C");
        // No returnType, no visibility - the service defaults the
        // return type to "void" (per OperationOperations.build)
        // and visibility to public (the UML default).
        Response r = httpPost("/d/" + DIAGRAM + "/classes/C/operations",
                "{\"name\":\"raw\"}");
        assertEquals("status should be 201, got " + r.status
                + " body: " + r.body, 201, r.status);
        assertTrue("body must echo the new name: " + r.body,
                r.body.contains("\"name\":\"raw\""));
    }

    public void testAddOperationAllVisibilities() throws Exception {
        createClass("C");
        String[] visibilities = {"public", "protected", "private", "package"};
        for (String v : visibilities) {
            Response r = httpPost("/d/" + DIAGRAM + "/classes/C/operations",
                    "{\"name\":\"x_" + v + "\","
                    + "\"visibility\":\"" + v + "\"}");
            assertEquals("visibility " + v + " POST should be 201, got "
                    + r.status + " body: " + r.body, 201, r.status);
        }
        // The list endpoint DOES expose visibility (unlike the
        // attribute list) - the handler walks the model facade
        // and pairs each op name with the result of the
        // isPublic / isProtected / isPrivate / isPackage
        // predicates. Verify all four visibility strings are
        // present in the list response.
        Response list = httpGet("/d/" + DIAGRAM
                + "/classes/C/operations");
        assertEquals(200, list.status);
        for (String v : visibilities) {
            assertTrue("list must contain x_" + v + ": " + list.body,
                    list.body.contains("\"name\":\"x_" + v + "\""));
            assertTrue("list must surface visibility " + v + ": "
                    + list.body,
                    list.body.contains("\"visibility\":\"" + v + "\""));
        }
    }

    public void testAddOperationEmptyName() throws Exception {
        createClass("C");
        Response r = httpPost("/d/" + DIAGRAM + "/classes/C/operations",
                "{\"name\":\"\"}");
        assertEquals("status should be 400, got " + r.status
                + " body: " + r.body, 400, r.status);
        assertTrue("body must mention INVALID_NAME: " + r.body,
                r.body.contains("INVALID_NAME"));
    }

    public void testAddOperationUnknownClass() throws Exception {
        Response r = httpPost("/d/" + DIAGRAM
                + "/classes/NoSuch/operations",
                "{\"name\":\"x\"}");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND: " + r.body,
                r.body.contains("CLASS_NOT_FOUND"));
    }

    public void testAddOperationDuplicate() throws Exception {
        createClass("C");
        Response first = httpPost("/d/" + DIAGRAM
                + "/classes/C/operations",
                "{\"name\":\"dup\"}");
        assertEquals("first POST should be 201, got " + first.status
                + " body: " + first.body, 201, first.status);
        Response second = httpPost("/d/" + DIAGRAM
                + "/classes/C/operations",
                "{\"name\":\"dup\"}");
        assertEquals("duplicate operation should be 409, got "
                + second.status + " body: " + second.body,
                409, second.status);
        assertTrue("body must mention DUPLICATE_OPERATION: "
                + second.body,
                second.body.contains("DUPLICATE_OPERATION"));
    }

    // -----------------------------------------------------------------
    // DELETE /d/{d}/classes/{c}/operations/{op}
    // -----------------------------------------------------------------

    public void testDeleteHappyPath() throws Exception {
        createClass("C");
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes/C/operations",
                "{\"name\":\"temp\",\"returnType\":\"void\"}").status);
        Response del = httpDelete("/d/" + DIAGRAM
                + "/classes/C/operations/temp");
        assertEquals("delete should be 204, got " + del.status
                + " body: " + del.body, 204, del.status);
        Response list = httpGet("/d/" + DIAGRAM + "/classes/C/operations");
        assertEquals(200, list.status);
        assertFalse("deleted operation must not appear in list: "
                + list.body, list.body.contains("\"name\":\"temp\""));
    }

    public void testDeleteUnknownOperation() throws Exception {
        createClass("C");
        // Per DeleteOperationHandler Javadoc, the delete path
        // re-uses the service's existing behaviour of throwing
        // NotFoundException(CODE_CLASS_NOT_FOUND, ...) for a
        // missing operation on an existing class. The dedicated
        // OPERATION_NOT_FOUND code exists only in the read path.
        Response r = httpDelete("/d/" + DIAGRAM
                + "/classes/C/operations/nope");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND (service"
                + " re-uses this code for missing operations): "
                + r.body, r.body.contains("CLASS_NOT_FOUND"));
    }

    public void testDeleteOnUnknownClass() throws Exception {
        Response r = httpDelete("/d/" + DIAGRAM
                + "/classes/NoSuch/operations/anything");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND: " + r.body,
                r.body.contains("CLASS_NOT_FOUND"));
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    /**
     * Create a class on the test diagram at (100, 100). Asserts
     * the POST returns 201; test methods that need a class to
     * exist on the diagram call this as the first step.
     */
    private void createClass(String name) throws Exception {
        Response r = httpPost("/d/" + DIAGRAM + "/classes",
                "{\"name\":\"" + name + "\",\"x\":100,\"y\":100}");
        assertEquals("class " + name + " create should be 201, got "
                + r.status + " body: " + r.body, 201, r.status);
    }
}
