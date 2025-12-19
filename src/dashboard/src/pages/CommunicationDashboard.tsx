import { useEffect, useMemo, useRef, useState } from "react";

type CommState =
  | "RECEIVED"
  | "CONNECTED"
  | "SENDING"
  | "SENT"
  | "NOTIFIED"
  | "FAILED";

interface EventEnvelope {
  id: string;
  eventType: string;
  timestamp: string;
  messageId: string;
  correlationId?: string | null;
  data: Record<string, any>;
}

interface MessageCard {
  messageId: string;
  correlationId?: string | null;
  lastState?: CommState;
  failed?: boolean;
  logs: { ts: number; msg: string }[];
  stateHistory: { ts: number; state: CommState }[];
  statusHistory: { ts: number; milestone: string }[];
}

function progress(state?: CommState): number {
  switch (state) {
    case "RECEIVED":
      return 10;
    case "CONNECTED":
      return 30;
    case "SENDING":
      return 60;
    case "SENT":
      return 80;
    case "NOTIFIED":
      return 100;
    case "FAILED":
      return 100;
    default:
      return 0;
  }
}

const stateBadgeClass = (s?: CommState) => {
  if (!s) return "bg-gray-100 border-gray-200";
  if (s === "FAILED") return "bg-red-100 border-red-200";
  if (s === "NOTIFIED") return "bg-green-100 border-green-200";
  return "bg-blue-100 border-blue-200";
};

export default function CommunicationDashboard() {
  const [messages, setMessages] = useState<Record<string, MessageCard>>({});
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (esRef.current) return;
    const es = new EventSource("http://backend:8080/communication/events");

    function handle(evtType: string, e: MessageEvent) {
      try {
        const env: EventEnvelope = JSON.parse(e.data);
        const mid = env.messageId;
        setMessages((prev) => {
          const current: MessageCard = prev[mid] ?? {
            messageId: mid,
            correlationId: env.correlationId,
            logs: [],
            stateHistory: [],
            statusHistory: [],
          };
          const next: MessageCard = { ...current };

          if (evtType === "state") {
            const st = env.data.state as CommState | undefined;
            if (st) {
              next.lastState = st;
              next.failed = st === "FAILED";
              next.stateHistory = [
                ...current.stateHistory,
                { ts: Date.parse(env.timestamp), state: st },
              ];
            }
          }

          if (evtType === "status") {
            const milestone = env.data.milestone as string | undefined;
            if (milestone) {
              next.statusHistory = [
                ...current.statusHistory,
                { ts: Date.parse(env.timestamp), milestone },
              ];
            }
          }

          if (evtType === "log") {
            const msg = env.data.message as string | undefined;
            if (msg) {
              next.logs = [
                ...current.logs,
                { ts: Date.parse(env.timestamp), msg },
              ];
            }
          }

          return { ...prev, [mid]: next };
        });
      } catch (err) {
        console.error("Failed to parse event", err);
      }
    }

    es.addEventListener("state", (e) => handle("state", e as MessageEvent));
    es.addEventListener("status", (e) => handle("status", e as MessageEvent));
    es.addEventListener("log", (e) => handle("log", e as MessageEvent));
    es.onerror = (err) => console.warn("SSE error", err);

    esRef.current = es;
    return () => {
      es.close();
      esRef.current = null;
    };
  }, []);

  const list = useMemo(
    () =>
      Object.values(messages).sort((a, b) =>
        a.messageId < b.messageId ? 1 : -1
      ),
    [messages]
  );

  return (
    <div className="flex flex-col w-full h-full rounded-xl bg-white shadow-sm border border-gray-200 p-4">
      <div className="flex items-center justify-between gap-2 mb-4">
        <h2 className="text-lg font-semibold">Communication Messages</h2>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 2xl:grid-cols-3 gap-3">
        {list.map((m) => {
          const prog = progress(m.lastState);
          return (
            <div
              key={m.messageId}
              className="flex flex-col gap-2 rounded-lg border border-gray-200 bg-white p-3 shadow-sm"
            >
              <div className="flex items-center justify-between gap-2">
                <strong className="text-xs truncate">{m.messageId}</strong>
                <span
                  className={`text-[11px] px-2 py-0.5 rounded-full border ${stateBadgeClass(
                    m.lastState
                  )}`}
                >
                  {m.lastState ?? "—"}
                </span>
              </div>

              <div className="text-[11px] text-gray-600">
                Correlation: {m.correlationId ?? "—"}
              </div>

              <div className="h-2 w-full bg-gray-100 rounded-full overflow-hidden">
                <div
                  className={`h-full ${m.failed ? "bg-red-400" : "bg-blue-300"
                    }`}
                  style={{ width: `${prog}%` }}
                />
              </div>

              <details className="text-xs">
                <summary className="cursor-pointer">
                  Details · States({m.stateHistory.length}) · Status(
                  {m.statusHistory.length}) · Logs({m.logs.length})
                </summary>
                <div className="grid gap-3 mt-2">
                  <section>
                    <div className="font-semibold text-[11px] mb-1">
                      State History
                    </div>
                    <ul className="mt-0 pl-4 max-h-24 overflow-auto list-disc">
                      {m.stateHistory.map((h, i) => (
                        <li key={i} className="text-[11px]">
                          [{new Date(h.ts).toLocaleTimeString()}] {h.state}
                        </li>
                      ))}
                    </ul>
                  </section>

                  <section>
                    <div className="font-semibold text-[11px] mb-1">
                      Status History
                    </div>
                    <ul className="mt-0 pl-4 max-h-24 overflow-auto list-disc">
                      {m.statusHistory.map((h, i) => (
                        <li key={i} className="text-[11px]">
                          [{new Date(h.ts).toLocaleTimeString()}] {h.milestone}
                        </li>
                      ))}
                    </ul>
                  </section>

                  <section>
                    <div className="font-semibold text-[11px] mb-1">Logs</div>
                    <ul className="mt-0 pl-4 max-h-24 overflow-auto list-disc">
                      {m.logs.map((l, i) => (
                        <li key={i} className="text-[11px]">
                          [{new Date(l.ts).toLocaleTimeString()}] {l.msg}
                        </li>
                      ))}
                    </ul>
                  </section>
                </div>
              </details>
            </div>
          );
        })}
      </div>
    </div>
  );
}
