# MasterMeister observability stack

Spring Boot 4のOpenTelemetry（トレース・メトリクス・ログ）を受信・可視化するための、`devenv/`とは独立したdocker-composeスタック。

## 構成

- **otel-collector**（`otel/opentelemetry-collector-contrib`）: アプリからのOTLP（トレース・メトリクス・ログ）を受信し、トレースはTempoへ、メトリクスはPrometheus用エクスポータへ、ログはLokiへ振り分ける
- **tempo**: トレースの保存・クエリバックエンド
- **prometheus**: メトリクスの保存・クエリバックエンド（otel-collectorの`prometheus`エクスポータをスクレイプ）
- **loki**: ログの保存・クエリバックエンド（OTLPログを`/otlp/v1/logs`でネイティブ受信）
- **grafana**: Tempo・Prometheus・Lokiをデータソースとして自動プロビジョニング済み

## 起動

```bash
docker compose -f observability/docker-compose.yml up -d
```

- Grafana: <http://localhost:3000>（匿名アクセス許可、Admin権限。ローカル開発専用の設定のため本番では使用しないこと）
- Prometheus: <http://localhost:9090>
- Tempo（クエリAPI）: <http://localhost:3200>
- Loki（クエリAPI）: <http://localhost:3100>
- OTLP受信エンドポイント: `http://localhost:4317`（gRPC）/ `http://localhost:4318`（HTTP）

## バックエンド（Spring Boot）からの送信を有効化する

デフォルトではOpenTelemetryのエクスポートは無効（既存の開発・テスト・CI環境に影響を与えないため）。有効化するには、`backend`起動時に以下の環境変数を設定する。

```bash
export MM_TRACING_ENABLED=true
export MM_OTLP_METRICS_ENABLED=true
export MM_OTLP_LOGGING_ENABLED=true
./gradlew :backend:bootRun
```

`MM_OTLP_TRACING_ENDPOINT`（既定`http://localhost:4318/v1/traces`）・`MM_OTLP_METRICS_ENDPOINT`（既定`http://localhost:4318/v1/metrics`）・`MM_OTLP_LOGGING_ENDPOINT`（既定`http://localhost:4318/v1/logs`）は、本スタックの既定ポート公開設定と一致しているため、通常は変更不要。

## 動作確認

1. 本スタックと`backend`（上記環境変数付き）を起動し、任意のAPIを何度か呼び出す
2. Grafana（<http://localhost:3000>）で「Explore」→ Tempoデータソースを選択し、`service.name="mastermeister"`等でトレースを検索
3. 同じくPrometheusデータソースで、メトリクス（例: `http_server_requests_seconds_count`）をクエリ
4. 同じくLokiデータソースで、ログ（例: `{service_name="mastermeister"}`）をクエリ

## 停止・データ削除

```bash
docker compose -f observability/docker-compose.yml down       # 停止（データは保持）
docker compose -f observability/docker-compose.yml down -v    # 停止＋データ削除
```
