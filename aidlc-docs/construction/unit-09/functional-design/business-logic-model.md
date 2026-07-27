# UNIT-09 監査ログ閲覧 - Business Logic Model

## 1. 概要

本ユニットは、UNIT-02〜UNIT-08で記録済みの`AuditLogEntry`（監査ログ、既存実装済み・変更なし）を対象に、管理者専用の閲覧・絞込・ページング機能を提供する。新規のデータ記録処理は持たない（Q1=A、既存の`AuditLogService`・`AuditEventPublisher`は変更しない）。

## 2. 監査ログ一覧取得フロー

1. 監査ログ一覧画面（単一画面、Q1=A）で、絞込条件（発生日時範囲・イベント種別・対象ユーザ・対象接続・結果ステータス）とページ番号を指定し、一覧を取得する。`connectionId`を持たないイベント（`LOGIN`等）も同一画面で扱うため、対象接続は数ある絞込条件の1つという位置付けであり、UNIT-05〜08のような接続選択画面は設けない
2. リクエストは`/api/admin/audit-log`配下のエンドポイントに送られる。Spring Securityの`SecurityConfig`が`.requestMatchers("/api/admin/**").hasRole("ADMIN")`によりエンドポイント全体を保護しているため（Q2=A）、一般ユーザのリクエストはController到達前に403で拒否される。Service層・Controller層でのロールベースのデータ絞込（UNIT-08のexecutedByFilterのような仕組み）は不要
3. Controller層は以下の順で処理する:
   a. 絞込パラメータをバリデーションする（日時範囲の相関検証、ページサイズ上限、後述§4）
   b. `AuditLogService`（既存、記録用）とは別に、本ユニットで新規に追加する`AuditLogQueryService`（閲覧専用）へ、絞込条件と`Pageable`を渡す
   c. `AuditLogQueryService`は`AuditLogEntryRepository`（既存、`JpaSpecificationExecutor`を新規実装、UNIT-08で確立した`Specification` APIパターンを踏襲、Q3=A）へ問い合わせる
   d. 取得した`AuditLogEntry`一覧のうち、`userId`が非nullのものをユニークに集約し`UserRepository`から、`connectionId`が非nullのものをユニークに集約し`RdbmsConnectionRepository`から、それぞれ`findAllById`で一括取得する（N+1回避、UNIT-08と同じ方式、Q5=A）
   e. 上記を結合し、`AuditLogEntryView`（表示用DTO、§5参照）のページ結果として返す

## 3. 絞込ロジック

すべての絞込条件はANDで組み合わされる（UNIT-08のBR-QUERYHISTORY-09と同じ方針）。

- **発生日時範囲**: `occurredAt`が指定範囲内（開始のみ・終了のみの片側指定も許容）
- **イベント種別**: `eventType`の完全一致。UI上はカテゴリ分けせず全28種別をフラットな選択肢として提示する（Q6=A）
- **対象ユーザ**: `userId`の完全一致。UNIT-02の`AdminUserController`同様、管理者はユーザ一覧から選択する想定（選択肢の取得方法はfrontend-components.md参照）
- **対象接続**: `connectionId`の完全一致。`connectionId`を持たないイベント種別（`LOGIN`等）を絞り込みたい場合は、この条件を指定しないことで対応する
- **結果ステータス**: `resultStatus`（`SUCCESS`/`FAILURE`）の完全一致
- **対象リソース（`targetResource`）のテキスト検索は提供しない**（Q4=A）。イベント種別ごとに`targetResource`の意味が異なり（domain-entities.md §6.1参照）、部分一致検索の実用性が低いため

対象ユーザ・対象ロールに基づくデータ絞込（フェイルクローズ）は行わない。管理者専用エンドポイントとして、アクセスできた時点で全件が閲覧対象となる（UNIT-08とのアクセス制御方式の違い、Q2の選定理由）。

## 4. リクエストバリデーション（UNIT-08の`QueryHistoryInvalidParameterException`と同じ方針）

- ページサイズが上限（Code Generation時に確定、UNIT-08と同程度の値を踏襲）を超える場合は400エラー
- 発生日時範囲が`from > to`の場合は400エラー
- 上記は新規の`AuditLogInvalidParameterException`（400 BAD_REQUEST、UNIT-08の`QueryHistoryInvalidParameterException`と同じパターン）で表現する

## 5. ページング（Q3=A）

UNIT-08で確立したSpring Data JPAの`Pageable`/`Page`をそのまま踏襲する。

- デフォルトソート順: `occurredAt`降順（新しい記録が先頭）
- ページサイズの既定値・上限値はUNIT-08と同程度を踏襲する（NFR Requirements段階で確定）
- フロントエンドの`Pagination`（1-indexed）↔`Pageable`（0-indexed）の変換は、UNIT-08で確立した「変換点を画面コンポーネント側の1箇所に限定する」方針をそのまま踏襲する

## 6. 表示用データの結合

- `AuditLogEntryView`（DTO、永続化なし）: `AuditLogEntry`の全フィールド + `userDisplayName`（`userId`が非nullの場合に`User.fullName`から解決。削除済み・不明な場合は「(不明なユーザ)」等のプレースホルダー、Q5=A） + `connectionDisplayName`（`connectionId`が非nullの場合に`RdbmsConnection.displayName`から解決。削除済みの場合は「(削除済み接続)」、UNIT-08と同じプレースホルダー方針）

## 7. 参照整合性の扱い（既存記録の不変性）

`AuditLogEntry`は外部キー制約を意図的に持たない（対象リソースのライフサイクル変更が監査履歴に影響しないための設計、UNIT-02で確立済み）。本ユニットの閲覧機能もこの不変性を尊重し、`userId`・`connectionId`が指す実体が削除されていても、監査ログエントリ自体は常に閲覧可能とする。表示名解決に失敗した場合はプレースホルダーで補う（§6参照）。

## 8. 画面遷移（Q8=A）

一覧から他画面への遷移導線は設けない。STORY-9.1の受け入れ基準に画面遷移の要件がなく、本ユニットは「事後的な確認」を目的とする純粋な閲覧・絞込機能に留める（UNIT-08のような「実行へ」「保存へ」等のボタンは設けない）。
