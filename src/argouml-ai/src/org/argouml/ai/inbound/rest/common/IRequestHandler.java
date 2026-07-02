/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.common;

import java.util.Map;

/**
 * SPI for REST handlers. The router resolves a {@link Route} by
 * (method, path-template); the handler receives the captured path
 * parameters, the flattened query parameters, and the raw request
 * body (already decoded from JSON when applicable) and returns a
 * {@link ResponseEnvelope}.
 *
 * <p>Handlers must NOT touch ArgoUML model state directly - that
 * happens on the EDT inside the dispatcher. Handlers should be
 * referentially transparent with respect to their arguments; all
 * state comes from the three maps plus {@code body}.</p>
 */
public interface IRequestHandler {

    ResponseEnvelope handle(Map<String, String> pathParams,
                            Map<String, String> queryParams,
                            String body);
}