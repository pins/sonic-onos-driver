/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai;

import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.pi.model.PiPipelineInterpreter;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Abstract implementation of a pipeliner logic for the SAI pipeconf.
 */
abstract class AbstractObjectiveTranslator<T extends Objective> {

    protected final Logger log = getLogger(this.getClass());

    protected final DeviceId deviceId;
    protected final PiPipelineInterpreter interpreter;

    public AbstractObjectiveTranslator(DeviceId deviceId) {
        this.deviceId = checkNotNull(deviceId);
        this.interpreter = new SaiInterpreter();
    }

    public ObjectiveTranslation translate(T obj) {
        try {
            return doTranslate(obj);
        } catch (SaiPipelinerException e) {
            log.warn("Cannot translate {}: {} [{}]",
                     obj.getClass().getSimpleName(), e.getMessage(), obj);
            return ObjectiveTranslation.ofError(e.objectiveError());
        }
    }

    public abstract ObjectiveTranslation doTranslate(T obj)
            throws SaiPipelinerException;

    public FlowRule flowRule(T obj, PiTableId tableId, TrafficSelector selector,
                             TrafficTreatment treatment)
            throws SaiPipelinerException {
        return flowRule(obj, tableId, selector, treatment, obj.priority());
    }

    public FlowRule flowRule(T obj, PiTableId tableId, TrafficSelector selector,
                             TrafficTreatment treatment, Integer priority)
            throws SaiPipelinerException {
        return DefaultFlowRule.builder()
                .withSelector(selector)
                .withTreatment(mapTreatmentToPiIfNeeded(treatment, tableId))
                .forTable(tableId)
                .makePermanent()
                .withPriority(priority)
                .forDevice(deviceId)
                .fromApp(obj.appId())
                .build();
    }

    TrafficTreatment mapTreatmentToPiIfNeeded(TrafficTreatment treatment, PiTableId tableId)
            throws SaiPipelinerException {
        if (isTreatmentPi(treatment)) {
            return treatment;
        }
        final PiAction piAction;
        try {
            piAction = interpreter.mapTreatment(treatment, tableId);
        } catch (PiPipelineInterpreter.PiInterpreterException ex) {
            throw new SaiPipelinerException(
                    format("Unable to map treatment for table '%s': %s",
                           tableId, ex.getMessage()),
                    ObjectiveError.UNSUPPORTED);
        }
        return DefaultTrafficTreatment.builder()
                .piTableAction(piAction)
                .build();
    }

    private boolean isTreatmentPi(TrafficTreatment treatment) {
        return treatment.allInstructions().size() == 1
                && treatment.allInstructions().get(0).type() == Instruction.Type.PROTOCOL_INDEPENDENT;
    }
}