/*
 * Copyright 2020-present Open Networking Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package org.onosproject.drivers.sonic;

import org.onosproject.net.driver.AbstractDriverLoader;
import org.osgi.service.component.annotations.Component;

/**
 * Loader for SONiC/PINS device drivers.
 */
@Component(immediate = true)
public class SonicDriversLoader extends AbstractDriverLoader {

    public SonicDriversLoader() {
        super("/sonic-drivers.xml");
    }
}
