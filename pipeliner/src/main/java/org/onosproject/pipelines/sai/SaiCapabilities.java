/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai;

import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiPipeconf;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Representation of the capabilities of a given SAI pipeconf.
 */
//FIXME (daniele): this is needed when we introduce multiple SAI programs with
// L2/L3/custom functionalities.
public class SaiCapabilities {

    private final Logger log = getLogger(getClass());

    private final PiPipeconf pipeconf;

    public SaiCapabilities(PiPipeconf pipeconf) {
        this.pipeconf = checkNotNull(pipeconf);
    }

    public boolean hasIngressAcl() {
        return pipeconf.pipelineModel()
                .table(SaiConstants.INGRESS_ACL_INGRESS_ACL_INGRESS_TABLE)
                .isPresent();
    }

    public boolean hasAclField(PiMatchFieldId matchFieldId) {
        return hasIngressAcl() &&
                pipeconf.pipelineModel()
                        .table(SaiConstants.INGRESS_ACL_INGRESS_ACL_INGRESS_TABLE)
                        .get().matchField(matchFieldId).isPresent();
    }
}
