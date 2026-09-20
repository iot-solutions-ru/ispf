// ESLint flat config for the ISPF web console (F-02 code-analysis follow-up).
//
// Scope: static analysis that `tsc --strict` does not cover — unused disables,
// hooks rules, obvious logic errors. Formatting is intentionally not enforced.
// Type-aware rules are off to keep `npm run lint` fast enough for the PR gate.
import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: [
      "dist/**",
      "dev-dist/**",
      "node_modules/**",
      "coverage/**",
      "playwright-report/**",
      "test-results/**",
      "public/**",
      "tmp-*",
      "scripts/**",
      "*.config.ts",
      "*.config.js",
      "*.mjs",
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser, ...globals.es2021 },
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      // Hooks correctness (classic rules only; React Compiler rules are opt-in later).
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
      "react-refresh/only-export-components": ["warn", { allowConstantExport: true }],

      // tsc already enforces noUnusedLocals/noUnusedParameters; keep ESLint's
      // version aligned so the two do not disagree on `_`-prefixed args.
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_", caughtErrorsIgnorePattern: "^_" },
      ],
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/no-non-null-assertion": "warn",
      // `import("./x").T` inline annotations are used deliberately in api.ts to avoid
      // import cycles, so only the import-statement form is checked here.
      "@typescript-eslint/consistent-type-imports": [
        "warn",
        { fixStyle: "inline-type-imports", disallowTypeAnnotations: false },
      ],
      "no-console": ["warn", { allow: ["warn", "error", "info", "debug"] }],
      "eqeqeq": ["error", "smart"],
      "no-debugger": "error",
      "prefer-const": ["warn", { destructuring: "all" }],
    },
  },
  {
    // Node CLI scripts that live under src/ (mimic exporters) — console output is their job.
    files: ["src/scada/templates/**/export*.ts"],
    languageOptions: { globals: { ...globals.node } },
    rules: { "no-console": "off" },
  },
  {
    // Tests: relax rules that fight with testing patterns.
    files: ["src/**/*.test.{ts,tsx}", "src/test/**"],
    rules: {
      "@typescript-eslint/no-non-null-assertion": "off",
      "react-refresh/only-export-components": "off",
      "no-console": "off",
    },
  },
);
