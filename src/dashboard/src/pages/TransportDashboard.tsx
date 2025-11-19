import { useEffect, useMemo, useRef, useState } from "react";

type TransportSystemState =
  | "IDLE"
  | "CREATING_ORDER"
  | "ORDER_CREATED"
  | "SENDING_ORDER"
  | "AWAITING_CONFIRMATION"
  | "EVALUATING_CONFIRMATION"
  | "ORDER_ACCEPTED"
  | "ACQUIRING_AGV"
  | "AGV_ACQUIRED"
  | "PERFORMING_TRANSPORT"
  | "TRANSPORT_COMPLETED"
  | "ORDER_COMPLETED"
  | "ORDER_DENIED"
  | "ORDER_TIMED_OUT"
  | "AGV_UNAVAILABLE"
  | "FULFILLING_ORDER";

type OrderStatus = "ACCEPTED" | "DENIED" | "COMPLETED" | "IN_PROGRESS";

interface TransportEvent {
  kind: "state" | "status" | "log";
  message?: string | null;
  state?: TransportSystemState | null;
  orderId?: string | null;
  ts: number;
}

interface OrderCard {
  orderId: string;
  lastState?: TransportSystemState;
  lastStatus?: OrderStatus;
  logs: { ts: number; msg: string }[];
  stateHistory: { ts: number; state: TransportSystemState }[];
  statusHistory: { ts: number; status: OrderStatus }[];
  events: TransportEvent[];
}

const KNOWN_STATUSES: OrderStatus[] = [
  "ACCEPTED",
  "DENIED",
  "COMPLETED",
  "IN_PROGRESS",
];

const isKnownStatus = (s?: string | null): s is OrderStatus =>
  !!s && (KNOWN_STATUSES as readonly string[]).includes(s);

/** Rough progress mapping from system state (0–100) */
function stateProgress(st?: TransportSystemState): number {
  switch (st) {
    case "CREATING_ORDER":
    case "ORDER_CREATED":
      return 5;
    case "SENDING_ORDER":
      return 10;
    case "AWAITING_CONFIRMATION":
      return 20;
    case "EVALUATING_CONFIRMATION":
      return 30;
    case "ORDER_ACCEPTED":
      return 40;
    case "ACQUIRING_AGV":
    case "AGV_ACQUIRED":
      return 50;
    case "PERFORMING_TRANSPORT":
    case "FULFILLING_ORDER":
      return 70;
    case "TRANSPORT_COMPLETED":
    case "ORDER_COMPLETED":
      return 100;
    case "ORDER_DENIED":
    case "ORDER_TIMED_OUT":
    case "AGV_UNAVAILABLE":
      return 100; // terminal, but not necessarily "success"
    default:
      return 0;
  }
}

const statusBadgeBg = (status?: OrderStatus) => {
  switch (status) {
    case "COMPLETED":
      return "bg-emerald-100 border-emerald-200";
    case "ACCEPTED":
      return "bg-sky-100 border-sky-200";
    case "IN_PROGRESS":
      return "bg-yellow-100 border-yellow-200";
    case "DENIED":
      return "bg-rose-100 border-rose-200";
    default:
      return "bg-gray-100 border-gray-200";
  }
};

export default function TransportDashboard() {
  const [orders, setOrders] = useState<Record<string, OrderCard>>({});
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (esRef.current) return;

    const es = new EventSource("http://backend:8080/transport/events");
    es.onmessage = () => {};

    const handleEvent = (type: "state" | "status" | "log", e: MessageEvent) => {
      try {
        const payload: TransportEvent = JSON.parse(e.data);
        if (!payload.orderId || typeof payload.orderId !== "string") return;

        const orderId = payload.orderId;

        setOrders((prev) => {
          const current: OrderCard = prev[orderId] ?? {
            orderId,
            logs: [],
            events: [],
            stateHistory: [],
            statusHistory: [],
          };

          const next: OrderCard = {
            ...current,
            events: [...current.events, payload],
          };

          if (type === "state" && payload.state) {
            next.lastState = payload.state;
            next.stateHistory = [
              ...current.stateHistory,
              { ts: payload.ts, state: payload.state },
            ];
          }

          if (type === "status" && isKnownStatus(payload.message)) {
            next.lastStatus = payload.message;
            next.statusHistory = [
              ...current.statusHistory,
              { ts: payload.ts, status: payload.message },
            ];
          }

          if (type === "log" && payload.message) {
            next.logs = [
              ...current.logs,
              { ts: payload.ts, msg: payload.message },
            ];
          }

          return { ...prev, [orderId]: next };
        });
      } catch (err) {
        console.error(`Failed to handle ${type} event:`, err);
      }
    };

    es.addEventListener("state", (e) =>
      handleEvent("state", e as MessageEvent)
    );
    es.addEventListener("status", (e) =>
      handleEvent("status", e as MessageEvent)
    );
    es.addEventListener("log", (e) => handleEvent("log", e as MessageEvent));

    es.onerror = (err) => {
      console.error("Transport EventSource error:", err);
      // optional reconnection/backoff could go here
    };

    esRef.current = es;

    return () => {
      es.close();
      esRef.current = null;
    };
  }, []);

  const orderList = useMemo(
    () =>
      Object.values(orders).sort((a, b) => (a.orderId < b.orderId ? 1 : -1)),
    [orders]
  );

  return (
    <div className="flex flex-col w-full h-full rounded-xl bg-white shadow-sm border border-gray-200 p-4">
      <div className="flex items-center justify-between gap-2 mb-4">
        <h2 className="text-lg font-semibold">Transport Orders</h2>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 2xl:grid-cols-3 gap-3">
        {orderList.map((oc) => {
          const prog = stateProgress(oc.lastState);
          return (
            <div
              key={oc.orderId}
              className="flex flex-col gap-2 rounded-lg border border-gray-200 bg-white p-3 shadow-sm"
            >
              <div className="flex items-center justify-between gap-2">
                <strong className="text-xs truncate">{oc.orderId}</strong>
                <span
                  className={`text-[11px] px-2 py-0.5 rounded-full border ${statusBadgeBg(
                    oc.lastStatus
                  )}`}
                >
                  {oc.lastStatus ?? "—"}
                </span>
              </div>

              <div className="text-[11px] text-gray-700">
                <strong>State:</strong> {oc.lastState ?? "—"}
              </div>

              <div className="h-2 w-full bg-gray-100 rounded-full overflow-hidden">
                <div
                  className="h-full bg-blue-300"
                  style={{ width: `${prog}%` }}
                />
              </div>

              <details className="text-xs mt-1">
                <summary className="cursor-pointer">
                  Details · States({oc.stateHistory.length}) · Status(
                  {oc.statusHistory.length}) · Logs({oc.logs.length})
                </summary>

                <div className="grid gap-3 mt-2">
                  <section>
                    <div className="font-semibold text-[11px] mb-1">
                      State History
                    </div>
                    <ul className="mt-0 pl-4 max-h-24 overflow-auto list-disc">
                      {oc.stateHistory.map((h, i) => (
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
                      {oc.statusHistory.map((h, i) => (
                        <li key={i} className="text-[11px]">
                          [{new Date(h.ts).toLocaleTimeString()}] {h.status}
                        </li>
                      ))}
                    </ul>
                  </section>

                  <section>
                    <div className="font-semibold text-[11px] mb-1">Logs</div>
                    <ul className="mt-0 pl-4 max-h-24 overflow-auto list-disc">
                      {oc.logs.map((l, i) => (
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
