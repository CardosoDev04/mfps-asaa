import type { TestReport } from "../@types/TestReport";

export async function getAllTestReports(): Promise<TestReport[]> {
  const res = await fetch(`http://localhost:8080/test-reports/all`, {
    method: "GET",
  });
  const reports: TestReport[] = await res.json();
  return reports;
}

export async function getTestReportById(id: string): Promise<TestReport> {
  const res = await fetch(`http://localhost:8080/test-reports/${id}`, {
    method: "GET",
  });
  const report: TestReport = await res.json();
  return report;
}