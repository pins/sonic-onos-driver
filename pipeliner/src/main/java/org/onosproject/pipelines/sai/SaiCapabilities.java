/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai;

import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiPacketMetadataId;
import org.onosproject.net.pi.model.PiPacketOperationType;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.model.PiTableId;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.onosproject.net.pi.model.PiPipeconf.ExtensionType.CPU_PORT_TXT;
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
        return isTablePresent(SaiConstants.INGRESS_ACL_INGRESS_ACL_INGRESS_TABLE);
    }

    public boolean hasAclField(PiMatchFieldId matchFieldId) {
        return isMatchFieldInTablePresent(
                SaiConstants.INGRESS_ACL_INGRESS_ACL_INGRESS_TABLE, matchFieldId);
    }

    // Check if a match field can be submitted as string
    public boolean isMatchFieldString(PiTableId tableId, PiMatchFieldId matchFieldId) {
        return isMatchFieldInTablePresent(tableId, matchFieldId) &&
                !pipeconf.pipelineModel().table(tableId).get()
                        .matchField(matchFieldId).get().hasBitWidth();
    }

    // Check if an action parameter can be submitted as string
    public boolean isActionParamString(PiTableId tableId, PiActionId actionId, PiActionParamId actionParamId) {
        return isActionParamInTablePresent(tableId, actionId, actionParamId) &&
                !pipeconf.pipelineModel().table(tableId).get()
                        .action(actionId).get()
                        .param(actionParamId).get().hasBitWidth();
    }

    private boolean isTablePresent(PiTableId tableId) {
        return pipeconf.pipelineModel().table(tableId).isPresent();
    }

    private boolean isMatchFieldInTablePresent(PiTableId tableId, PiMatchFieldId matchFieldId) {
        return isTablePresent(tableId) &&
                pipeconf.pipelineModel().table(tableId).get()
                        .matchField(matchFieldId).isPresent();
    }

    private boolean isActionParamInTablePresent(PiTableId tableId, PiActionId actionId, PiActionParamId actionparamId) {
        return isTablePresent(tableId) &&
                pipeconf.pipelineModel().table(tableId).get()
                        .action(actionId).isPresent() &&
                pipeconf.pipelineModel().table(tableId).get()
                        .action(actionId).get()
                        .param(actionparamId).isPresent();
    }

    public boolean isPktInMetadataString(PiPacketMetadataId metadataId) {
        return isPktMetadataString(PiPacketOperationType.PACKET_IN, metadataId);
    }

    public boolean isPktOutMetadataString(PiPacketMetadataId metadataId) {
        return isPktMetadataString(PiPacketOperationType.PACKET_OUT, metadataId);
    }

    private boolean isPktMetadataString(PiPacketOperationType inOrOut,
                                        PiPacketMetadataId metadataId) {
        return pipeconf.pipelineModel()
                .packetOperationModel(inOrOut)
                .isPresent() &&
                !pipeconf.pipelineModel()
                        .packetOperationModel(inOrOut).get().metadatas().stream()
                        .anyMatch(piPktMetaModel -> piPktMetaModel.id().equals(metadataId) &&
                                piPktMetaModel.hasBitWidth());
    }

    public Optional<Integer> cpuPort() {
        // This is probably brittle, but needed to dynamically get the CPU port
        // for different platforms.
        if (!pipeconf.extension(CPU_PORT_TXT).isPresent()) {
            log.warn("Missing {} extension in pipeconf {}", CPU_PORT_TXT, pipeconf.id());
            return Optional.empty();
        }
        try {
            final InputStream stream = pipeconf.extension(CPU_PORT_TXT).get();
            final BufferedReader buff = new BufferedReader(
                    new InputStreamReader(stream));
            final String str = buff.readLine();
            buff.close();
            if (str == null) {
                log.error("Empty CPU port file for {}", pipeconf.id());
                return Optional.empty();
            }
            try {
                return Optional.of(Integer.parseInt(str));
            } catch (NumberFormatException e) {
                log.error("Invalid CPU port for {}: {}", pipeconf.id(), str);
                return Optional.empty();
            }
        } catch (IOException e) {
            log.error("Unable to read CPU port file of {}: {}",
                      pipeconf.id(), e.getMessage());
            return Optional.empty();
        }
    }
}
