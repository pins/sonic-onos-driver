/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai;

import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiActionProfileId;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiPacketMetadataId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;
/**
 * Constants for SAI pipeline.
 */
// TODO (daniele): autogenerate from P4 program.
public final class SaiConstants {

    // hide default constructor
    private SaiConstants() {
    }

    public static final String DEFAULT_VRF_ID = "vrf-0";

    // Header field IDs
    public static final PiMatchFieldId HDR_IS_IP = PiMatchFieldId.of("is_ip");
    public static final PiMatchFieldId HDR_ETHER_TYPE =
            PiMatchFieldId.of("ether_type");
    public static final PiMatchFieldId HDR_SRC_IPV6 =
            PiMatchFieldId.of("src_ipv6");
    public static final PiMatchFieldId HDR_IPV6_DST =
            PiMatchFieldId.of("ipv6_dst");
    public static final PiMatchFieldId HDR_ARP_TPA =
            PiMatchFieldId.of("arp_tpa");
    public static final PiMatchFieldId HDR_ICMPV6_TYPE =
            PiMatchFieldId.of("icmpv6_type");
    public static final PiMatchFieldId HDR_WCMP_GROUP_ID =
            PiMatchFieldId.of("wcmp_group_id");
    public static final PiMatchFieldId HDR_TTL = PiMatchFieldId.of("ttl");
    public static final PiMatchFieldId HDR_MIRROR_SESSION_ID =
            PiMatchFieldId.of("mirror_session_id");
    public static final PiMatchFieldId HDR_DST_IPV6 =
            PiMatchFieldId.of("dst_ipv6");
    public static final PiMatchFieldId HDR_IPV4_DST =
            PiMatchFieldId.of("ipv4_dst");
    public static final PiMatchFieldId HDR_L4_DST_PORT =
            PiMatchFieldId.of("l4_dst_port");
    public static final PiMatchFieldId HDR_SRC_IP = PiMatchFieldId.of("src_ip");
    public static final PiMatchFieldId HDR_NEIGHBOR_ID =
            PiMatchFieldId.of("neighbor_id");
    public static final PiMatchFieldId HDR_IN_PORT =
            PiMatchFieldId.of("in_port");
    public static final PiMatchFieldId HDR_DST_MAC =
            PiMatchFieldId.of("dst_mac");
    public static final PiMatchFieldId HDR_IP_PROTOCOL =
            PiMatchFieldId.of("ip_protocol");
    public static final PiMatchFieldId HDR_ECN = PiMatchFieldId.of("ecn");
    public static final PiMatchFieldId HDR_DSCP = PiMatchFieldId.of("dscp");
    public static final PiMatchFieldId HDR_VRF_ID = PiMatchFieldId.of("vrf_id");
    public static final PiMatchFieldId HDR_IS_IPV4 =
            PiMatchFieldId.of("is_ipv4");
    public static final PiMatchFieldId HDR_IS_IPV6 =
            PiMatchFieldId.of("is_ipv6");
    public static final PiMatchFieldId HDR_NEXTHOP_ID =
            PiMatchFieldId.of("nexthop_id");
    public static final PiMatchFieldId HDR_SRC_MAC =
            PiMatchFieldId.of("src_mac");
    public static final PiMatchFieldId HDR_DST_IP = PiMatchFieldId.of("dst_ip");
    public static final PiMatchFieldId HDR_ROUTER_INTERFACE_ID =
            PiMatchFieldId.of("router_interface_id");
    // Table IDs
    public static final PiTableId INGRESS_MIRRORING_MIRROR_SESSION_TABLE =
            PiTableId.of("ingress.mirroring.mirror_session_table");
    public static final PiTableId INGRESS_ACL_LOOKUP_ACL_LOOKUP_TABLE =
            PiTableId.of("ingress.acl_lookup.acl_lookup_table");
    public static final PiTableId INGRESS_ROUTING_IPV4_TABLE =
            PiTableId.of("ingress.routing.ipv4_table");
    public static final PiTableId INGRESS_ROUTING_WCMP_GROUP_TABLE =
            PiTableId.of("ingress.routing.wcmp_group_table");
    public static final PiTableId INGRESS_ROUTING_IPV6_TABLE =
            PiTableId.of("ingress.routing.ipv6_table");
    public static final PiTableId INGRESS_ACL_INGRESS_ACL_INGRESS_TABLE =
            PiTableId.of("ingress.acl_ingress.acl_ingress_table");
    public static final PiTableId INGRESS_ROUTING_ROUTER_INTERFACE_TABLE =
            PiTableId.of("ingress.routing.router_interface_table");
    public static final PiTableId INGRESS_ROUTING_NEXTHOP_TABLE =
            PiTableId.of("ingress.routing.nexthop_table");
    public static final PiTableId INGRESS_ROUTING_NEIGHBOR_TABLE =
            PiTableId.of("ingress.routing.neighbor_table");
    public static final PiTableId INGRESS_L3_ADMIT_L3_ADMIT_TABLE =
            PiTableId.of("ingress.l3_admit.l3_admit_table");
    public static final PiTableId INGRESS_ACL_LINKQUAL_ACL_LINKQUAL_TABLE =
            PiTableId.of("ingress.acl_linkqual.acl_linkqual_table");
    // Direct Counter IDs
    public static final PiCounterId INGRESS_ACL_LOOKUP_ACL_LOOKUP_COUNTER =
            PiCounterId.of("ingress.acl_lookup.acl_lookup_counter");
    public static final PiCounterId INGRESS_ACL_LINKQUAL_LINKQUAL_COUNTER =
            PiCounterId.of("ingress.acl_linkqual.linkqual_counter");
    public static final PiCounterId INGRESS_ACL_INGRESS_ACL_INGRESS_COUNTER =
            PiCounterId.of("ingress.acl_ingress.acl_ingress_counter");
    // Action IDs
    public static final PiActionId INGRESS_ROUTING_SET_NEXTHOP =
            PiActionId.of("ingress.routing.set_nexthop");
    public static final PiActionId INGRESS_ACL_LOOKUP_SET_VRF =
            PiActionId.of("ingress.acl_lookup.set_vrf");
    public static final PiActionId INGRESS_ACL_INGRESS_COPY =
            PiActionId.of("ingress.acl_ingress.copy");
    public static final PiActionId INGRESS_ROUTING_DROP =
            PiActionId.of("ingress.routing.drop");
    public static final PiActionId INGRESS_ACL_LINKQUAL_LINKQUAL_DROP =
            PiActionId.of("ingress.acl_linkqual.linkqual_drop");
    public static final PiActionId INGRESS_L3_ADMIT_ADMIT_TO_L3 =
            PiActionId.of("ingress.l3_admit.admit_to_l3");
    public static final PiActionId INGRESS_ACL_INGRESS_MIRROR =
            PiActionId.of("ingress.acl_ingress.mirror");
    public static final PiActionId INGRESS_ACL_LINKQUAL_LINKQUAL_SET_PORT =
            PiActionId.of("ingress.acl_linkqual.linkqual_set_port");
    public static final PiActionId NO_ACTION = PiActionId.of("NoAction");
    public static final PiActionId INGRESS_MIRRORING_MIRROR_AS_IPV4_ERSPAN =
            PiActionId.of("ingress.mirroring.mirror_as_ipv4_erspan");
    public static final PiActionId INGRESS_ROUTING_SET_NEXTHOP_ID =
            PiActionId.of("ingress.routing.set_nexthop_id");
    public static final PiActionId INGRESS_ACL_INGRESS_FORWARD =
            PiActionId.of("ingress.acl_ingress.forward");
    public static final PiActionId INGRESS_ROUTING_SET_WCMP_GROUP_ID =
            PiActionId.of("ingress.routing.set_wcmp_group_id");
    public static final PiActionId INGRESS_ROUTING_SET_PORT_AND_SRC_MAC =
            PiActionId.of("ingress.routing.set_port_and_src_mac");
    public static final PiActionId INGRESS_ROUTING_SET_DST_MAC =
            PiActionId.of("ingress.routing.set_dst_mac");
    public static final PiActionId INGRESS_ACL_INGRESS_TRAP =
            PiActionId.of("ingress.acl_ingress.trap");
    // Action Param IDs
    public static final PiActionParamId QOS_QUEUE =
            PiActionParamId.of("qos_queue");
    public static final PiActionParamId DST_MAC = PiActionParamId.of("dst_mac");
    public static final PiActionParamId NEXTHOP_ID =
            PiActionParamId.of("nexthop_id");
    public static final PiActionParamId DST_IP = PiActionParamId.of("dst_ip");
    public static final PiActionParamId TOS = PiActionParamId.of("tos");
    public static final PiActionParamId TTL = PiActionParamId.of("ttl");
    public static final PiActionParamId NEIGHBOR_ID =
            PiActionParamId.of("neighbor_id");
    public static final PiActionParamId SRC_IP = PiActionParamId.of("src_ip");
    public static final PiActionParamId WCMP_GROUP_ID =
            PiActionParamId.of("wcmp_group_id");
    public static final PiActionParamId MIRROR_SESSION_ID =
            PiActionParamId.of("mirror_session_id");
    public static final PiActionParamId VRF_ID = PiActionParamId.of("vrf_id");
    public static final PiActionParamId SRC_MAC = PiActionParamId.of("src_mac");
    public static final PiActionParamId ROUTER_INTERFACE_ID =
            PiActionParamId.of("router_interface_id");
    public static final PiActionParamId PORT = PiActionParamId.of("port");
    // Action Profile IDs
    public static final PiActionProfileId INGRESS_ROUTING_WCMP_GROUP_SELECTOR =
            PiActionProfileId.of("ingress.routing.wcmp_group_selector");
    // Packet Metadata IDs
    public static final PiPacketMetadataId EGRESS_PORT =
            PiPacketMetadataId.of("egress_port");
    public static final PiPacketMetadataId UNUSED_PAD =
            PiPacketMetadataId.of("unused_pad");
    public static final PiPacketMetadataId INGRESS_PORT =
            PiPacketMetadataId.of("ingress_port");
    public static final PiPacketMetadataId SUBMIT_TO_INGRESS =
            PiPacketMetadataId.of("submit_to_ingress");
    public static final PiPacketMetadataId TARGET_EGRESS_PORT =
            PiPacketMetadataId.of("target_egress_port");
}