package org.onosproject.drivers.sonic;

import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.driver.DriverData;
import org.onosproject.net.driver.DriverHandler;
import org.onosproject.net.driver.HandlerBehaviour;

public abstract class AbstractSonicBehaviour<B extends HandlerBehaviour>
        extends AbstractHandlerBehaviour {

    protected B p4runtime;
    protected B gnmi;
    //protected B gnoi;

    public AbstractSonicBehaviour(B p4runtime, B gnmi) {
        this.p4runtime = p4runtime;
        this.gnmi = gnmi;
        //this.gnoi = gnoi;
    }

    @Override
    public void setHandler(DriverHandler handler) {
        super.setHandler(handler);
        p4runtime.setHandler(handler);
        gnmi.setHandler(handler);
        //gnoi.setHandler(handler);
    }

    @Override
    public void setData(DriverData data) {
        super.setData(data);
        p4runtime.setData(data);
        gnmi.setData(data);
        //gnoi.setData(data);
    }
}