package com.group9.asaa.test

import com.group9.asaa.classes.test.TestReport

/**
 * Interface for Test Report Repository
 */
interface ITestReportRepository {
    /**
     * Get all test reports
     *
     * @return List of TestReport
     */
    fun getAllTestReports(): List<TestReport>

    /**
     * Get a specific test report by test run ID
     *
     * @param testRunId The ID of the test run
     * @return TestReport or null if not found
     */
    fun getTestReport(testRunId: String): TestReport?
}
