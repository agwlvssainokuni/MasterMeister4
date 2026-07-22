# UNIT-04 アクセス制御 - NFR Requirements 計画

## Scalability / Availability
requirements.mdの前提（同時利用者約10名規模の社内ツール）を踏まえ、大規模なスケーラビリティ要件はN/A（UNIT-01〜UNIT-03と同様の判断）。グループ数・所属ユーザ数・権限エントリ数の想定規模のみ、後述Q7で確認する。

## Security Baseline 該当ルール評価

| ルール | 該当性 | 理由 |
|---|---|---|
| SECURITY-01（保存時・通信時暗号化） | N/A（対応済み） | `Group`/`AccessPermission`はパスワード等の機微情報を含まない。内部DB暗号化の例外方針（UNIT-02 NFR-4.8）をそのまま踏襲 |
| SECURITY-02（ネットワーク中間機器のアクセスログ） | N/A | 本ユニットのスコープ外 |
| SECURITY-03（アプリケーションログ） | 該当（対応済み） | AuditEventPublisher経由の記録（domain-entities.md §4、8種のイベント）で対応済み |
| SECURITY-04（HTTPセキュリティヘッダ） | N/A（対応済み） | UNIT-02 NFR Designで全体設定済み。本ユニット固有の追加なし |
| SECURITY-05（全APIパラメータの入力検証） | 該当 | グループ作成・権限設定・YAML importのバリデーション実装方式をQ8で確認 |
| SECURITY-06（最小権限アクセスポリシー） | 該当（対応済み） | BR-ACCESS-03（未設定時デフォルトNONE）で最小権限原則をアプリのデフォルト値として実装済み |
| SECURITY-07（制限的なネットワーク構成） | N/A | インフラレベルの構成であり、Infrastructure DesignもSKIP判定済み |
| SECURITY-08（アプリケーション層アクセス制御） | 該当 | 管理画面自体はUNIT-02のロールチェック（ADMIN専用）を流用（対応済み）。一方`EffectivePermissionResolver`（一般ユーザの実効権限判定）自体をUNIT-04時点でREST APIとして公開するかをQ4で確認 |
| SECURITY-09（セキュリティ堅牢化・誤設定防止） | N/A（対応済み） | 追加検討事項なし |
| SECURITY-10（ソフトウェアサプライチェーン） | 該当 | 新規追加するCaffeine・YAML処理ライブラリの依存関係についてQ1・Q5で確認 |
| SECURITY-11（セキュアデザイン原則） | 該当（対応済み） | フェイルクローズ（BR-ACCESS-03）、`group`/`permission`パッケージ分離（モジュール境界の明確化）で対応済み |
| SECURITY-12（認証・認証情報管理） | N/A | 本ユニットに新規の認証・認証情報管理要件はない |
| SECURITY-13（データ整合性検証） | 該当（対応済み） | 権限変更・グループ変更はAuditLogEntryで変更内容を記録（domain-entities.md §4）。追加検討事項なし |
| SECURITY-14（アラート・監視） | 該当 | YAML import失敗多発等に対する専用アラート要否をQ9で確認 |
| SECURITY-15（例外処理・フェイルセーフ） | 該当（対応済み） | UNIT-02のグローバル例外ハンドラを流用。BR-ACCESS-03のデフォルトNONEはフェイルクローズの実践例 |

## Property-Based Testing 拡張
requirements.md §7.10のNFR-5.1でPBT候補として識別済み（権限判定・合成ロジック、YAML入出力）。business-logic-model.md §5でテスト可能プロパティを識別済み。フレームワークはUNIT-02でjqwikに確定済みのため、本ステージでの追加決定は不要（N/A）。

## 計画チェックリスト

- [ ] Step A: 質問への回答を収集する
- [ ] Step B: 回答内容の曖昧性を確認する（必要なら追加質問）
- [ ] Step C: `nfr-requirements.md`（カテゴリ別NFR要件、Security Baseline該当ルール一覧）を作成する
- [ ] Step D: `tech-stack-decisions.md`（Caffeineキャッシュ設定、YAML処理ライブラリ、実効権限判定APIの公開方針等）を作成する
- [ ] Step E: 完了メッセージを提示し、承認を得る

## 質問

### Question 1（Tech Stack Selection・Performance、FR-2.9）
実効権限キャッシュ（Caffeine）のサイズ・有効期限の設計方針は？

A) `maximumSize`（例: 10,000エントリ）と`expireAfterWrite`（例: 30分）を設定する。明示的な`invalidateCache()`呼び出し（BR-ACCESS-08）を主とし、時間経過による期限切れは万一の無効化漏れに対する保険的役割とする

