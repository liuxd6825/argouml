/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.tools;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.application.common.DiagramServices;
import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.CreateActorHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.DeleteActorByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.DeleteActorByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.GetActorByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.GetActorByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.ListActorsHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.MoveActorHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.CreateAssociationHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.CreateExtendHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.CreateIncludeHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.DeleteAssociationHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.DeleteExtendHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.DeleteIncludeHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.ListUseCaseAssociationsHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.CreateUseCaseHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.DeleteUseCaseByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.DeleteUseCaseByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.GetUseCaseByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.GetUseCaseByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.ListUseCasesHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.MoveUseCaseHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.GetDiagramStateHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.ListLayerFigsHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.CreateLifelineHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.DeleteLifelineByNameHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.DeleteLifelineByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.GetLifelineByNameHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.GetLifelineByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.ListLifelinesHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.message.CreateMessageHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.message.DeleteMessageByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.message.GetMessageByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.message.ListMessagesHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.CreateRoleHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.DeleteRoleByNameHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.DeleteRoleByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.GetRoleByNameHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.GetRoleByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.ListRolesHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.MoveRoleHandler;
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
import org.argouml.ai.inbound.rest.common.handlers.common.CreateDiagramHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.CreatePackageHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.DeletePackageHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.GetDiagramHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.HealthHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.GetPackageHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.ListDiagramsHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.ListPackageClassesHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.ListPackagesHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.MoveClassToPackageHandler;
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
 * Headless launcher for the argouml-ai HTTP server. Bypasses the
 * ArgoUML GUI window requirement of {@code org.argouml.application.Main}
 * by performing only the minimal model+project initialization needed
 * for the HTTP server to bind and serve requests.
 *
 * <p>Use this when you want a real HTTP server (bound to a real
 * port) without spawning the ArgoUML main window. Production
 * deployments should still use {@code InitHttpServerSubsystem} via
 * {@code Main.main()}; this class is for test harnesses and headless
 * environments.</p>
 *
 * <p>Usage:
 * <pre>
 *   java -cp ... org.argouml.ai.tools.StandaloneHttpServer [port] [bind]
 * </pre>
 * Defaults: {@code port=8766}, {@code bind=127.0.0.1}. Send
 * {@code SIGTERM} to stop.</p>
 *
 * <p>Project state: a single UML class diagram named {@code "Test"}
 * is created in the empty project. CRUD operations via the HTTP API
 * operate on this diagram.</p>
 */
public final class StandaloneHttpServer {

