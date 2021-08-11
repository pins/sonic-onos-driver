package org.onosproject.drivers.sonic;

import gnmi.Gnmi;
import org.onosproject.drivers.gnmi.OpenConfigGnmiDeviceDescriptionDiscovery;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DefaultPortDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SonicOpenConfigGnmiDeviceDescriptionDiscovery extends OpenConfigGnmiDeviceDescriptionDiscovery {

    private static final Logger log = LoggerFactory
            .getLogger(SonicOpenConfigGnmiDeviceDescriptionDiscovery.class);

    @Override
    protected Gnmi.GetRequest buildPortStateRequest() {
        Gnmi.Path path = Gnmi.Path.newBuilder()
                .addElem(Gnmi.PathElem.newBuilder().setName("openconfig-interfaces:interfaces").build())
                .build();
        return Gnmi.GetRequest.newBuilder()
                .addPath(path)
                .setType(Gnmi.GetRequest.DataType.ALL)
                .setEncoding(Gnmi.Encoding.PROTO)
                .setPrefix(
                        Gnmi.Path.newBuilder().setTarget("OC_YANG")
                )
                .build();
    }

    @Override
    protected void parseInterfaceInfo(Gnmi.Update update,
                                                String ifName,
                                                DefaultPortDescription.Builder builder,
                                                DefaultAnnotations.Builder annotationsBuilder) {
        super.parseInterfaceInfo(update, ifName, builder, annotationsBuilder);
        final Gnmi.Path path = update.getPath();
        final List<Gnmi.PathElem> elems = path.getElemList();
        final Gnmi.TypedValue val = update.getVal();
        if (elems.size() == 4) {
            // /openconfig-interfaces:interfaces/interface/state/openconfig-pins-interfaces:id
            final String pathElemName = elems.get(3).getName();
            switch (pathElemName) {
                case "id":
                    builder.withPortNumber(PortNumber.portNumber(val.getUintVal(), ifName));
                    return;
                default:
                    break;
            }
        }
    }
}
