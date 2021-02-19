/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai;

import org.onosproject.net.pi.model.PiPipeconf;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Representation of the capabilities of a given SAI pipeconf.
 */
//FIXME: is the capabilities helper needed for SAI?
public class SaiCapabilities {

    private final Logger log = getLogger(getClass());

    private final PiPipeconf pipeconf;

    public SaiCapabilities(PiPipeconf pipeconf) {
        this.pipeconf = checkNotNull(pipeconf);
    }
}
