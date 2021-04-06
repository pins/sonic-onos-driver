/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai.pipeliner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.onlab.util.KryoNamespace;
import org.onlab.util.SharedExecutors;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.NextGroup;
import org.onosproject.net.behaviour.Pipeliner;
import org.onosproject.net.behaviour.PipelinerContext;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleOperations;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.FlowObjectiveStore;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.IdNextTreatment;
import org.onosproject.net.flowobjective.NextObjective;
import org.onosproject.net.flowobjective.NextTreatment;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.pipelines.sai.AbstractSaiHandlerBehavior;
import org.onosproject.pipelines.sai.SaiConstants;
import org.onosproject.store.serializers.KryoNamespaces;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.onosproject.pipelines.sai.SaiPipelineUtils.outputPort;
import static org.slf4j.LoggerFactory.getLogger;


public class SaiPipeliner extends AbstractSaiHandlerBehavior implements Pipeliner {

    private static final Logger log = getLogger(SaiPipeliner.class);

    protected static final KryoNamespace KRYO = new KryoNamespace.Builder()
            .register(KryoNamespaces.API)
            .register(SaiNextGroup.class)
            .build("SaiPipeliner");

    private DeviceId deviceId;

    private FlowRuleService flowRuleService;
    private FlowObjectiveStore flowObjectiveStore;
    private DeviceService deviceService;

    private ForwardingObjectiveTranslator forwardingTranslator;
    private NextObjectiveTranslator nextTranslator;
    private FilteringObjectiveTranslator filteringTranslator;

    private final ExecutorService callbackExecutor = SharedExecutors.getPoolThreadExecutor();

    public static Instructions.OutputInstruction instruction(TrafficTreatment treatment, Instruction.Type type) {
        return treatment.allInstructions()
                .stream()
                .filter(inst -> inst.type() == type)
                .map(inst -> (Instructions.OutputInstruction) inst)
                .findFirst().orElse(null);
    }

    @Override
    public void init(DeviceId deviceId, PipelinerContext context) {
        this.deviceId = deviceId;
        this.flowRuleService = context.directory().get(FlowRuleService.class);
        this.flowObjectiveStore = context.directory().get(FlowObjectiveStore.class);
        this.deviceService = context.directory().get(DeviceService.class);

        forwardingTranslator = new ForwardingObjectiveTranslator(
                deviceId, capabilities);
        nextTranslator = new NextObjectiveTranslator(
                deviceId, capabilities, flowObjectiveStore, deviceService);
        filteringTranslator = new FilteringObjectiveTranslator(
                deviceId, capabilities, deviceService);
    }

    @Override
    public void filter(FilteringObjective obj) {
        final ObjectiveTranslation result = filteringTranslator.translate(obj);
        handleResult(obj, result);
    }

    @Override
    public void forward(ForwardingObjective obj) {
        final ObjectiveTranslation result = forwardingTranslator.translate(obj);
        handleResult(obj, result);
    }

    @Override
    public void next(NextObjective obj) {
        if (obj.op() == Objective.Operation.MODIFY) {
            log.error("Operation {} is unsupported in SAI pipeliner", obj.op());
            fail(obj, ObjectiveError.UNSUPPORTED);
            return;
        }
        final ObjectiveTranslation result = nextTranslator.translate(obj);
        handleResult(obj, result);
    }

    @Override
    public List<String> getNextMappings(NextGroup nextGroup) {
        return Collections.emptyList();
    }

    private void handleResult(Objective obj, ObjectiveTranslation result) {
        if (result.error().isPresent()) {
            fail(obj, result.error().get());
            return;
        }
        processFlows(obj, result.stages());
        if (obj instanceof NextObjective) {
            handleNextGroup((NextObjective) obj);
        }
        success(obj);
    }

