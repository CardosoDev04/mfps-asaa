import { useEffect, useMemo, useRef, useState } from "react";
import type { TestReport } from "../../../@types/TestReport";
import { getAllTestReports, getTestReportById } from "../../../services/test";

type DetailsMap = Record<string, TestReport>;
type BoolMap = Record<string, boolean>;
type ErrorMap = Record<string, string | undefined>;

export default function TestReportsDashboard() {
  const [reports, setReports] = useState<TestReport[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [expanded, setExpanded] = useState<BoolMap>({});
  const [details, setDetails] = useState<DetailsMap>({});
  const [loadingDetails, setLoadingDetails] = useState<BoolMap>({});
  const [detailsError, setDetailsError] = useState<ErrorMap>({});
  const isFirstLoad = useRef(true);

  useEffect(() => {
    let cancelled = false;
  
    async function loadReports() {
      if (isFirstLoad.current) {
        setLoading(true);
      }
      setLoadError(null);
      try {
        const list = await getAllTestReports();
        if (!cancelled) {
          const sorted = [...list].sort((a, b) =>
            a.date < b.date ? 1 : a.date > b.date ? -1 : 0
          );
          // Ensure a new array reference is created
          setReports(() => [...sorted]);
        }
      } catch (err) {
        console.error("Failed to fetch test reports", err);
        if (!cancelled) setLoadError("Failed to load test reports.");
      } finally {
        if (!cancelled && isFirstLoad) {
          setLoading(false);
          isFirstLoad.current = false;
        }
      }
    }
  
    loadReports();
  
    const intervalId = setInterval(() => {
      loadReports();
    }, 2000);
  
    return () => {
      cancelled = true;
      clearInterval(intervalId);
    };
  }, []);

  function toggleExpand(id: string) {
    const nextExpanded = !expanded[id];
    setExpanded((prev) => ({ ...prev, [id]: nextExpanded }));

    if (nextExpanded && !details[id]) {
      loadDetails(id);
    }
  }

  async function loadDetails(id: string) {
    setLoadingDetails((prev) => ({ ...prev, [id]: true }));
    setDetailsError((prev) => ({ ...prev, [id]: undefined }));

    try {
      const full = await getTestReportById(id);
      setDetails((prev) => ({ ...prev, [id]: full }));
    } catch (err) {
      console.error(`Failed to load details for report ${id}`, err);
      setDetailsError((prev) => ({
        ...prev,
        [id]: "Failed to load report details.",
      }));
    } finally {
      setLoadingDetails((prev) => ({ ...prev, [id]: false }));
    }
  }

  return (
    <div className="mx-auto max-w-5xl p-4 font-sans">
      <h1 className="mb-3 text-xl font-bold text-slate-900">
        Test Reports
      </h1>

      {loading && <p className="text-sm text-slate-600">Loading reports…</p>}

      {loadError && (
        <p className="mb-3 text-sm text-red-600">
          {loadError}
        </p>
      )}

      {!loading && !loadError && reports.length === 0 && (
        <p className="text-sm text-slate-500">No test reports found.</p>
      )}

      <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
        {reports.map((r) => {
          const isExpanded = !!expanded[r.id];
          const detailsForReport = details[r.id] ?? r;
          const isLoadingDetails = !!loadingDetails[r.id];
          const err = detailsError[r.id];

          return (
            <div
              key={r.id}
              className="flex flex-col rounded-xl border border-slate-200 bg-white p-3 shadow-sm"
            >
              <div className="mb-2 flex items-center justify-between gap-2">
                <strong
                  className="max-w-[70%] truncate text-sm text-slate-900"
                  title={r.id}
                >
                  {r.id}
                </strong>
                <span className="whitespace-nowrap text-xs text-slate-500">
                  {formatDate(r.date)}
                </span>
              </div>

              <button
                type="button"
                onClick={() => toggleExpand(r.id)}
                className="inline-flex w-fit items-center justify-center rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-medium text-slate-800 transition hover:bg-slate-100"
              >
                {isExpanded ? "Hide details" : "Show details"}
              </button>

              {isExpanded && (
                <div className="mt-3 text-xs text-slate-800">
                  {isLoadingDetails && (
                    <p className="text-slate-600">Loading details…</p>
                  )}

                  {err && (
                    <p className="text-red-600 mb-1">{err}</p>
                  )}

                  {!isLoadingDetails && !err && (
                    <div className="mt-1 grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
                      <div>
                        <div className="text-[0.65rem] font-semibold uppercase tracking-wide text-slate-500">
                          Average time to confirmation
                        </div>
                        <div className="text-sm font-semibold text-slate-900">
                          {detailsForReport.averageTimeToConfirmationMs} ms
                        </div>
                      </div>
                      <div>
                        <div className="text-[0.65rem] font-semibold uppercase tracking-wide text-slate-500">
                          Average time to assembling
                        </div>
                        <div className="text-sm font-semibold text-slate-900">
                          {detailsForReport.averageTimeToAssemblingMs} ms
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function formatDate(raw: string) {
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return raw;
  return d.toLocaleString();
}
