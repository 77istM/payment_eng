package com.paymenteng.service;

import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controls whether the service should accept new payment traffic.
 */
@Service
public class AvailabilityGateService {

    private final ApplicationContext applicationContext;
    private final AtomicBoolean acceptingTraffic = new AtomicBoolean(true);

    public AvailabilityGateService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public boolean isAcceptingTraffic() {
        return acceptingTraffic.get();
    }

    public void startDrain() {
        if (acceptingTraffic.compareAndSet(true, false)) {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
        }
    }

    public void stopDrain() {
        if (acceptingTraffic.compareAndSet(false, true)) {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }
}
