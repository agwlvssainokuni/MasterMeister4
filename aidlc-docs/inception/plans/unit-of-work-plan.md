# Unit of Work Plan

execution-plan.mdの暫定ユニット見通し（10ユニット）と、components.md/services.mdのコンポーネント構成を踏まえ、正式なユニット分解を行う。

**プロジェクト形態**: モノリス（単一Spring Bootバックエンド＋単一Reactフロントエンド）。「Unit of Work」はマイクロサービス分割ではなく、開発・実装の作業単位（論理モジュールのまとまり）として扱う。

## 実行チェックリスト

- [ ] Step A: `aidlc-docs/inception/application-design/unit-of-work.md` を作成する（ユニット定義・責務、Greenfield向けコード構成方針を含む）
- [ ] Step B: `aidlc-docs/inception/application-design/unit-of-work-dependency.md` を作成する（依存関係マトリクス）
- [ ] Step C: `aidlc-docs/inception/application-design/unit-of-work-story-map.md` を作成する（全29ストーリーのユニットへの割当）
- [ ] Step D: ユニット境界・依存関係を検証する
- [ ] Step E: 全ストーリーがいずれかのユニットに割り当てられていることを確認する

---

## 質問カテゴリ別の評価

- **Story Grouping**: Question 1
- **Dependencies**: Question 2
- **Team Alignment**: 単独開発者のため対象外（N/A） — チーム編成・所有権境界の検討は不要
- **Technical Considerations**: Question 3
- **Business Domain**: Question 4
- **Code Organization (Greenfield multi-unit)**: Question 5

## 質問

各設問に A/B/C... の記号で回答してください。当てはまる選択肢がない場合は最後の「Other」を選び、[Answer]: の後ろに内容を記述してください。

### Question 1: ユーザ登録・ユーザ認証の統合
execution-plan.mdの暫定見通しでは「ユーザ登録・認証」を優先度P1の1ユニットとしていますが、stories.mdではEpic1（ユーザ登録）とEpic3（ユーザ認証）は別エピックで、「Epic1と並行して実装」という位置づけでした。正式なユニット区分としてどちらにしますか？

A) 1つのユニット「ユーザ登録・認証」として統合する（登録直後にログインが必要になるため、一体で動作確認しやすい）

B) 「ユーザ登録」「ユーザ認証」を別ユニットとする（責務が異なるため独立させる。優先度は同じP1で、着手順序はどちらが先でもよい）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 2: ユニット間の着手順序
execution-plan.mdの優先順位（デザインシステム基盤→ユーザ登録・認証→RDBMSセットアップ→アクセス制御→マスタメンテナンス→クエリ保存・実行→クエリビルダー→クエリ履歴→監査ログ閲覧→CI/CD）は、厳密な逐次実行（前ユニットの完了・承認を待ってから次に着手）を意図していますか、それとも一部のユニットは並行着手が可能ですか？

A) 厳密な逐次実行とする（1ユニットずつ完了・承認してから次に進む。単独開発でシンプルに進められる）

B) 依存関係のないユニット同士は並行着手を許容する（例: デザインシステム基盤完了後、ユーザ登録・認証とRDBMSセットアップは互いに依存しないため並行して進めてもよい）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 3: ユニットごとの動作確認粒度
Build and Testステージは全ユニット完了後にまとめて実施する計画（execution-plan.md）ですが、開発中、各ユニット完了時点で個別に動作確認（ローカルでの手動確認やユニットテスト実行）を行いますか？

A) 各ユニット完了時点で、そのユニット単体の動作確認（ユニットテスト・簡単な手動確認）を行う。全体の統合テストはBuild and Testステージでまとめて実施する

B) 個別の動作確認は行わず、全ユニット完了後にまとめてBuild and Testステージで確認する

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 4: ユニット区分の過不足確認
現在の10ユニット区分（デザインシステム基盤、ユーザ登録・認証、RDBMSセットアップ、アクセス制御、マスタメンテナンス、クエリ保存・実行、クエリビルダー、クエリ履歴、監査ログ閲覧、CI/CD）で過不足ないですか？

A) この10区分（Question 1の回答に応じて調整）で問題ない

B) 過不足がある（Otherに具体的に記載してください）

X) Other（[Answer]: の後に内容を記述）

[Answer]:

### Question 5: バックエンドのパッケージ構成方針
バックエンド（Spring Boot）のパッケージ構成は、どちらの方針とりますか？

A) 機能単位（package-by-feature）: 例 `jp.example.mastermeister.registration`, `jp.example.mastermeister.accesscontrol`, `jp.example.mastermeister.query` のように、ユニット／ドメインごとにパッケージを分ける。1ユニットの変更が1パッケージに閉じやすい

B) レイヤ単位（package-by-layer）: 例 `jp.example.mastermeister.controller`, `jp.example.mastermeister.service`, `jp.example.mastermeister.repository` のように技術層で分ける

X) Other（[Answer]: の後に内容を記述）

[Answer]:

---

すべての設問に回答後、完了したことを教えてください。回答内容を分析し、曖昧な点があれば追加の確認質問を作成します。
