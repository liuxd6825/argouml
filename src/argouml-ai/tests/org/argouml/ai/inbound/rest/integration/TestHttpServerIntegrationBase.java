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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import junit.framework.TestCase;

import fi.iki.elonen.NanoHTTPD;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.classdiagram.handlers.CreateClassHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.CreateInterfaceHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.DeleteClassHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.GetClassHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.ListClassesHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.UpdateClassHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.attribute.AddAttributeHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.attribute.DeleteAttributeHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.attribute.GetAttributeHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.attribute.ListAttributesHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.operation.AddOperationHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.operation.DeleteOperationHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.operation.GetOperationHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.operation.ListOperationsHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.AddAssociationHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.AddDependencyHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.AddGeneralizationHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.DeleteRelationshipHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.ListAssociationsHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.ListDependenciesHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.ListGeneralizationsHandler;
import org.argouml.ai.inbound.rest.common.Dispatcher;
import org.argouml.ai.inbound.rest.common.Method;
import org.argouml.ai.inbound.rest.common.Router;
import org.argouml.ai.inbound.rest.common.handlers.common.GetDiagramHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.HealthHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.ListDiagramsHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.SnapshotHandler;
import org.argouml.ai.infrastructure.http.NanoHttpAdapter;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;

/**
 * Abstract base for HTTP integration tests. Spins up a real
 * {@link NanoHTTPD} bound to {@code 127.0.0.1:0} (OS-assigned
 * ephemeral port), wires it to a fully populated {@link Router} and
 * {@link Dispatcher}, and gives subclasses plain {@code httpGet /
 * httpPost / httpPut / httpDelete} helpers that talk JSON over
 * {@link HttpURLConnection}.
 *
 * <p>Per test setUp/tearDown: a fresh {@link Project} with one
 * class diagram named {@value #DIAGRAM} is created and torn down,
 * so test methods cannot influence each other. The dispatcher
 * re-uses the same JVM singleton
 * ({@link ProjectManager#getManager()}) as production, so
 * handlers resolve diagrams through the same
 * {@link org.argouml.ai.domain.common.DiagramLocator} code path
 * that ships to users.</p>
 *
 * <p>The base class deliberately mirrors the route table built by
 * {@code org.argouml.ai.InitHttpServerSubsystem#buildRouter()} so
 * every endpoint the production server exposes is reachable in
 * the test, without depending on the subsystem's
 * {@code ServerConfigStore} file and without leaving a server
 * running between tests.</p>
 */
public abstract class TestHttpServerIntegrationBase extends TestCase {

    protected Project project;
    protected ArgoDiagram diagram;
    protected ClassDiagramService svc;

    private NanoHttpAdapter adapter;
    private int port;

    protected static final String DIAGRAM = "Test";

