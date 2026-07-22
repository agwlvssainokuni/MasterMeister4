# UNIT-04 アクセス制御 - NFR Requirements

`unit-04-nfr-requirements-plan.md`の回答（Q1=A, Q2=A, Q3=C（Other、チャットでの検証により`@CacheEvict(allEntries = true)`に決定）, Q4=A, Q5=B, Q6=A, Q7=A, Q8=A, Q9=A）に基づく。カテゴリ別のNFR要件と、Security Baseline拡張・Property-Based Testing拡張の該当ルール評価を記載する。技術選定の詳細（ライブラリ・キャッシュ設定等）はtech-stack-decisions.md参照。

---

## 1. Scalability

requirements.mdの前提（同時利用者約10名規模の社内ツール）により、本ユニット固有の大規模スケーリング設計は不要と判断する（NFR-04-01）。グループ数・所属ユーザ数・権限エントリ数の前提はQ7のとおり、小〜中規模（グループ数十、1グループ数十ユーザ、1接続あたりの権限エントリ数百件程度）とする。

## 2. Availability — N/A

requirements.mdにHA・DR要件の記載はない。既存方針（NFR-2.x）と整合する前提で、本ユニット固有のAvailability要件は設けない。

## 3. Performance

- **NFR-04-02**: 実効権限の解決結果はCaffeineでキャッシュする（FR-2.9、既存決定）。`maximumSize`（例: 10,000エントリ）と`expireAfterWrite`（例: 30分）を設定する。明示的な`invalidateCache()`呼び出し（BR-ACCESS-08）を主たる無効化手段とし、時間経過による期限切れは無効化漏れに対する保険的役割とする（Q1=A）
- **NFR-04-03**: キャッシュ実装はSpring Cache抽象化（`@Cacheable`/`@CacheEvict`）と`CaffeineCacheManager`を組み合わせる（Q2=A）。無効化は接続単位やプリンシパル単位の部分一致削除ではなく、`@CacheEvict(allEntries = true)`によるキャッシュ全体クリアとする（Q3。理由: Spring Cacheの`Cache`インタフェースは「単一キー削除」と「全件クリア」のみを宣言的にサポートし、部分一致による削除は表現できないため。権限変更・グループ変更の頻度は低い前提のため、無効化範囲が全体に及んでも許容範囲と判断）

## 4. Reliability

- **NFR-04-04**: 複数の管理者による同時編集は、楽観的ロックを導入せず後勝ち（last-write-wins）とする（Q6=A。`RdbmsConnection`更新等、既存ユニットと同じ方針）
- **NFR-04-05**: YAML importの失敗多発等に対する専用のアラート機構（通知・監視）は設けない（Q9=A）。管理者による手動操作の結果はUI上のエラー表示とアプリケーションログで完結させる

## 5. Maintainability

- **NFR-04-06**: グループ作成・権限設定のバリデーションはBean Validation（`jakarta.validation`）で実装し、UNIT-02/03と同じ方式に揃える（Q8=A）。YAML importの内容検証（構造・重複エントリ・プリンシパル解決）はBean Validationでは表現しきれないため、サービス層で個別に実装する
- **NFR-04-07**: テストフレームワークはNFR-9.1・NFR-5.2のとおりJUnit5 + Mockito + jqwikを踏襲する。本ユニットはbusiness-logic-model.md §5でPBT対象プロパティを識別済み（権限判定・合成ロジック、YAML入出力のラウンドトリップ）
- **NFR-04-08**: バックエンドパッケージ構成は`group`（Group, GroupMembership, GroupController）・`permission`（AccessPermission, AccessControlService, EffectivePermissionResolver, PermissionYamlService, PermissionController）の2パッケージに分割する（frontend-components.mdでの訂正、既存決定）

## 6. Usability — N/A

Functional Design（frontend-components.md）で対応済み。追加のUsability要件は設けない。

---

## 7. YAML importのサイズ上限（Q9=A）

- **NFR-04-09**: YAML importのファイルサイズに上限を設ける（具体的な上限値はtech-stack-decisions.md参照）。エントリ数自体には別途上限を設けず、ファイルサイズ上限による自然な制約に委ねる。専用のアラート機構は設けない

---

## 8. Security Baseline拡張 該当ルール評価

| ルール | 判定 | 本ユニットでの対応方針 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A（対応済み） | `Group`/`AccessPermission`はパスワード等の機微情報を含まない。内部DB暗号化の例外方針（UNIT-02 NFR-4.8）を踏襲 |
| SECURITY-02（ネットワーク中継のアクセスログ） | N/A | 本ユニットのスコープ外 |
| SECURITY-03（アプリケーションログ） | 該当（対応済み） | AuditEventPublisher経由の記録（8種のイベント種別、domain-entities.md §4）で対応済み |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み |
| SECURITY-05（入力バリデーション） | 該当 | Bean Validationによる形式チェック（Q8=A、NFR-04-06）。YAML importのサイズ上限（NFR-04-09） |
| SECURITY-06（最小権限アクセスポリシー） | 該当（対応済み） | BR-ACCESS-03（未設定時デフォルトNONE）で最小権限原則をアプリのデフォルト値として実装済み |
| SECURITY-07（制限的なネットワーク構成） | N/A | Infrastructure DesignがSKIP判定のユニットであり対象外 |
| SECURITY-08（アプリケーション層アクセス制御） | 該当（対応済み・再確認） | 管理画面自体はUNIT-02のロールチェック（ADMIN専用）を流用。`EffectivePermissionResolver`はUNIT-04時点ではREST APIとして公開せず、内部Java APIのみとする（Q4=A、NFR-08参照） |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A（対応済み） | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | 該当 | 新規追加するCaffeineは、UNIT-01で導入済みのOWASP Dependency-Checkプラグインの既存スキャン対象に自動的に含まれる。YAML処理は既存の推移的依存（`jackson-dataformat-yaml`）を流用し新規依存を追加しない（Q5=B） |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | フェイルクローズ（BR-ACCESS-03）、`group`/`permission`パッケージ分離によるモジュール境界の明確化 |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規の認証・認証情報管理要件はない |
| SECURITY-13（データ整合性検証） | 該当（対応済み） | 権限変更・グループ変更はAuditLogEntryで変更内容を記録（domain-entities.md §4） |
| SECURITY-14（アラート・監視） | N/A | YAML import失敗等は管理者による手動操作であり、外部からの自動化された攻撃対象ではないため専用のアラート機構は不要と判断（Q9=A） |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。BR-ACCESS-03のデフォルトNONEはフェイルクローズの実践例 |

## 9. Property-Based Testing拡張

該当あり。business-logic-model.md §5で識別済みのプロパティ（階層優先の不変条件、個別設定優先の不変条件、グループ合成の単調性、作成・削除可否判定の整合性、YAMLラウンドトリップ、重複拒否の原子性）を、jqwik（UNIT-02で確定済み）を用いてCode Generation段階で実装する。
