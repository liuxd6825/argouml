package org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

public final class ListUseCaseRepresentedDiagramsHandler implements IRequestHandler {
    private final UseCaseDiagramService svc;
    public ListUseCaseRepresentedDiagramsHandler(UseCaseDiagramService svc) {
        if (svc == null) throw new IllegalArgumentException("svc");
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
        String uuid = pathParams == null ? null : pathParams.get("uuid");
        if (uuid == null || uuid.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "UseCase uuid required in path"));
        }
        List<String> uuids = svc.listUseCaseRepresentedDiagrams(diagram, uuid);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("diagramUuids", uuids);
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }
}