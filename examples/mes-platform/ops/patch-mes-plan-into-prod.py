#!/usr/bin/env python3
"""Add Official plan + Monitor pages into the live mes-demo frontend without replacing it."""
from __future__ import annotations

import re
import shutil
from datetime import datetime
from pathlib import Path

ROOT = Path("/opt/ispf/mes-demo/frontend")
ASSETS = ROOT / "assets"
HTML = ROOT / "index.html"
ENTRY_NAME = "index-Bv02O2Notif1-NoTech-LabelPdf2-ActiveOut.js"
ENTRY = ASSETS / ENTRY_NAME
STUB = ASSETS / "index-Bv02O2Notif1.js"
HUB = "root.platform.singleton-blueprints.mes-platform-hub-v1"
OFFICIAL = "official-plan.page-PlanExec1.js"
MONITOR = "plan-monitor.page-PlanExec1.js"

PAGE_PREFIX = r'''import{r as r,j as j,B as Button,I as Informer,L as Label}from"./index-Bv02O2Notif1.js";import{I as Input}from"./input-BE5Mt6vD-R321b.js";
const HUB="__HUB__";
async function call(functionName,input){
  const token=localStorage.getItem("token")||"";
  const fields=Object.keys(input).map(name=>({name,type:"STRING"}));
  const res=await fetch("/api/v1/bff/invoke",{
    method:"POST",
    headers:{"Content-Type":"application/json",...(token?{Authorization:"Bearer "+token}:{})},
    body:JSON.stringify({
      objectPath:HUB,
      functionName,
      input:{schema:{name:"in",fields},rows:[input]},
      wireProfile:"ispf-operator-v1"
    })
  });
  if(!res.ok) throw new Error("HTTP "+res.status+" "+functionName);
  const wire=await res.json();
  const nested=wire&&typeof wire.result==="object"&&!Array.isArray(wire.result)?wire.result:{};
  const code=String(wire.error_code||nested.error_code||"").toUpperCase();
  if(code&&code!=="OK") throw new Error(String(wire.error_message||nested.error_message||code));
  const rows=Array.isArray(nested.rows)?nested.rows:Array.isArray(wire.result)?wire.result:[];
  return Object.assign({}, nested, {rows});
}
function kpi(items){
  return j.jsx("div",{className:"flex flex-wrap gap-2",children:items.map(it=>j.jsxs("div",{className:"min-w-[8rem] rounded-md border bg-card px-3 py-2 shadow-sm",children:[
    j.jsx("div",{className:"text-xs text-muted-foreground",children:it.label}),
    j.jsx("div",{className:"text-base font-semibold",children:it.value})
  ]},it.label))});
}
function table(cols,rows,empty){
  empty=empty||"Нет данных";
  const bodyRows=(rows&&rows.length)?rows:[null];
  return j.jsx("div",{className:"overflow-auto rounded-md border bg-card",children:j.jsxs("table",{className:"w-full caption-bottom text-sm",children:[
    j.jsx("thead",{children:j.jsx("tr",{className:"border-b",children:cols.map(c=>j.jsx("th",{className:"px-3 py-2 text-left font-medium",children:c[1]},c[0]))})}),
    j.jsx("tbody",{children:bodyRows.map((row,idx)=>row?j.jsx("tr",{className:"border-b",children:cols.map(c=>j.jsx("td",{className:"px-3 py-2",children:String(row[c[0]]??"")},c[0]))},String(row.pubId||row.tailId||row.deviationId||idx)):j.jsx("tr",{children:j.jsx("td",{className:"px-3 py-2 text-muted-foreground",colSpan:cols.length,children:empty})}))})
  ]})});
}
'''

