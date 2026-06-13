import js from "@eslint/js";
import globals from "globals";

// ESLint flat config for the frontend's hand-written browser scripts under
// static/js. The static/js/vendor/ directory (minified third-party bundles,
// if any) is excluded — such files are not ours to lint and would drown the
// report.
export default [
  {
    ignores: [
      "src/main/resources/static/js/vendor/**",
      "build/**",
      "node_modules/**",
    ],
  },
  js.configs.recommended,
  {
    files: ["src/main/resources/static/js/**/*.js"],
    languageOptions: {
      ecmaVersion: 2023,
      // The scripts are loaded as classic <script> tags, not ES modules.
      sourceType: "script",
      globals: {
        ...globals.browser,
        // Cross-file helpers declared at script scope in escape-html.js and
        // consumed by other scripts loaded as separate <script> tags.
        escapeHtml: "readonly",
        escapeAttr: "readonly",
      },
    },
    rules: {
      "no-var": "error",
      eqeqeq: ["error", "smart"],
      // Honour the codebase's "_"-prefix convention for intentionally unused
      // bindings: unused function args and caught errors named `_e` / `_ignored`
      // are deliberate signals, not dead code.
      "no-unused-vars": [
        "warn",
        {
          argsIgnorePattern: "^_",
          caughtErrorsIgnorePattern: "^_",
          varsIgnorePattern: "^_",
        },
      ],
      "no-undef": "error",
    },
  },
];
