package org.onosproject.drivers.sonic;

import org.onosproject.drivers.gnmi.GnmiHandshaker;
import org.onosproject.drivers.p4runtime.P4RuntimeHandshaker;
import org.onosproject.net.MastershipRole;
import org.onosproject.net.device.DeviceAgentListener;
import org.onosproject.net.device.DeviceHandshaker;
import org.onosproject.net.provider.ProviderId;

import java.util.concurrent.CompletableFuture;

/**
 * Implementation of DeviceHandshaker for Stratum device.
 */
public class SonicHandshaker
        extends AbstractSonicBehaviour<DeviceHandshaker>
        implements DeviceHandshaker {

    public SonicHandshaker() {
        super(new P4RuntimeHandshaker(), new GnmiHandshaker());
    }

    @Override
    public boolean connect() {
        return p4runtime.connect() && gnmi.connect();
    }

    @Override
    public boolean hasConnection() {
        return p4runtime.hasConnection() && gnmi.hasConnection();
    }

    @Override
    public void disconnect() {
        p4runtime.disconnect();
        gnmi.disconnect();
    }

    @Override
    public boolean isReachable() {
        // Reachability is mainly used for mastership contests and it's a
        // prerequisite for availability.
        return p4runtime.isReachable();
        //return gnmi.isReachable();
    }

    @Override
    public CompletableFuture<Boolean> probeReachability() {
        return p4runtime.probeReachability();
        //CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();
        //completableFuture.complete(true);
        //return completableFuture;
    }

    @Override
    public boolean isAvailable() {
        // Availability concerns packet forwarding, hence we consider only
        // P4Runtime.
        return p4runtime.isAvailable();
        //return gnmi.isReachable();
    }

    @Override
    public CompletableFuture<Boolean> probeAvailability() {
        return p4runtime.probeAvailability();
        //CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();
        //completableFuture.complete(true);
        //return completableFuture;
    }

    @Override
    public void roleChanged(MastershipRole newRole) {
        p4runtime.roleChanged(newRole);
    }

    @Override
    public void roleChanged(int preference, long term) {
        p4runtime.roleChanged(preference, term);
    }

    @Override
    public MastershipRole getRole() {
        return p4runtime.getRole();
    }

    @Override
    public void addDeviceAgentListener(ProviderId providerId, DeviceAgentListener listener) {
        p4runtime.addDeviceAgentListener(providerId, listener);
    }

    @Override
    public void removeDeviceAgentListener(ProviderId providerId) {
        p4runtime.removeDeviceAgentListener(providerId);
    }
}
