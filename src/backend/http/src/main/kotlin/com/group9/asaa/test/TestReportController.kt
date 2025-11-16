package com.group9.asaa.test

import com.group9.asaa.classes.test.TestReport
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/test-reports")
class TestReportController(
    private val testReportService: ITestReportService
) {
    @GetMapping("/all")
    fun getAllTestReports(): ResponseEntity<List<TestReport>> {
        val testReports = testReportService.getAllTestReports()
        return ResponseEntity.ok(testReports)
    }

    @GetMapping("/{id}")
    fun getTestReportById(@PathVariable id: String): ResponseEntity<TestReport?> {
        val testReport = testReportService.getTestReport(id)
        return if (testReport != null) {
            ResponseEntity.ok(testReport)
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
