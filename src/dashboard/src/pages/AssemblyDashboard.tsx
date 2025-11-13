import React, { useEffect, useMemo, useRef, useState } from "react";

type AssemblySystemStates =
  | "IDLE" | "CREATING_ORDER" | "ORDER_CREATED" | "SENDING_ORDER" | "RECEIVING_CONFIRMATION"
  | "EVALUATING_CONFIRMATION" | "ORDER_ACCEPTED" | "ORDER_DENIED" | "ORDER_TIMED_OUT"
  | "WAITING_FOR_TRANSPORT" | "ASSEMBLING" | "ASSEMBLY_COMPLETED" | "ASSEMBLY_TIMED_OUT"
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

/** Derive coarse progress from state machine states (0–100) */
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
    case "ASSEMBLING":
      return 80;
    case "ASSEMBLY_COMPLETED":
      return 100;
    case "ORDER_DENIED":
    case "ORDER_TIMED_OUT":
    case "ASSEMBLY_TIMED_OUT":
      return 100; // terminal, but not a success badge — status pill will communicate the outcome
    default:
      return 0;
  }
}

export default function AssemblyDashboard() {
  const [count, setCount] = useState(5);
  const [demo, setDemo] = useState(true);
  const [creating, setCreating] = useState(false);
  const [orders, setOrders] = useState<Record<string, OrderCard>>({});
  const esRef = useRef<EventSource | null>(null);

  // Open SSE once (or reconnect if closed)
  useEffect(() => {
    if (esRef.current) return;

    const es = new EventSource("http://localhost:8080/assembly/events");
    es.onmessage = () => {}; // unused default

    const handleEvent = (type: "state" | "status" | "log", e: MessageEvent) => {
      try {
        const payload: EventPayload = JSON.parse(e.data);
        if (!payload.orderId || typeof payload.orderId !== "string") return;
        const orderId = payload.orderId;

        setOrders((prev) => {
          const current: OrderCard =
            prev[orderId] ??
            {
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
            next.stateHistory = [...current.stateHistory, { ts: payload.ts, state: payload.state }];
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
      // Optional: reconnection/backoff could go here
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
      const res = await fetch(`http://localhost:8080/assembly/transport-order?demo=${demo}`, {
        method: "POST",
      });
      const order: AssemblyTransportOrder = await res.json();
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
      const res = await fetch(
        `http://localhost:8080/assembly/transport-order/bulk?n=${count}&demo=${demo}&testRunId=${testRunId}`,
        { method: "POST" }
      );
      const list: AssemblyTransportOrder[] = await res.json();
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

  const orderList = useMemo(
    () => Object.values(orders).sort((a, b) => (a.order.orderId < b.order.orderId ? 1 : -1)),
    [orders]
  );

  return (
    <div style={{ padding: 16, maxWidth: 1100, margin: "0 auto", fontFamily: "system-ui, sans-serif" }}>
      <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 12 }}>Assembly Orders</h1>

      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 16 }}>
        <label>
          Count:&nbsp;
          <input
            type="number"
            min={1}
            value={count}
            onChange={(e) => setCount(parseInt(e.target.value || "1", 10))}
            style={{ width: 80, padding: 6 }}
          />
        </label>
        <label style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <input type="checkbox" checked={demo} onChange={(e) => setDemo(e.target.checked)} />
          demo
        </label>
        <button onClick={createOne} disabled={creating} style={btnStyle}>
          Create 1
        </button>
        <button onClick={createBulk} disabled={creating} style={btnStyle}>
          Create {count}
        </button>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))", gap: 12 }}>
        {orderList.map((oc) => {
          const prog = stateProgress(oc.lastState);
          return (
            <div key={oc.order.orderId} style={cardStyle}>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8, gap: 8 }}>
                <strong style={{ overflow: "hidden", textOverflow: "ellipsis" }}>
                  {oc.order.orderId}
                </strong>
                <span
                  title="Order status"
                  style={{
                    fontSize: 12,
                    padding: "2px 6px",
                    borderRadius: 8,
                    background: badgeBg(oc.lastStatus),
                    border: "1px solid #e5e7eb",
                  }}
                >
                  {oc.lastStatus ?? "—"}
                </span>
              </div>

              <div style={{ fontSize: 13, color: "#555", marginBottom: 6 }}>
                <strong>State:</strong> {oc.lastState ?? "—"}
              </div>

              {/* Progress from machine state (not business status) */}
              <div style={{ height: 8, background: "#F3F4F6", borderRadius: 6, overflow: "hidden" }} aria-label="state-progress">
                <div style={{ width: `${prog}%`, height: "100%", background: "#BFDBFE" }} />
              </div>

              <details style={{ marginTop: 10 }}>
                <summary style={{ cursor: "pointer" }}>
                  Logs ({oc.logs.length}) · States ({oc.stateHistory.length}) · Statuses ({oc.statusHistory.length})
                </summary>
                <div style={{ marginTop: 8, display: "grid", gap: 10 }}>
                  <section>
                    <div style={{ fontWeight: 600, fontSize: 12, marginBottom: 4 }}>State history</div>
                    <ul style={{ marginTop: 4, paddingLeft: 16, maxHeight: 120, overflow: "auto" }}>
                      {oc.stateHistory.map((h, i) => (
                        <li key={i} style={{ fontSize: 12 }}>
                          [{new Date(h.ts).toLocaleTimeString()}] {h.state}
                        </li>
                      ))}
                    </ul>
                  </section>
                  <section>
                    <div style={{ fontWeight: 600, fontSize: 12, marginBottom: 4 }}>Status history</div>
                    <ul style={{ marginTop: 4, paddingLeft: 16, maxHeight: 120, overflow: "auto" }}>
                      {oc.statusHistory.map((h, i) => (
                        <li key={i} style={{ fontSize: 12 }}>
                          [{new Date(h.ts).toLocaleTimeString()}] {h.status}
                        </li>
                      ))}
                    </ul>
                  </section>
                  <section>
                    <div style={{ fontWeight: 600, fontSize: 12, marginBottom: 4 }}>Logs</div>
                    <ul style={{ marginTop: 4, paddingLeft: 16, maxHeight: 120, overflow: "auto" }}>
                      {oc.logs.map((l, i) => (
                        <li key={i} style={{ fontSize: 12 }}>
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

const btnStyle: React.CSSProperties = {
  padding: "8px 12px",
  borderRadius: 8,
  border: "1px solid #ddd",
  background: "#f6f6f6",
  cursor: "pointer",
};

const cardStyle: React.CSSProperties = {
  border: "1px solid #eee",
  borderRadius: 12,
  padding: 12,
  background: "white",
  boxShadow: "0 1px 2px rgba(0,0,0,0.05)",
};

function badgeBg(status?: OrderStatus) {
  switch (status) {
    case "COMPLETED":
      return "#DCFCE7";
    case "ACCEPTED":
      return "#E0F2FE";
    case "DENIED":
      return "#FEE2E2";
    default:
      return "#F3F4F6";
  }
}
