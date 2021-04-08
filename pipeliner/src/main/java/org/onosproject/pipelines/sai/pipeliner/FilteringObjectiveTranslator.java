/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai.pipeliner;

import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.flow.criteria.PortCriterion;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.pipelines.sai.SaiCapabilities;
import org.onosproject.pipelines.sai.SaiConstants;

import static java.lang.String.format;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.criterion;

public class FilteringObjectiveTranslator
        extends AbstractObjectiveTranslator<FilteringObjective> {

    private DeviceService deviceService;

    public FilteringObjectiveTranslator(DeviceId deviceId,
                                        SaiCapabilities capabilities,
                                        DeviceService deviceService) {
        super(deviceId, capabilities);
        this.deviceService = deviceService;
    }

    @Override
    public ObjectiveTranslation doTranslate(FilteringObjective obj)
            throws SaiPipelinerException {
        final ObjectiveTranslation.Builder resultBuilder =
                ObjectiveTranslation.builder();
        if (obj.type().equals(FilteringObjective.Type.DENY)) {
            log.info("Unsupported DENY filtering objective type");
            return resultBuilder.build();
        }

        if (obj.key() == null || obj.key().type() != Criterion.Type.IN_PORT) {
            throw new SaiPipelinerException(
                    format("Unsupported or missing filtering key: key=%s", obj.key()),
                    ObjectiveError.BADPARAMS);
        }

        // FIXME: generalize and move to pipeline utils
        final PortCriterion inPort = (PortCriterion) obj.key();
        if (inPort == null) {
            throw new SaiPipelinerException("No PORT KEY in FilteringObjective");
        }
        PortNumber inPortNumber = inPort.port();
        final Port actualPort = deviceService.getPort(deviceId, inPortNumber);
        if (actualPort == null) {
            log.warn("{} port not found in device: {}", inPort, deviceId);
        } else {
            inPortNumber = actualPort.number();
        }


        final PiCriterion inPortCriterion = PiCriterion.builder()
                .matchOptional(SaiConstants.HDR_IN_PORT,
                               inPortNumber.name()).build();
        final EthCriterion ethDst = (EthCriterion) criterion(
                obj.conditions(), Criterion.Type.ETH_DST);
        final TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        if (ethDst != null) {
            selectorBuilder
                    .matchEthDstMasked(ethDst.mac(), ethDst.mask() == null ?
                            MacAddress.EXACT_MASK : ethDst.mask())
                    .matchPi(inPortCriterion);
        }
        final PiAction action = PiAction.builder()
                .withId(SaiConstants.INGRESS_L3_ADMIT_ADMIT_TO_L3)
                .build();

        resultBuilder.addFlowRule(
                flowRule(obj, SaiConstants.INGRESS_L3_ADMIT_L3_ADMIT_TABLE,
                         selectorBuilder.build(), DefaultTrafficTreatment.builder()
                                 .piTableAction(action)
                                 .build()
                )
        );

        return resultBuilder.build();
    }
}
