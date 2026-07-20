# UNIT-01 デザインシステム基盤 - Code Generation Summary

`aidlc-docs/construction/plans/unit-01-code-generation-plan.md`の実行結果サマリ。詳細なコンポーネント一覧は`component-inventory.md`を参照。

## 生成した成果物（ワークスペース直下）

| パス | 内容 |
|---|---|
| `settings.gradle.kts` | Gradleマルチモジュール構成の定義（`backend`, `frontend`） |
| `backend/` | Spring Boot 4.1 + Java 25。最小起動クラス（`MasterMeisterApplication`、`SpringBootServletInitializer`継承）、i18n基盤、OWASP Dependency-Checkプラグイン |
| `frontend/` | Vite + React 19 + TypeScript。デザインシステム一式・代表画面モック |
| `devenv/docker-compose.yml` | MailPit, MySQL, MariaDB, PostgreSQL（ローカル開発用） |
| `gradlew`, `gradle/` | Gradle Wrapper（9.6.1） |

## 主要な設計判断

1. **単一WAR統合**: `frontend`をGradleサブプロジェクトとして取り込み、`backend`の`bootWar`タスクのみがビルド成果物（`frontend/dist`）を`WEB-INF/classes/static/`へ内包する。`backend:build`/`backend:assemble`はfrontendの影響を受けない（`providedRuntime`は`spring-boot-starter-tomcat-runtime`を使用）。実機で`java -jar`起動・HTTP 200・静的配信を確認済み
2. **デザイントークン**: 2層アーキテクチャ（プリミティブ`--mm-palette-*` / セマンティック`--mm-color-*`等）。ライト/ダークは`:root[data-theme]`で切り替え
3. **参考資材の取込**: `reference/design-system/`を基盤としつつ、ユーザー判断で当初「後続ユニットに委ねる」予定だったTabs/Toast/CodeBlock/KeyValueList、および仕分け時に見落としていたDropdown/Tooltipも今回まとめて構築（詳細は`component-inventory.md`）
4. **DataTable**: 列定義・簡易表示のみ実装。ソート・選択はコールバックとして提供し、実データ連携は後続ユニット（UNIT-04・UNIT-05等）で行う
5. **モックのルーティング**: `/mock/*`はdevビルド限定。`lazy()`呼び出し自体を`import.meta.env.DEV`の三項演算子内に置くことで、本番ビルドからモック関連コードを完全に除外（詳細は本ファイル末尾のトラブルシューティング参照）
6. **i18n**: `common`/`design-system`の2名前空間。フロントエンドはreact-i18next、バックエンドはSpring `MessageSource`（NFR-7.3対応、UNIT-01の最小スケルトンに雛形のみ用意）

## テスト

- コンポーネント: Vitest + RTLで19ファイル・51テスト（状態遷移・キーボード操作・アクセシビリティ属性を中心に、ロジックを持つコンポーネントを選定）
- モック画面: 5ファイル・14テスト（画面状態の切り替わりを検証）
- 合計: 24ファイル・65テスト、すべて成功

## 後続対応が必要な項目

- **OWASP Dependency-Check未完走**: `./gradlew :backend:dependencyCheckAnalyze`はプラグインの導入・起動は確認できたが、NVD APIキー未設定のため初回データベース同期に非常に長時間を要し、本セッションでは完走を待たずに中断した。NVD APIキーを取得の上、改めて実行することを推奨する（UNIT-10 CI/CD着手時までに対応。`npm audit`（フロントエンド側）は実行済みで0件）

## トラブルシューティング（後続ユニットへの申し送り）

- **`providedRuntime`の落とし穴**: `spring-boot-starter-tomcat`をそのまま`providedRuntime`に指定すると、`spring-boot-starter-web`が透過的に持ち込む同一アーティファクトとの解決順序の問題で`spring-web`本体まで実行時クラスパスから除外される不具合があった。`spring-boot-starter-tomcat-runtime`を使うことで解決
- **コード分割とツリーシェイク**: `{条件 ? <Route element={<LazyComp />} /> : null}`のようにJSX側だけを条件分岐させても、`lazy(() => import(...))`という関数呼び出し自体はバンドラーに副作用ありとみなされ、変数が未参照でも除去されない。`const X = 条件 ? lazy(...) : null`のように`lazy()`呼び出し自体を条件分岐の中に置く必要がある
- **RTLのテスト分離**: `vitest.config`で`globals: false`にした場合、`@testing-library/react`の自動DOMクリーンアップ（`afterEach`）は自動登録されないため、`test/setup.ts`に明示的に`afterEach(cleanup)`を登録する必要がある
