/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.drivers.sonic;

import org.onosproject.drivers.p4runtime.AbstractP4RuntimePipelineProgrammable;
import org.onosproject.net.behaviour.PiPipelineProgrammable;
import org.onosproject.net.pi.model.PiPipeconf;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Implementation of the PiPipelineProgrammable behaviour for a SONiC/PINS target.
 */
public class SonicPipelineProgrammable
        extends AbstractP4RuntimePipelineProgrammable
        implements PiPipelineProgrammable {

    @Override
    public Optional<PiPipeconf> getDefaultPipeconf() {
        return Optional.empty();
    }

    @Override
    public ByteBuffer createDeviceDataBuffer(PiPipeconf pipeconf) {
        // No pipeline binary needed
        return ByteBuffer.allocate(0);
    }
}
