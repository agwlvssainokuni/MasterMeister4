# UNIT-04 アクセス制御 - Tech Stack Decisions

`unit-04-nfr-requirements-plan.md`の回答（Q1=A, Q2=A, Q3=C（Other）, Q4=A, Q5=B, Q6=A, Q7=A, Q8=A, Q9=A）に基づく、バックエンドの技術選定一覧。

---

## 1. Caffeineキャッシュのサイズ・有効期限（Q1=A）

**決定**: `maximumSize`（例: 10,000エントリ）と`expireAfterWrite`（例: 30分）の両方を設定する。

**理由**: `invalidateCache()`（BR-ACCESS-08）による明示的な無効化を主たる手段とするが、無効化漏れ（実装バグ等）が発生した場合、本来アクセスできないはずの実効権限が古いキャッシュにより見え続けるという静かなセキュリティ問題になりうる。時間経過による期限切れを保険的な安全網として併用する。

**依存関係**: `com.github.ben-manes.caffeine:caffeine`（新規追加）

---

## 2. キャッシュ実装方式（Q2=A）

**決定**: Spring Cache抽象化（`@Cacheable`/`@CacheEvict`）を`CaffeineCacheManager`と組み合わせて使用する。

**理由**: UNIT-02/03で確立した「Spring標準機能を優先活用する」という方針と一貫する。宣言的なアノテーションにより実装がシンプルになる。

**依存関係**: `org.springframework.boot:spring-boot-starter-cache`（新規追加）

---

## 3. キャッシュ無効化の実装方式（Q3、チャットでの検証を経てOtherに決定）

**決定**: `@CacheEvict(cacheNames = "effectivePermission", allEntries = true)`によるキャッシュ全体クリアとする。接続単位・プリンシパル単位の部分一致削除は行わない。

**検証経緯**: 当初Q3=A（接続ID単位の無効化）を推奨したが、Spring Cacheの`org.springframework.cache.Cache`インタフェースは「単一キー削除」（`evict(key)`）と「全件クリア」（`clear()`、`@CacheEvict(allEntries=true)`が対応）の2種類の操作のみを提供し、「特定の接続IDを含むキーだけを部分一致で削除する」という操作は宣言的アノテーションでは表現できないことが判明した。これを実現するには、`CacheManager`経由で取得したCaffeineの`Cache`オブジェクトの`asMap()`を直接操作するプログラム的な実装が必要になり、Q2で選んだ「Spring Cache抽象化への統一」という方針と矛盾する。ユーザー判断により、無効化範囲をキャッシュ全体に広げる代わりに完全な宣言的実装（アノテーションのみ）を優先することとした。

**理由**: 権限変更・グループ変更の頻度は低い（Q7の前提規模: 社内ツール、管理者による低頻度の設定変更）ため、無効化範囲が全接続・全ユーザに及んでも実用上の影響は小さいと判断する。

**適用箇所**: `AccessControlService.setPermission()`/グループ作成・改名・削除・所属追加・削除の各メソッド、`SchemaIntrospectionService.refreshSchema()`（UNIT-03、スキーマ再取込時）に`@CacheEvict(cacheNames = "effectivePermission", allEntries = true)`を付与する。

---

## 4. EffectivePermissionResolverのAPI公開方針（Q4=A）

**決定**: UNIT-04時点ではREST APIとして公開しない。`EffectivePermissionResolver`は他サービスから呼び出される内部Java APIとする。

**理由**: 一般ユーザ向けの実際の利用はUNIT-05（マスタメンテナンス）以降で発生する。そのユニットが実際に必要とする具体的な問い合わせ形状（例:「このテーブルは編集可能か」）に応じてAPIを設計する方が、汎用的な「実効権限を返すAPI」を今の時点で先取りして設計するより適切と判断する（YAGNI）。

---

## 5. YAML処理ライブラリ（Q5=B）

**決定**: 新規依存関係を追加せず、`jackson-dataformat-yaml`（Spring Boot経由で`application.yml`読込用に既にクラスパスに存在する推移的依存）を流用する。

**理由**: SECURITY-10（ソフトウェアサプライチェーン）の観点で、既に信頼済みの依存関係を再利用し新規の依存関係を増やさない方を優先する。エクスポートされるYAMLの人間可読性の差（SnakeYAMLとの比較）は、本用途（バックアップ・移行・レビュー、STORY-2.5）において致命的な差ではないと判断する。

**依存関係**: 追加不要（`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`は既存の推移的依存を利用）

---

## 6. 並行編集時の競合方針（Q6=A）

**決定**: 楽観的ロックを導入せず、後勝ち（last-write-wins）でシンプルに実装する。

**理由**: `RdbmsConnection`の更新等、既存ユニットも同様の方針を採っている。社内ツール規模（同時利用者数名の管理者）では、同一の権限設定・グループを複数の管理者が同時に編集する競合自体が稀であり、楽観的ロックの実装・保守コストに見合わない。

---

## 7. グループ数・所属ユーザ数・権限エントリ数の前提規模（Q7=A）

**決定**: 小〜中規模（グループ数は数十程度、1グループあたり数十ユーザ、1接続あたりの権限エントリは数百件程度）を設計上の前提規模とする。

**理由**: requirements.mdの前提（社内ツール、同時利用者約10名規模）と整合する典型的な規模。この前提を超える大規模ケースへの最適化（ページング必須のグループ一覧等）は本ユニットのスコープ外とする。

---

## 8. バリデーション実装方式（Q8=A）

**決定**: Bean Validation（`jakarta.validation`）をグループ作成・権限設定のリクエストDTOに付与する。YAML importの内容検証（構造・重複エントリ・プリンシパル解決）はBean Validationでは表現しきれないため、サービス層（`PermissionYamlService`）で個別に実装する。

**理由**: UNIT-02/03と同じ方式に揃えることで、実装・レビューの一貫性を保つ。

**依存関係**: 追加不要（`spring-boot-starter-validation`は導入済み）

---

## 9. YAML importのサイズ上限（Q9=A）

**決定**: YAML importのファイルサイズに上限（例: 1MB）を設ける。エントリ数自体には別途の上限を設けず、ファイルサイズ上限による自然な制約に委ねる。専用のアラート機構（失敗多発の監視等）は設けない。

**理由**: SECURITY-05（入力バリデーション、リクエストボディサイズの上限）への最低限の配慮として、無制限のファイルサイズ受け入れによるリソース消費（DoS）を防ぐ。一方、YAML import失敗は管理者による手動操作であり、UNIT-03の接続テスト失敗等と同様の理由で監視対象外と判断する（Q9=A）。具体的な上限値・設定方法（`spring.servlet.multipart.max-file-size`等）はNFR Design段階で確定する。