    private void processFlows(Objective objective, ImmutableList<ImmutableSet<FlowRule>> flowRulesStages) {
        if (flowRulesStages.isEmpty()) {
            return;
        }
        final FlowRuleOperations.Builder ops = FlowRuleOperations.builder();
        for (var listFlowRules : flowRulesStages) {
            if (listFlowRules.isEmpty()) {
                continue;
            }
            switch (objective.op()) {
                case ADD:
                case ADD_TO_EXISTING:
                case MODIFY:
                    listFlowRules.forEach(ops::add);
                    break;
                case REMOVE:
                    listFlowRules.forEach(ops::remove);
                case REMOVE_FROM_EXISTING:
                    // When removing from existing, the WCMP_GROUP_TABLE
                    // has to be treated by updating the flow rule, not removing.
                    listFlowRules.stream()
                            .filter(flowRule -> flowRule.table() != SaiConstants.INGRESS_ROUTING_WCMP_GROUP_TABLE)
                            .forEach(ops::remove);
                    listFlowRules.stream()
                            .filter(flowRule -> flowRule.table() == SaiConstants.INGRESS_ROUTING_WCMP_GROUP_TABLE)
                            .forEach(ops::add);
                    break;
                default:
                    log.warn("Unsupported Objective operation '{}'", objective.op());
                    return;
            }
            ops.newStage();
        }
        flowRuleService.apply(ops.build());
    }

    private void handleNextGroup(NextObjective obj) {

        switch (obj.op()) {
            case REMOVE:
                // TODO (daniele): what happens if an incomplete next objective is removed?
                //  should, the NextObjectiveTranslator, be sure that no other
                //  neighbor/interface/nexthop entries are in the store?
                removeNextGroup(obj);
                break;
            case ADD:
                // TODO (daniele): what happens if a next objective is added when it's already present?
                //  should, the NextObjectiveTranslator, be sure that this is not happening?
                //  If we don't check, then we will loose the reference to the
                //  neighbor/interface/nexthop added by the previous next obj.
                putNextGroup(obj);
                break;
            case ADD_TO_EXISTING:
            case REMOVE_FROM_EXISTING:
                // ADD_TO_EXISITNG and REMOVE_FROM_EXISTING are managed
                // directly into the NextObjectiveTranslator.
            case MODIFY:
            case VERIFY:
                break;
            default:
                log.error("Unknown NextObjective operation '{}'", obj.op());
        }
    }
    private void removeNextGroup(NextObjective obj) {
        final NextGroup removed = flowObjectiveStore.removeNextGroup(obj.id());
        if (removed == null) {
            log.debug("NextGroup {} was not found in FlowObjectiveStore", obj.id());
        }
    }

    private void putNextGroup(NextObjective obj) {
        final List<String> nextMappings = obj.nextTreatments().stream()
                .map(SaiPipeliner::nextTreatmentToMappingString)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        final SaiNextGroup nextGroup = new SaiNextGroup(obj.type(), nextMappings, obj.nextTreatments());
        flowObjectiveStore.putNextGroup(obj.id(), nextGroup);
    }

    static String nextTreatmentToMappingString(NextTreatment n) {
        switch (n.type()) {
            case TREATMENT:
                final PortNumber p = outputPort(n);
                return p == null ? "UNKNOWN"
                        : format("OUTPUT:%s", p.toString());
            case ID:
                final IdNextTreatment id = (IdNextTreatment) n;
                return format("NEXT_ID:%d", id.nextId());
            default:
                log.warn("Unknown NextTreatment type '{}'", n.type());
                return "???";
        }
    }

    private void fail(Objective objective, ObjectiveError error) {
        CompletableFuture.runAsync(
                () -> objective.context().ifPresent(
                        ctx -> ctx.onError(objective, error)), callbackExecutor);
    }

    private void success(Objective objective) {
        CompletableFuture.runAsync(
                () -> objective.context().ifPresent(
                        ctx -> ctx.onSuccess(objective)), callbackExecutor);
    }

    /**
     * NextGroup implementation.
     */
    static class SaiNextGroup implements NextGroup {

        private final NextObjective.Type type;
        private final List<String> nextMappings;
        private final Collection<NextTreatment> nextTreatments;

        SaiNextGroup(NextObjective.Type type, List<String> nextMappings, Collection<NextTreatment> nextTreatments) {
            this.type = type;
            this.nextMappings = ImmutableList.copyOf(nextMappings);
            this.nextTreatments = ImmutableList.copyOf(nextTreatments);
        }

        NextObjective.Type type() {
            return type;
        }

        Collection<String> nextMappings() {
            return nextMappings;
        }

        Collection<NextTreatment> nextTreatments() {
            return nextTreatments;
        }

        @Override
        public byte[] data() {
            return KRYO.serialize(this);
        }
    }
}
