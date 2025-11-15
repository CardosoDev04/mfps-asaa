package com.group9.asaa.test

import com.group9.asaa.classes.test.TestReport
import org.springframework.stereotype.Service

@Service
class TestReportService(
    private val repository: ITestReportRepository
): ITestReportService {
    override fun getAllTestReports(): List<TestReport> =
        repository.getAllTestReports()

    override fun getTestReport(testRunId: String): TestReport? =
        repository.getTestReport(testRunId)
}
