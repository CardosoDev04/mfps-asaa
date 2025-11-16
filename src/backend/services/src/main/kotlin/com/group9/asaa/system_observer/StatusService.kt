package com.group9.asaa.system_observer

import com.group9.asaa.classes.observer.Status
import org.springframework.stereotype.Service

@Service
class StatusService(
    private val repository: IStatusRepository
) : IStatusService {
    override fun storeStatus(status: Status): Int =
        repository.storeStatus(status)

    override fun retrieveStatus(statusId: String): Status =
        repository.retrieveStatus(statusId)

    override fun fetchStatusForTestRun(testRunId: String): List<Status> =
        repository.fetchStatusForTestRun(testRunId)
}