B) `expireAfterWrite`は設定せず、`invalidateCache()`呼び出しのみに完全に依存する（キャッシュは無期限に保持される）

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 2（Tech Stack Selection）
キャッシュの実装方式は？

A) Spring Cache抽象化（`@Cacheable`/`@CacheEvict`）を`CaffeineCacheManager`と組み合わせて使用する（宣言的で実装がシンプル、UNIT-02/03で確立したSpring標準機能活用の方針と一貫する）

B) Spring Cache抽象化を使わず、`com.github.benmanes.caffeine.cache.Cache`オブジェクトを`EffectivePermissionResolver`内で直接生成・管理する（無効化ロジック（BR-ACCESS-08、キーの部分一致に基づく無効化等）をきめ細かく制御しやすい）

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 3（Performance・BR-ACCESS-08）
キャッシュ無効化の粒度は？

A) 該当接続ID全体に関連するキャッシュエントリを丸ごと無効化する（実装がシンプル。無効化範囲はやや広めだが、権限変更・グループ変更の頻度は低いため許容範囲と判断）

B) 変更のあったプリンシパル（ユーザ／グループ）・リソースに関連するエントリのみをピンポイントで無効化する（無効化範囲は最小限だが、実装が複雑。グループ変更時は所属ユーザ全員分の関連エントリを洗い出す必要がある）

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 4（Security Requirements、SECURITY-08）
`EffectivePermissionResolver`（実効権限判定）は、UNIT-04時点でREST APIとして公開しますか？

A) 公開しない。UNIT-04時点では管理者向けの権限設定・グループ管理画面のみを提供し、`EffectivePermissionResolver`は他サービスから呼び出される内部Java APIとする（一般ユーザ向けの実際の利用はUNIT-05マスタメンテナンス以降で、そのユニットのControllerが内部的に呼び出す）

B) 公開する。「自分の実効権限を確認する」ような一般ユーザ向けAPIをUNIT-04時点で用意する

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 5（Tech Stack Selection、SECURITY-10）
YAMLエクスポート／インポートの処理ライブラリは？

A) SnakeYAMLを新規依存関係として追加する（YAML専用ライブラリでネイティブな整形出力が得意、手動レビュー・編集を意図したエクスポート形式（STORY-2.5）に適する）

B) `jackson-dataformat-yaml`（Spring Boot経由で既にクラスパスに存在する、`application.yml`読込用の推移的依存）を流用し、新規依存関係を追加しない

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 6（Reliability・並行編集）
複数の管理者が同時に同じ権限設定・グループを編集した場合の競合方針は？

A) 楽観的ロックなし、後勝ち（last-write-wins）でシンプルに実装する（`RdbmsConnection`更新等、既存ユニットも同様の方針）

B) バージョン列（`@Version`）による楽観的ロックを導入し、競合時はエラーを返す

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 7（Scalability、前提確認）
グループ数・1グループあたりの所属ユーザ数・1接続あたりの権限エントリ数について、設計上の前提規模は？

A) 小〜中規模（グループ数は数十程度、1グループあたり数十ユーザ、1接続あたりの権限エントリは数百件程度。社内ツールとしての典型的な利用規模）

B) より大規模（グループ数百、1グループ数百ユーザ、権限エントリ数千件）を想定する

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 8（Security Requirements、SECURITY-05）
グループ作成・権限設定・YAML importのバリデーション実装方式は？

A) Bean Validation（Jakarta Validation）をリクエストDTOに付与する（UNIT-02/03と同じ方式に揃える）。YAML importの内容検証（構造・重複エントリ・プリンシパル解決）はBean Validationでは表現しきれないため、サービス層で個別に実装する

B) すべてサービス層での手続き的なバリデーションとする

C) Other（[Answer]: の後に内容を記述）

[Answer]: 

### Question 9（Security Requirements、SECURITY-14、SECURITY-05関連）
YAML importについて、ファイルサイズ・エントリ数の上限や、失敗多発時のアラート機構は設けますか？

A) ファイルサイズ・エントリ数に上限を設ける（例: ファイルサイズ1MB、エントリ数上限は特に設けず自然なサイズ制約に委ねる）が、専用のアラート機構（失敗多発の監視等）は設けない。管理者専用の手動操作であり、UNIT-03の接続テスト失敗等と同様の理由で監視対象外と判断する

B) サイズ上限もアラート機構も設けない（管理者専用機能のため信頼できる入力とみなす）

C) Other（[Answer]: の後に内容を記述）

[Answer]: 
