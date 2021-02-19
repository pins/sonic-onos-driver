/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.onlab.packet.DeserializationException;
import org.onlab.packet.Ethernet;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.packet.DefaultInboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiPipelineInterpreter;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiPacketMetadata;
import org.onosproject.net.pi.runtime.PiPacketOperation;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.onlab.util.ImmutableByteSequence.copyFrom;
import static org.onosproject.net.PortNumber.FLOOD;
import static org.onosproject.net.flow.instructions.Instruction.Type.OUTPUT;
import static org.onosproject.net.pi.model.PiPacketOperationType.PACKET_OUT;
import static org.onosproject.pipelines.sai.SaiConstants.*;

/**
 * Pipeline interpreter for SAI.
 */
public class SaiInterpreter extends AbstractHandlerBehaviour implements PiPipelineInterpreter {

    private static final int PORT_BITWIDTH = 32;

    // N.B.: DO NOT add the IN_PORT inside the criterion map. We don't want to
    //  use the ONOS criterion translation logic for IN_PORT criterion
    //  (e.g., CriterionTranslators.PortCriterionTranslator).
    //  In SAI we (will) refer to ports via strings while in ONOS, by default,
    //  ports are referred via the integer numbers (see ONOS PortNumber class).
    //  We should always push flow rule with PROTOCOL_INDEPENDENT Criterion
    //  when referring to IN_PORT.
    // TODO: is there a possibility of a single Criterion type that is mapped into multiple PiMatchFieldId?
    private static final Map<Criterion.Type, PiMatchFieldId> CRITERION_MAP =
            new ImmutableMap.Builder<Criterion.Type, PiMatchFieldId>()
                    .put(Criterion.Type.ETH_DST, HDR_DST_MAC)
                    .put(Criterion.Type.ETH_DST_MASKED, HDR_DST_MAC)
                    .put(Criterion.Type.ETH_TYPE, HDR_ETHER_TYPE)
                    .put(Criterion.Type.IPV4_DST, HDR_DST_IP)
                    .put(Criterion.Type.IPV4_SRC, HDR_SRC_IP)
                    .put(Criterion.Type.IPV6_DST, HDR_DST_IPV6)
                    .put(Criterion.Type.IPV6_SRC, HDR_SRC_IPV6)
                    .put(Criterion.Type.IP_PROTO, HDR_IP_PROTOCOL)
                    .put(Criterion.Type.UDP_DST, HDR_L4_DST_PORT)
                    .put(Criterion.Type.TCP_DST, HDR_L4_DST_PORT)
                    //.put(Criterion.Type.ARP_TPA, HDR_ARP_TPA)
                    .build();

    public SaiInterpreter() {
        super();
    }

    @Override
    public PiAction mapTreatment(TrafficTreatment treatment, PiTableId piTableId)
            throws PiInterpreterException {
        if (piTableId.equals(INGRESS_ROUTING_WCMP_GROUP_TABLE)) {
            // I should never reach this point!
            throw new PiInterpreterException("Invalid treatment for WCMP GROUP table");
        }
        if (piTableId.equals(INGRESS_ACL_INGRESS_ACL_INGRESS_TABLE)) {
            // I should never reach this point!
            throw new PiInterpreterException("Invalid treatment for ACL table");
        }
        throw new PiInterpreterException("Unsupported mapTreatment method in SAI");
    }

