# コンポーネントメソッド一覧

各コンポーネントの主要メソッドシグネチャ（高レベル）。詳細な業務ルールはFunctional Design（ユニットごと）で定義する。

## COMP-01: UserRegistrationService
- `startRegistration(email: String): void` — メールアドレス登録開始、確認メール送信トリガー
- `completeRegistration(token: String, password: String): UserId` — トークン検証・パスワード設定・登録完了
- `listPendingUsers(): List<PendingUser>` — 承認待ちユーザ一覧
- `approveUser(userId: UserId, approvedBy: UserId): void`
- `rejectUser(userId: UserId, rejectedBy: UserId): void`
- `createApprovedAccount(email: String, rawPassword: String, role: Role): UserId` — 通常フロー（トークン発行〜承認）を経ずに、パスワードハッシュ化等の共通ロジックのみ再利用して承認済みアカウントを作成する。AdminBootstrapService専用の内部エントリポイント

## COMP-02: AdminBootstrapService
- `bootstrapInitialAdmin(): void` — アプリ起動時に呼び出される（`ApplicationRunner`等）。内部で`UserRegistrationService.createApprovedAccount()`を呼び出す

## COMP-03: AuthenticationService
- `login(email: String, password: String): TokenPair` — アクセストークン+リフレッシュトークン発行
- `logout(refreshToken: String): void`
- `validateAccessToken(token: String): Principal`

## COMP-04: RefreshTokenService
- `refresh(refreshToken: String): TokenPair` — ローテーション、旧トークン無効化
- `detectReuse(refreshToken: String): boolean` — 再利用検知、検知時はファミリ一括失効
- `revokeFamily(tokenFamilyId: TokenFamilyId): void`

## COMP-05: LoginAttemptGuard
- `recordFailure(email: String): void`
- `isLocked(email: String): boolean`
- `reset(email: String): void` — 成功時のカウンタリセット

## COMP-06: EmailNotificationService
- `sendRegistrationConfirmation(email: String, token: String, locale: Locale): void`
- `sendApprovalResult(email: String, approved: boolean, locale: Locale): void`

## COMP-07: RdbmsConnectionService
- `registerConnection(config: ConnectionConfig): ConnectionId`
- `updateConnection(connectionId: ConnectionId, config: ConnectionConfig): void`
- `getDataSource(connectionId: ConnectionId): DataSource` — コネクションプール経由

## COMP-08: SchemaIntrospectionService
- `refreshSchema(connectionId: ConnectionId): SchemaSnapshot` — テーブル/ビュー/カラム構造取込
- `getSchema(connectionId: ConnectionId): SchemaSnapshot`

## COMP-09: RdbmsDialectStrategy（インターフェース）
- `requiresSchemaSwitch(): boolean`
- `applySchemaSwitch(connection: Connection, schema: String): void`
- `resolveDialect(dbType: DbType): RdbmsDialectStrategy` — ファクトリメソッド

## COMP-10: AccessControlService
- `setPermission(principal: Principal, resource: ResourcePath, primary: PrimaryPermission, auxiliary: Set<AuxiliaryPermission>): void`
- `createGroup(name: String): GroupId` / `renameGroup(groupId: GroupId, name: String): void` / `deleteGroup(groupId: GroupId): void`
- `addUserToGroup(groupId: GroupId, userId: UserId): void` / `removeUserFromGroup(groupId: GroupId, userId: UserId): void`

## COMP-11: EffectivePermissionResolver
- `resolvePrimary(userId: UserId, connectionId: ConnectionId, resource: ResourcePath): PrimaryPermission`
- `canCreate(userId: UserId, connectionId: ConnectionId, table: TablePath): boolean`
- `canDelete(userId: UserId, connectionId: ConnectionId, table: TablePath): boolean`
- `invalidateCache(scope: InvalidationScope): void` — 権限変更/グループ変更/スキーマ再取込時に呼び出し

## COMP-12: PermissionYamlService
- `exportToYaml(connectionId: ConnectionId): String`
- `importFromYaml(connectionId: ConnectionId, yaml: String): ImportResult` — 全置換、重複検出時は例外で全体拒否

## COMP-13: MasterDataService
- `listTables(userId: UserId, connectionId: ConnectionId): List<TableSummary>`
- `queryRecords(userId: UserId, connectionId: ConnectionId, table: TablePath, filter: FilterCriteria, page: PageRequest): Page<Record>`
- `applyChanges(userId: UserId, connectionId: ConnectionId, changeSet: RecordChangeSet): void` — 単一トランザクション、オールオアナッシング

## COMP-14: QueryExecutionService
- `execute(userId: UserId, connectionId: ConnectionId, sql: String, params: Map<String, Object>, schema: SchemaName, page: PageRequest): QueryResult`
- `executeSavedQuery(userId: UserId, savedQueryId: SavedQueryId, params: Map<String, Object>, schema: SchemaName): QueryResult`

## COMP-15: SavedQueryService
- `saveQuery(userId: UserId, name: String, sql: String, visibility: Visibility): SavedQueryId`
- `updateQuery(savedQueryId: SavedQueryId, requestedBy: UserId, sql: String): void` — 作成者以外は例外
- `retireQuery(savedQueryId: SavedQueryId): void`
- `listVisibleQueries(userId: UserId): List<SavedQuerySummary>`

## COMP-16: QueryBuilderService
- `generateSql(builderState: QueryBuilderState): String`
- `parseToBuilderState(sql: String): QueryBuilderState` — リバースエンジニアリング

## COMP-17: QueryHistoryService
- `recordExecution(entry: QueryExecutionRecord): void`
- `searchHistory(userId: UserId, filter: HistoryFilter, page: PageRequest): Page<QueryExecutionRecord>`

## COMP-18: AuditLogService
- `onAuditEvent(event: AuditEvent): void` — AuditEventPublisherからのイベントを受信し別トランザクションで記録
- `searchLogs(filter: AuditLogFilter, page: PageRequest): Page<AuditLogEntry>` — 管理者限定

## COMP-19: AuditEventPublisher
- `publish(event: AuditEvent): void` — Spring `ApplicationEventPublisher`のラッパー
