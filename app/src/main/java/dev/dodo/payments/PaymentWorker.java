package dev.dodo.payments;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.workers-enabled", havingValue = "true")
public class PaymentWorker {
  private final PaymentAttemptRepository attempts;
  private final PaymentProcessorClient processor;
  private final PaymentService service;
  private final ExecutorService executor = Executors.newFixedThreadPool(4);
  private final Semaphore slots = new Semaphore(4);
  private volatile boolean stopping;

  public PaymentWorker(
      PaymentAttemptRepository attempts, PaymentProcessorClient processor, PaymentService service) {
    this.attempts = attempts;
    this.processor = processor;
    this.service = service;
  }

  @Scheduled(fixedDelay = 1000)
  public void poll() {
    if (stopping) return;
    while (slots.tryAcquire()) {
      try {
        var claim = attempts.claim();
        if (claim.isEmpty()) {
          slots.release();
          return;
        }
        executor.submit(
            () -> {
              try {
                service.finish(claim.get(), processor.execute(claim.get()));
              } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn(
                        "payment_job_incomplete attempt_id={} type={}",
                        claim.get().get("id"),
                        e.getClass().getSimpleName());
              } finally {
                slots.release();
              }
            });
      } catch (Exception e) {
        slots.release();
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("payment_claim_failed type={}", e.getClass().getSimpleName());
        return;
      }
    }
  }

  @PreDestroy
  public void close() throws InterruptedException {
    stopping = true;
    executor.shutdown();
    if (!executor.awaitTermination(15, TimeUnit.SECONDS)) executor.shutdownNow();
  }
}
