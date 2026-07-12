package com.liteworkflow.infra.export;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportJobStore {

    private final ExportJobRepository jobs;
    private final ExportOutboxRepository outbox;

    public ExportJobStore(ExportJobRepository jobs, ExportOutboxRepository outbox) {
        this.jobs = jobs;
        this.outbox = outbox;
    }

    @Transactional
    public void create(ExportJob job, ExportOutboxEvent event) {
        jobs.save(job);
        outbox.save(event);
    }
}
