import { useMemo, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "antd";
import { formatJournalJson } from "../../utils/journal/journalDetails";

export interface JournalDetailSection {
  id: string;
  label: string;
  value: unknown;
}

interface JournalExpandableItemProps {
  className?: string;
  sections: JournalDetailSection[];
  children: ReactNode;
}

export default function JournalExpandableItem({
  className = "event-journal-item",
  sections,
  children,
}: JournalExpandableItemProps) {
  const { t } = useTranslation("journal");
  const [open, setOpen] = useState(false);
  const visibleSections = useMemo(
    () => sections.filter((section) => {
      if (section.value == null) {
        return false;
      }
      if (typeof section.value === "string" && !section.value.trim()) {
        return false;
      }
      return true;
    }),
    [sections],
  );
  const hasDetails = visibleSections.length > 0;

  return (
    <li className={`${className}${open ? " journal-entry-expanded" : ""}`.trim()}>
      <div className="journal-entry-head">
        <div className="journal-entry-summary">{children}</div>
        {hasDetails && (
          <Button
            size="small"
            className="journal-entry-toggle"
            aria-expanded={open}
            onClick={() => setOpen((value) => !value)}
          >
            {open ? t("details.hide") : t("details.show")}
          </Button>
        )}
      </div>
      {open && hasDetails && (
        <div className="journal-entry-details">
          {visibleSections.map((section) => (
            <div key={section.id} className="journal-entry-detail-pane">
              <h4>{section.label}</h4>
              <pre className="journal-entry-detail-pre">{formatJournalJson(section.value)}</pre>
            </div>
          ))}
        </div>
      )}
    </li>
  );
}
