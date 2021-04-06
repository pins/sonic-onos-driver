package org.onosproject.pipelines.sai;

import org.onosproject.net.DeviceId;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.driver.DriverHandler;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.model.PiPipeconfId;
import org.onosproject.net.pi.service.PiPipeconfService;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Abstract implementation of HandlerBehaviour for the SAI pipeconf
 * behaviors.
 * <p>
 * All sub-classes must implement a default constructor, used by the abstract
 * projectable model (i.e., {@link org.onosproject.net.Device#as(Class)}.
 */
public class AbstractSaiHandlerBehavior extends AbstractHandlerBehaviour {
    protected final Logger log = getLogger(getClass());

    protected SaiCapabilities capabilities;

    /**
     * Creates a new instance of this behavior with the given capabilities.
     * Note: this constructor should be invoked only by other classes of this
     * package that can retrieve capabilities on their own.
     * <p>
     * When using the abstract projectable model (i.e., {@link
     * org.onosproject.net.Device#as(Class)}, capabilities will be set by the
     * driver manager when calling {@link #setHandler(DriverHandler)})
     *
     * @param capabilities capabilities
     */
    protected AbstractSaiHandlerBehavior(SaiCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * Create a new instance of this behaviour. Used by the abstract projectable
     * model (i.e., {@link org.onosproject.net.Device#as(Class)}.
     */
    public AbstractSaiHandlerBehavior() {
        // Do nothing
    }

    @Override
    public void setHandler(DriverHandler handler) {
        super.setHandler(handler);
        final PiPipeconfService pipeconfService = handler().get(PiPipeconfService.class);
        setCapabilitiesFromHandler(handler().data().deviceId(), pipeconfService);
    }

    private void setCapabilitiesFromHandler(
            DeviceId deviceId, PiPipeconfService pipeconfService) {
        checkNotNull(deviceId);
        checkNotNull(pipeconfService);
        // Get pipeconf capabilities.
        final PiPipeconf pipeconf = pipeconfService.getPipeconf(deviceId)
                .orElse(null);
        if (pipeconf == null) {
            throw new IllegalStateException(format(
                    "Unable to get pipeconf of device %s", deviceId.toString()));
        }
        final PiPipeconfId pipeconfId = pipeconf.id();
        if (pipeconfService.getPipeconf(pipeconfId).isEmpty()) {
            throw new IllegalStateException(format(
                    "Pipeconf '%s' is not registered ", pipeconfId));
        }
        this.capabilities = new SaiCapabilities(
                pipeconfService.getPipeconf(pipeconfId).get());
    }
}
