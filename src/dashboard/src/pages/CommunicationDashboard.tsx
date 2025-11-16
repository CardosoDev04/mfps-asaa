import React, { useEffect, useMemo, useRef, useState } from "react";

type CommState = "RECEIVED" | "CONNECTED" | "SENDING" | "SENT" | "NOTIFIED" | "FAILED";
interface EventEnvelope { id: string; eventType: string; timestamp: string; messageId: string; correlationId?: string | null; data: Record<string, any>; }
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
  switch(state){
    case "RECEIVED": return 10;
    case "CONNECTED": return 30;
    case "SENDING": return 60;
    case "SENT": return 80;
    case "NOTIFIED": return 100;
    case "FAILED": return 100;
    default: return 0;
  }
}

const badgeBg = (s?: CommState) => {
  if(!s) return "#e5e7eb";
  if(s === "FAILED") return "#fecaca";
  if(s === "NOTIFIED") return "#bbf7d0";
  return "#bfdbfe";
};

export default function CommunicationDashboard(){
  const [messages, setMessages] = useState<Record<string, MessageCard>>({});
  const [creating, setCreating] = useState(false);
  const [count, setCount] = useState(5);
  const [subsystem, setSubsystem] = useState("SubsystemA");
  const [correlate, setCorrelate] = useState(true);
  const esRef = useRef<EventSource | null>(null);

  useEffect(()=>{
    if(esRef.current) return;
    const es = new EventSource("/api/communication/events");
    function handle(evtType: string, e: MessageEvent){
      try {
        const env: EventEnvelope = JSON.parse(e.data);
        const mid = env.messageId;
        setMessages(prev => {
          const current: MessageCard = prev[mid] ?? { messageId: mid, correlationId: env.correlationId, logs: [], stateHistory: [], statusHistory: [] };
          const next: MessageCard = { ...current };
          if(evtType === "state"){
            const st = env.data.state as CommState | undefined;
            if(st){ next.lastState = st; next.failed = st === "FAILED"; next.stateHistory = [...current.stateHistory, { ts: Date.parse(env.timestamp), state: st }]; }
          }
          if(evtType === "status"){
            const milestone = env.data.milestone as string | undefined;
            if(milestone){ next.statusHistory = [...current.statusHistory, { ts: Date.parse(env.timestamp), milestone }]; }
          }
          if(evtType === "log"){
            const msg = env.data.message as string | undefined;
            if(msg){ next.logs = [...current.logs, { ts: Date.parse(env.timestamp), msg }]; }
          }
          return { ...prev, [mid]: next };
        });
      } catch(err){ console.error("Failed to parse event", err); }
    }
    es.addEventListener("state", e => handle("state", e as MessageEvent));
    es.addEventListener("status", e => handle("status", e as MessageEvent));
    es.addEventListener("log", e => handle("log", e as MessageEvent));
    es.onerror = err => console.warn("SSE error", err);
    esRef.current = es;
    return () => { es.close(); esRef.current = null; };
  }, []);

  const list = useMemo(()=> Object.values(messages).sort((a,b)=> a.messageId < b.messageId ? 1 : -1), [messages]);

  async function safeText(res: Response): Promise<string | undefined> {
    try { return await res.text(); } catch { return undefined; }
  }

  async function postOne(idx?: number, corrId?: string) {
    const body = {
      subsystem,
      payload: `demo-payload-${idx ?? ''}-${new Date().toISOString()}`,
      correlationId: corrId,
    };
    const res = await fetch("/api/communication/messages", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      const t = await safeText(res);
      throw new Error(`POST /communication/messages failed: ${res.status} ${res.statusText} ${t ?? ''}`);
    }
    return res.json() as Promise<{ messageId: string; status: string }>;
  }

  async function createOne() {
    setCreating(true);
    try {
      await postOne(1, correlate ? cryptoRandomId() : undefined);
    } catch (e) {
      console.error(e);
      alert(String(e));
    } finally {
      setCreating(false);
    }
  }

  async function createBulk() {
    setCreating(true);
    const corr = correlate ? cryptoRandomId() : undefined;
    try {
      for (let i = 1; i <= count; i++) {
        // Slight pacing to keep UI responsive and Kafka readable
        // eslint-disable-next-line no-await-in-loop
        await postOne(i, corr);
      }
    } catch (e) {
      console.error(e);
      alert(String(e));
    } finally {
      setCreating(false);
    }
  }

  function cryptoRandomId() {
    try {
      // Use Web Crypto if available
      const arr = new Uint8Array(8);
      crypto.getRandomValues(arr);
      return Array.from(arr).map(b => b.toString(16).padStart(2, '0')).join('');
    } catch {
      // Fallback
      return Math.random().toString(36).slice(2, 10);
    }
  }

  return (
    <div style={{ padding:16, maxWidth:1200, margin:"0 auto", fontFamily:"system-ui,sans-serif" }}>
      <h1 style={{ fontSize:22, fontWeight:600, marginBottom:12 }}>Communication Messages</h1>
      <div style={{ display:"flex", gap:12, alignItems:"center", marginBottom:16, flexWrap:"wrap" }}>
        <label>
          Count:&nbsp;
          <input type="number" min={1} value={count} onChange={e => setCount(parseInt(e.target.value || "1", 10))} style={{ width:80, padding:6 }} />
        </label>
        <label>
          Subsystem:&nbsp;
          <input type="text" value={subsystem} onChange={e => setSubsystem(e.target.value)} style={{ width:160, padding:6 }} />
        </label>
        <label style={{ display:"flex", alignItems:"center", gap:6 }}>
          <input type="checkbox" checked={correlate} onChange={e => setCorrelate(e.target.checked)} />
          same correlationId
        </label>
        <button onClick={createOne} disabled={creating} style={{ padding:"8px 12px", borderRadius:8, border:"1px solid #ddd", background:"#f6f6f6", cursor:"pointer" }}>Create 1</button>
        <button onClick={createBulk} disabled={creating} style={{ padding:"8px 12px", borderRadius:8, border:"1px solid #ddd", background:"#f6f6f6", cursor:"pointer" }}>Create {count}</button>
      </div>
      <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill,minmax(320px,1fr))", gap:12 }}>
        {list.map(m => {
          const prog = progress(m.lastState);
          return (
            <div key={m.messageId} style={{ border:"1px solid #e5e7eb", borderRadius:10, padding:12, background:"#fff", display:"flex", flexDirection:"column", gap:8 }}>
              <div style={{ display:"flex", justifyContent:"space-between", gap:8 }}>
                <strong style={{ fontSize:13, overflow:"hidden", textOverflow:"ellipsis" }}>{m.messageId}</strong>
                <span style={{ fontSize:12, padding:"2px 6px", borderRadius:8, background:badgeBg(m.lastState), border:"1px solid #e5e7eb" }}>{m.lastState ?? "—"}</span>
              </div>
              <div style={{ fontSize:11, color:"#555" }}>Correlation: {m.correlationId ?? "—"}</div>
              <div style={{ height:8, background:"#f3f4f6", borderRadius:6, overflow:"hidden" }}>
                <div style={{ width:`${prog}%`, height:"100%", background: m.failed ? "#f87171" : "#93c5fd" }} />
              </div>
              <details>
                <summary style={{ cursor:"pointer", fontSize:12 }}>Details · States({m.stateHistory.length}) · Status({m.statusHistory.length}) · Logs({m.logs.length})</summary>
                <div style={{ display:"grid", gap:10, marginTop:8 }}>
                  <section>
                    <div style={{ fontWeight:600, fontSize:11, marginBottom:4 }}>State History</div>
                    <ul style={{ margin:0, paddingLeft:16, maxHeight:100, overflow:"auto" }}>
                      {m.stateHistory.map((h,i)=>(<li key={i} style={{ fontSize:11 }}>[{new Date(h.ts).toLocaleTimeString()}] {h.state}</li>))}
                    </ul>
                  </section>
                  <section>
                    <div style={{ fontWeight:600, fontSize:11, marginBottom:4 }}>Status History</div>
                    <ul style={{ margin:0, paddingLeft:16, maxHeight:100, overflow:"auto" }}>
                      {m.statusHistory.map((h,i)=>(<li key={i} style={{ fontSize:11 }}>[{new Date(h.ts).toLocaleTimeString()}] {h.milestone}</li>))}
                    </ul>
                  </section>
                  <section>
                    <div style={{ fontWeight:600, fontSize:11, marginBottom:4 }}>Logs</div>
                    <ul style={{ margin:0, paddingLeft:16, maxHeight:100, overflow:"auto" }}>
                      {m.logs.map((l,i)=>(<li key={i} style={{ fontSize:11 }}>[{new Date(l.ts).toLocaleTimeString()}] {l.msg}</li>))}
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