OFFICIAL_PAGE = PAGE_PREFIX + r'''
async function loadAll(siteCode){
  const [ready, pubs, dels] = await Promise.all([
    call("mes_plan_getDataReadiness", {siteCode}),
    call("mes_plan_listPubs", {siteCode}),
    call("mes_plan_listDeliveriesBySite", {siteCode}),
  ]);
  return {ready, pubs, dels};
}
function Page(){
  const[siteCode,setSiteCode]=r.useState("PLANT-A");
  const[loading,setLoading]=r.useState(false);
  const[error,setError]=r.useState(null);
  const[ready,setReady]=r.useState({});
  const[pubs,setPubs]=r.useState([]);
  const[dels,setDels]=r.useState([]);
  const refresh=r.useCallback(async()=>{
    setLoading(true); setError(null);
    try{
      const res=await loadAll(siteCode);
      setReady(res.ready); setPubs(res.pubs.rows||[]); setDels(res.dels.rows||[]);
    }catch(e){ setError(e instanceof Error?e.message:String(e)); }
    finally{ setLoading(false); }
  },[siteCode]);
  r.useEffect(()=>{ refresh(); },[refresh]);
  return j.jsxs("div",{className:"flex h-full min-h-0 flex-col gap-3 p-4",children:[
    j.jsx("h1",{className:"text-lg font-semibold",children:"Официальный план"}),
    j.jsxs("div",{className:"flex flex-wrap items-end gap-3",children:[
      j.jsxs("div",{className:"space-y-1",children:[
        j.jsx(Label,{htmlFor:"plan-site",children:"Площадка"}),
        j.jsx(Input,{id:"plan-site",value:siteCode,onChange:e=>setSiteCode(e.target.value),className:"w-48"})
      ]}),
      j.jsx(Button,{type:"button",onClick:()=>void refresh(),disabled:loading,children:loading?"Обновление…":"Обновить"})
    ]}),
    error?j.jsx(Informer,{tone:"alert",variant:"filled",size:"s",title:"Ошибка",description:error}):null,
    kpi([{label:"Готовность",value:String(ready.status??"—")},{label:"Устарело",value:String(ready.staleCount??"—")},{label:"Активов",value:String(ready.assetCount??"—")}]),
    j.jsxs("section",{className:"space-y-2",children:[
      j.jsx("h2",{className:"text-base font-semibold",children:"Официальные версии"}),
      table([["pubId","План"],["skuCode","SKU"],["plannedQty","Объём"],["status","Статус"],["shopStatus","Цех"],["snapshotId","Снимок"]], pubs, "Официальных планов пока нет")
    ]}),
    j.jsxs("section",{className:"space-y-2",children:[
      j.jsx("h2",{className:"text-base font-semibold",children:"Доставка получателям"}),
      table([["pubId","План"],["recipientCode","Получатель"],["status","Статус"],["errorText","Ошибка"]], dels)
    ]})
  ]});
}
const Component=Page;
export{Component};
'''

MONITOR_PAGE = PAGE_PREFIX + r'''
async function loadAll(siteCode){
  const [facts, tails, devs] = await Promise.all([
    call("mes_plan_planFactBySite", {siteCode}),
    call("mes_plan_listTailsBySite", {siteCode}),
    call("mes_plan_listDeviationsBySite", {siteCode}),
  ]);
  return {facts, tails, devs};
}
function Page(){
  const[siteCode,setSiteCode]=r.useState("PLANT-A");
  const[loading,setLoading]=r.useState(false);
  const[error,setError]=r.useState(null);
  const[facts,setFacts]=r.useState([]);
  const[tails,setTails]=r.useState([]);
  const[devs,setDevs]=r.useState([]);
  const refresh=r.useCallback(async()=>{
    setLoading(true); setError(null);
    try{
      const res=await loadAll(siteCode);
      setFacts(res.facts.rows||[]); setTails(res.tails.rows||[]); setDevs(res.devs.rows||[]);
    }catch(e){ setError(e instanceof Error?e.message:String(e)); }
    finally{ setLoading(false); }
  },[siteCode]);
  r.useEffect(()=>{ refresh(); },[refresh]);
  return j.jsxs("div",{className:"flex h-full min-h-0 flex-col gap-3 p-4",children:[
    j.jsx("h1",{className:"text-lg font-semibold",children:"Мониторинг плана"}),
    j.jsxs("div",{className:"flex flex-wrap items-end gap-3",children:[
      j.jsxs("div",{className:"space-y-1",children:[
        j.jsx(Label,{htmlFor:"mon-site",children:"Площадка"}),
        j.jsx(Input,{id:"mon-site",value:siteCode,onChange:e=>setSiteCode(e.target.value),className:"w-48"})
      ]}),
      j.jsx(Button,{type:"button",onClick:()=>void refresh(),disabled:loading,children:loading?"Обновление…":"Обновить"})
    ]}),
    error?j.jsx(Informer,{tone:"alert",variant:"filled",size:"s",title:"Ошибка",description:error}):null,
    j.jsxs("section",{className:"space-y-2",children:[
      j.jsx("h2",{className:"text-base font-semibold",children:"План / факт"}),
      table([["pubId","План"],["skuCode","SKU"],["plannedQty","План"],["actualQty","Факт"],["deltaQty","Остаток"],["risk","Риск"]], facts, "Нет опубликованных планов")
    ]}),
    j.jsxs("section",{className:"space-y-2",children:[
      j.jsx("h2",{className:"text-base font-semibold",children:"Хвосты"}),
      table([["pubId","План"],["tailId","Хвост"],["tailQty","Объём"],["reasonCode","Причина"],["periodCode","Период"],["status","Статус"]], tails)
    ]}),
    j.jsxs("section",{className:"space-y-2",children:[
      j.jsx("h2",{className:"text-base font-semibold",children:"Отклонения"}),
      table([["pubId","План"],["deviationId","Id"],["severity","Важность"],["reasonCode","Причина"],["delayMinutes","Минуты"],["status","Статус"]], devs)
    ]})
  ]});
}
const Component=Page;
export{Component};
'''


