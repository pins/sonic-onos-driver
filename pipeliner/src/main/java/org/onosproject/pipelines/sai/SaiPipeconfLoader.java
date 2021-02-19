/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.pipelines.sai;

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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;

import java.net.URL;

import static org.onosproject.net.pi.model.PiPipeconf.ExtensionType.*;
import static org.slf4j.LoggerFactory.getLogger;


@Component(immediate = true)
public final class SaiPipeconfLoader {

    private static Logger log = getLogger(SaiPipeconfLoader.class);

    private static final String APP_NAME = "org.onosproject.pipelines.sai";
    private static final PiPipeconfId SAI_PIPECONF_ID = new PiPipeconfId("org.onosproject.pipelines.sai");
    private static final String SAI_JSON_PATH = "/bmv2.json";
    private static final String SAI_P4INFO = "/p4info.txt";
    // TODO (daniele): Is the CPU port really needed for sai.p4?
    private static final String CPU_PORT = "/cpu_port.txt";

    public static final PiPipeconf SAI_PIPECONF = buildSaiPipeconf();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private PiPipeconfService piPipeconfService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private CoreService coreService;

    @Activate
    public void activate() {
        log.info("Started");
        coreService.registerApplication(APP_NAME);
        // Registers all pipeconf at component activation.
        piPipeconfService.register(SAI_PIPECONF);
    }

    @Deactivate
    public void deactivate() {
        piPipeconfService.unregister(SAI_PIPECONF_ID);
        log.info("Stopped");
    }

    private static PiPipeconf buildSaiPipeconf() {
        final URL jsonUrl = SaiPipeconfLoader.class.getResource(SAI_JSON_PATH);
        final URL p4InfoUrl = SaiPipeconfLoader.class.getResource(SAI_P4INFO);
        final URL cpuPortUrl = SaiPipeconfLoader.class.getResource(CPU_PORT);

        return DefaultPiPipeconf.builder()
                .withId(SAI_PIPECONF_ID)
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
