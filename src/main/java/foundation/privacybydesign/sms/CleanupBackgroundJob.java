package foundation.privacybydesign.sms;

import foundation.privacybydesign.sms.ratelimit.MemoryRateLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Clean up in-memory data structures once in a while.
 */
@WebListener
public class CleanupBackgroundJob implements ServletContextListener {
    private static Logger logger = LoggerFactory.getLogger(CleanupBackgroundJob.class);
    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        logger.info("Setting up background cleanup task");
        scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                try {
                    TokenManager.getInstance().periodicCleanup();
                    MemoryRateLimit.getInstance().periodicCleanup();
                } catch (Exception e) {
                    logger.error("Failed to run periodic cleanup:");
                    e.printStackTrace();
                }
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        scheduler.shutdownNow();
    }
}
