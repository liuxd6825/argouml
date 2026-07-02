/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.infrastructure.config;

/**
 * Plain data class for HTTP server configuration. Loaded/persisted by
 * {@link ServerConfigStore}; mutable because the store reads/writes
 * fields by name and a copy-constructor would not save anything.
 */
public class ServerConfig {

    public boolean enabled = true;
    public int port = 8766;
    public String bind = "127.0.0.1";
    public int timeoutSec = 30;
    public int maxBodyBytes = 1024 * 1024;
}