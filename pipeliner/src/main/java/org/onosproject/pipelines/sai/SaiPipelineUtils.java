/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai;

import com.google.common.primitives.Bytes;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flow.instructions.L2ModificationInstruction;
import org.onosproject.net.flow.instructions.L3ModificationInstruction;
import org.onosproject.net.flowobjective.DefaultNextTreatment;
import org.onosproject.net.flowobjective.NextObjective;
import org.onosproject.net.flowobjective.NextTreatment;
import org.onosproject.net.pi.model.PiPipelineInterpreter;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;

import java.util.Collection;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * Utility class with methods common to SAI pipeconf operations.
 */
public final class SaiPipelineUtils {

    private SaiPipelineUtils() {
        // hide constructor
    }

    public static Criterion criterion(Collection<Criterion> criteria, Criterion.Type type) {
        return criteria.stream()
                .filter(c -> c.type().equals(type))
                .findFirst().orElse(null);
    }

    public static Criterion criterionNotNull(Collection<Criterion> criteria, Criterion.Type type) {
        return checkNotNull(criterion(criteria, type),
                            format("%s criterion cannot be null", type));
    }

    public static Instructions.OutputInstruction instruction(TrafficTreatment treatment, Instruction.Type type) {
        return treatment.allInstructions()
                .stream()
                .filter(inst -> inst.type() == type)
                .map(inst -> (Instructions.OutputInstruction) inst)
                .findFirst().orElse(null);
    }

    public static Instructions.OutputInstruction outputInstruction(TrafficTreatment treatment) {
        return instruction(treatment, Instruction.Type.OUTPUT);
    }

    public static L2ModificationInstruction.ModEtherInstruction ethDstInstruction(TrafficTreatment treatment) {
        return (L2ModificationInstruction.ModEtherInstruction)
                l2Instruction(treatment, L2ModificationInstruction.L2SubType.ETH_DST);
    }

    public static L2ModificationInstruction.ModEtherInstruction ethSrcInstruction(TrafficTreatment treatment) {
        return (L2ModificationInstruction.ModEtherInstruction)
                l2Instruction(treatment, L2ModificationInstruction.L2SubType.ETH_SRC);
    }

    public static L3ModificationInstruction.ModIPInstruction ipv4DstInstruction(TrafficTreatment treatment) {
        return (L3ModificationInstruction.ModIPInstruction)
                l3Instruction(treatment, L3ModificationInstruction.L3SubType.IPV4_DST);
    }

    public static PortNumber outputPort(TrafficTreatment treatment) {
        final Instructions.OutputInstruction inst = outputInstruction(treatment);
        return inst == null ? null : inst.port();
    }

    public static PortNumber outputPort(NextTreatment treatment) {
        if (treatment.type() == NextTreatment.Type.TREATMENT) {
            final DefaultNextTreatment t = (DefaultNextTreatment) treatment;
            return outputPort(t.treatment());
        }
        return null;
    }

    public static MacAddress ethDst(NextTreatment treatment) {
        if (treatment.type() == NextTreatment.Type.TREATMENT) {
            final DefaultNextTreatment t = (DefaultNextTreatment) treatment;
            final L2ModificationInstruction.ModEtherInstruction inst = ethDstInstruction(t.treatment());
            return inst == null ? null : inst.mac();
        }
        return null;
    }

    public static IpAddress ipv4Dst(NextTreatment treatment) {
        if (treatment.type() == NextTreatment.Type.TREATMENT) {
            final DefaultNextTreatment t = (DefaultNextTreatment) treatment;
            final L3ModificationInstruction.ModIPInstruction inst = ipv4DstInstruction(t.treatment());
            return inst == null ? null : inst.ip();
        }
        return null;
    }

    public static MacAddress ethSrc(NextTreatment treatment) {
        if (treatment.type() == NextTreatment.Type.TREATMENT) {
            final DefaultNextTreatment t = (DefaultNextTreatment) treatment;
            final L2ModificationInstruction.ModEtherInstruction inst = ethSrcInstruction(t.treatment());
            return inst == null ? null : inst.mac();
        }
        return null;
    }

    public static L2ModificationInstruction l2Instruction(
            TrafficTreatment treatment, L2ModificationInstruction.L2SubType subType) {
        return treatment.allInstructions().stream()
                .filter(i -> i.type().equals(Instruction.Type.L2MODIFICATION))
                .map(i -> (L2ModificationInstruction) i)
                .filter(i -> i.subtype().equals(subType))
                .findFirst().orElse(null);
    }

    private static Stream<L2ModificationInstruction> l2ModificationInstructions(NextObjective obj) {
        return obj.nextTreatments().stream()
                .filter(t -> t.type() == NextTreatment.Type.TREATMENT)
                .map(t -> (DefaultNextTreatment) t)
                .map(t -> t.treatment())
                .flatMap(t -> t.allInstructions().stream())
                .filter(ins -> ins.type() == Instruction.Type.L2MODIFICATION)
                .map(ins -> (L2ModificationInstruction) ins);
    }

    public static L3ModificationInstruction l3Instruction(
            TrafficTreatment treatment, L3ModificationInstruction.L3SubType subType) {
        return treatment.allInstructions().stream()
                .filter(i -> i.type().equals(Instruction.Type.L3MODIFICATION))
                .map(i -> (L3ModificationInstruction) i)
                .filter(i -> i.subtype().equals(subType))
                .findFirst().orElse(null);
    }

    public static boolean isL3NextObj(NextObjective obj) {
        return l2ModificationInstructions(obj)
                .anyMatch(ins -> ins.subtype() == L2ModificationInstruction.L2SubType.ETH_DST);
    }

    public static boolean isMplsObj(NextObjective obj) {
        return l2ModificationInstructions(obj)
                .anyMatch(ins -> ins.subtype() == L2ModificationInstruction.L2SubType.MPLS_LABEL);
    }

    public static boolean isMplsOp(NextObjective obj, L2ModificationInstruction.L2SubType mplsOp) {
        return l2ModificationInstructions(obj)
                .anyMatch(ins -> ins.subtype() == mplsOp);
    }

    public static Instruction instructionOrFail(
            TrafficTreatment treatment, Instruction.Type type, PiTableId tableId)
            throws PiPipelineInterpreter.PiInterpreterException {
        final Instruction inst = instruction(treatment, type);
        if (inst == null) {
            treatmentException(tableId, treatment, format("missing %s instruction", type));
        }
        return inst;
    }
    public static void treatmentException(
            PiTableId tableId, TrafficTreatment treatment, String explanation)
            throws PiPipelineInterpreter.PiInterpreterException {
        throw new PiPipelineInterpreter.PiInterpreterException(format(
                "Invalid treatment for table '%s', %s: %s", tableId, explanation, treatment));
    }

    public static PiAction mapNextHashedTreatment(
            String routerInterfaceId, String neighborId) {
        return PiAction.builder()
                .withId(SaiConstants.INGRESS_ROUTING_SET_NEXTHOP_ID)
                .withParameter(new PiActionParam(SaiConstants.NEXTHOP_ID,
                                                 Bytes.concat(neighborId.getBytes(),
                                                              routerInterfaceId.getBytes())))
                .build();
    }
}
