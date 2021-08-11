package org.onosproject.drivers.sonic;

import gnmi.Gnmi;
import org.onosproject.drivers.gnmi.OpenConfigGnmiPortAdminBehaviour;
import org.onosproject.net.PortNumber;

public class SonicOpenConfigGnmiPortAdminBehaviour extends OpenConfigGnmiPortAdminBehaviour {

    @Override
    protected Gnmi.Path interfaceGnmiPath(PortNumber portNumber) {
        final Gnmi.Path path = Gnmi.Path.newBuilder()
                .addElem(Gnmi.PathElem.newBuilder().setName("openconfig-interfaces:interfaces").build())
                .addElem(Gnmi.PathElem.newBuilder().setName("interface")
                        .putKey("name", portNumber.name()).build())
                .addElem(Gnmi.PathElem.newBuilder().setName("config").build())
                .addElem(Gnmi.PathElem.newBuilder().setName("enabled").build())
                .build();
        return path;
    }
}
