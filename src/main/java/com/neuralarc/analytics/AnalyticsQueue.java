package com.neuralarc.analytics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AnalyticsQueue {
    private final BlockingQueue<AnalyticsEvent> queue = new LinkedBlockingQueue<>();
    private final Path diskFile;

    public AnalyticsQueue(Path diskFile) {
        this.diskFile = diskFile;
    }

    public void enqueue(AnalyticsEvent event) {
        queue.offer(event);
    }

    public AnalyticsEvent take() throws InterruptedException {
        return queue.take();
    }

    public void persist(AnalyticsEvent event) {
        try {
            Files.writeString(diskFile, event.toJson() + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    public List<String> loadPersisted() {
        try {
            if (Files.exists(diskFile)) {
                return Files.readAllLines(diskFile);
            }
        } catch (IOException ignored) {
        }
        return new ArrayList<>();
    }
}
