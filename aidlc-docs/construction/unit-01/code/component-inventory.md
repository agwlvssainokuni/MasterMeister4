# UNIT-01 デザインシステム基盤 - コンポーネント一覧

`frontend/src/design-system/`配下に生成した成果物の一覧。パスはすべて`frontend/src/design-system/`からの相対。

## 基盤

| ファイル | 内容 |
|---|---|
| `tokens/tokens.css` | 2層デザイントークン（プリミティブ`--mm-palette-*` / セマンティック`--mm-color-*`等）、ライト/ダーク |
| `tokens/fonts.ts` | `@fontsource`によるNoto Sans JP・Noto Sans Monoのセルフホスト |
| `theme/ThemeProvider.tsx` | ダークモード（light/dark/system） |
| ~~`i18n/index.ts`, `i18n/locales/{ja,en}/{common,design-system}.json`~~ | react-i18next初期化、2名前空間。**訂正（UNIT-02にて）**: `common`名前空間にUNIT-02以降の画面固有文言も蓄積される運用となったため、`frontend/src/design-system/i18n/`から`frontend/src/i18n/`（`design-system/`と同階層）へ移動。詳細は`aidlc-docs/construction/unit-02/code/frontend-summary.md`参照 |
| `ErrorBoundary.tsx` | 描画エラー時の汎用フォールバック（コンソール出力のみ） |

## コンポーネント（`components/`配下）

| コンポーネント | ファイル | 分類 |
|---|---|---|
| `Icon` | Icon.tsx | 基本部品 |
| `Button`, `IconButton` | Button.tsx | 基本部品 |
| `TextInput`, `PasswordInput`, `TextArea`, `Select`, `SearchInput` | TextInput.tsx | 基本部品 |
| `Checkbox`, `RadioGroup`, `Switch` | Choice.tsx | 基本部品 |
| `FormField` | FormField.tsx | 基本部品 |
| `PublicLayout` | PublicLayout.tsx | グランドデザイン |
| `AppShell`（Header/SideNav/Footerを内包） | AppShell.tsx | グランドデザイン |
| `Footer` | Footer.tsx | グランドデザイン |
| `LanguageSwitcher`, `ThemeToggle` | LanguageSwitcher.tsx, ThemeToggle.tsx | グランドデザイン |
| `useDefaultNavItems`, `NAV_ROUTES` | navigation.ts | グランドデザイン |
| `Badge`, `Alert`, `Card`, `EmptyState`, `CodeBlock`, `KeyValueList` | Display.tsx | 表示・フィードバック |
| `AuthCard` | AuthCard.tsx | 表示・フィードバック |
| `PageHeader` | PageHeader.tsx | 表示・フィードバック |
| `DataTable` | DataTable.tsx | 表示・フィードバック |
| `Pagination` | Pagination.tsx | 表示・フィードバック |
| `Spinner` | Spinner.tsx | 表示・フィードバック |
| `Modal`, `ConfirmDialog` | Modal.tsx | 表示・フィードバック |
| `FilterBar` | FilterBar.tsx | 表示・フィードバック |
| `Tabs` | Tabs.tsx | 表示・フィードバック |
| `ToastProvider`, `useToast` | Toast.tsx | 表示・フィードバック |
| `Dropdown` | Dropdown.tsx | 表示・フィードバック |
| `Tooltip` | Tooltip.tsx | 表示・フィードバック |

## 取込方針の要約

`reference/design-system/`（参考資材）を基に構築。当初計画からの主な変更点:

- `ErrorAlert`/`SuccessAlert`の個別コンポーネント案は、`Alert`（`tone`プロパティ）に統合
- `Overlay`という汎用背景幕コンポーネントは存在しないことが判明し、`Modal`内部で直接処理する構成に修正。代わりに参考実装の`Overlay.tsx`が実際に持っていた`Dropdown`・`Tooltip`をユーザー確認の上追加
- `Tabs`/`Toast`/`CodeBlock`/`KeyValueList`は当初「後続ユニットに委ねる」候補だったが、ユーザー判断で今回まとめて構築
- `DataTable`は列定義・簡易表示のみ実装。ソート・選択のロジックはコールバックとして提供する設計とし、実データ連携は後続ユニット（UNIT-04・UNIT-05等）で行う

## テスト

Vitest + React Testing Libraryで19ファイル・51テストを作成（`*.test.tsx`）。対象は状態遷移・キーボード操作・アクセシビリティ属性など、ロジックを持つコンポーネントを中心に選定。純粋に静的な表示のみのコンポーネント（`PageHeader`, `AuthCard`, `Spinner`, `Icon`等）は個別テストを省略した。

## Icon一覧

自作SVGアイコン22種（ベースライン20種 + `eye`/`eye-off`）。詳細は`aidlc-docs/construction/plans/unit-01-code-generation-plan.md`の「Icon一覧（ベースライン）」参照。
