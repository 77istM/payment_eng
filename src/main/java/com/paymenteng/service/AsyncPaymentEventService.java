package com.paymenteng.service;

import com.paymenteng.model.PaymentEventMessage;
import com.paymenteng.model.PaymentEventType;
import com.paymenteng.model.RailPayment;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory, Kafka-like queue simulation with consumers, exponential backoff, and DLQ.
 */
@Service
public class AsyncPaymentEventService {

    private static final Logger log = LoggerFactory.getLogger(AsyncPaymentEventService.class);
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final RailPaymentService railPaymentService;
    private final AvailabilityGateService availabilityGateService;
    private final MeterRegistry meterRegistry;

    private final int consumerCount;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final double backoffMultiplier;
    private final int queueCapacity;
    private final long pollTimeoutMs;
    private final int retrySchedulerThreadCount;
    private final double autoDrainThreshold;

    private final BlockingQueue<PaymentEventMessage> queue;
    private final List<PaymentEventMessage> deadLetterQueue = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong consumedCount = new AtomicLong(0);
    private final AtomicLong retryCount = new AtomicLong(0);
    private final AtomicLong queueFullCount = new AtomicLong(0);
    private final Map<String, LocalDateTime> inFlightEventStartedAt = new ConcurrentHashMap<>();
    private final Timer settlementEventTimer;

    private ScheduledExecutorService consumerExecutor;
    private ScheduledExecutorService retryScheduler;

    public AsyncPaymentEventService(RailPaymentService railPaymentService,
                                    AvailabilityGateService availabilityGateService,
                                    MeterRegistry meterRegistry,
                                    @Value("${payment.async.consumer-count:4}") int consumerCount,
                                    @Value("${payment.async.max-retries:5}") int maxRetries,
                                    @Value("${payment.async.initial-backoff-ms:100}") long initialBackoffMs,
                                    @Value("${payment.async.backoff-multiplier:2.0}") double backoffMultiplier,
                                    @Value("${payment.async.queue-capacity:10000}") int queueCapacity,
                                    @Value("${payment.async.poll-timeout-ms:100}") long pollTimeoutMs,
                                    @Value("${payment.async.retry-scheduler-threads:2}") int retrySchedulerThreadCount,
                                    @Value("${payment.async.auto-drain-threshold:0.8}") double autoDrainThreshold) {
        this.railPaymentService = railPaymentService;
        this.availabilityGateService = availabilityGateService;
        this.meterRegistry = meterRegistry;
        this.consumerCount = Math.max(0, consumerCount);
        this.maxRetries = Math.max(0, maxRetries);
        this.initialBackoffMs = Math.max(1, initialBackoffMs);
        this.backoffMultiplier = Math.max(1.0d, backoffMultiplier);
        this.queueCapacity = Math.max(100, queueCapacity);
        this.pollTimeoutMs = Math.max(10, pollTimeoutMs);
        this.retrySchedulerThreadCount = Math.max(1, retrySchedulerThreadCount);
        this.autoDrainThreshold = Math.min(1.0d, Math.max(0.1d, autoDrainThreshold));
        this.queue = new LinkedBlockingQueue<>(this.queueCapacity);

                this.settlementEventTimer = Timer.builder("payment.async.settlement.event.duration")
                    .description("Settlement event processing duration")
                    .register(this.meterRegistry);

                Gauge.builder("payment.async.queue.depth", queue, BlockingQueue::size)
                    .description("Current async settlement queue depth")
                    .register(this.meterRegistry);
                Gauge.builder("payment.async.queue.remaining", queue, BlockingQueue::remainingCapacity)
                    .description("Remaining async settlement queue capacity")
                    .register(this.meterRegistry);
                Gauge.builder("payment.async.inflight", inFlightEventStartedAt, Map::size)
                    .description("Events currently in-flight")
                    .register(this.meterRegistry);
                Gauge.builder("payment.async.dlq.depth", deadLetterQueue, List::size)
                    .description("Dead letter queue depth")
                    .register(this.meterRegistry);
                Gauge.builder("payment.async.queue.full.count", queueFullCount, AtomicLong::get)
                    .description("Total rejected publishes due to full queue")
                    .register(this.meterRegistry);
                Gauge.builder("payment.async.retry.count", retryCount, AtomicLong::get)
                    .description("Total scheduled retries")
                    .register(this.meterRegistry);
                Gauge.builder("payment.async.consumed.count", consumedCount, AtomicLong::get)
                    .description("Total consumed events")
                    .register(this.meterRegistry);
    }

    @PostConstruct
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        if (consumerCount == 0) {
            return;
        }

        this.consumerExecutor = Executors.newScheduledThreadPool(consumerCount);
        this.retryScheduler = Executors.newScheduledThreadPool(retrySchedulerThreadCount);

        for (int i = 0; i < consumerCount; i++) {
            consumerExecutor.execute(this::consumerLoop);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (consumerExecutor != null) {
            consumerExecutor.shutdown();
            awaitTermination(consumerExecutor, Duration.ofSeconds(10));
        }
        if (retryScheduler != null) {
            retryScheduler.shutdown();
            awaitTermination(retryScheduler, Duration.ofSeconds(10));
        }
    }

