package org.onosproject.pipelines.sai;

import com.google.common.collect.Lists;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.flowobjective.DefaultNextTreatment;
import org.onosproject.net.flowobjective.NextObjective;
import org.onosproject.net.flowobjective.NextTreatment;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiActionProfileActionSet;

import java.util.Collection;
import java.util.List;
import static java.lang.String.format;
import static org.onlab.packet.IPv6.getLinkLocalAddress;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.ethDst;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.ethSrc;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.isL3NextObj;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.isMplsObj;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.mapNextHashedTreatment;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.outputPort;

public class NextObjectiveTranslator
        extends AbstractObjectiveTranslator<NextObjective> {

    NextObjectiveTranslator(DeviceId deviceId) {
        super(deviceId);
    }

    @Override
    public ObjectiveTranslation doTranslate(NextObjective obj)
            throws SaiPipelinerException {
        final ObjectiveTranslation.Builder resultBuilder =
                ObjectiveTranslation.builder();
        switch (obj.type()) {
            case SIMPLE:
                simpleNext(obj, resultBuilder);
                break;
            case HASHED:
                hashedNext(obj, resultBuilder);
                break;
            case BROADCAST:
                // This can be multicast or xconnect. Both are currently unsupported
                // by sai.p4. Do not fail, log a warning.
                log.warn("Unsupported NextObjective type '{}', ignore it", obj);
                break;
            default:
                log.warn("Unsupported NextObjective type '{}'", obj);
                return ObjectiveTranslation.ofError(ObjectiveError.UNSUPPORTED);
        }
        return resultBuilder.build();
    }

    private void simpleNext(NextObjective obj,
                            ObjectiveTranslation.Builder resultBuilder)
            throws SaiPipelinerException {
        // Currently we only support L3 Next Objective
        if (isL3NextObj(obj)) {
            hashedNext(obj, resultBuilder);
            return;
        }
        // We could have L2 Next Objective (e.g., bridging rules).
        log.warn("Unsupported NextObjective '{}', currently only L3 Next " +
                         "Objective are supported", obj);
    }

    private void hashedNext(NextObjective obj,
                            ObjectiveTranslation.Builder resultBuilder)
            throws SaiPipelinerException {
        if (isMplsObj(obj)) {
            log.warn("Unsupported NextObjective type '{}', ignore it", obj);
            return;
        }

        var builderActProfActSet = PiActionProfileActionSet.builder();
        final List<DefaultNextTreatment> defaultNextTreatments = defaultNextTreatments(obj.nextTreatments(), true);
        final List<FlowRule> routerInterfaceEntries = Lists.newArrayList();
        final List<FlowRule> neighborEntries = Lists.newArrayList();
        final List<FlowRule> nextHopEntries = Lists.newArrayList();

        for (DefaultNextTreatment t : defaultNextTreatments) {
            // Create the Neighbor table and Router Interface Entries
            final MacAddress srcMac = ethSrc(t);
            if (srcMac == null) {
                throw new SaiPipelinerException("No ETH_SRC l2instruction in NextObjective");
            }
            final MacAddress dstMac = ethDst(t);
            if (dstMac == null) {
                throw new SaiPipelinerException("No ETH_DST l2instruction in NextObjective");
            }
            final PortNumber outPort = outputPort(t);
            if (outPort == null) {
                throw new SaiPipelinerException("No OUTPUT instruction in NextObjective");
            }
            // Currently we use output port name as the router interface ID
            final String routerInterfaceId = outPort.name();
            // Neighbor ID should be the IPv6 LL address of the destination (calculated from the dst MAC)
            final String neighborId = Ip6Address.valueOf(getLinkLocalAddress(dstMac.toBytes())).toString();

            buildRouterInterfaceEntry(routerInterfaceId, outPort, srcMac, obj, routerInterfaceEntries);
            buildNeighbourEntry(routerInterfaceId, neighborId, dstMac, obj, neighborEntries);

            // Create Next Hop Entry
            // TODO (daniele): Find something more meaningful than concat
            String nextHopId = neighborId + routerInterfaceId;
            // TODO (daniele): I could do similarly to what is done in the selectGroup method
            //  for each treatment in the NextObjective convert it to PI...
            //  Or simply for every treatment push a flowrule in the result builder
            //  as a the end of the method
            buildNextHopEntry(routerInterfaceId, neighborId, nextHopId, obj, nextHopEntries);

            // Map treatment to PiActionProfileAction
            builderActProfActSet.addActionProfileAction(
                    mapNextHashedTreatment(routerInterfaceId, neighborId), 1);
        }
        final TrafficSelector selector = nextIdSelector(obj.id());
        final TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .piTableAction(builderActProfActSet.build())
                .build();
        final FlowRule wcmpFlowRule = flowRule(
                obj, SaiConstants.INGRESS_ROUTING_WCMP_GROUP_TABLE,
                selector, treatment);

        switch (obj.op()) {
            case REMOVE:
            case REMOVE_FROM_EXISTING:
                resultBuilder.addFlowRule(wcmpFlowRule);
                resultBuilder.newStage();
                resultBuilder.addFlowRules(nextHopEntries);
                resultBuilder.newStage();
                resultBuilder.addFlowRules(neighborEntries);
                resultBuilder.newStage();
                resultBuilder.addFlowRules(routerInterfaceEntries);
                break;
            case ADD:
            case ADD_TO_EXISTING:
            default:
                resultBuilder.addFlowRules(routerInterfaceEntries);
                resultBuilder.newStage();
                resultBuilder.addFlowRules(neighborEntries);
                resultBuilder.newStage();
                resultBuilder.addFlowRules(nextHopEntries);
                resultBuilder.newStage();
                resultBuilder.addFlowRule(wcmpFlowRule);
        }
        resultBuilder.newStage();
    }

    private void buildNextHopEntry(String routerInterfaceId,
                                   String neighborId,
                                   String nextHopId,
                                   NextObjective obj,
                                   List<FlowRule> flowRules)
            throws SaiPipelinerException {
        final PiCriterion routerInterfaceIdCriterion = PiCriterion.builder()
                .matchExact(SaiConstants.HDR_NEXTHOP_ID, nextHopId.getBytes())
                .build();
        final TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchPi(routerInterfaceIdCriterion)
                .build();

        final List<PiActionParam> actionParams = Lists.newArrayList(
                new PiActionParam(SaiConstants.ROUTER_INTERFACE_ID, routerInterfaceId.getBytes()),
                new PiActionParam(SaiConstants.NEIGHBOR_ID, neighborId.getBytes())
        );
        final TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .piTableAction(
                        PiAction.builder()
                                .withId(SaiConstants.INGRESS_ROUTING_SET_NEXTHOP)
                                .withParameters(actionParams)
                                .build())
                .build();
        flowRules.add(flowRule(
                obj, SaiConstants.INGRESS_ROUTING_NEXTHOP_TABLE,
                selector, treatment));
    }

    private void buildRouterInterfaceEntry(String routerInterfaceId,
                                           PortNumber outputPort,
                                           MacAddress srcMac,
                                           NextObjective obj,
                                           List<FlowRule> flowRules)
            throws SaiPipelinerException {

        final PiCriterion routerInterfaceIdCriterion = PiCriterion.builder()
                .matchExact(SaiConstants.HDR_ROUTER_INTERFACE_ID, routerInterfaceId.getBytes())
                .build();
        final TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchPi(routerInterfaceIdCriterion)
                .build();

        final List<PiActionParam> actionParams = Lists.newArrayList(
                new PiActionParam(SaiConstants.SRC_MAC, srcMac.toBytes()),
                new PiActionParam(SaiConstants.PORT, outputPort.toLong())
        );
        final TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .piTableAction(
                        PiAction.builder()
                                .withId(SaiConstants.INGRESS_ROUTING_SET_PORT_AND_SRC_MAC)
                                .withParameters(actionParams)
                                .build())
                .build();
        flowRules.add(flowRule(
                obj, SaiConstants.INGRESS_ROUTING_ROUTER_INTERFACE_TABLE,
                selector, treatment));
    }

    private void buildNeighbourEntry(String routerInterfaceId,
                                     String neighborId,
                                     MacAddress dstMac,
                                     NextObjective obj,
                                     List<FlowRule> flowRules)
            throws SaiPipelinerException {
        final PiCriterion routerInterfaceIdCriterion = PiCriterion.builder()
                .matchExact(SaiConstants.HDR_ROUTER_INTERFACE_ID, routerInterfaceId.getBytes())
                .matchExact(SaiConstants.HDR_NEIGHBOR_ID, neighborId.getBytes())
                .build();
        final TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchPi(routerInterfaceIdCriterion)
                .build();
        final TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .piTableAction(
                        PiAction.builder()
                                .withId(SaiConstants.INGRESS_ROUTING_SET_DST_MAC)
                                .withParameter(new PiActionParam(
                                        SaiConstants.DST_MAC, dstMac.toBytes()))
                                .build())
                .build();
        flowRules.add(flowRule(
                obj, SaiConstants.INGRESS_ROUTING_NEIGHBOR_TABLE, selector, treatment));
    }

    private TrafficSelector nextIdSelector(int nextId) {
        return nextIdSelectorBuilder(nextId).build();
    }

    private TrafficSelector.Builder nextIdSelectorBuilder(int nextId) {
        final PiCriterion nextIdCriterion = PiCriterion.builder()
                .matchExact(SaiConstants.HDR_WCMP_GROUP_ID, String.valueOf(nextId).getBytes())
                .build();
        return DefaultTrafficSelector.builder()
                .matchPi(nextIdCriterion);
    }

//    private int selectGroup(NextObjective obj,
//                            ObjectiveTranslation.Builder resultBuilder)
//            throws SaiPipelinerException {
//
//        final PiTableId hashedTableId = SaiConstants.INGRESS_ROUTING_WCMP_GROUP_TABLE;
//        final List<DefaultNextTreatment> defaultNextTreatments =
//                defaultNextTreatments(obj.nextTreatments(), true);
//        final List<TrafficTreatment> piTreatments = Lists.newArrayList();
//
//        for (DefaultNextTreatment t : defaultNextTreatments) {
//            // Map treatment to PI
//            piTreatments.add(mapTreatmentToPiIfNeeded(t.treatment(), hashedTableId));
//        }
//
//        // TODO (daniele): here for WCMP we could insert the weight
//        final List<GroupBucket> bucketList = piTreatments.stream()
//                .map(DefaultGroupBucket::createSelectGroupBucket)
//                .collect(Collectors.toList());
//
//        final int groupId = obj.id();
//        final PiGroupKey groupKey = new PiGroupKey(
//                hashedTableId,
//                SaiConstants.INGRESS_ROUTING_WCMP_GROUP_SELECTOR,
//                groupId);
//
//        resultBuilder.addGroup(new DefaultGroupDescription(
//                deviceId,
//                GroupDescription.Type.SELECT,
//                new GroupBuckets(bucketList),
//                groupKey,
//                groupId,
//                obj.appId()));
//
//        return groupId;
//    }

    private List<DefaultNextTreatment> defaultNextTreatments(
            Collection<NextTreatment> nextTreatments, boolean strict)
            throws SaiPipelinerException {
        final List<DefaultNextTreatment> defaultNextTreatments = Lists.newArrayList();
        final List<NextTreatment> unsupportedNextTreatments = Lists.newArrayList();
        for (NextTreatment n : nextTreatments) {
            if (n.type() == NextTreatment.Type.TREATMENT) {
                defaultNextTreatments.add((DefaultNextTreatment) n);
            } else {
                unsupportedNextTreatments.add(n);
            }
        }
        if (strict && !unsupportedNextTreatments.isEmpty()) {
            throw new SaiPipelinerException(format(
                    "Unsupported NextTreatments: %s",
                    unsupportedNextTreatments));
        }
        return defaultNextTreatments;
    }

    private boolean isGroupModifyOp(NextObjective obj) {
        // If operation is ADD_TO_EXIST, REMOVE_FROM_EXIST or MODIFY, it means we modify
        // group buckets only, no changes for flow rules.
        // FIXME Please note that for MODIFY op this could not apply in future if we extend the scope of MODIFY
        return obj.op() == Objective.Operation.ADD_TO_EXISTING ||
                obj.op() == Objective.Operation.REMOVE_FROM_EXISTING ||
                obj.op() == Objective.Operation.MODIFY;
    }

}