def main() -> None:
    if not ENTRY.exists():
        raise SystemExit(f"missing entry {ENTRY}")

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = Path(f"/opt/ispf/staging/backups/index-pre-plan-exec-{stamp}.js")
    backup.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ENTRY, backup)
    print("backup", backup)

    (ASSETS / OFFICIAL).write_text(OFFICIAL_PAGE.replace("__HUB__", HUB), encoding="utf-8")
    (ASSETS / MONITOR).write_text(MONITOR_PAGE.replace("__HUB__", HUB), encoding="utf-8")
    print("wrote", OFFICIAL, MONITOR)

    t = ENTRY.read_text(encoding="utf-8")
    if "official-plan.page-PlanExec1.js" in t:
        print("already patched in entry text; rewriting entry file only if needed")

    old_routes = (
        'OPERATOR:{PRODUCTION_PLAN:"/operator/production-plan",ORDER_EXECUTION:"/operator/order-execution"'
    )
    new_routes = (
        'OPERATOR:{PRODUCTION_PLAN:"/operator/production-plan",'
        'OFFICIAL_PLAN:"/operator/official-plan",'
        'PLAN_MONITOR:"/operator/plan-monitor",'
        'ORDER_EXECUTION:"/operator/order-execution"'
    )
    if "OFFICIAL_PLAN:" not in t:
        if old_routes not in t:
            raise SystemExit("OPERATOR routes block not found")
        t = t.replace(old_routes, new_routes, 1)

    old_nav = (
        '{id:"op.plan",label:"Производственный план",to:Ee.OPERATOR.PRODUCTION_PLAN,roles:["operator","manager"]},'
        '{id:"op.exec",label:"Исполнение заказа",to:Ee.OPERATOR.ORDER_EXECUTION,roles:["operator","manager"]},'
    )
    new_nav = (
        '{id:"op.plan",label:"Производственный план",to:Ee.OPERATOR.PRODUCTION_PLAN,roles:["operator","manager"]},'
        '{id:"op.officialPlan",label:"Официальный план",to:Ee.OPERATOR.OFFICIAL_PLAN,roles:["operator","manager"]},'
        '{id:"op.planMonitor",label:"Мониторинг плана",to:Ee.OPERATOR.PLAN_MONITOR,roles:["operator","manager"]},'
        '{id:"op.exec",label:"Исполнение заказа",to:Ee.OPERATOR.ORDER_EXECUTION,roles:["operator","manager"]},'
    )
    if "op.officialPlan" not in t:
        if old_nav not in t:
            raise SystemExit("nav operator items not found")
        t = t.replace(old_nav, new_nav, 1)

    m = re.search(r"__vite__mapDeps=\(i,m=__vite__mapDeps,d=\(m\.f\|\|\(m\.f=\[(.*?)\]\)\)\)", t)
    if not m:
        raise SystemExit("mapDeps not found")
    deps = re.findall(r'"([^"]+)"', m.group(1))
    if f"assets/{OFFICIAL}" not in deps:
        deps.append(f"assets/{OFFICIAL}")
    if f"assets/{MONITOR}" not in deps:
        deps.append(f"assets/{MONITOR}")
    official_idx = deps.index(f"assets/{OFFICIAL}")
    monitor_idx = deps.index(f"assets/{MONITOR}")
    input_idx = deps.index("assets/input-BE5Mt6vD-R321b.js")
    new_deps = ",".join(f'"{d}"' for d in deps)
    t = t[: m.start(1)] + new_deps + t[m.end(1) :]

    anchor = '{path:Ee.OPERATOR.PRODUCTION_PLAN,lazy:()=>ut(()=>import("./production-plan.page-B5vbrZak-R321b.js"),__vite__mapDeps([0,1,2,3,4,5,6]))},'
    if "OFFICIAL_PLAN,lazy" not in t and "path:Ee.OPERATOR.OFFICIAL_PLAN" not in t:
        if anchor not in t:
            raise SystemExit("production-plan route not found")
        insert = (
            anchor
            + f'{{path:Ee.OPERATOR.OFFICIAL_PLAN,lazy:()=>ut(()=>import("./{OFFICIAL}"),__vite__mapDeps([{official_idx},{input_idx}]))}},'
            + f'{{path:Ee.OPERATOR.PLAN_MONITOR,lazy:()=>ut(()=>import("./{MONITOR}"),__vite__mapDeps([{monitor_idx},{input_idx}]))}},'
        )
        t = t.replace(anchor, insert, 1)

    new_entry_name = "index-Bv02O2Notif1-NoTech-LabelPdf2-ActiveOut-PlanExec1.js"
    new_entry = ASSETS / new_entry_name
    new_entry.write_text(t, encoding="utf-8")
    STUB.write_text(f'export * from "./{new_entry_name}";\n', encoding="utf-8")

    html = HTML.read_text(encoding="utf-8")
    html2, n = re.subn(
        r'src="/assets/index-Bv02O2Notif1[^"]*\.js"',
        f'src="/assets/{new_entry_name}"',
        html,
        count=1,
    )
    if n != 1:
        raise SystemExit(f"html entry replace failed n={n}")
    HTML.write_text(html2, encoding="utf-8")

    print("entry", new_entry_name)
    print("official_idx", official_idx, "monitor_idx", monitor_idx, "input_idx", input_idx)
    print("HAS_OFFICIAL_NAV", "Официальный план" in t)
    print("HAS_MONITOR_NAV", "Мониторинг плана" in t)
    print("HTML_OK", new_entry_name in HTML.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