    private StandaloneHttpServer() {}

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8766;
        String bind = args.length > 1 ? args[1] : "127.0.0.1";
        run(bind, port);
    }

    /**
     * Initialize the model, build the router, bind NanoHTTPD, and
     * block forever. Exposed for tests that want to bind on a
     * different lifecycle (e.g. JUnit {@code @BeforeClass} /
     * {@code @AfterClass}).
     */
    public static void run(String bind, int port) throws Exception {
        System.out.println("[standalone] Initializing model...");
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();

        // Notation subsystem must be initialized BEFORE any ArgoDiagram
        // is created; otherwise FigSingleLineTextWithNotation has a null
        // NotationProvider and NPEs on the first model property change
        // (e.g. adding an association between two existing classes).
        // These calls mirror what org.argouml.application.Main runs via
        // SubsystemUtility.initSubsystem(...) for production.
        System.out.println("[standalone] Initializing notation subsystem...");
        new InitNotation().init();
        new InitNotationUml().init();
        new InitNotationJava().init();

        System.out.println("[standalone] Creating project + diagram 'Test'...");
        Project project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getModel();
        ArgoDiagram d = new UMLClassDiagram("Test", ns);
        project.addDiagram(d);

        System.out.println("[standalone] Building router (25 routes)...");
        Router router = buildRouter();
        final Dispatcher dispatcher = new Dispatcher(router);

        System.out.println("[standalone] Binding NanoHTTPD to " + bind + ":" + port + "...");
        final NanoHTTPD server = new NanoHTTPD(bind, port) {
            @Override
            public Response serve(NanoHTTPD.IHTTPSession s) {
                return dispatcher.serve(s);
            }
        };
        final NanoHttpAdapter adapter = new NanoHttpAdapter(server);
        adapter.start();

        System.out.println("[standalone] READY: HTTP server bound to http://"
                + bind + ":" + adapter.boundPort());
        System.out.println("[standalone] Project has 1 class diagram 'Test' (UML)");
        System.out.println("[standalone] Block forever. Send SIGTERM to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("[standalone] Shutdown hook: stopping adapter...");
                adapter.stop();
            } catch (Exception e) {
                System.err.println("[standalone] Shutdown error: " + e);
            }
        }, "standalone-shutdown"));

        Thread.currentThread().join();
    }

    /**
     * Build the same routes as {@code InitHttpServerSubsystem}.
     * Kept in sync by hand; if a route is added/removed there,
     * update this method too. (Phase 4 added 17 usecase routes which
     * were initially missing here — see AGENTS.md §"Pre-existing 编译问题
     * StandaloneHttpServer §8a". The entity refactor added 4 more
     * usecase by-uuid routes — Actor + UseCase × GET/DELETE.)
     */
    static Router buildRouter() {
        Router router = new Router();

        // single class-diagram service for every classdiagram/* handler
        // AND for the project-level CreateDiagramHandler
        ClassDiagramService svc = DiagramServices.classSvc();

        // single use-case-diagram service for every
        // /d/{d}/usecasediagram/* handler. Mirrored from
        // InitHttpServerSubsystem so this standalone server exposes
        // the same MVP route surface as the production server.
        UseCaseDiagramService ucSvc = DiagramServices.useCaseSvc();

        // single sequence-diagram service for every
        // /d/{d}/sequencediagram/* handler. Mirrored from
        // InitHttpServerSubsystem so this standalone server exposes
        // the same MVP route surface as the production server.
        org.argouml.ai.application.sequencediagram.SequenceDiagramService seqSvc =
                DiagramServices.sequenceSvc();

        // common (project-scoped) routes
        router.add(Method.GET, "/health", new HealthHandler());
        router.add(Method.GET, "/project/diagrams", new ListDiagramsHandler());
        router.add(Method.POST, "/project/diagrams", new CreateDiagramHandler(svc));
        // Package management
        router.add(Method.GET,  "/project/packages", new ListPackagesHandler(svc));
        router.add(Method.POST, "/project/packages", new CreatePackageHandler(svc));
        router.add(Method.GET,  "/project/packages/{name}", new GetPackageHandler(svc));
        router.add(Method.DELETE, "/project/packages/{name}", new DeletePackageHandler(svc));
        router.add(Method.POST,  "/project/packages/{name}/classes/{c}", new MoveClassToPackageHandler(svc));
        router.add(Method.GET,  "/project/packages/{name}/classes", new ListPackageClassesHandler(svc));
        router.add(Method.GET, "/project/diagrams/{d}", new GetDiagramHandler());
        router.add(Method.GET, "/project/diagrams/{d}/snapshot", new SnapshotHandler());

        // Classes & interfaces
        router.add(Method.GET,  "/d/{d}/classes",            new ListClassesHandler(svc));
        router.add(Method.GET,  "/d/{d}/classes/{c}",        new GetClassHandler(svc));
        router.add(Method.POST, "/d/{d}/classes",            new CreateClassHandler(svc));
        router.add(Method.PUT,  "/d/{d}/classes/{c}",        new UpdateClassHandler(svc));
        router.add(Method.DELETE,"/d/{d}/classes/{c}",       new DeleteClassHandler(svc));
        router.add(Method.POST, "/d/{d}/interfaces",         new CreateInterfaceHandler(svc));

        // Attributes
        router.add(Method.POST,   "/d/{d}/classes/{c}/attributes",     new AddAttributeHandler(svc));
        router.add(Method.GET,    "/d/{d}/classes/{c}/attributes",     new ListAttributesHandler(svc));
        router.add(Method.GET,    "/d/{d}/classes/{c}/attributes/{a}", new GetAttributeHandler(svc));
        router.add(Method.DELETE, "/d/{d}/classes/{c}/attributes/{a}", new DeleteAttributeHandler(svc));

        // Operations
        router.add(Method.POST,   "/d/{d}/classes/{c}/operations",     new AddOperationHandler(svc));
        router.add(Method.GET,    "/d/{d}/classes/{c}/operations",     new ListOperationsHandler(svc));
        router.add(Method.GET,    "/d/{d}/classes/{c}/operations/{op}", new GetOperationHandler(svc));
        router.add(Method.DELETE, "/d/{d}/classes/{c}/operations/{op}", new DeleteOperationHandler(svc));

        // Relationships
        router.add(Method.GET,    "/d/{d}/associations",     new ListAssociationsHandler(svc));
        router.add(Method.POST,   "/d/{d}/associations",     new AddAssociationHandler(svc));
        router.add(Method.GET,    "/d/{d}/generalizations",  new ListGeneralizationsHandler(svc));
        router.add(Method.POST,   "/d/{d}/generalizations",  new AddGeneralizationHandler(svc));
        router.add(Method.GET,    "/d/{d}/dependencies",     new ListDependenciesHandler(svc));
        router.add(Method.POST,   "/d/{d}/dependencies",     new AddDependencyHandler(svc));
        router.add(Method.DELETE, "/d/{d}/relationships/{id}", new DeleteRelationshipHandler(svc));

        // Use-case diagram API (entity-based; mirrored from
        // InitHttpServerSubsystem)
        // Actor CRUD
        router.add(Method.POST, "/d/{d}/usecasediagram/actors",
                new CreateActorHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/actors",
                new ListActorsHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/actors/by-name/{a}",
                new GetActorByNameHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/actors/{uuid}",
                new GetActorByUuidHandler(ucSvc));
        router.add(Method.PUT, "/d/{d}/usecasediagram/actors/by-name/{a}",
                new MoveActorHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/actors/by-name/{a}",
                new DeleteActorByNameHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/actors/{uuid}",
                new DeleteActorByUuidHandler(ucSvc));
        // UseCase CRUD
        router.add(Method.POST, "/d/{d}/usecasediagram/usecases",
                new CreateUseCaseHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/usecases",
                new ListUseCasesHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/usecases/by-name/{u}",
                new GetUseCaseByNameHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/usecases/{uuid}",
                new GetUseCaseByUuidHandler(ucSvc));
        router.add(Method.PUT, "/d/{d}/usecasediagram/usecases/by-name/{u}",
                new MoveUseCaseHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/usecases/by-name/{u}",
                new DeleteUseCaseByNameHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/usecases/{uuid}",
                new DeleteUseCaseByUuidHandler(ucSvc));
        // Relationships
        router.add(Method.POST, "/d/{d}/usecasediagram/associations",
                new CreateAssociationHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/associations",
                new ListUseCaseAssociationsHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/associations/{id}",
                new DeleteAssociationHandler(ucSvc));
        router.add(Method.POST, "/d/{d}/usecasediagram/includes",
                new CreateIncludeHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/includes/{id}",
                new DeleteIncludeHandler(ucSvc));
        router.add(Method.POST, "/d/{d}/usecasediagram/extends",
                new CreateExtendHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/extends/{id}",
                new DeleteExtendHandler(ucSvc));

        // Sequence diagram API (entity-based; mirrored from
        // InitHttpServerSubsystem)
        // Diagram is created via POST /project/diagrams with kind:"sequencediagram" (existing endpoint)
        // ClassifierRole CRUD
        router.add(Method.POST, "/d/{d}/sequencediagram/roles",
                new CreateRoleHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/roles",
                new ListRolesHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/roles/by-name/{n}",
                new GetRoleByNameHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/roles/{uuid}",
                new GetRoleByUuidHandler(seqSvc));
        router.add(Method.PUT, "/d/{d}/sequencediagram/roles/by-name/{n}",
                new MoveRoleHandler(seqSvc));
        router.add(Method.DELETE, "/d/{d}/sequencediagram/roles/by-name/{n}",
                new DeleteRoleByNameHandler(seqSvc));
        router.add(Method.DELETE, "/d/{d}/sequencediagram/roles/{uuid}",
                new DeleteRoleByUuidHandler(seqSvc));
        // Lifeline CRUD
        router.add(Method.POST, "/d/{d}/sequencediagram/lifelines",
                new CreateLifelineHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/lifelines",
                new ListLifelinesHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/lifelines/by-name/{n}",
                new GetLifelineByNameHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/lifelines/{uuid}",
                new GetLifelineByUuidHandler(seqSvc));
        router.add(Method.DELETE, "/d/{d}/sequencediagram/lifelines/by-name/{n}",
                new DeleteLifelineByNameHandler(seqSvc));
        router.add(Method.DELETE, "/d/{d}/sequencediagram/lifelines/{uuid}",
                new DeleteLifelineByUuidHandler(seqSvc));
        // Message CRUD
        router.add(Method.POST, "/d/{d}/sequencediagram/messages",
                new CreateMessageHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/messages",
                new ListMessagesHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/messages/{uuid}",
                new GetMessageByUuidHandler(seqSvc));
        router.add(Method.DELETE, "/d/{d}/sequencediagram/messages/{uuid}",
                new DeleteMessageByUuidHandler(seqSvc));

        // Diagnostic — fig-count snapshot (mirrors InitHttpServerSubsystem).
        router.add(Method.GET, "/d/{d}/sequencediagram/figs",
                new GetDiagramStateHandler(seqSvc));

        // Diagnostic — list every fig in the layer (mirrors).
        router.add(Method.GET, "/d/{d}/sequencediagram/figs/dump",
                new ListLayerFigsHandler(seqSvc));

        return router;
    }
}
