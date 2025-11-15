import { useEffect, useRef, useState } from "react";
import type { AssemblyTransportOrder } from "../../@types/AssemblyTransportOrder";
import { createBulkAssemblyOrders, createOneAssemblyOrder } from "../../services/assembly";
import { AssemblyOrdersGrid, type EventPayload, type OrderCard } from "./parts/AssemblyOrdersGrid";
import TestReportsDashboard from "./parts/TestReportDashboard";

type AssemblySystemStates =
  | "IDLE" | "CREATING_ORDER" | "ORDER_CREATED" | "SENDING_ORDER" | "RECEIVING_CONFIRMATION"
  | "EVALUATING_CONFIRMATION" | "ORDER_ACCEPTED" | "ORDER_DENIED" | "ORDER_TIMED_OUT"
  | "WAITING_FOR_TRANSPORT" | "ASSEMBLING" | "ASSEMBLY_COMPLETED" | "ASSEMBLY_TIMED_OUT"
  | "NOTIFYING_STATUS";

type OrderStatus = "ACCEPTED" | "DENIED" | "COMPLETED";

const KNOWN_STATUSES: OrderStatus[] = ["ACCEPTED", "DENIED", "COMPLETED"];
const isKnownStatus = (s?: string | null): s is OrderStatus =>
  !!s && (KNOWN_STATUSES as readonly string[]).includes(s);

export default function AssemblyDashboard() {
  const [count, setCount] = useState(5);
  const [demo, setDemo] = useState(true);
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
          const current: OrderCard =
            prev[orderId] ??
            {
              order: { orderId, components: [], deliveryLocation: "" } as AssemblyTransportOrder,
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
            next.lastState = payload.state as AssemblySystemStates;
            next.stateHistory = [...current.stateHistory, { ts: payload.ts, state: payload.state as AssemblySystemStates }];
          }

          if (type === "status" && isKnownStatus(payload.message)) {
            next.lastStatus = payload.message;
            next.statusHistory = [...current.statusHistory, { ts: payload.ts, status: payload.message }];
          }

          if (type === "log" && payload.message) {
            next.logs = [...current.logs, { ts: payload.ts, msg: payload.message }];
          }

          return { ...prev, [orderId]: next };
        });
      } catch (err) {
        console.error(`Failed to handle ${type} event:`, err);
      }
    };

    es.addEventListener("state", (e) => handleEvent("state", e as MessageEvent));
    es.addEventListener("status", (e) => handleEvent("status", e as MessageEvent));
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
      const order = await createOneAssemblyOrder(demo);
      setOrders((prev) => ({
        ...prev,
        [order.orderId]:
          prev[order.orderId] ??
          { order, logs: [], events: [], stateHistory: [], statusHistory: [] },
      }));
    } finally {
      setCreating(false);
    }
  }

  async function createBulk() {
    setCreating(true);
    const testRunId = Math.random().toString(36).substring(2, 14);
    try {
      const list = await createBulkAssemblyOrders(demo, count, testRunId);
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
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="mx-auto max-w-5xl p-4 font-sans">
      <h1 className="mb-3 text-2xl font-bold text-slate-900">
        Assembly Orders
      </h1>

      <div className="mb-4 flex flex-wrap items-center gap-3 text-sm text-slate-800">
        <label className="flex items-center gap-1.5">
          <span>Count:</span>
          <input
            type="number"
            min={1}
            value={count}
            onChange={(e) =>
              setCount(parseInt(e.target.value || "1", 10))
            }
            className="w-20 rounded-lg border border-slate-300 px-2 py-1 text-sm shadow-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
          />
        </label>

        <label className="flex items-center gap-1.5 text-sm">
          <input
            type="checkbox"
            checked={demo}
            onChange={(e) => setDemo(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300 text-sky-600 focus:ring-sky-500"
          />
          <span>demo</span>
        </label>

        <button
          onClick={createOne}
          disabled={creating}
          className="inline-flex items-center rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm font-medium text-slate-800 shadow-sm transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
        >
          Create 1
        </button>

        <button
          onClick={createBulk}
          disabled={creating}
          className="inline-flex items-center rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm font-medium text-slate-800 shadow-sm transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
        >
          Create {count}
        </button>
      </div>

      <AssemblyOrdersGrid orders={orders} />
      <TestReportsDashboard />
    </div>
  );
}
