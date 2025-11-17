import { useEffect, useMemo, useRef, useState } from "react";

type AssemblySystemStates =
  | "IDLE"
  | "CREATING_ORDER"
  | "ORDER_CREATED"
  | "SENDING_ORDER"
  | "RECEIVING_CONFIRMATION"
  | "EVALUATING_CONFIRMATION"
  | "ORDER_ACCEPTED"
  | "ORDER_DENIED"
  | "ORDER_TIMED_OUT"
  | "WAITING_FOR_TRANSPORT"
  | "RECEIVED_TRANSPORT"
  | "ASSEMBLING"
  | "ASSEMBLY_COMPLETED"
  | "ASSEMBLY_TIMED_OUT"
  | "NOTIFYING_STATUS";

type OrderStatus = "ACCEPTED" | "DENIED" | "COMPLETED";

type AssemblyTransportOrder = {
  orderId: string;
  components: any[];
  deliveryLocation: string;
};

type EventPayload = {
  kind: "state" | "status" | "log";
  message?: string | null;
  state?: AssemblySystemStates | null;
  orderId?: string | null;
  ts: number;
};

type OrderCard = {
  order: AssemblyTransportOrder;
  lastState?: AssemblySystemStates;
  lastStatus?: OrderStatus;
  logs: { ts: number; msg: string }[];
  stateHistory: { ts: number; state: AssemblySystemStates }[];
  statusHistory: { ts: number; status: OrderStatus }[];
  events: EventPayload[];
};

const KNOWN_STATUSES: OrderStatus[] = ["ACCEPTED", "DENIED", "COMPLETED"];
const isKnownStatus = (s?: string | null): s is OrderStatus =>
  !!s && (KNOWN_STATUSES as readonly string[]).includes(s);

function stateProgress(st?: AssemblySystemStates): number {
  switch (st) {
    case "CREATING_ORDER":
    case "ORDER_CREATED":
      return 5;
    case "SENDING_ORDER":
      return 10;
    case "RECEIVING_CONFIRMATION":
      return 20;
    case "EVALUATING_CONFIRMATION":
      return 30;
    case "ORDER_ACCEPTED":
      return 40;
    case "WAITING_FOR_TRANSPORT":
      return 60;
    case "RECEIVED_TRANSPORT":
      return 70;
    case "ASSEMBLING":
      return 80;
    case "ASSEMBLY_COMPLETED":
      return 100;
    case "ORDER_DENIED":
    case "ORDER_TIMED_OUT":
    case "ASSEMBLY_TIMED_OUT":
      return 100;
    default:
      return 0;
  }
}

function statusBadgeBg(status?: OrderStatus) {
  switch (status) {
    case "COMPLETED":
      return "bg-emerald-100 border-emerald-200";
    case "ACCEPTED":
      return "bg-sky-100 border-sky-200";
    case "DENIED":
      return "bg-rose-100 border-rose-200";
    default:
      return "bg-gray-100 border-gray-200";
  }
}

