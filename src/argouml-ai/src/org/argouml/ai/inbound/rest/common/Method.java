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

/**
 * HTTP method vocabulary the REST router understands. Matches the
 * shape of {@link fi.iki.elonen.NanoHTTPD.Method} but only the four
 * verbs the MVP exposes (see the plan's endpoint table).
 */
public enum Method {

    GET,
    POST,
    PUT,
    DELETE
}