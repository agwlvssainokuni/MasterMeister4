# Unit Test Execution

## Run Unit Tests

### 1. バックエンド単体テストの実行（backend, cherry-mustache-core）

```bash
./gradlew :cherry-mustache-core:test :backend:test
```

### 2. フロントエンド単体テストの実行

```bash
cd frontend
npm test -- --run
```

### 3. テスト結果の確認

- **期待される結果**（本ドキュメント作成時点、UNIT-01〜10全ユニット完了時）:
  - `backend`: 427件成功、0件失敗
  - `cherry-mustache-core`: 197件成功、0件失敗
  - `frontend`: 253件成功、0件失敗（60ファイル）
- **テストレポート格納先**:
  - `backend/build/test-results/test/*.xml`（JUnit XML形式）、`backend/build/reports/tests/test/index.html`（HTMLレポート）
  - `cherry-mustache-core/build/test-results/test/*.xml`、`cherry-mustache-core/build/reports/tests/test/index.html`
  - `frontend`はコンソール出力のみ（Vitestのデフォルト設定、HTMLレポート未設定）

### 4. テスト失敗時の対応

失敗した場合:
1. `backend/build/reports/tests/test/index.html`または該当する`*.xml`でスタックトレースを確認する
2. フロントエンドはコンソール出力（`npx vitest run <失敗したテストファイルパス>`で個別実行すると詳細を確認しやすい）
3. コードを修正し、該当テストのみを再実行して確認する（例: `./gradlew :backend:test --tests "cherry.mastermeister.<package>.*"`、`npx vitest run <path>`）
4. 全件が通ることを確認してから、フルテストスイート（Step 1・2）を再実行する

## テストの構成（参考）

各ユニットのCode Generation時に、以下の観点でテストを作成してきた（詳細は`aidlc-docs/construction/unit-0{1-9}/code/*-summary.md`を参照）。

- **バックエンド**: Service層の単体テスト（Mockito）、Repository層の統合テスト（`@DataJpaTest`、実際にFlywayマイグレーションを適用してH2上で検証）、Controller層のWeb統合テスト（`@WebMvcTest`、`SecurityConfig`を実際にインポートしロールベースのアクセス制御を実証）
- **フロントエンド**: APIクライアントの単体テスト（`vi.mock`によるHTTPレイヤーのモック）、ページコンポーネントのレンダリング・インタラクションテスト（Testing Library、`userEvent`）
- **PBT（Property-Based Testing）**: `stories.md`でPBT対象「はい」と明記されたストーリーのみ実施 — STORY-2.4「実効権限の判定ロジック」（PBT-01, PBT-03、UNIT-04）、STORY-2.5「アクセス権限のYAMLエクスポート／インポート」（PBT-02、UNIT-04）、STORY-5.2「SQL生成と実行・保存への連携」（PBT-05、UNIT-07）。他ストーリーは例示ベーステストのみ