export default function AssemblyDashboard() {
  const [count, setCount] = useState(5);
  const [creating, setCreating] = useState(false);
  const [orders, setOrders] = useState<Record<string, OrderCard>>({});
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (esRef.current) return;

    const es = new EventSource("http://localhost:8080/assembly/events");
    es.onmessage = () => {};

    const handleEvent = (type: "state" | "status" | "log", e: MessageEvent) => {
      try {
        const payload: EventPayload = JSON.parse(e.data);
        if (!payload.orderId || typeof payload.orderId !== "string") return;
        const orderId = payload.orderId;

        setOrders((prev) => {
          const current: OrderCard = prev[orderId] ?? {
            order: { orderId, components: [], deliveryLocation: "" },
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
      console.error("EventSource error:", err);
    };

    esRef.current = es;

    return () => {
      es.close();
      esRef.current = null;
    };
  }, []);

  async function createOne() {
    setCreating(true);
    try {
      const res = await fetch(
        `http://localhost:8080/assembly/transport-order?demo=false`,
        {
          method: "POST",
        }
      );
      const order: AssemblyTransportOrder = await res.json();
      setOrders((prev) => ({
        ...prev,
        [order.orderId]: prev[order.orderId] ?? {
          order,
          logs: [],
          events: [],
          stateHistory: [],
          statusHistory: [],
        },
      }));
    } finally {
      setCreating(false);
    }
  }

  async function createBulk() {
    setCreating(true);
    const testRunId = Math.random().toString(36).substring(2, 14);
    try {
      const res = await fetch(
        `http://localhost:8080/assembly/transport-order/bulk?n=${count}&demo=false&testRunId=${testRunId}`,
        { method: "POST" }
      );

      if (!res.ok) {
        throw new Error(`Failed to fetch: ${res.status} ${res.statusText}`);
      }

      const list = await res.json();

      if (!Array.isArray(list)) {
        throw new TypeError(
          "Expected an array of orders, but received something else."
        );
      }

      setOrders((prev) => {
        const next = { ...prev };
        for (const order of list) {
          if (!next[order.orderId]) {
            next[order.orderId] = {
              order,
              logs: [],
              events: [],
              stateHistory: [],
              statusHistory: [],
            };
          }
        }
        return next;
      });
    } catch (error) {
      console.error("Error in createBulk:", error);
    } finally {
      setCreating(false);
    }
  }

  const orderList = useMemo(
    () =>
      Object.values(orders).sort((a, b) =>
        a.order.orderId < b.order.orderId ? 1 : -1
      ),
    [orders]
  );

  return (
    <div className="flex flex-col w-full h-full rounded-xl bg-white shadow-sm border border-gray-200 p-4">
      <div className="flex items-center justify-between gap-2 mb-4">
        <h2 className="text-lg font-semibold">Assembly Orders</h2>
      </div>

      <div className="flex flex-wrap items-center gap-3 mb-4">
        <label className="flex items-center gap-2 text-sm">
          <span>Count:</span>
          <input
            type="number"
            min={1}
            value={count}
            onChange={(e) => setCount(parseInt(e.target.value || "1", 10))}
            className="w-20 rounded-md border border-gray-300 px-2 py-1 text-sm"
          />
        </label>

        <button
          onClick={createOne}
          disabled={creating}
          className="inline-flex items-center rounded-md border border-gray-300 bg-gray-100 px-3 py-1.5 text-sm hover:bg-gray-200 disabled:opacity-50"
        >
          Create 1
        </button>
        <button
          onClick={createBulk}
          disabled={creating}
          className="inline-flex items-center rounded-md border border-gray-300 bg-gray-100 px-3 py-1.5 text-sm hover:bg-gray-200 disabled:opacity-50"
        >
          Create {count}
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 2xl:grid-cols-3 gap-3">
        {orderList.map((oc) => {
          const prog = stateProgress(oc.lastState);
          return (
            <div
              key={oc.order.orderId}
              className="border border-gray-200 rounded-xl p-3 bg-white shadow-sm flex flex-col gap-2"
            >
              <div className="flex justify-between items-center mb-1 gap-2">
                <strong className="truncate text-sm">{oc.order.orderId}</strong>
                <span
                  className={`text-xs px-2 py-0.5 rounded-full border ${statusBadgeBg(
                    oc.lastStatus
                  )}`}
                  title="Order status"
                >
                  {oc.lastStatus ?? "—"}
                </span>
              </div>

              <div className="text-xs text-gray-700 mb-1">
                <strong>State:</strong> {oc.lastState ?? "—"}
              </div>

              <div
                className="h-2 w-full bg-gray-100 rounded-full overflow-hidden"
                aria-label="state-progress"
              >
                <div
                  className="h-full bg-blue-300"
                  style={{ width: `${prog}%` }}
                />
              </div>

              <details className="mt-2 text-xs">
                <summary className="cursor-pointer">
                  Logs ({oc.logs.length}) · States ({oc.stateHistory.length}) ·
                  Statuses ({oc.statusHistory.length})
                </summary>
                <div className="mt-2 grid gap-3">
                  <section>
                    <div className="font-semibold text-xs mb-1">
                      State history
                    </div>
                    <ul className="mt-1 pl-4 max-h-32 overflow-auto list-disc">
                      {oc.stateHistory.map((h, i) => (
                        <li key={i} className="text-xs">
                          [{new Date(h.ts).toLocaleTimeString()}] {h.state}
                        </li>
                      ))}
                    </ul>
                  </section>

                  <section>
                    <div className="font-semibold text-xs mb-1">
                      Status history
                    </div>
                    <ul className="mt-1 pl-4 max-h-32 overflow-auto list-disc">
                      {oc.statusHistory.map((h, i) => (
                        <li key={i} className="text-xs">
                          [{new Date(h.ts).toLocaleTimeString()}] {h.status}
                        </li>
                      ))}
                    </ul>
                  </section>

                  <section>
                    <div className="font-semibold text-xs mb-1">Logs</div>
                    <ul className="mt-1 pl-4 max-h-32 overflow-auto list-disc">
                      {oc.logs.map((l, i) => (
                        <li key={i} className="text-xs">
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
