package org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase;

import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

public final class AddUseCaseRepresentedDiagramHandler implements IRequestHandler {
    private final UseCaseDiagramService svc;
    public AddUseCaseRepresentedDiagramHandler(UseCaseDiagramService svc) {
        if (svc == null) throw new IllegalArgumentException("svc");
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
        String name = pathParams == null ? null : pathParams.get("u");
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "UseCase name required"));
        }
        Map<String, Object> json;
        try { json = JsonBodyReader.readMap(body); }
        catch (IllegalArgumentException ex) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY", ex.getMessage()));
        }
        Object o = json == null ? null : json.get("diagramUuid");
        if (o == null) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY",
                    "diagramUuid required"));
        }
        boolean added = svc.addUseCaseRepresentedDiagram(diagram, name, String.valueOf(o));
        if (!added) {
            return ResponseEnvelope.json(409, JsonError.of("DUPLICATE",
                    "UUID already in link list"));
        }
        return ResponseEnvelope.json(200, JsonWriter.ok(null));
    }
}