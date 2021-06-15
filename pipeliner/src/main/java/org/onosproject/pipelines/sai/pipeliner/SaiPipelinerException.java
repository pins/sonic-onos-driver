/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai.pipeliner;

import org.onosproject.net.flowobjective.ObjectiveError;

/**
 * Signals an exception when translating a flow objective.
 */
class SaiPipelinerException extends Exception {

    private final ObjectiveError error;

    /**
     * Creates a new exception for the given message. Sets ObjectiveError to
     * UNSUPPORTED.
     *
     * @param message message
     */
    SaiPipelinerException(String message) {
        super(message);
        this.error = ObjectiveError.UNSUPPORTED;
    }

    /**
     * Creates a new exception for the given message and ObjectiveError.
     *
     * @param message message
     * @param error objective error
     */
    SaiPipelinerException(String message, ObjectiveError error) {
        super(message);
        this.error = error;
    }

    /**
     * Returns the ObjectiveError of this exception.
     *
     * @return objective error
     */
    ObjectiveError objectiveError() {
        return error;
    }
}