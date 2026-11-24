#!/usr/bin/env python3
"""Add Resources + Publish screens and scale bars on Monitor into live mes-demo SPA."""
from __future__ import annotations

import re
import shutil
from datetime import datetime
from pathlib import Path

ROOT = Path("/opt/ispf/mes-demo/frontend")
ASSETS = ROOT / "assets"
HTML = ROOT / "index.html"
ENTRY_NAME = "index-Bv02O2Notif1-NoTech-LabelPdf2-ActiveOut-PlanExec1.js"
ENTRY = ASSETS / ENTRY_NAME
STUB = ASSETS / "index-Bv02O2Notif1.js"
HUB = "root.platform.singleton-blueprints.mes-platform-hub-v1"

RESOURCES = "plan-resources.page-PlanExec2.js"
PUBLISH = "plan-publish.page-PlanExec2.js"
MONITOR = "plan-monitor.page-PlanExec2.js"
OFFICIAL = "official-plan.page-PlanExec1.js"
NEW_ENTRY = "index-Bv02O2Notif1-NoTech-LabelPdf2-ActiveOut-PlanExec2.js"

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
    j.jsx("tbody",{children:bodyRows.map((row,idx)=>row?j.jsx("tr",{className:"border-b",children:cols.map(c=>j.jsx("td",{className:"px-3 py-2",children:String(row[c[0]]??"")},c[0]))},String(row.pubId||row.resourceId||row.candidateId||row.tailId||row.deviationId||idx)):j.jsx("tr",{children:j.jsx("td",{className:"px-3 py-2 text-muted-foreground",colSpan:cols.length,children:empty})}))})
  ]})});
}
function bar(planned,actual){
  const p=Number(planned)||0; const a=Number(actual)||0;
  const pct=p>0?Math.min(100,Math.round(100*a/p)):0;
  return j.jsxs("div",{className:"space-y-1 min-w-[10rem]",children:[
    j.jsx("div",{className:"h-2 w-full overflow-hidden rounded bg-muted",children:j.jsx("div",{className:"h-full bg-foreground/70",style:{width:pct+"%"}})}),
    j.jsx("div",{className:"text-xs text-muted-foreground",children:pct+"% · "+a+" / "+p})
  ]});
}
'''

RESOURCES_PAGE = PAGE_PREFIX + r'''
function Page(){
  const[siteCode,setSiteCode]=r.useState("PLANT-A");
  const[resourceId,setResourceId]=r.useState("RES-TANK-01");
  const[reserveId,setReserveId]=r.useState("RSV-UI-1");
  const[pubId,setPubId]=r.useState("PUB-CAND-DEMO-1");
  const[qty,setQty]=r.useState("10");
  const[loading,setLoading]=r.useState(false);
  const[error,setError]=r.useState(null);
  const[msg,setMsg]=r.useState(null);
  const[rows,setRows]=r.useState([]);
  const refresh=r.useCallback(async()=>{
    setLoading(true); setError(null);
    try{ const res=await call("mes_plan_listResources",{siteCode}); setRows(res.rows||[]); }
    catch(e){ setError(e instanceof Error?e.message:String(e)); }
    finally{ setLoading(false); }
  },[siteCode]);
  r.useEffect(()=>{ refresh(); },[refresh]);
  const confirm=async()=>{
    setLoading(true); setError(null); setMsg(null);
    try{
      const out=await call("mes_plan_confirmResource",{resourceId,actor:"operator",reason:"UI confirm"});
      setMsg("Подтверждён "+out.resourceId+" → "+out.dataStatus); await refresh();
    }catch(e){ setError(e instanceof Error?e.message:String(e)); }
    finally{ setLoading(false); }
  };
  const reserve=async()=>{
    setLoading(true); setError(null); setMsg(null);
    try{
      const out=await call("mes_plan_reserveSoft",{reserveId,resourceId,pubId,qty});
      setMsg("Резерв "+out.reserveId+" → "+out.status); await refresh();
    }catch(e){ setError(e instanceof Error?e.message:String(e)); }
    finally{ setLoading(false); }
  };
  return j.jsxs("div",{className:"flex h-full min-h-0 flex-col gap-3 p-4",children:[
    j.jsx("h1",{className:"text-lg font-semibold",children:"Ресурсы плана"}),
    j.jsxs("div",{className:"flex flex-wrap items-end gap-3",children:[
      j.jsxs("div",{className:"space-y-1",children:[j.jsx(Label,{children:"Площадка"}),j.jsx(Input,{value:siteCode,onChange:e=>setSiteCode(e.target.value),className:"w-40"})]}),
      j.jsx(Button,{type:"button",onClick:()=>void refresh(),disabled:loading,children:loading?"Обновление…":"Обновить"})
    ]}),
    error?j.jsx(Informer,{tone:"alert",variant:"filled",size:"s",title:"Ошибка",description:error}):null,
    msg?j.jsx(Informer,{tone:"success",variant:"filled",size:"s",title:"Готово",description:msg}):null,
    table([["resourceId","Ресурс"],["resourceType","Тип"],["qty","Кол-во"],["reservedQty","Резерв"],["freeQty","Свободно"],["dataStatus","Данные"],["qualityStatus","Качество"],["freshness","Свежесть"]], rows, "Нет ресурсов"),
    j.jsxs("section",{className:"grid gap-3 md:grid-cols-2",children:[
      j.jsxs("div",{className:"space-y-2 rounded-md border p-3",children:[
        j.jsx("h2",{className:"font-semibold",children:"Подтвердить ресурс"}),
        j.jsx(Input,{value:resourceId,onChange:e=>setResourceId(e.target.value),placeholder:"RES-…"}),
        j.jsx(Button,{type:"button",onClick:()=>void confirm(),disabled:loading,children:"Подтвердить"})
      ]}),
      j.jsxs("div",{className:"space-y-2 rounded-md border p-3",children:[
        j.jsx("h2",{className:"font-semibold",children:"Мягкий резерв"}),
        j.jsx(Input,{value:reserveId,onChange:e=>setReserveId(e.target.value),placeholder:"RSV-…"}),
        j.jsx(Input,{value:resourceId,onChange:e=>setResourceId(e.target.value),placeholder:"RES-…"}),
        j.jsx(Input,{value:pubId,onChange:e=>setPubId(e.target.value),placeholder:"PUB-…"}),
        j.jsx(Input,{value:qty,onChange:e=>setQty(e.target.value),placeholder:"qty"}),
        j.jsx(Button,{type:"button",onClick:()=>void reserve(),disabled:loading,children:"Зарезервировать"})
      ]})
    ]})
  ]});
}
const Component=Page; export{Component};
'''

PUBLISH_PAGE = PAGE_PREFIX + r'''
function Page(){
  const[siteCode,setSiteCode]=r.useState("PLANT-A");
  const[candidateId,setCandidateId]=r.useState("CAND-DEMO-1");
  const[pubId,setPubId]=r.useState("PUB-CAND-DEMO-1");
  const[reason,setReason]=r.useState("");
  const[loading,setLoading]=r.useState(false);
  const[error,setError]=r.useState(null);
  const[msg,setMsg]=r.useState(null);
  const[cands,setCands]=r.useState([]);
  const[pubs,setPubs]=r.useState([]);
  const refresh=r.useCallback(async()=>{
    setLoading(true); setError(null);
    try{
      const[c,p]=await Promise.all([
        call("mes_plan_listCandidates",{siteCode}),
        call("mes_plan_listPubs",{siteCode})
      ]);
      setCands(c.rows||[]); setPubs(p.rows||[]);
    }catch(e){ setError(e instanceof Error?e.message:String(e)); }
    finally{ setLoading(false); }
  },[siteCode]);
  r.useEffect(()=>{ refresh(); },[refresh]);
  const run=async(fn,input,okText)=>{
    setLoading(true); setError(null); setMsg(null);
    try{ const out=await call(fn,input); setMsg(okText+" "+JSON.stringify(out).slice(0,160)); await refresh(); }
    catch(e){ setError(e instanceof Error?e.message:String(e)); }
    finally{ setLoading(false); }
  };
  return j.jsxs("div",{className:"flex h-full min-h-0 flex-col gap-3 p-4",children:[
    j.jsx("h1",{className:"text-lg font-semibold",children:"Публикация плана"}),
    j.jsxs("div",{className:"flex flex-wrap items-end gap-3",children:[
      j.jsxs("div",{className:"space-y-1",children:[j.jsx(Label,{children:"Площадка"}),j.jsx(Input,{value:siteCode,onChange:e=>setSiteCode(e.target.value),className:"w-40"})]}),
      j.jsx(Button,{type:"button",onClick:()=>void refresh(),disabled:loading,children:loading?"Обновление…":"Обновить"})
    ]}),
    error?j.jsx(Informer,{tone:"alert",variant:"filled",size:"s",title:"Ошибка",description:error}):null,
    msg?j.jsx(Informer,{tone:"success",variant:"filled",size:"s",title:"Готово",description:msg}):null,
    j.jsxs("section",{className:"space-y-2",children:[
      j.jsx("h2",{className:"text-base font-semibold",children:"Кандидаты"}),
      table([["candidateId","Кандидат"],["skuCode","SKU"],["plannedQty","Объём"],["qualityStatus","Качество"],["validationStatus","Проверка"]], cands)
    ]}),
    j.jsxs("section",{className:"space-y-2",children:[
      j.jsx("h2",{className:"text-base font-semibold",children:"Официальные версии"}),
      table([["pubId","План"],["skuCode","SKU"],["plannedQty","Объём"],["status","Статус"],["shopStatus","Цех"],["snapshotId","Снимок"]], pubs)
    ]}),
    j.jsxs("section",{className:"grid gap-3 md:grid-cols-2",children:[
      j.jsxs("div",{className:"space-y-2 rounded-md border p-3",children:[
        j.jsx("h2",{className:"font-semibold",children:"Кандидат → публикация"}),
        j.jsx(Input,{value:candidateId,onChange:e=>setCandidateId(e.target.value),placeholder:"CAND-…"}),
        j.jsx(Button,{type:"button",onClick:()=>void run("mes_plan_validate",{candidateId},"Проверка"),disabled:loading,children:"Проверить"}),
        j.jsx(Button,{type:"button",onClick:()=>void run("mes_plan_publish",{candidateId,actor:"planner"},"Опубликован"),disabled:loading,children:"Опубликовать"}),
        j.jsx(Button,{type:"button",onClick:()=>void run("mes_plan_republish",{pubId:candidateId.startsWith("PUB-")?candidateId:"PUB-"+candidateId,reason:reason||"replan",actor:"planner"},"Переопубликован"),disabled:loading,children:"Переопубликовать по pubId ниже"})
      ]}),
      j.jsxs("div",{className:"space-y-2 rounded-md border p-3",children:[
        j.jsx("h2",{className:"font-semibold",children:"Ответ цеха"}),
        j.jsx(Input,{value:pubId,onChange:e=>setPubId(e.target.value),placeholder:"PUB-…"}),
        j.jsx(Input,{value:reason,onChange:e=>setReason(e.target.value),placeholder:"Причина отклонения"}),
        j.jsx(Button,{type:"button",onClick:()=>void run("mes_plan_acceptPub",{pubId,actor:"shop"},"Принят"),disabled:loading,children:"Принять"}),
        j.jsx(Button,{type:"button",onClick:()=>void run("mes_plan_rejectPub",{pubId,reason},"Отклонён"),disabled:loading,children:"Отклонить"})
      ]})
    ]})
  ]});
}
const Component=Page; export{Component};
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
  const atRisk=(facts||[]).filter(f=>String(f.risk)==="at_risk").length;
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
    kpi([{label:"Планов",value:String((facts||[]).length)},{label:"Под риском",value:String(atRisk)},{label:"Хвостов",value:String((tails||[]).length)},{label:"Отклонений",value:String((devs||[]).length)}]),
    j.jsxs("section",{className:"space-y-2",children:[
      j.jsx("h2",{className:"text-base font-semibold",children:"План / факт (шкала)"}),
      j.jsx("div",{className:"space-y-3 rounded-md border bg-card p-3",children:(facts&&facts.length)?facts.map(f=>j.jsxs("div",{className:"grid gap-2 md:grid-cols-[1fr_12rem_6rem] items-center border-b pb-2 last:border-0",children:[
        j.jsxs("div",{children:[j.jsx("div",{className:"font-medium",children:f.pubId}),j.jsx("div",{className:"text-xs text-muted-foreground",children:f.skuCode+" · риск "+(f.risk||"—")})]}),
        bar(f.plannedQty,f.actualQty),
        j.jsx("div",{className:"text-sm",children:"Δ "+String(f.deltaQty??"—")})
      ]},f.pubId)):j.jsx("div",{className:"text-sm text-muted-foreground",children:"Нет опубликованных планов"})})
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
const Component=Page; export{Component};
'''


def main() -> None:
    if not ENTRY.exists():
        raise SystemExit(f"missing entry {ENTRY}")

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = Path(f"/opt/ispf/staging/backups/index-pre-plan-ui2-{stamp}.js")
    backup.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ENTRY, backup)
    print("backup", backup)

    (ASSETS / RESOURCES).write_text(RESOURCES_PAGE.replace("__HUB__", HUB), encoding="utf-8")
    (ASSETS / PUBLISH).write_text(PUBLISH_PAGE.replace("__HUB__", HUB), encoding="utf-8")
    (ASSETS / MONITOR).write_text(MONITOR_PAGE.replace("__HUB__", HUB), encoding="utf-8")
    print("wrote", RESOURCES, PUBLISH, MONITOR)

    t = ENTRY.read_text(encoding="utf-8")

    # routes enum
    if "PLAN_RESOURCES:" not in t:
        needle = 'PLAN_MONITOR:"/operator/plan-monitor",'
        if needle not in t:
            raise SystemExit("PLAN_MONITOR route missing")
        t = t.replace(
            needle,
            needle
            + 'PLAN_RESOURCES:"/operator/plan-resources",'
            + 'PLAN_PUBLISH:"/operator/plan-publish",',
            1,
        )

    # nav
    if "op.planResources" not in t:
        needle = '{id:"op.planMonitor",label:"Мониторинг плана",to:Ee.OPERATOR.PLAN_MONITOR,roles:["operator","manager"]},'
        if needle not in t:
            raise SystemExit("planMonitor nav missing")
        t = t.replace(
            needle,
            needle
            + '{id:"op.planResources",label:"Ресурсы плана",to:Ee.OPERATOR.PLAN_RESOURCES,roles:["operator","manager"]},'
            + '{id:"op.planPublish",label:"Публикация плана",to:Ee.OPERATOR.PLAN_PUBLISH,roles:["operator","manager"]},',
            1,
        )

    m = re.search(r"__vite__mapDeps=\(i,m=__vite__mapDeps,d=\(m\.f\|\|\(m\.f=\[(.*?)\]\)\)\)", t)
    if not m:
        raise SystemExit("mapDeps not found")
    deps = re.findall(r'"([^"]+)"', m.group(1))
    for name in (RESOURCES, PUBLISH, MONITOR, OFFICIAL):
        key = f"assets/{name}"
        if key not in deps:
            deps.append(key)
    res_idx = deps.index(f"assets/{RESOURCES}")
    pub_idx = deps.index(f"assets/{PUBLISH}")
    mon_idx = deps.index(f"assets/{MONITOR}")
    input_idx = deps.index("assets/input-BE5Mt6vD-R321b.js")
    new_deps = ",".join(f'"{d}"' for d in deps)
    t = t[: m.start(1)] + new_deps + t[m.end(1) :]

    # Replace PLAN_MONITOR route and inject resources/publish after it.
    mon_re = re.compile(
        r'\{path:Ee\.OPERATOR\.PLAN_MONITOR,lazy:\(\)=>ut\(\(\)=>import\("\./[^"]+"\),__vite__mapDeps\(\[[^\]]+\]\)\)\},'
    )
    if not mon_re.search(t):
        raise SystemExit("PLAN_MONITOR route not found for inject")
    if "path:Ee.OPERATOR.PLAN_RESOURCES" in t:
        replacement = (
            f'{{path:Ee.OPERATOR.PLAN_MONITOR,lazy:()=>ut(()=>import("./{MONITOR}"),__vite__mapDeps([{mon_idx},{input_idx}]))}},'
        )
        t = mon_re.sub(replacement, t, count=1)
    else:
        replacement = (
            f'{{path:Ee.OPERATOR.PLAN_MONITOR,lazy:()=>ut(()=>import("./{MONITOR}"),__vite__mapDeps([{mon_idx},{input_idx}]))}},'
            f'{{path:Ee.OPERATOR.PLAN_RESOURCES,lazy:()=>ut(()=>import("./{RESOURCES}"),__vite__mapDeps([{res_idx},{input_idx}]))}},'
            f'{{path:Ee.OPERATOR.PLAN_PUBLISH,lazy:()=>ut(()=>import("./{PUBLISH}"),__vite__mapDeps([{pub_idx},{input_idx}]))}},'
        )
        t = mon_re.sub(replacement, t, count=1)

    new_entry = ASSETS / NEW_ENTRY
    new_entry.write_text(t, encoding="utf-8")
    STUB.write_text(f'export * from "./{NEW_ENTRY}";\n', encoding="utf-8")

    html = HTML.read_text(encoding="utf-8")
    html2, n = re.subn(
        r'src="/assets/index-Bv02O2Notif1[^"]*\.js"',
        f'src="/assets/{NEW_ENTRY}"',
        html,
        count=1,
    )
    if n != 1:
        raise SystemExit(f"html entry replace failed n={n}")
    HTML.write_text(html2, encoding="utf-8")

    print("entry", NEW_ENTRY)
    print("HAS_RES", "Ресурсы плана" in t)
    print("HAS_PUB", "Публикация плана" in t)
    print("HTML_OK", NEW_ENTRY in HTML.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