    public boolean publishSettlementRequested(Long paymentId) {
        String correlationId = MDC.get(MDC_CORRELATION_ID);
        return publishSettlementRequested(paymentId, correlationId);
    }

    public boolean publishSettlementRequested(Long paymentId, String correlationId) {
        if (paymentId == null) {
            return false;
        }
        boolean offered = queue.offer(new PaymentEventMessage(paymentId, PaymentEventType.SETTLEMENT_REQUESTED, 0, correlationId));
        if (!offered) {
            queueFullCount.incrementAndGet();
            log.warn("Queue full while publishing settlement event paymentId={} correlationId={}", paymentId, correlationId);
            availabilityGateService.startDrain();
            return false;
        }

        if (queueDepthRatio() >= autoDrainThreshold) {
            log.warn("Queue pressure exceeded threshold depth={} capacity={} threshold={} triggering drain mode",
                    queue.size(), queueCapacity, autoDrainThreshold);
            availabilityGateService.startDrain();
        }
        return true;
    }

    public Map<String, Object> queueStats() {
        return Map.ofEntries(
            Map.entry("consumers", consumerCount),
            Map.entry("ready", running.get()),
            Map.entry("queueDepth", queue.size()),
            Map.entry("queueCapacity", queueCapacity),
            Map.entry("queueRemainingCapacity", queue.remainingCapacity()),
            Map.entry("consumed", consumedCount.get()),
            Map.entry("retried", retryCount.get()),
            Map.entry("queueFullCount", queueFullCount.get()),
            Map.entry("deadLetterDepth", deadLetterQueue.size()),
            Map.entry("inFlight", inFlightEventStartedAt.size()),
            Map.entry("pollTimeoutMs", pollTimeoutMs),
            Map.entry("retrySchedulerThreads", retrySchedulerThreadCount),
            Map.entry("autoDrainThreshold", autoDrainThreshold)
        );
    }

    public List<PaymentEventMessage> deadLetters() {
        return new ArrayList<>(deadLetterQueue);
    }

    private void consumerLoop() {
        while (running.get()) {
            try {
                PaymentEventMessage event = queue.poll(pollTimeoutMs, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }
                handleEvent(event);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void handleEvent(PaymentEventMessage event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        if (event.getCorrelationId() != null && !event.getCorrelationId().isBlank()) {
            MDC.put(MDC_CORRELATION_ID, event.getCorrelationId());
        }
        inFlightEventStartedAt.put(event.getEventId(), LocalDateTime.now());
        try {
            if (event.getEventType() == PaymentEventType.SETTLEMENT_REQUESTED) {
                RailPayment payment = railPaymentService.processSettlement(event.getPaymentId());
                railPaymentService.logAsyncEvent(payment.getId(), event.getAttempt(), "EVENT_PROCESSED", "Settlement event processed");
                consumedCount.incrementAndGet();
                log.info("Processed async settlement event paymentId={} eventId={} attempt={} correlationId={}",
                        event.getPaymentId(), event.getEventId(), event.getAttempt(), event.getCorrelationId());
            }
        } catch (TransientSettlementException ex) {
            scheduleRetry(event, ex.getMessage());
        } catch (RuntimeException ex) {
            scheduleRetry(event, ex.getMessage() == null ? "Unexpected runtime error" : ex.getMessage());
        } finally {
            inFlightEventStartedAt.remove(event.getEventId());
            MDC.remove(MDC_CORRELATION_ID);
            sample.stop(settlementEventTimer);
        }
    }

    private void scheduleRetry(PaymentEventMessage event, String reason) {
        int nextAttempt = event.getAttempt() + 1;
        if (nextAttempt > maxRetries) {
            deadLetterQueue.add(event);
            railPaymentService.logAsyncEvent(event.getPaymentId(), event.getAttempt(), "DLQ", reason);
            log.error("Moved event to DLQ paymentId={} eventId={} attempt={} reason={} correlationId={}",
                    event.getPaymentId(), event.getEventId(), event.getAttempt(), reason, event.getCorrelationId());
            return;
        }

        retryCount.incrementAndGet();
        long delay = computeBackoffDelay(nextAttempt);
        railPaymentService.logAsyncEvent(event.getPaymentId(), event.getAttempt(), "RETRY_SCHEDULED",
                "Retry " + nextAttempt + " in " + delay + "ms: " + reason);

        retryScheduler.schedule(() -> queue.offer(new PaymentEventMessage(event.getPaymentId(), event.getEventType(), nextAttempt, event.getCorrelationId())),
                delay,
                TimeUnit.MILLISECONDS);
    }

    private long computeBackoffDelay(int attempt) {
        double exponential = initialBackoffMs * Math.pow(backoffMultiplier, Math.max(0, attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(0, initialBackoffMs);
        long bounded = Math.min((long) exponential + jitter, 30_000L);
        return Math.max(initialBackoffMs, bounded);
    }

    private void awaitTermination(ScheduledExecutorService executor, Duration timeout) {
        try {
            executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private double queueDepthRatio() {
        return queueCapacity == 0 ? 0.0d : (double) queue.size() / (double) queueCapacity;
    }
}
