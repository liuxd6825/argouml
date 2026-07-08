/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.entity;

/**
 * A message edge on a sequence diagram (call, send, reply, create,
 * destroy). {@code sequenceNumber} reflects the 1-based position of
 * this message on its lifeline; {@code activation} marks messages
 * that open a new activation box.
 */
public final class SequenceMessageEntity implements ElementEntity {

    private final String uuid;
    private final String name;
    private final String actionSignature;
    private final String messageType;
    private final int sequenceNumber;
    private final boolean activation;
    private final String fromUuid;
    private final String toUuid;
    private final String diagramUuid;
    private final int x;
    private final int y;
    private final String methodUuid;

    public SequenceMessageEntity(String uuid, String name,
            String actionSignature, String messageType,
            int sequenceNumber, boolean activation,
            String fromUuid, String toUuid,
            String diagramUuid, int x, int y,
            String methodUuid) {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name;
        this.actionSignature = actionSignature == null ? "" : actionSignature;
        this.messageType = messageType == null ? "" : messageType;
        this.sequenceNumber = sequenceNumber;
        this.activation = activation;
        this.fromUuid = fromUuid == null ? "" : fromUuid;
        this.toUuid = toUuid == null ? "" : toUuid;
        this.diagramUuid = diagramUuid == null ? "" : diagramUuid;
        this.x = x;
        this.y = y;
        this.methodUuid = methodUuid == null ? "" : methodUuid;
    }

    @Override public String uuid() { return uuid; }
    @Override public String name() { return name; }
    @Override public String kind() { return "message"; }
    @Override public String diagramUuid() { return diagramUuid; }
    @Override public int x() { return x; }
    @Override public int y() { return y; }
    public String actionSignature() { return actionSignature; }
    /**
     * UML 2.x message sort. One of
     * {@code "syncCall"}, {@code "asyncCall"}, {@code "asyncSignal"},
     * {@code "reply"}, {@code "create"}, {@code "delete"}.
     * Validated server-side in
     * {@link org.argouml.ai.inbound.rest.sequencediagram.handlers.message.CreateMessageHandler}
     * against the same six-value set.
     */
    public String messageType() { return messageType; }
    public int sequenceNumber() { return sequenceNumber; }
    public boolean activation() { return activation; }
    public String fromUuid() { return fromUuid; }
    public String toUuid() { return toUuid; }
    /**
     * UUID of the {@code MOperation} attached to the to-lifeline's
     * bound classifier via method extraction. Empty string when the
     * message is not a {@code syncCall} or carries no
     * {@code actionSignature}.
     */
    public String methodUuid() { return methodUuid; }
}