package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Map;

/**
 * Soft pace reminders as a turn grows — nudge toward finish without hard-stopping early.
 */
final class AgentTurnPaceHints {

    private AgentTurnPaceHints() {
    }

    /** Tool / finish / guard / error rows — the turn budget. Live LLM dialogue and status rows do not count. */
    static int actionStepCount(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> step : steps) {
            String type = step != null ? String.valueOf(step.get("type")) : "";
            if ("llm".equals(type) || "status".equals(type)) {
                continue;
            }
            count++;
        }
        return count;
    }

    static String gentlePaceSuffix(List<Map<String, Object>> steps, int maxStepsTotal) {
        int stepCount = actionStepCount(steps);
        int remaining = Math.max(0, maxStepsTotal - stepCount);
        if (stepCount >= 40) {
            return " Шагов уже " + stepCount + " — если цель достигнута, лучше finish; иначе только нужный следующий инструмент.";
        }
        if (stepCount >= 24) {
            return " Шагов " + stepCount + " — проверьте, хватает ли данных для ответа или завершения плана.";
        }
        if (stepCount >= 12) {
            return " Избегайте шума в логе: не повторяйте один и тот же tool; пакетные записи вместо серии мелких add_*/set_*.";
        }
        if (remaining <= 12 && remaining > 0) {
            return " Осталось ~" + remaining + " шагов до лимита turn (" + maxStepsTotal + ") — завершите finish, если цель достигнута.";
        }
        return "";
    }
}
