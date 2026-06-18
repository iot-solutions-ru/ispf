import AlertRulesPanel from "./AlertRulesPanel";
import CorrelatorsPanel from "./CorrelatorsPanel";

interface AutomationViewProps {
  readOnly?: boolean;
}

export default function AutomationView({ readOnly = false }: AutomationViewProps) {
  return (
    <main className="main automation-main">
      <header className="automation-header">
        <h1>Автоматизация</h1>
        <p className="hint">
          Alert rules (CEL) публикуют события; корреляторы реагируют на события и запускают workflow.
          {readOnly && " Режим оператора: только просмотр."}
        </p>
      </header>
      <AlertRulesPanel readOnly={readOnly} />
      <CorrelatorsPanel readOnly={readOnly} />
    </main>
  );
}