    @Override
    public Collection<PiPacketOperation> mapOutboundPacket(OutboundPacket packet)
            throws PiInterpreterException {
        DeviceId deviceId = packet.sendThrough();
        TrafficTreatment treatment = packet.treatment();

        // sai.p4 supports only OUTPUT instructions.
        List<Instructions.OutputInstruction> outInstructions = treatment
                .allInstructions()
                .stream()
                .filter(i -> i.type().equals(OUTPUT))
                .map(i -> (Instructions.OutputInstruction) i)
                .collect(toList());

        if (treatment.allInstructions().size() != outInstructions.size()) {
            // There are other instructions that are not of type OUTPUT.
            throw new PiInterpreterException("Treatment not supported: " + treatment);
        }

        ImmutableList.Builder<PiPacketOperation> builder = ImmutableList.builder();
        for (Instructions.OutputInstruction outInst : outInstructions) {
            if (outInst.port().isLogical() && !outInst.port().equals(FLOOD)) {
                throw new PiInterpreterException(format(
                        "Output on logical port '%s' not supported", outInst.port()));
            } else if (outInst.port().equals(FLOOD)) {
                // Since sai.p4 does not support flooding, we create a packet
                // operation for each switch port.
                final DeviceService deviceService = handler().get(DeviceService.class);
                for (Port port : deviceService.getPorts(packet.sendThrough())) {
                    builder.add(createPiPacketOperation(deviceId, packet.data(), port.number().toLong()));
                }
            } else {
                builder.add(createPiPacketOperation(deviceId, packet.data(), outInst.port().toLong()));
            }
        }
        return builder.build();
    }

    @Override
    public InboundPacket mapInboundPacket(PiPacketOperation packetIn, DeviceId deviceId)
            throws PiInterpreterException {
        // Assuming that the packet is ethernet, which is fine since sai.p4
        // can deparse only ethernet packets.
        Ethernet ethPkt;
        try {
            ethPkt = Ethernet.deserializer().deserialize(packetIn.data().asArray(), 0,
                                                         packetIn.data().size());
        } catch (DeserializationException dex) {
            throw new PiInterpreterException(dex.getMessage());
        }

        // Returns the ingress port packet metadata.
        Optional<PiPacketMetadata> packetMetadata = packetIn.metadatas()
                .stream().filter(m -> m.id().equals(INGRESS_PORT))
                .findFirst();

        if (packetMetadata.isPresent()) {
            // TODO (daniele) Get the port name via DeviceService?
            ImmutableByteSequence portByteSequence = packetMetadata.get().value();
            short s = portByteSequence.asReadOnlyBuffer().getShort();
            ConnectPoint receivedFrom = new ConnectPoint(deviceId, PortNumber.portNumber(s));
            ByteBuffer rawData = ByteBuffer.wrap(packetIn.data().asArray());
            return new DefaultInboundPacket(receivedFrom, ethPkt, rawData);
        } else {
            throw new PiInterpreterException(format(
                    "Missing metadata '%s' in packet-in received from '%s': %s",
                    INGRESS_PORT, deviceId, packetIn));
        }
    }

    private PiPacketOperation createPiPacketOperation(
            DeviceId deviceId, ByteBuffer data, long portNumber)
            throws PiInterpreterException {
        List<PiPacketMetadata> metadata = createPacketMetadata(portNumber);
        return PiPacketOperation.builder()
                .withType(PACKET_OUT)
                .withData(copyFrom(data))
                .withMetadatas(metadata)
                .build();
    }

    private List<PiPacketMetadata> createPacketMetadata(long portNumber)
            throws PiInterpreterException {
        try {
            return ImmutableList.of(
                    // TODO: this will be modified when ports will be string
                    PiPacketMetadata.builder()
                            .withId(EGRESS_PORT)
                            .withValue(copyFrom(portNumber).fit(PORT_BITWIDTH))
                            .build(),
                    // FIXME: should submit to ingress or directly output to port
                    PiPacketMetadata.builder()
                            .withId(SUBMIT_TO_INGRESS)
                            .withValue(copyFrom((byte) 0b0))
                            .build(),
                    PiPacketMetadata.builder()
                            .withId(UNUSED_PAD)
                            .withValue(copyFrom((byte) 0b0))
                            .build()
                    );
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            throw new PiInterpreterException(format(
                    "Port number '%d' too big, %s", portNumber, e.getMessage()));
        }
    }

    @Override
    public Optional<PiMatchFieldId> mapCriterionType(Criterion.Type type) {
        return Optional.ofNullable(CRITERION_MAP.get(type));
    }

    @Override
    public Optional<PiTableId> mapFlowRuleTableId(int flowRuleTableId) {
        // The only use case for Index ID->PiTableId is when using the single
        // table pipeliner. sai.p4 is never used with such pipeliner.
        return Optional.empty();
    }
}
