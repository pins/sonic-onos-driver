package org.onosproject.pipelines.sai;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.glassfish.jersey.internal.guava.Sets;
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
import org.onosproject.net.flowobjective.FlowObjectiveStore;
import org.onosproject.net.flowobjective.NextObjective;
import org.onosproject.net.flowobjective.NextTreatment;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiActionProfileActionSet;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.onlab.packet.IPv6.getLinkLocalAddress;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.buildWcmpTableNextHopAction;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.ethDst;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.ethSrc;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.isL3NextObj;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.isMplsObj;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.outputPort;
import static org.onosproject.pipelines.sai.SaiPipeliner.KRYO;

public class NextObjectiveTranslator
        extends AbstractObjectiveTranslator<NextObjective> {

    private final FlowObjectiveStore flowObjectiveStore;

    NextObjectiveTranslator(DeviceId deviceId, FlowObjectiveStore flowObjectiveStore) {
        super(deviceId);
        this.flowObjectiveStore = flowObjectiveStore;
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

        final var builderActProfActSet = PiActionProfileActionSet.builder();
        final List<DefaultNextTreatment> defaultNextTreatments = defaultNextTreatments(obj.nextTreatments(), true);
        final List<FlowRule> routerInterfaceEntries = Lists.newArrayList();
        final List<FlowRule> neighborEntries = Lists.newArrayList();
        final List<FlowRule> nextHopEntries = Lists.newArrayList();
        Set<Pair<PiAction, Integer>> wcmpBuckets = Sets.newHashSet();

        // Build flow entries needed by the submitted Next Objective
        for (DefaultNextTreatment t : defaultNextTreatments) {
            final MacAddress srcMac = getSrcMacOrException(t);
            final MacAddress dstMac = getDstMacOrException(t);
            final PortNumber outPort = getOutPortOrException(t);
            // TODO: outPort from REST api won't contain the correct string representation
            // Currently we use output port name as the router interface ID
            final String routerInterfaceId = deviceId.toString() + "/" + outPort.name();
            // Neighbor ID should be the IPv6 LL address of the destination (calculated from the dst MAC)
            final String neighborId = Ip6Address.valueOf(getLinkLocalAddress(dstMac.toBytes())).toString();
            // TODO (daniele): Something more meaningful than concat for nextHopId
            final String nextHopId = neighborId + "@" + routerInterfaceId;

            routerInterfaceEntries.add(buildRouterInterfaceEntry(routerInterfaceId, outPort, srcMac, obj));
            neighborEntries.add(buildNeighbourEntry(routerInterfaceId, neighborId, dstMac, obj));
            nextHopEntries.add(buildNextHopEntry(routerInterfaceId, neighborId, nextHopId, obj));

            // TODO (daniele): modify weight when WCMP is supported
            wcmpBuckets.add(Pair.of(buildWcmpTableNextHopAction(nextHopId), 1));
        }

        if (isGroupModifyOp(obj)) {
            // When group modify operation we need to regenerate the WCMP group table entry
            // by adding or removing WCMP buckets.

            final SaiPipeliner.SaiNextGroup saiNextGroup = KRYO.deserialize(
                    flowObjectiveStore.getNextGroup(obj.id()).data());
            final Set<NextTreatment> oldNextTreatments = Sets.newHashSet();
            oldNextTreatments.addAll(saiNextGroup.nextTreatments());
            final Set<Pair<PiAction, Integer>> oldWcmpBuckets = Sets.newHashSet();
            // Re-build the WCMP buckets from the information in the flowobjstore.
            for (var t : oldNextTreatments) {
                final MacAddress dstMac = getDstMacOrException(t);
                final PortNumber outPort = getOutPortOrException(t);
                // Currently we use output port name as the router interface ID
                final String routerInterfaceId = deviceId.toString() + "/" + outPort.name();
                // Neighbor ID should be the IPv6 LL address of the destination (calculated from the dst MAC)
                final String neighborId = Ip6Address.valueOf(getLinkLocalAddress(dstMac.toBytes())).toString();
                // TODO (daniele): Something more meaningful than concat for nextHopId
                final String nextHopId = neighborId + "@" + routerInterfaceId;
                // TODO (daniele): modify weight when WCMP is supported
                oldWcmpBuckets.add(Pair.of(buildWcmpTableNextHopAction(nextHopId), 1));
            }
            switch (obj.op()) {
                case ADD_TO_EXISTING:
                    wcmpBuckets.addAll(oldWcmpBuckets);
                    oldNextTreatments.addAll(obj.nextTreatments());
                    break;
                case REMOVE_FROM_EXISTING:
                    oldWcmpBuckets.removeAll(wcmpBuckets);
                    wcmpBuckets = oldWcmpBuckets;
                    oldNextTreatments.removeAll(obj.nextTreatments());
                    break;
                default:
                    log.error("I should never reach this point");
                    throw new SaiPipelinerException("Unreachable point");
            }
            // Update the Next Group in the store for future use.
            updateNextGroup(obj, oldNextTreatments);
        }
        // Create the WCMP group table entry.
        wcmpBuckets.forEach(bucket -> builderActProfActSet
                .addActionProfileAction(bucket.getLeft(), bucket.getRight()));
        final TrafficSelector selector = nextIdSelector(obj.id());
        final TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .piTableAction(builderActProfActSet.build())
                .build();
        final FlowRule wcmpFlowRule = flowRule(
                obj, SaiConstants.INGRESS_ROUTING_WCMP_GROUP_TABLE,
                selector, treatment);

        // TODO (daniele): probably in the future we could push everything together
        //  and the P4RT server will figure out dependencies between rules.
        // Rules have dependencies between them, P4RT server, for now, expects
        // rules to be added and removed with a certain order.
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
            case MODIFY:
            case ADD_TO_EXISTING:
                resultBuilder.addFlowRules(routerInterfaceEntries);
                resultBuilder.newStage();
                resultBuilder.addFlowRules(neighborEntries);
                resultBuilder.newStage();
                resultBuilder.addFlowRules(nextHopEntries);
                resultBuilder.newStage();
                resultBuilder.addFlowRule(wcmpFlowRule);
                break;
            default:
                log.error("Unsuppored NextObjective operation: {}", obj.op());
                return;
        }
        resultBuilder.newStage();
    }

    private FlowRule buildNextHopEntry(String routerInterfaceId,
                                       String neighborId,
                                       String nextHopId,
                                       NextObjective obj)
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
        return flowRule(obj, SaiConstants.INGRESS_ROUTING_NEXTHOP_TABLE, selector, treatment);
    }

    private FlowRule buildRouterInterfaceEntry(String routerInterfaceId,
                                               PortNumber outputPort,
                                               MacAddress srcMac,
                                               NextObjective obj)
            throws SaiPipelinerException {

        final PiCriterion routerInterfaceIdCriterion = PiCriterion.builder()
                .matchExact(SaiConstants.HDR_ROUTER_INTERFACE_ID, routerInterfaceId.getBytes())
                .build();
        final TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchPi(routerInterfaceIdCriterion)
                .build();

        final List<PiActionParam> actionParams = Lists.newArrayList(
                new PiActionParam(SaiConstants.SRC_MAC, srcMac.toBytes()),
                new PiActionParam(SaiConstants.PORT, outputPort.name())
        );
        final TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .piTableAction(
                        PiAction.builder()
                                .withId(SaiConstants.INGRESS_ROUTING_SET_PORT_AND_SRC_MAC)
                                .withParameters(actionParams)
                                .build())
                .build();
        return flowRule(
                obj, SaiConstants.INGRESS_ROUTING_ROUTER_INTERFACE_TABLE,
                selector, treatment);
    }

    private FlowRule buildNeighbourEntry(String routerInterfaceId,
                                         String neighborId,
                                         MacAddress dstMac,
                                         NextObjective obj)
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
        return flowRule(obj, SaiConstants.INGRESS_ROUTING_NEIGHBOR_TABLE, selector, treatment);
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
        // If operation is ADD_TO_EXIST, REMOVE_FROM_EXIST, it means we need to
        // modify the WCMP table entry already in the store.
        return obj.op() == Objective.Operation.ADD_TO_EXISTING ||
                obj.op() == Objective.Operation.REMOVE_FROM_EXISTING;
    }

    private void updateNextGroup(NextObjective obj, Collection<NextTreatment> newNextTreatments) {
        final List<String> nextMappings = newNextTreatments.stream()
                .map(SaiPipeliner::nextTreatmentToMappingString)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        final SaiPipeliner.SaiNextGroup nextGroup = new SaiPipeliner.SaiNextGroup(
                obj.type(), nextMappings, newNextTreatments);
        flowObjectiveStore.putNextGroup(obj.id(), nextGroup);
    }

    private MacAddress getSrcMacOrException(NextTreatment treatment)
            throws SaiPipelinerException {
        final MacAddress srcMac = ethSrc(treatment);
        if (srcMac == null) {
            throw new SaiPipelinerException("No ETH_SRC l2instruction in NextObjective");
        }
        return srcMac;
    }

    private MacAddress getDstMacOrException(NextTreatment treatment)
            throws SaiPipelinerException {
        final MacAddress dstMac = ethDst(treatment);
        if (dstMac == null) {
            throw new SaiPipelinerException("No ETH_DST l2instruction in NextObjective");
        }
        return dstMac;
    }

    private PortNumber getOutPortOrException(NextTreatment treatment)
            throws SaiPipelinerException {
        final PortNumber outPort = outputPort(treatment);
        if (outPort == null) {
            throw new SaiPipelinerException("No OUTPUT instruction in NextObjective");
        }
        return outPort;
    }

}
