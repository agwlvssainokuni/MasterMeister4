# サービス定義・オーケストレーションパターン

components.mdで定義した19コンポーネントは、いずれもサービス層のコンポーネントとして扱う。本文書では、複数コンポーネントが連携する主要なオーケストレーションフローを示す。

## オーケストレーション方針

- **同期呼び出しが基本**: 業務処理は基本的にコンポーネント間の直接メソッド呼び出し（同期）で構成する（Q3参照）
- **監査ログのみイベント経由**: AuditLogServiceへの通知だけは、業務ロジックとの結合度を下げるためAuditEventPublisher経由のイベント発行とする。ただし非同期ではなく同期実行し、監査ログの記録自体は本業務トランザクションとは別トランザクションでコミットする（詳細な伝播方式はNFR Design/Code Generationで確定）
- **横断的コンポーネントへの依存**: EffectivePermissionResolver、RdbmsDialectStrategy、AuditEventPublisherは複数のドメインサービスから共通利用される

---

## フロー1: ユーザ登録〜承認

1. コントローラ → `UserRegistrationService.startRegistration()`
2. `UserRegistrationService` → `EmailNotificationService.sendRegistrationConfirmation()`
3. `UserRegistrationService` → `AuditEventPublisher.publish()`（登録申請イベント）→ `AuditLogService`
4. コントローラ（管理者操作）→ `UserRegistrationService.approveUser()` / `rejectUser()`
5. `UserRegistrationService` → `EmailNotificationService.sendApprovalResult()`
6. `UserRegistrationService` → `AuditEventPublisher.publish()`（承認/却下イベント）→ `AuditLogService`

## フロー2: ログイン〜トークンリフレッシュ〜再利用検知

1. コントローラ → `AuthenticationService.login()` → 内部で`LoginAttemptGuard.isLocked()`確認
2. 認証成功 → `RefreshTokenService`へリフレッシュトークン発行を委譲、`LoginAttemptGuard.reset()`
3. 認証失敗 → `LoginAttemptGuard.recordFailure()`
4. `AuthenticationService`/`RefreshTokenService` → `AuditEventPublisher.publish()`（ログイン成功/失敗イベント）
5. リフレッシュ時: コントローラ → `RefreshTokenService.refresh()` → 内部で`detectReuse()`判定 → 検知時は`revokeFamily()`

## フロー3: RDBMSセットアップ〜アクセス制御

1. コントローラ → `RdbmsConnectionService.registerConnection()`
2. コントローラ → `SchemaIntrospectionService.refreshSchema()` → 内部で`RdbmsDialectStrategy`を利用してDB接続・メタデータ取得
3. `SchemaIntrospectionService`の完了後 → `EffectivePermissionResolver.invalidateCache()`（スキーマ再取込によるキャッシュ無効化）
4. コントローラ → ~~`AccessControlService.setPermission()`~~ 訂正（UNIT-04 NFR Designにて）: `PermissionService.setPermission()` / `GroupService`のグループ操作 → 完了後 `EffectivePermissionResolver.invalidateCache()`
5. 上記操作それぞれから `AuditEventPublisher.publish()` → `AuditLogService`（接続変更・スキーマ取込・権限変更・グループ変更を別イベント種別で記録）
6. YAML入出力: コントローラ → `PermissionYamlService.exportToYaml()` / `importFromYaml()` → 完了後 `EffectivePermissionResolver.invalidateCache()`、`AuditEventPublisher.publish()`

**訂正（UNIT-04 NFR Designにて）**: 上記フロー中の`EffectivePermissionResolver.invalidateCache()`という明示的呼び出しは、実装上は独立メソッドとして存在しない。各mutationメソッド自体への`@CacheEvict(cacheNames = "effectivePermission", allEntries = true)`宣言的アノテーション付与に確定した（unit-04/nfr-design/nfr-design-patterns.md §2.1）。上記の記述は「このタイミングでキャッシュ無効化が必要」という意図として読み替える。

## フロー4: マスタメンテナンス反映

1. コントローラ → `MasterDataService.queryRecords()` → 内部で`EffectivePermissionResolver.resolvePrimary()`を用いてカラム単位の可視性・編集可否を決定
2. コントローラ → `MasterDataService.applyChanges()`
   - 内部で`EffectivePermissionResolver.canCreate()`/`canDelete()`/`resolvePrimary()`を用いて全変更行を検証
   - 1件でも失敗した場合は例外をスローし、単一トランザクション全体をロールバック（オールオアナッシング）
   - 成功時は`AuditEventPublisher.publish()`（データ更新イベント）

## フロー5: クエリビルダー〜実行〜保存〜履歴

1. コントローラ → `QueryBuilderService.generateSql()`（内部でアクセス可能カラムの絞込に`EffectivePermissionResolver`を参照）
2. 生成SQLを実行: コントローラ → `QueryExecutionService.execute()`
   - 内部で`RdbmsDialectStrategy`によるスキーマ切替、`EffectivePermissionResolver`による読み取り専用チェック
   - 完了後 `QueryHistoryService.recordExecution()` を直接呼び出し（監査ログとは別に、ユーザ向け履歴として記録）
   - 大量データ取得時は`AuditEventPublisher.publish()`（データアクセスイベント）
3. 生成SQLを保存: コントローラ → `SavedQueryService.saveQuery()`
4. 保存クエリを実行: コントローラ → `QueryExecutionService.executeSavedQuery()` → 内部で`SavedQueryService`から取得したSQLを②と同じ経路で実行
5. 履歴からの遷移: コントローラ → `QueryHistoryService.searchHistory()` の結果から、`QueryBuilderService.parseToBuilderState()` / `SavedQueryService` / `QueryExecutionService` への遷移用データを返却

## フロー6: 監査ログ閲覧

1. コントローラ（管理者限定）→ `AuditLogService.searchLogs()`

---

## サービス分類まとめ

| 分類 | コンポーネント |
|---|---|
| ドメインサービス（ビジネスユースケースの主体） | UserRegistrationService, AuthenticationService, RdbmsConnectionService, SchemaIntrospectionService, ~~AccessControlService~~ 訂正（UNIT-04 NFR Designにて）: PermissionService, GroupService, MasterDataService, QueryExecutionService, SavedQueryService, QueryBuilderService, QueryHistoryService, AuditLogService |
| 補助サービス（単一責務の内部委譲先） | AdminBootstrapService, RefreshTokenService, LoginAttemptGuard, EmailNotificationService, PermissionYamlService |
| 横断的コンポーネント（複数ドメインから参照） | EffectivePermissionResolver, RdbmsDialectStrategy, AuditEventPublisher |
