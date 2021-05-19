/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai;

import com.google.common.collect.ImmutableList;
import org.onosproject.core.CoreService;
import org.onosproject.net.behaviour.Pipeliner;
import org.onosproject.net.pi.model.DefaultPiPipeconf;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.model.PiPipeconfId;
import org.onosproject.net.pi.model.PiPipelineInterpreter;
import org.onosproject.net.pi.model.PiPipelineModel;
import org.onosproject.net.pi.service.PiPipeconfService;
import org.onosproject.p4runtime.model.P4InfoParser;
import org.onosproject.p4runtime.model.P4InfoParserException;
import org.onosproject.pipelines.sai.pipeliner.SaiPipeliner;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;

import java.net.URL;
import java.util.Collection;

import static org.onosproject.net.pi.model.PiPipeconf.ExtensionType.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Component responsible of building and registering pipeconfs at app
 * activation.
 */

@Component(immediate = true)
public final class SaiPipeconfLoader {

    private static Logger log = getLogger(SaiPipeconfLoader.class);

    private static final String APP_NAME = "org.onosproject.pipelines.sai";

    private static final PiPipeconfId SAI_PIPECONF_ID =
            new PiPipeconfId("org.onosproject.pipelines.sai");
    private static final String SAI_P4INFO = "/pins/sai_onf.p4info";

    private static final PiPipeconfId FIXED_SAI_PIPECONF_ID =
            new PiPipeconfId("org.onosproject.pipelines.sai_fixed");
    private static final String FIXED_SAI_P4INFO = "/pins/sai_fixed.p4info";

    private static final PiPipeconfId SAI_GOOGLE_BMV2_PIPECONF_ID =
            new PiPipeconfId("org.onosproject.pipelines.sai.google.bmv2");
    private static final String SAI_GOOGLE_BMV2_P4INFO = "/bmv2/sai_google.bmv2.p4info";
    private static final String SAI_GOOGLE_BMV2_JSON = "/bmv2/sai_google.bmv2.json";

    // CPU_PORT is required for bmv2 target
    private static final String CPU_PORT = "/bmv2/cpu_port.txt";

    public static final Collection<PiPipeconf> PIPECONFS = ImmutableList.of(
            buildPipeconfPins(SAI_PIPECONF_ID, SAI_P4INFO),
            buildPipeconfPins(FIXED_SAI_PIPECONF_ID, FIXED_SAI_P4INFO),
            buildPipeconfBmv2(SAI_GOOGLE_BMV2_PIPECONF_ID, SAI_GOOGLE_BMV2_P4INFO,
                              SAI_GOOGLE_BMV2_JSON));

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private PiPipeconfService piPipeconfService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private CoreService coreService;

    @Activate
    public void activate() {
        log.info("Started");
        coreService.registerApplication(APP_NAME);
        // Registers all pipeconf at component activation.
        PIPECONFS.forEach(piPipeconfService::register);
    }

    @Deactivate
    public void deactivate() {
        PIPECONFS.stream()
                .map(PiPipeconf::id)
                .forEach(piPipeconfService::unregister);
        log.info("Stopped");
    }

    private static PiPipeconf buildPipeconfPins(PiPipeconfId piPipeconfId, String p4Info) {
        final URL p4InfoUrl = SaiPipeconfLoader.class.getResource(p4Info);
        return DefaultPiPipeconf.builder()
                .withId(piPipeconfId)
                .withPipelineModel(parseP4Info(p4InfoUrl))
                .addBehaviour(PiPipelineInterpreter.class, SaiInterpreter.class)
                .addBehaviour(Pipeliner.class, SaiPipeliner.class)
                .addExtension(P4_INFO_TEXT, p4InfoUrl)
                .build();
    }

    private static PiPipeconf buildPipeconfBmv2(PiPipeconfId piPipeconfId, String p4Info,
                                            String bmv2Json) {
        final URL jsonUrl = SaiPipeconfLoader.class.getResource(bmv2Json);
        final URL p4InfoUrl = SaiPipeconfLoader.class.getResource(p4Info);
        final URL cpuPortUrl = SaiPipeconfLoader.class.getResource(CPU_PORT);

        return DefaultPiPipeconf.builder()
                .withId(piPipeconfId)
                .withPipelineModel(parseP4Info(p4InfoUrl))
                .addBehaviour(PiPipelineInterpreter.class, SaiInterpreter.class)
                .addBehaviour(Pipeliner.class, SaiPipeliner.class)
                .addExtension(P4_INFO_TEXT, p4InfoUrl)
                // Not actually needed if we do not plan to support BMv2
                .addExtension(BMV2_JSON, jsonUrl)
                .addExtension(CPU_PORT_TXT, cpuPortUrl)
                .build();
    }

    private static PiPipelineModel parseP4Info(URL p4InfoUrl) {
        try {
            return P4InfoParser.parse(p4InfoUrl);
        } catch (P4InfoParserException e) {
            throw new IllegalStateException(e);
        }
    }
}
