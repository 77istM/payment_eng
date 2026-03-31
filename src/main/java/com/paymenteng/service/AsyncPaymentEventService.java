package com.paymenteng.service;

import com.paymenteng.model.PaymentEventMessage;
import com.paymenteng.model.PaymentEventType;
import com.paymenteng.model.RailPayment;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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

    private final RailPaymentService railPaymentService;

    private final int consumerCount;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final double backoffMultiplier;
    private final int queueCapacity;
    private final long pollTimeoutMs;
    private final int retrySchedulerThreadCount;

    private final BlockingQueue<PaymentEventMessage> queue;
    private final List<PaymentEventMessage> deadLetterQueue = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong consumedCount = new AtomicLong(0);
    private final AtomicLong retryCount = new AtomicLong(0);
    private final Map<String, LocalDateTime> inFlightEventStartedAt = new ConcurrentHashMap<>();

    private ScheduledExecutorService consumerExecutor;
    private ScheduledExecutorService retryScheduler;

    public AsyncPaymentEventService(RailPaymentService railPaymentService,
                                    @Value("${payment.async.consumer-count:4}") int consumerCount,
                                    @Value("${payment.async.max-retries:5}") int maxRetries,
                                    @Value("${payment.async.initial-backoff-ms:100}") long initialBackoffMs,
                                    @Value("${payment.async.backoff-multiplier:2.0}") double backoffMultiplier,
                                    @Value("${payment.async.queue-capacity:10000}") int queueCapacity,
                                    @Value("${payment.async.poll-timeout-ms:100}") long pollTimeoutMs,
                                    @Value("${payment.async.retry-scheduler-threads:2}") int retrySchedulerThreadCount) {
        this.railPaymentService = railPaymentService;
        this.consumerCount = Math.max(1, consumerCount);
        this.maxRetries = Math.max(0, maxRetries);
        this.initialBackoffMs = Math.max(1, initialBackoffMs);
        this.backoffMultiplier = Math.max(1.0d, backoffMultiplier);
        this.queueCapacity = Math.max(100, queueCapacity);
        this.pollTimeoutMs = Math.max(10, pollTimeoutMs);
        this.retrySchedulerThreadCount = Math.max(1, retrySchedulerThreadCount);
        this.queue = new LinkedBlockingQueue<>(this.queueCapacity);
    }

    @PostConstruct
    public void start() {
        if (!running.compareAndSet(false, true)) {
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
        if (paymentId == null) {
            return false;
        }
        return queue.offer(new PaymentEventMessage(paymentId, PaymentEventType.SETTLEMENT_REQUESTED, 0));
    }

    public Map<String, Object> queueStats() {
        return Map.of(
                "consumers", consumerCount,
                "ready", running.get(),
                "queueDepth", queue.size(),
                "queueCapacity", queueCapacity,
                "queueRemainingCapacity", queue.remainingCapacity(),
                "consumed", consumedCount.get(),
                "retried", retryCount.get(),
                "deadLetterDepth", deadLetterQueue.size(),
                "inFlight", inFlightEventStartedAt.size(),
                "pollTimeoutMs", pollTimeoutMs,
                "retrySchedulerThreads", retrySchedulerThreadCount
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
        inFlightEventStartedAt.put(event.getEventId(), LocalDateTime.now());
        try {
            if (event.getEventType() == PaymentEventType.SETTLEMENT_REQUESTED) {
                RailPayment payment = railPaymentService.processSettlement(event.getPaymentId());
                railPaymentService.logAsyncEvent(payment.getId(), event.getAttempt(), "EVENT_PROCESSED", "Settlement event processed");
                consumedCount.incrementAndGet();
            }
        } catch (TransientSettlementException ex) {
            scheduleRetry(event, ex.getMessage());
        } catch (RuntimeException ex) {
            scheduleRetry(event, ex.getMessage() == null ? "Unexpected runtime error" : ex.getMessage());
        } finally {
            inFlightEventStartedAt.remove(event.getEventId());
        }
    }

    private void scheduleRetry(PaymentEventMessage event, String reason) {
        int nextAttempt = event.getAttempt() + 1;
        if (nextAttempt > maxRetries) {
            deadLetterQueue.add(event);
            railPaymentService.logAsyncEvent(event.getPaymentId(), event.getAttempt(), "DLQ", reason);
            return;
        }

        retryCount.incrementAndGet();
        long delay = computeBackoffDelay(nextAttempt);
        railPaymentService.logAsyncEvent(event.getPaymentId(), event.getAttempt(), "RETRY_SCHEDULED",
                "Retry " + nextAttempt + " in " + delay + "ms: " + reason);

        retryScheduler.schedule(() -> queue.offer(new PaymentEventMessage(event.getPaymentId(), event.getEventType(), nextAttempt)),
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
}
