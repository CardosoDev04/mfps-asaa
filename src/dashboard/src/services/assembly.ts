import type { AssemblyTransportOrder } from "../@types/AssemblyTransportOrder";

export async function createOneAssemblyOrder(demo: boolean): Promise<AssemblyTransportOrder> {
  const res = await fetch(`http://localhost:8080/assembly/transport-order?demo=${demo}`, {
    method: "POST",
  });
  const order: AssemblyTransportOrder = await res.json();
  return order
}

export async function createBulkAssemblyOrders(demo: boolean, count: number, testRunId: string): Promise<AssemblyTransportOrder[]> {
  const res = await fetch(
    `http://localhost:8080/assembly/transport-order/bulk?n=${count}&demo=${demo}&testRunId=${testRunId}`,
    { method: "POST" }
  );
  const list: AssemblyTransportOrder[] = await res.json();
  return list;
}