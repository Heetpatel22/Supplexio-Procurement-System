package com.axaltacoating.listener;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public class CommoditiesScraperListener implements ServletContextListener {
    private static final Logger LOGGER = Logger.getLogger(CommoditiesScraperListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Runnable scraperTask = () -> {
            String scriptPath = sce.getServletContext().getRealPath("/WEB-INF/data/scrape_copper_prices.py");
            if (scriptPath == null || !new File(scriptPath).exists()) {
                scriptPath = sce.getServletContext().getRealPath("/../../scrape_copper_prices.py");
            }
            if (scriptPath == null || !new File(scriptPath).exists()) {
                File localFile = new File("scrape_copper_prices.py");
                if (localFile.exists()) {
                    scriptPath = localFile.getAbsolutePath();
                }
            }

            String pythonExe = System.getenv("PYTHON_EXE");
            if (pythonExe == null || pythonExe.trim().isEmpty()) {
                pythonExe = "python3";
            }

            while (true) {
                try {
                    if (scriptPath != null && new File(scriptPath).exists()) {
                        ProcessBuilder pb = new ProcessBuilder(pythonExe, scriptPath);
                        pb.inheritIO();
                        try {
                            pb.start().waitFor();
                        } catch (IOException e) {
                            if ("python3".equals(pythonExe)) {
                                pythonExe = "python";
                                pb = new ProcessBuilder(pythonExe, scriptPath);
                                pb.inheritIO();
                                pb.start().waitFor();
                            }
                        }
                    }
                    Thread.sleep(15 * 60 * 1000); // 15 minutes
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Commodities scraper task paused: {0}", e.getMessage());
                    try {
                        Thread.sleep(15 * 60 * 1000);
                    } catch (InterruptedException ignored) {}
                }
            }
        };
        Thread scraperThread = new Thread(scraperTask);
        scraperThread.setName("CommoditiesScraperThread");
        scraperThread.setDaemon(true);
        scraperThread.start();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No cleanup needed
    }
}
