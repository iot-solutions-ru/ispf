package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picks the best report path from a catalog for operator natural-language requests.
 */
final class OperatorAgentReportResolver {

    private static final Set<String> SHIFT_TOKENS = Set.of(
            "смен", "сменн", "сменный", "смена", "shift"
    );
    private static final Set<String> ENERGY_TOKENS = Set.of(
            "энерг", "energy", "квт", "kwh", "журнал", "journal", "суточ", "daily"
    );
    private static final Set<String> GPU_TOKENS = Set.of(
            "gpu", "гпу", "наработ", "runhours", "run-hours", "агрегат"
    );

    record ReportEntry(String path, String title, String reportType) {
    }

    record ScoredEntry(ReportEntry entry, int score) {
    }

    record MatchAnalysis(
            String bestPath,
            String bestTitle,
            int bestScore,
            boolean terminologyMismatch,
            String mismatchReason,
            List<ScoredEntry> ranked
    ) {
        boolean needsClarification() {
            if (ranked == null || ranked.isEmpty()) {
                return false;
            }
            if (terminologyMismatch) {
                return true;
            }
            if (ranked.size() >= 2) {
                int first = ranked.getFirst().score();
                int second = ranked.get(1).score();
                if (first > 0 && second > 0 && first - second <= 3) {
                    return true;
                }
            }
            return bestScore <= 0 && ranked.size() > 1;
        }
    }

    private OperatorAgentReportResolver() {
    }

