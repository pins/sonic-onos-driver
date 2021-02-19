/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.group.DefaultGroupDescription;
import org.onosproject.net.group.DefaultGroupKey;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupKey;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.onosproject.net.group.DefaultGroupBucket.createCloneGroupBucket;
import static org.onosproject.pipelines.sai.SaiConstants.HDR_IPV4_DST;
import static org.onosproject.pipelines.sai.SaiConstants.HDR_IPV6_DST;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.criterionNotNull;

/**
 * The translator that translates ForwardingObjective to
 * flows of the BCM pipeline.
 */
public class ForwardingObjectiveTranslator
        extends AbstractObjectiveTranslator<ForwardingObjective> {

    // FIXME: check with the new ACL from google sai
    private static final Set<Criterion.Type> PUNT_CRITERIA = ImmutableSet.of(
            Criterion.Type.IN_PORT,
            Criterion.Type.IN_PHY_PORT,
            Criterion.Type.ETH_TYPE,
            Criterion.Type.IPV4_SRC,
            Criterion.Type.IPV4_DST,
            Criterion.Type.IP_PROTO,
            Criterion.Type.ICMPV4_CODE,
            Criterion.Type.VLAN_VID,
            Criterion.Type.VLAN_PCP,
            Criterion.Type.PROTOCOL_INDEPENDENT);
    // Supported ACL Criterion
    private static final Set<Criterion.Type> ACL_SUPPORTED_CRITERIA = ImmutableSet.of(
            Criterion.Type.ETH_TYPE,
            Criterion.Type.ETH_DST,
            Criterion.Type.ETH_DST_MASKED,
            Criterion.Type.ETH_SRC,
            Criterion.Type.ETH_SRC_MASKED,
            Criterion.Type.IPV6_DST,
            //No Criterion for: headers.ipv6.next_header
            //No Criterion for: headers.ipv6.hop_limit
            Criterion.Type.ICMPV4_TYPE,
            Criterion.Type.ICMPV6_TYPE,
            Criterion.Type.TCP_DST,
            Criterion.Type.TCP_DST_MASKED,
            Criterion.Type.UDP_DST,
            Criterion.Type.UDP_DST_MASKED,
            Criterion.Type.PROTOCOL_INDEPENDENT
    );

    private static final Map<Criterion.Type, PiMatchFieldId> IP_CRITERION_MAP =
            new ImmutableMap.Builder<Criterion.Type, PiMatchFieldId>()
                    .put(Criterion.Type.IPV4_DST, HDR_IPV4_DST)
                    .put(Criterion.Type.IPV6_DST, HDR_IPV6_DST)
            .build();


    ForwardingObjectiveTranslator(DeviceId deviceId) {
        super(deviceId);
    }

    @Override
    public ObjectiveTranslation doTranslate(ForwardingObjective obj) throws SaiPipelinerException {
        log.warn("FWDTranslator DOTRANSLATE!");
        final ObjectiveTranslation.Builder resultBuilder =
                ObjectiveTranslation.builder();
        switch (obj.flag()) {
            case SPECIFIC:
                processSpecificFwd(obj, resultBuilder);
                break;
            case VERSATILE:
                // TODO (daniele): reactivate ACL rules generation.
                log.warn("Skipping ACL rules for now!");
                //processVersatileFwd(obj, resultBuilder);
                break;
            case EGRESS:
            default:
                log.warn("Unsupported ForwardingObjective type '{}'", obj.flag());
                return ObjectiveTranslation.ofError(ObjectiveError.UNSUPPORTED);
        }
        return resultBuilder.build();
    }

    private void processSpecificFwd(ForwardingObjective obj,
                                    ObjectiveTranslation.Builder resultBuilder)
            throws SaiPipelinerException {

        final Set<Criterion> criteriaWithMeta = Sets.newHashSet(obj.selector().criteria());

        // FIXME: Is this really needed? Meta is such an ambiguous field...
        // Why would we match on a META field?
        if (obj.meta() != null) {
            criteriaWithMeta.addAll(obj.meta().criteria());
        }

        final ForwardingFunctionType fft = ForwardingFunctionType.getForwardingFunctionType(obj);

        switch (fft.type()) {
            case UNKNOWN:
                throw new SaiPipelinerException("unable to detect forwarding function type");
            // We currently support only IPv4 and IPv6 Routing
            case IPV4_ROUTING:
                ipv4RoutingRule(obj, criteriaWithMeta, resultBuilder);
                break;
            case IPV6_ROUTING:
                ipv6RoutingRule(obj, criteriaWithMeta, resultBuilder);
                break;
            case MPLS_SEGMENT_ROUTING:
            case L2_UNICAST:
            case L2_BROADCAST:
            case IPV4_ROUTING_MULTICAST:
                log.warn("unsupported forwarding function type '{}', ignore it", fft.type());
                break;
            case IPV6_ROUTING_MULTICAST:
            default:
                throw new SaiPipelinerException(String.format(
                        "unsupported forwarding function type '%s'", fft.type()));
        }
    }

    private void ipv4RoutingRule(ForwardingObjective obj, Set<Criterion> criteriaWithMeta,
                                 ObjectiveTranslation.Builder resultBuilder)
            throws SaiPipelinerException {
        // Build IPCriterion
        final IPCriterion ipDstCriterion = (IPCriterion) criterionNotNull(
                criteriaWithMeta, Criterion.Type.IPV4_DST);

        resultBuilder.addFlowRule(buildIpRoutingRule(obj, ipDstCriterion,
                                                     SaiConstants.INGRESS_ROUTING_IPV4_TABLE));
    }

    private void ipv6RoutingRule(ForwardingObjective obj, Set<Criterion> criteriaWithMeta,
                                 ObjectiveTranslation.Builder resultBuilder)
            throws SaiPipelinerException {
        // Build IPCriterion
        final IPCriterion ipDstCriterion = (IPCriterion) criterionNotNull(
                criteriaWithMeta, Criterion.Type.IPV6_DST);

        resultBuilder.addFlowRule(buildIpRoutingRule(obj, ipDstCriterion,
                                                     SaiConstants.INGRESS_ROUTING_IPV6_TABLE));
    }

    private FlowRule buildIpRoutingRule(ForwardingObjective obj,
                                        IPCriterion ipDstCriterion,
                                        PiTableId ipRoutingTableId)
            throws SaiPipelinerException {
        // IpCriterion won't be translated correctly by PiFlowRuleTranslator
        // because CRITERION_MAP in SaiInterpreter has different translation for IPV4/6_DST criterion
        // TODO (daniele): Here we should set the default VRF in the set_vrf_table.
        //  is that needed in SONiC or it's supposed to be already in default VRF?
        final TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchPi(PiCriterion.builder()
                                 .matchExact(SaiConstants.HDR_VRF_ID,
                                             SaiConstants.DEFAULT_VRF_ID.getBytes())
                                 .matchLpm(IP_CRITERION_MAP.get(ipDstCriterion.type()),
                                           ipDstCriterion.ip().address().toOctets(),
                                           ipDstCriterion.ip().prefixLength())
                                 .build())
                .build();
        final PiActionParam nextIdParam = new PiActionParam(SaiConstants.WCMP_GROUP_ID,
                                                            String.valueOf(obj.nextId()).getBytes());

        // FIXME (daniele): here we should distinguish between WCMP and SIMPLE routing.
        //  However, the type of forwarding is selected in the NextObjective.
        //  For now, we always use WCMP and if the forwarding is SIMPLE we add a single ActionProfileMember.

        // FIXME: CURRENTLY ONLY SUPPORTING SIMPLE FORWARDING.
        final PiAction action = PiAction.builder()
                .withId(SaiConstants.INGRESS_ROUTING_SET_WCMP_GROUP_ID)
                .withParameter(nextIdParam)
                .build();

        final TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .piTableAction(action)
                .build();

        return flowRule(obj, ipRoutingTableId, selector, treatment);
    }

    private void processVersatileFwd(ForwardingObjective obj,
                                     ObjectiveTranslation.Builder resultBuilder)
            throws SaiPipelinerException {
        // ACL Rule
        final Set<Criterion.Type> unsupportedCriteria = obj.selector().criteria()
                .stream()
                .map(Criterion::type)
                .filter(t -> !ACL_SUPPORTED_CRITERIA.contains(t))
                .collect(Collectors.toSet());
        if (!unsupportedCriteria.isEmpty()) {
            log.warn("unsupported punt criteria {}", unsupportedCriteria.toString());
            return;
        }
        // TODO (daniele): how to deal with packets that are already punted from SONiC?
        //  (e.g., LLDP, ARP already punted by SONiC)
        //  Could we have a driver configuration or a pipeconf extension for signaling that?
        //  If so, should we filter them here?
        aclRule(obj, resultBuilder);
    }

    void aclRule(ForwardingObjective obj, ObjectiveTranslation.Builder resultBuilder)
            throws SaiPipelinerException {
        // ACL Punt Table in SAI supports PUNT or COPY to CPU.
        if (obj.nextId() == null && obj.treatment() != null) {
            final TrafficTreatment treatment = obj.treatment();
            final PortNumber outPort = SaiPipelineUtils.outputPort(treatment);
            if (outPort != null
                    && outPort.equals(PortNumber.CONTROLLER)
                    && obj.treatment().allInstructions().size() == 1) {
                final PiAction aclAction;
                if (treatment.clearedDeferred()) {
                    // Action is PUNT packet to the CPU
                    aclAction = PiAction.builder()
                            .withId(SaiConstants.INGRESS_ACL_INGRESS_TRAP)
                            .build();
                } else {
                    // Action is clone packet to the CPU
                    aclAction = PiAction.builder()
                            .withId(SaiConstants.INGRESS_ACL_INGRESS_COPY)
                            .build();
                }
                final TrafficTreatment piTreatment = DefaultTrafficTreatment.builder()
                        .piTableAction(aclAction)
                        .build();
                resultBuilder.addFlowRule(flowRule(
                        obj, SaiConstants.INGRESS_ACL_INGRESS_ACL_INGRESS_TABLE,
                        obj.selector(), piTreatment));
                return;
            }
        }
        // We could get here if:
        // - we have a nextID, ACL punt in SAI doesn't support punting to a nextId.
        // - the output port is a different port than CPU
        // - we don't have treatment, SAI ACL punt table doesn't support NOP,
        //    in this case simply do not generate FlowRule
        log.warn("SAI ACL table supports only COPY and PUNT to CPU!");
    }

    private DefaultGroupDescription createCloneGroup(
            ApplicationId appId,
            int cloneSessionId,
            PortNumber outPort) {
        // FIXME (daniele): I'm not sure the clone group is needed for cloning packets to the CPU.
        final GroupKey groupKey = new DefaultGroupKey(
                SaiPipeliner.KRYO.serialize(cloneSessionId));

        final List<GroupBucket> bucketList = ImmutableList.of(
                createCloneGroupBucket(DefaultTrafficTreatment.builder()
                                               .setOutput(outPort)
                                               .build()));

        final DefaultGroupDescription cloneGroup = new DefaultGroupDescription(
                deviceId, GroupDescription.Type.CLONE,
                new GroupBuckets(bucketList),
                groupKey, cloneSessionId, appId);
        return cloneGroup;
    }
}