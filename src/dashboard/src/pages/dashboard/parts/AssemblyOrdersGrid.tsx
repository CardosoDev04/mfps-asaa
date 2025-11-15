import { useMemo } from "react";
import type { AssemblyTransportOrder } from "../../../@types/AssemblyTransportOrder";

type AssemblySystemStates =
  | "IDLE" | "CREATING_ORDER" | "ORDER_CREATED" | "SENDING_ORDER" | "RECEIVING_CONFIRMATION"
  | "EVALUATING_CONFIRMATION" | "ORDER_ACCEPTED" | "ORDER_DENIED" | "ORDER_TIMED_OUT"
  | "WAITING_FOR_TRANSPORT" | "ASSEMBLING" | "ASSEMBLY_COMPLETED" | "ASSEMBLY_TIMED_OUT"
  | "NOTIFYING_STATUS";

type OrderStatus = "ACCEPTED" | "DENIED" | "COMPLETED" | "IN_PROGRESS";

export type EventPayload = {
  kind: "state" | "status" | "log";
  message?: string | null;
  state?: AssemblySystemStates | null;
  orderId?: string | null;
  ts: number;
};

export type OrderCard = {
  order: AssemblyTransportOrder;
  lastState?: AssemblySystemStates;
  lastStatus?: OrderStatus;
  logs: { ts: number; msg: string }[];
  stateHistory: { ts: number; state: AssemblySystemStates }[];
  statusHistory: { ts: number; status: OrderStatus }[];
  events: EventPayload[];
};

type Props = {
  orders: Record<string, OrderCard>;
};

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
      return 100;
    default:
      return 0;
  }
}

function badgeBgClasses(status?: OrderStatus) {
  switch (status) {
    case "COMPLETED":
      return "bg-emerald-100 text-emerald-800 border-emerald-200";
    case "ACCEPTED":
      return "bg-sky-100 text-sky-800 border-sky-200";
    case "DENIED":
      return "bg-rose-100 text-rose-800 border-rose-200";
    case "IN_PROGRESS":
      return "bg-yellow-100 text-yellow-800 border-yellow-200";
    default:
      return "bg-slate-100 text-slate-700 border-slate-200";
  }
}

export function AssemblyOrdersGrid({ orders }: Props) {
  const orderList = useMemo(
    () => Object.values(orders).sort((a, b) => (a.order.orderId < b.order.orderId ? 1 : -1)),
    [orders]
  );

  if (orderList.length === 0) {
    return (
      <p className="mt-4 text-sm text-slate-500">
        No assembly orders yet. Create one to get started.
      </p>
    );
  }

  return (
    <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
      {orderList.map((oc) => {
        const prog = stateProgress(oc.lastState);

        return (
          <div
            key={oc.order.orderId}
            className="flex flex-col rounded-xl border border-slate-200 bg-white p-3 shadow-sm"
          >
            <div className="mb-2 flex items-center justify-between gap-2">
              <strong className="truncate text-sm font-semibold text-slate-900">
                {oc.order.orderId}
              </strong>

              <span
                title="Order status"
                className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${badgeBgClasses(
                  oc.lastStatus
                )}`}
              >
                {oc.lastStatus ?? "—"}
              </span>
            </div>

            <div className="mb-2 text-xs text-slate-600">
              <span className="font-semibold">State:</span>{" "}
              {oc.lastState ?? "—"}
            </div>

            {/* Progress from machine state (not business status) */}
            <div
              className="h-2 overflow-hidden rounded-full bg-slate-100"
              aria-label="state-progress"
            >
              <div
                className="h-full bg-sky-300 transition-[width] duration-300"
                style={{ width: `${prog}%` }}
              />
            </div>

            <details className="mt-3 text-xs text-slate-800">
              <summary className="cursor-pointer text-xs font-medium text-slate-700 hover:text-slate-900">
                Logs ({oc.logs.length}) · States ({oc.stateHistory.length}) · Statuses ({oc.statusHistory.length})
              </summary>

              <div className="mt-2 grid gap-3">
                <section>
                  <div className="mb-1 text-[0.7rem] font-semibold uppercase tracking-wide text-slate-500">
                    State history
                  </div>
                  <ul className="max-h-32 space-y-0.5 overflow-auto pl-4 text-[0.7rem]">
                    {oc.stateHistory.map((h, i) => (
                      <li key={i}>
                        [{new Date(h.ts).toLocaleTimeString()}] {h.state}
                      </li>
                    ))}
                  </ul>
                </section>

                <section>
                  <div className="mb-1 text-[0.7rem] font-semibold uppercase tracking-wide text-slate-500">
                    Status history
                  </div>
                  <ul className="max-h-32 space-y-0.5 overflow-auto pl-4 text[0.7rem]">
                    {oc.statusHistory.map((h, i) => (
                      <li key={i}>
                        [{new Date(h.ts).toLocaleTimeString()}] {h.status}
                      </li>
                    ))}
                  </ul>
                </section>

                <section>
                  <div className="mb-1 text-[0.7rem] font-semibold uppercase tracking-wide text-slate-500">
                    Logs
                  </div>
                  <ul className="max-h-32 space-y-0.5 overflow-auto pl-4 text-[0.7rem]">
                    {oc.logs.map((l, i) => (
                      <li key={i}>
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
  );
}
