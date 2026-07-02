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
 * Integration tests for the attribute endpoints
 * ({@code GET/POST/DELETE /d/{d}/classes/{c}/attributes[/{a}]}).
 * Each test runs against a real
 * {@link fi.iki.elonen.NanoHTTPD} instance wired to the production
 * router / dispatcher / service stack, so the assertions cover the
 * whole request path - HTTP framing, URL-decoding of path
 * segments, JSON envelope, exception mapping, service-level
 * validation, and model mutation.
 *
 * <p>The attribute read shape ({@code ListAttributesHandler}) does
 * <em>not</em> surface visibility: each entry in the
 * {@code data} array carries only {@code name} and {@code type}.
 * Visibility is part of the model state but is intentionally
 * omitted from the MVP list payload (see the handler Javadoc).
 * The visibilities test (#10) therefore only asserts that all
 * four POSTs return 201 and the four attributes show up in the
 * list - it does not try to round-trip the visibility string
 * through the read path.</p>
 */
public class TestAttributeEndpoints extends TestHttpServerIntegrationBase {

    // -----------------------------------------------------------------
    // GET /d/{d}/classes/{c}/attributes
    // -----------------------------------------------------------------

    public void testListOnEmptyClass() throws Exception {
        createClass("C");
        Response r = httpGet("/d/" + DIAGRAM + "/classes/C/attributes");
        assertEquals("status should be 200, got " + r.status
                + " body: " + r.body, 200, r.status);
        assertTrue("body must wrap ok:true: " + r.body,
                r.body.contains("\"ok\":true"));
        assertTrue("body must contain empty data array: " + r.body,
                r.body.contains("\"data\":[]"));
    }

    public void testListAfterAdd() throws Exception {
        createClass("C");
        Response first = httpPost("/d/" + DIAGRAM + "/classes/C/attributes",
                "{\"name\":\"a1\",\"type\":\"int\"}");
        assertEquals("first attr POST should be 201, got " + first.status
                + " body: " + first.body, 201, first.status);
        Response second = httpPost("/d/" + DIAGRAM
                + "/classes/C/attributes",
                "{\"name\":\"a2\",\"type\":\"String\"}");
        assertEquals("second attr POST should be 201, got " + second.status
                + " body: " + second.body, 201, second.status);
        Response r = httpGet("/d/" + DIAGRAM + "/classes/C/attributes");
        assertEquals(200, r.status);
        assertTrue("body must list a1: " + r.body,
                r.body.contains("\"name\":\"a1\""));
        assertTrue("body must list a2: " + r.body,
                r.body.contains("\"name\":\"a2\""));
        // The type is encoded into the "name:type" field of
        // ClassView.attributes and surfaced as a separate "type"
        // key in the read pipeline.
        assertTrue("body must list a1's type: " + r.body,
                r.body.contains("\"type\":\"int\""));
        assertTrue("body must list a2's type: " + r.body,
                r.body.contains("\"type\":\"String\""));
    }

    public void testListOnUnknownClass() throws Exception {
        Response r = httpGet("/d/" + DIAGRAM
                + "/classes/NoSuch/attributes");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND: " + r.body,
                r.body.contains("CLASS_NOT_FOUND"));
    }

    // -----------------------------------------------------------------
    // GET /d/{d}/classes/{c}/attributes/{a}
    // -----------------------------------------------------------------

    public void testGetExistingAttribute() throws Exception {
        createClass("C");
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes/C/attributes",
                "{\"name\":\"id\",\"type\":\"long\","
                + "\"visibility\":\"private\"}").status);
        Response r = httpGet("/d/" + DIAGRAM + "/classes/C/attributes/id");
        assertEquals("status should be 200, got " + r.status
                + " body: " + r.body, 200, r.status);
        assertTrue("body must expose name: " + r.body,
                r.body.contains("\"name\":\"id\""));
        assertTrue("body must expose type: " + r.body,
                r.body.contains("\"type\":\"long\""));
    }

    public void testGetUnknownAttribute() throws Exception {
        createClass("C");
        // Attribute does not exist on an existing class - the GET
        // path is the one that returns the dedicated
        // ATTRIBUTE_NOT_FOUND code (per GetAttributeHandler
        // Javadoc). The DELETE path on the same miss returns
        // CLASS_NOT_FOUND instead; that divergence is covered by
        // testDeleteUnknownAttribute below.
        Response r = httpGet("/d/" + DIAGRAM
                + "/classes/C/attributes/nope");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must mention ATTRIBUTE_NOT_FOUND: " + r.body,
                r.body.contains("ATTRIBUTE_NOT_FOUND"));
    }

    public void testGetOnUnknownClass() throws Exception {
        Response r = httpGet("/d/" + DIAGRAM
                + "/classes/NoSuch/attributes/anything");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND: " + r.body,
                r.body.contains("CLASS_NOT_FOUND"));
    }

    public void testGetWithUrlEncodedName() throws Exception {
        createClass("C");
        // Attribute name carries a space; the path segment must be
        // percent-encoded by the client. URLEncoder produces
        // form-style encoding (spaces become '+'); PathMatcher
        // URL-decodes with URLDecoder which decodes '+' as space
        // for UTF-8, so the round-trip resolves to the original
        // name on the server side.
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes/C/attributes",
                "{\"name\":\"a b\",\"type\":\"int\"}").status);
        String encoded = URLEncoder.encode("a b", "UTF-8");
        Response r = httpGet("/d/" + DIAGRAM
                + "/classes/C/attributes/" + encoded);
        assertEquals("status should be 200, got " + r.status
                + " body: " + r.body, 200, r.status);
        assertTrue("body must expose decoded name: " + r.body,
                r.body.contains("\"name\":\"a b\""));
    }

    // -----------------------------------------------------------------
    // POST /d/{d}/classes/{c}/attributes
    // -----------------------------------------------------------------

    public void testAddAttributeWithType() throws Exception {
        createClass("C");
        Response r = httpPost("/d/" + DIAGRAM + "/classes/C/attributes",
                "{\"name\":\"id\",\"type\":\"long\","
                + "\"visibility\":\"private\"}");
        assertEquals("status should be 201, got " + r.status
                + " body: " + r.body, 201, r.status);
        assertTrue("body must wrap ok:true: " + r.body,
                r.body.contains("\"ok\":true"));
        assertTrue("body must echo the new name: " + r.body,
                r.body.contains("\"name\":\"id\""));
        assertTrue("body must echo the type: " + r.body,
                r.body.contains("\"type\":\"long\""));
        // Round-trip via GET to confirm the model now carries the
        // attribute and it surfaces through the read pipeline.
        Response get = httpGet("/d/" + DIAGRAM
                + "/classes/C/attributes/id");
        assertEquals(200, get.status);
        assertTrue("GET must include name: " + get.body,
                get.body.contains("\"name\":\"id\""));
        assertTrue("GET must include type: " + get.body,
                get.body.contains("\"type\":\"long\""));
    }

    public void testAddAttributeWithoutType() throws Exception {
        createClass("C");
        // No type, no visibility - the service should still
        // create the attribute (rendered as "name : Untyped").
        Response r = httpPost("/d/" + DIAGRAM + "/classes/C/attributes",
                "{\"name\":\"raw\"}");
        assertEquals("status should be 201, got " + r.status
                + " body: " + r.body, 201, r.status);
        assertTrue("body must echo the new name: " + r.body,
                r.body.contains("\"name\":\"raw\""));
    }

    public void testAddAttributeAllVisibilities() throws Exception {
        createClass("C");
        String[] visibilities = {"public", "protected", "private", "package"};
        for (String v : visibilities) {
            Response r = httpPost("/d/" + DIAGRAM + "/classes/C/attributes",
                    "{\"name\":\"x_" + v + "\","
                    + "\"visibility\":\"" + v + "\"}");
            assertEquals("visibility " + v + " POST should be 201, got "
                    + r.status + " body: " + r.body, 201, r.status);
        }
        // The list endpoint does NOT expose visibility (see
        // ListAttributesHandler Javadoc); we just confirm all
        // four attributes were created and are reachable by
        // name.
        for (String v : visibilities) {
            Response get = httpGet("/d/" + DIAGRAM
                    + "/classes/C/attributes/x_" + v);
            assertEquals("GET for x_" + v + " should be 200, got "
                    + get.status + " body: " + get.body, 200, get.status);
            assertTrue("GET for x_" + v + " must expose name: " + get.body,
                    get.body.contains("\"name\":\"x_" + v + "\""));
        }
    }

    public void testAddAttributeEmptyName() throws Exception {
        createClass("C");
        Response r = httpPost("/d/" + DIAGRAM + "/classes/C/attributes",
                "{\"name\":\"\"}");
        assertEquals("status should be 400, got " + r.status
                + " body: " + r.body, 400, r.status);
        assertTrue("body must mention INVALID_NAME: " + r.body,
                r.body.contains("INVALID_NAME"));
    }

    public void testAddAttributeUnknownClass() throws Exception {
        Response r = httpPost("/d/" + DIAGRAM
                + "/classes/NoSuch/attributes",
                "{\"name\":\"x\"}");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND: " + r.body,
                r.body.contains("CLASS_NOT_FOUND"));
    }

    public void testAddAttributeDuplicate() throws Exception {
        createClass("C");
        Response first = httpPost("/d/" + DIAGRAM
                + "/classes/C/attributes",
                "{\"name\":\"dup\"}");
        assertEquals("first POST should be 201, got " + first.status
                + " body: " + first.body, 201, first.status);
        Response second = httpPost("/d/" + DIAGRAM
                + "/classes/C/attributes",
                "{\"name\":\"dup\"}");
        assertEquals("duplicate attribute should be 409, got "
                + second.status + " body: " + second.body,
                409, second.status);
        assertTrue("body must mention DUPLICATE_ATTRIBUTE: "
                + second.body,
                second.body.contains("DUPLICATE_ATTRIBUTE"));
    }

    // -----------------------------------------------------------------
    // DELETE /d/{d}/classes/{c}/attributes/{a}
    // -----------------------------------------------------------------

    public void testDeleteHappyPath() throws Exception {
        createClass("C");
        assertEquals(201, httpPost("/d/" + DIAGRAM + "/classes/C/attributes",
                "{\"name\":\"temp\",\"type\":\"int\"}").status);
        Response del = httpDelete("/d/" + DIAGRAM
                + "/classes/C/attributes/temp");
        assertEquals("delete should be 204, got " + del.status
                + " body: " + del.body, 204, del.status);
        Response list = httpGet("/d/" + DIAGRAM + "/classes/C/attributes");
        assertEquals(200, list.status);
        assertFalse("deleted attribute must not appear in list: "
                + list.body, list.body.contains("\"name\":\"temp\""));
    }

    public void testDeleteUnknownAttribute() throws Exception {
        createClass("C");
        // Per DeleteAttributeHandler Javadoc, the delete path
        // re-uses the service's existing behaviour of throwing
        // NotFoundException(CODE_CLASS_NOT_FOUND, ...) for a
        // missing attribute on an existing class. The dedicated
        // ATTRIBUTE_NOT_FOUND code exists only in the read path.
        Response r = httpDelete("/d/" + DIAGRAM
                + "/classes/C/attributes/nope");
        assertEquals("status should be 404, got " + r.status
                + " body: " + r.body, 404, r.status);
        assertTrue("body must mention CLASS_NOT_FOUND (service"
                + " re-uses this code for missing attributes): "
                + r.body, r.body.contains("CLASS_NOT_FOUND"));
    }

    public void testDeleteOnUnknownClass() throws Exception {
        Response r = httpDelete("/d/" + DIAGRAM
                + "/classes/NoSuch/attributes/anything");
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
