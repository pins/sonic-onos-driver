package org.onosproject.pipelines.sai;

import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
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

import static java.lang.String.format;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.criterion;

public class FilteringObjectiveTranslator
        extends AbstractObjectiveTranslator<FilteringObjective> {

    public FilteringObjectiveTranslator(DeviceId deviceId) {
        super(deviceId);
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

        final PortCriterion inPort = (PortCriterion) obj.key();
        final EthCriterion ethDst = (EthCriterion) criterion(
                obj.conditions(), Criterion.Type.ETH_DST);
        final PiCriterion inPortCriterion = PiCriterion.builder()
                .matchOptional(SaiConstants.HDR_IN_PORT,
                               inPort.port().name()).build();

        final TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthDstMasked(ethDst.mac(), ethDst.mask() == null ?
                        MacAddress.EXACT_MASK : ethDst.mask())
                .matchPi(inPortCriterion)
                .build();

        final PiAction action = PiAction.builder()
                .withId(SaiConstants.INGRESS_L3_ADMIT_ADMIT_TO_L3)
                .build();

        resultBuilder.addFlowRule(
                flowRule(obj, SaiConstants.INGRESS_L3_ADMIT_L3_ADMIT_TABLE,
                         selector, DefaultTrafficTreatment.builder()
                                 .piTableAction(action)
                                 .build()
                )
        );

        return resultBuilder.build();
    }
}
