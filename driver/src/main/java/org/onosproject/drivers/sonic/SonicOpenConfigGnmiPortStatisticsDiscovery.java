package org.onosproject.drivers.sonic;

import gnmi.Gnmi;
import org.onosproject.drivers.gnmi.OpenConfigGnmiPortStatisticsDiscovery;

public class SonicOpenConfigGnmiPortStatisticsDiscovery extends OpenConfigGnmiPortStatisticsDiscovery {

    @Override
    protected Gnmi.GetRequest.Builder newGetRequestBuilder() {
        Gnmi.GetRequest.Builder getRequest = Gnmi.GetRequest.newBuilder();
        getRequest.setEncoding(Gnmi.Encoding.PROTO);
        getRequest.setPrefix(
                Gnmi.Path.newBuilder().setTarget("OC_YANG")
        );
        return getRequest;
    }

    @Override
    protected Gnmi.Path interfaceCounterPath(String portName) {
        return Gnmi.Path.newBuilder()
                .addElem(Gnmi.PathElem.newBuilder().setName("openconfig-interfaces:interfaces").build())
                .addElem(Gnmi.PathElem.newBuilder().setName("interface")
                        .putKey("name", portName).build())
                .addElem(Gnmi.PathElem.newBuilder().setName("state").build())
                .addElem(Gnmi.PathElem.newBuilder().setName("counters").build())
                .build();
    }
}