    @SuppressWarnings("unchecked")
    static List<ReportEntry> parseCatalog(Map<String, Object> listReportsResult) {
        if (listReportsResult == null || !"OK".equals(String.valueOf(listReportsResult.get("status")))) {
            return List.of();
        }
        Object reports = listReportsResult.get("reports");
        if (!(reports instanceof List<?> list)) {
            return List.of();
        }
        List<ReportEntry> entries = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            String path = stringValue(row.get("path"));
            if (path.isBlank()) {
                continue;
            }
            entries.add(new ReportEntry(
                    path,
                    stringValue(row.get("title")),
                    stringValue(row.get("reportType"))
            ));
        }
        return entries;
    }

    static String resolveBestPath(String userMessage, List<ReportEntry> catalog) {
        MatchAnalysis analysis = analyze(userMessage, catalog);
        return analysis.bestPath();
    }

    static MatchAnalysis analyze(String userMessage, List<ReportEntry> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return new MatchAnalysis(null, null, 0, false, null, List.of());
        }
        if (catalog.size() == 1) {
            ReportEntry only = catalog.getFirst();
            return new MatchAnalysis(
                    only.path(),
                    only.title(),
                    100,
                    hasTerminologyMismatch(userMessage, only),
                    buildMismatchReason(userMessage, only),
                    List.of(new ScoredEntry(only, 100))
            );
        }
        String query = userMessage != null ? userMessage.toLowerCase(Locale.ROOT) : "";
        List<String> tokens = tokenize(query);

        List<ScoredEntry> ranked = new ArrayList<>();
        for (ReportEntry entry : catalog) {
            ranked.add(new ScoredEntry(entry, scoreEntry(entry, tokens, query)));
        }
        ranked.sort(Comparator.comparingInt(ScoredEntry::score).reversed());

        ScoredEntry best = ranked.getFirst();
        int bestScore = best.score();
        if (bestScore <= 0) {
            ReportEntry fallback = catalog.stream()
                    .filter(entry -> haystack(entry).contains("daily")
                            || haystack(entry).contains("journal")
                            || haystack(entry).contains("суточ")
                            || haystack(entry).contains("журнал")
                            || haystack(entry).contains("energy")
                            || haystack(entry).contains("энерг"))
                    .findFirst()
                    .orElse(catalog.getFirst());
            best = new ScoredEntry(fallback, 1);
            bestScore = 1;
            ranked = reorderWithBest(ranked, fallback);
        }
        return new MatchAnalysis(
                best.entry().path(),
                best.entry().title(),
                bestScore,
                hasTerminologyMismatch(userMessage, best.entry()),
                buildMismatchReason(userMessage, best.entry()),
                ranked
        );
    }

    private static List<ScoredEntry> reorderWithBest(List<ScoredEntry> ranked, ReportEntry best) {
        List<ScoredEntry> copy = new ArrayList<>(ranked);
        copy.removeIf(item -> item.entry().path().equals(best.path()));
        copy.addFirst(new ScoredEntry(best, Math.max(1, ranked.isEmpty() ? 1 : ranked.getFirst().score())));
        return copy;
    }

    private static boolean hasTerminologyMismatch(String userMessage, ReportEntry entry) {
        if (userMessage == null || userMessage.isBlank() || entry == null) {
            return false;
        }
        String query = userMessage.toLowerCase(Locale.ROOT);
        String hay = haystack(entry);
        if (containsAny(query, SHIFT_TOKENS)) {
            boolean titleHasShift = hay.contains("смен") || hay.contains("shift");
            boolean titleHasDailyJournal = hay.contains("суточ") || hay.contains("журнал")
                    || hay.contains("daily") || hay.contains("journal")
                    || hay.contains("энерг") || hay.contains("energy");
            if (!titleHasShift && titleHasDailyJournal) {
                return true;
            }
            if (!titleHasShift && !titleHasDailyJournal) {
                return true;
            }
        }
        List<String> tokens = tokenize(query);
        boolean hasSubstantiveToken = false;
        for (String token : tokens) {
            if (token.length() < 4) {
                continue;
            }
            if (isGenericRequestToken(token)) {
                continue;
            }
            hasSubstantiveToken = true;
            if (!hay.contains(token)) {
                return true;
            }
        }
        return !hasSubstantiveToken && containsAny(query, SHIFT_TOKENS);
    }

    static boolean mentionsUnknownReportId(String userMessage, List<ReportEntry> catalog) {
        if (userMessage == null || userMessage.isBlank() || catalog == null || catalog.isEmpty()) {
            return false;
        }
        Set<String> known = new java.util.HashSet<>();
        for (ReportEntry entry : catalog) {
            known.add(pathLeaf(entry.path()).toLowerCase(Locale.ROOT));
            if (!entry.title().isBlank()) {
                known.add(entry.title().toLowerCase(Locale.ROOT));
            }
        }
        String query = userMessage.toLowerCase(Locale.ROOT);
        for (String token : tokenize(query)) {
            if (token.length() < 8) {
                continue;
            }
            if (!token.contains("-") && !token.contains("report") && !token.contains("отч")) {
                continue;
            }
            boolean knownToken = known.stream().anyMatch(id -> id.contains(token) || token.contains(id));
            if (!knownToken) {
                return true;
            }
        }
        if (query.contains("shift-report") || query.contains("mini-tec-shift")) {
            return known.stream().noneMatch(id -> id.contains("shift"));
        }
        return false;
    }

    private static String pathLeaf(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private static boolean isGenericRequestToken(String token) {
        return Set.of(
                "запусти", "запустить", "покажи", "выведи", "сформируй", "кратко", "опиши", "цифры",
                "run", "execute", "open", "show", "report", "отчёт", "отчет"
        ).contains(token);
    }

    private static String buildMismatchReason(String userMessage, ReportEntry entry) {
        if (!hasTerminologyMismatch(userMessage, entry)) {
            return null;
        }
        String title = entry.title().isBlank() ? entry.path() : entry.title();
        String query = userMessage != null ? userMessage.trim() : "";
        if (containsAny(query.toLowerCase(Locale.ROOT), SHIFT_TOKENS)) {
            return "Точного отчёта «сменный» в каталоге нет. В этом приложении для сменной сводки "
                    + "обычно используется «" + title + "».";
        }
        return "Запрос «" + query + "» не совпадает с названиями отчётов. "
                + "Ближе всего подходит «" + title + "».";
    }

    private static int scoreEntry(ReportEntry entry, List<String> tokens, String query) {
        String haystack = haystack(entry);
        int score = 0;
        for (String token : tokens) {
            if (token.length() < 3) {
                continue;
            }
            if (haystack.contains(token)) {
                score += 4;
            }
        }
        if (containsAny(query, SHIFT_TOKENS)) {
            if (haystack.contains("daily") || haystack.contains("journal")
                    || haystack.contains("суточ") || haystack.contains("журнал")
                    || haystack.contains("energy") || haystack.contains("энерг")) {
                score += 12;
            }
        }
        if (containsAny(query, ENERGY_TOKENS)) {
            if (haystack.contains("energy") || haystack.contains("энерг")
                    || haystack.contains("journal") || haystack.contains("журнал")) {
                score += 10;
            }
        }
        if (containsAny(query, GPU_TOKENS)) {
            if (haystack.contains("gpu") || haystack.contains("гпу") || haystack.contains("run-hours")) {
                score += 10;
            }
        }
        if (query.contains("отчёт") || query.contains("отчет") || query.contains("report")) {
            score += 1;
        }
        return score;
    }

    private static String haystack(ReportEntry entry) {
        return (entry.path() + " " + entry.title() + " " + entry.reportType())
                .toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, Set<String> needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return tokens;
        }
        for (String part : query.split("[^\\p{L}\\p{N}_\\-]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
