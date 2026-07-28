# MasterMeister

RDBMSに格納されたマスタデータをメンテナンスするためのWebアプリケーション。Spring Bootバックエンドと ReactフロントエンドによるSPAとして構築している。

同時利用者約10名規模の社内ツールを想定し、MVPファーストで段階的に機能を追加してきた（AI-DLCワークフローによる開発、詳細は`aidlc-docs/`参照）。

## 主な機能

- **ユーザ登録・認証**（UNIT-02）: メールアドレスによる登録申請・管理者承認・JWT認証・ユーザ管理
- **対象RDBMSセットアップ**（UNIT-03）: PostgreSQL/MySQL/MariaDBへの接続登録・スキーマ取込
- **アクセス制御**（UNIT-04）: グループ管理、スキーマ／テーブル／カラム単位の主権限・補助権限、実効権限判定、YAMLエクスポート／インポート
- **マスタメンテナンス**（UNIT-05）: テーブル/ビュー一覧・レコード一覧の表示、絞込・SQL入力による検索、レコード編集・作成・削除
- **クエリ保存・実行**（UNIT-06）: 任意SQLの読み取り専用実行、名前付きクエリの保存・管理
- **クエリビルダー**（UNIT-07）: タブUIによるSQL組み立て、既存SQLからのリバースエンジニアリング
- **クエリ履歴**（UNIT-08）: 実行済みクエリの履歴閲覧・絞込
- **監査ログ閲覧**（UNIT-09）: 管理者専用の監査ログ閲覧・絞込

## ディレクトリ構成

```
.
├── backend/              # Spring Boot 4.1 / Spring Security 7.x / Java 25
├── frontend/              # React 19 + TypeScript + Vite
├── cherry-mustache-core/  # メールテンプレート用の自作Mustacheエンジン（独立サブプロジェクト）
├── devenv/                # 開発用Docker Compose（MailPit, MySQL, MariaDB, PostgreSQL）
├── .github/workflows/     # CI/CD（GitHub Actions）
└── aidlc-docs/            # 要件定義・設計・構築記録一式（AI-DLCワークフロー成果物）
```

詳細は`backend/README.md`・`frontend/README.md`を参照。

## クイックスタート

```bash
# 開発用インフラの起動（MailPit, MySQL, MariaDB, PostgreSQL）
docker compose -f devenv/docker-compose.yml up -d

# バックエンド起動（必須環境変数はbackend/README.md参照）
export MM_APP_JWT_SECRET="$(openssl rand -base64 32)"
export MM_APP_RDBMS_ENCRYPTION_KEYS="1:$(openssl rand -base64 32)"
./gradlew :backend:bootRun
```

別ターミナルでフロントエンドの開発サーバを起動する。

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:5173/`でアプリにアクセスできる（devサーバが`/api/**`を`http://localhost:8080`へプロキシする）。

## ビルド・テスト

```bash
# バックエンド（cherry-mustache-core含む）
./gradlew :cherry-mustache-core:build :backend:build

# フロントエンド
cd frontend && npm ci && npm run lint && npm test -- --run && npm run build

# リリース用単一WAR（フロントエンド内包、SpringBootServletInitializer継承で自己完結実行可能）
./gradlew :backend:bootWar
```

## CI/CD

GitHub Actions（`.github/workflows/`）で構成する。

- **CI**（`ci.yml`）: `main`へのpush・プルリクエストをトリガーに、バックエンド・フロントエンドを並行ビルド・テストする
- **Release**（`release.yml`）: `v*`形式のタグpushをトリガーに、`bootWar`でビルドしたWARをGitHub Releasesに添付する。リリース前に`backend/build.gradle.kts`の`version`を対象バージョンへ更新し、一致するタグ（例: `v0.0.0`）をpushすること（バージョン不一致時はリリースを中断する）

## ドキュメント

要件定義・設計・各ユニットの実装記録は`aidlc-docs/`配下にまとまっている。

- `aidlc-docs/inception/`: 要件定義・ユーザストーリー・アプリケーション設計
- `aidlc-docs/construction/`: ユニットごとの機能設計・NFR設計・実装記録、ビルド・テスト手順
- `aidlc-docs/aidlc-state.md`: 全体の進捗状況

## ライセンス

Apache License 2.0（`LICENSE`参照）