    static {
        try {
            new InitNotation().init();
            new InitNotationUml().init();
            new InitNotationJava().init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getModel();
        diagram = new UMLClassDiagram(DIAGRAM, ns);
        project.addDiagram(diagram);
        svc = new ClassDiagramService();
        port = startServer();
    }

    @Override
    protected void tearDown() throws Exception {
        if (adapter != null) {
            try {
                adapter.stop();
            } catch (RuntimeException ignored) {
            }
            adapter = null;
        }
        if (project != null) {
            try {
                ProjectManager.getManager().removeProject(project);
            } catch (RuntimeException ignored) {
            }
            project = null;
        }
        super.tearDown();
    }

    private int startServer() {
        Router router = new Router();
        router.add(Method.GET,  "/health",                          new HealthHandler());
        router.add(Method.GET,  "/project/diagrams",                new ListDiagramsHandler());
        router.add(Method.GET,  "/project/diagrams/{d}",            new GetDiagramHandler());
        router.add(Method.GET,  "/project/diagrams/{d}/snapshot",   new SnapshotHandler());

        router.add(Method.GET,    "/d/{d}/classes",                 new ListClassesHandler(svc));
        router.add(Method.GET,    "/d/{d}/classes/{c}",             new GetClassHandler(svc));
        router.add(Method.POST,   "/d/{d}/classes",                 new CreateClassHandler(svc));
        router.add(Method.PUT,    "/d/{d}/classes/{c}",             new UpdateClassHandler(svc));
        router.add(Method.DELETE, "/d/{d}/classes/{c}",             new DeleteClassHandler(svc));
        router.add(Method.POST,   "/d/{d}/interfaces",              new CreateInterfaceHandler(svc));

        router.add(Method.POST,   "/d/{d}/classes/{c}/attributes",  new AddAttributeHandler(svc));
        router.add(Method.GET,    "/d/{d}/classes/{c}/attributes",  new ListAttributesHandler(svc));
        router.add(Method.GET,    "/d/{d}/classes/{c}/attributes/{a}", new GetAttributeHandler(svc));
        router.add(Method.DELETE, "/d/{d}/classes/{c}/attributes/{a}", new DeleteAttributeHandler(svc));

        router.add(Method.POST,   "/d/{d}/classes/{c}/operations",  new AddOperationHandler(svc));
        router.add(Method.GET,    "/d/{d}/classes/{c}/operations",  new ListOperationsHandler(svc));
        router.add(Method.GET,    "/d/{d}/classes/{c}/operations/{op}", new GetOperationHandler(svc));
        router.add(Method.DELETE, "/d/{d}/classes/{c}/operations/{op}", new DeleteOperationHandler(svc));

        router.add(Method.GET,    "/d/{d}/associations",            new ListAssociationsHandler(svc));
        router.add(Method.POST,   "/d/{d}/associations",            new AddAssociationHandler(svc));
        router.add(Method.GET,    "/d/{d}/generalizations",         new ListGeneralizationsHandler(svc));
        router.add(Method.POST,   "/d/{d}/generalizations",         new AddGeneralizationHandler(svc));
        router.add(Method.GET,    "/d/{d}/dependencies",            new ListDependenciesHandler(svc));
        router.add(Method.POST,   "/d/{d}/dependencies",            new AddDependencyHandler(svc));
        router.add(Method.DELETE, "/d/{d}/relationships/{id}",       new DeleteRelationshipHandler(svc));

        final Dispatcher dispatcher = new Dispatcher(router);
        NanoHTTPD server = new NanoHTTPD("127.0.0.1", 0) {
            @Override
            public Response serve(IHTTPSession s) {
                return dispatcher.serve(s);
            }
        };
        adapter = new NanoHttpAdapter(server);
        adapter.start();
        return adapter.boundPort();
    }

    protected String base() {
        return "http://127.0.0.1:" + port;
    }

    protected Response httpGet(String path) throws IOException {
        return doHttp("GET", path, null, null);
    }

    protected Response httpPost(String path, String body) throws IOException {
        return doHttp("POST", path, body, "application/json; charset=utf-8");
    }

    protected Response httpPut(String path, String body) throws IOException {
        return doHttp("PUT", path, body, "application/json; charset=utf-8");
    }

    protected Response httpDelete(String path) throws IOException {
        return doHttp("DELETE", path, null, null);
    }

    protected Response httpPostRaw(String path, String body, String contentType) throws IOException {
        return doHttp("POST", path, body, contentType);
    }

    private Response doHttp(String method, String path, String body, String contentType) throws IOException {
        URL u = URI.create(base() + path).toURL();
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(2000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(false);
        if (body != null) {
            c.setDoOutput(true);
            if (contentType != null) {
                c.setRequestProperty("Content-Type", contentType);
            }
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }
        }
        int status;
        try {
            status = c.getResponseCode();
        } catch (IOException e) {
            c.disconnect();
            return new Response(0, "");
        }
        String respBody = (status >= 400)
                ? readStream(c.getErrorStream())
                : readStream(c.getInputStream());
        c.disconnect();
        return new Response(status, respBody);
    }

    private String readStream(InputStream s) throws IOException {
        if (s == null) {
            return "";
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = s.read(buf)) > 0) {
            b.write(buf, 0, n);
        }
        return b.toString("UTF-8");
    }

    /**
     * Minimal {@code (status, body)} pair returned by the helpers.
     * Inline POJO (public final fields) to keep the test surface
     * area small - JUnit 3 has no {@code record}, and a proper
     * getter pair would just add boilerplate.
     */
    public static final class Response {
        public final int status;
        public final String body;

        public Response(int s, String b) {
            this.status = s;
            this.body = b;
        }
    }
}
