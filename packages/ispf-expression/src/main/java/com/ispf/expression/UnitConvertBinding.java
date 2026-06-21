package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code unitConvert(sourceVariable, fromUnit, toUnit[, field])}.
 * <p>
 * Temperature conversion between C, F, and K (case insensitive).
 */
public final class UnitConvertBinding implements PlatformBinding {

    static final UnitConvertBinding INSTANCE = new UnitConvertBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "unitConvert\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*,\\s*(" + BindingSourceHelper.IDENT
                    + ")\\s*,\\s*(" + BindingSourceHelper.IDENT + ")\\s*(?:,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private UnitConvertBinding() {
    }

    @Override
    public boolean matches(String expression) {
        return expression != null && PATTERN.matcher(expression.trim()).matches();
    }

    @Override
    public Optional<Object> evaluate(
            PlatformObject object,
            String targetVariable,
            String expression,
            BindingEvaluationContext context
    ) {
        Matcher matcher = PATTERN.matcher(expression.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        BindingSourceHelper.SourceField source = BindingSourceHelper.sourceField(
                matcher.group(1),
                matcher.group(4),
                "value"
        );
        String fromUnit = matcher.group(2).toUpperCase(Locale.ROOT);
        String toUnit = matcher.group(3).toUpperCase(Locale.ROOT);

        return BindingSourceHelper.readNumericField(object, source.sourceVariable(), source.field())
                .flatMap(value -> convert(value, fromUnit, toUnit));
    }

    static Optional<Double> convert(double value, String fromUnit, String toUnit) {
        if (fromUnit.equals(toUnit)) {
            return Optional.of(value);
        }
        Double celsius = toCelsius(value, fromUnit);
        if (celsius == null) {
            return Optional.empty();
        }
        return fromCelsius(celsius, toUnit);
    }

    private static Double toCelsius(double value, String unit) {
        return switch (unit) {
            case "C" -> value;
            case "F" -> (value - 32) * 5 / 9;
            case "K" -> value - 273.15;
            default -> null;
        };
    }

    private static Optional<Double> fromCelsius(double celsius, String unit) {
        return switch (unit) {
            case "C" -> Optional.of(celsius);
            case "F" -> Optional.of(celsius * 9 / 5 + 32);
            case "K" -> Optional.of(celsius + 273.15);
            default -> Optional.empty();
        };
    }
}
