package com.group9.asaa.system_observer

import com.group9.asaa.classes.observer.Status

interface IStatusService {

    fun storeStatus(status: Status): Int

    fun retrieveStatus(statusId: String): Status

    fun fetchStatusForTestRun(testRunId: String): List<Status>
}


