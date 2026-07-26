/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useCallback, useEffect, useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Alert,
  Button,
  CodeBlock,
  DataTable,
  Modal,
  PageHeader,
  Pagination,
  Select,
  TextInput,
} from '../design-system/components'
import type { TableColumn } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { getAccessToken } from '../auth/tokenStorage'
import { decodeJwtRole } from '../auth/jwt'
import { listQueryHistory, listQueryHistorySchemas } from '../api/queryHistory'
import type { ExecutedByScope, QueryHistoryRecord } from '../api/queryHistory'
import { ApiError } from '../api/http'
import styles from './QueryHistoryPage.module.css'

const DEFAULT_PAGE_SIZE = 50

// frontend-components.md 画面2。BR-QUERYHISTORY-10・11: スキーマ一覧・接続一覧はいずれも
// 履歴実績ベース（アクセス可否は問わない）。実行者スコープSelectの変更時はスキーマ一覧も
// 再取得する（他ユーザのスキーマ名を漏らさないため、承認前レビューでの追加）。
export function QueryHistoryPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { connectionId } = useParams<{ connectionId: string }>()
  const connectionIdNum = Number(connectionId)

  const isAdmin = decodeJwtRole(getAccessToken() ?? '') === 'ADMIN'
  const sqlKeywordId = useId()
  const executedAtFromId = useId()
  const executedAtToId = useId()
  const schemaNameId = useId()
  const executedByScopeId = useId()

  const [executedByScope, setExecutedByScope] = useState<ExecutedByScope>('ALL')
  const [schemas, setSchemas] = useState<string[]>([])
  const [schemaName, setSchemaName] = useState('')
  const [sqlKeyword, setSqlKeyword] = useState('')
  const [executedAtFrom, setExecutedAtFrom] = useState('')
  const [executedAtTo, setExecutedAtTo] = useState('')

  const [records, setRecords] = useState<QueryHistoryRecord[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [selectedRecord, setSelectedRecord] = useState<QueryHistoryRecord | null>(null)

  useEffect(() => {
    listQueryHistorySchemas(connectionIdNum, executedByScope)
      .then(setSchemas)
      .catch((error) => setErrorMessage(error instanceof ApiError ? error.message : t('state.error')))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionIdNum, executedByScope])

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    try {
      const result = await listQueryHistory(connectionIdNum, {
        executedByScope,
        executedAtFrom: executedAtFrom ? new Date(executedAtFrom).toISOString() : undefined,
        executedAtTo: executedAtTo ? new Date(executedAtTo).toISOString() : undefined,
        schemaName: schemaName || undefined,
        sqlKeyword: sqlKeyword || undefined,
        page,
        pageSize: DEFAULT_PAGE_SIZE,
      })
      setRecords(result.content)
      setTotalPages(Math.max(result.totalPages, 1))
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setLoading(false)
    }
  }, [connectionIdNum, executedByScope, executedAtFrom, executedAtTo, schemaName, sqlKeyword, page, t])

  useEffect(() => {
    void load()
  }, [load])

  // 絞込条件変更時はページを先頭へ戻す
  const onFilterChange = (apply: () => void) => {
    apply()
    setPage(0)
  }

  const columns: readonly TableColumn<QueryHistoryRecord>[] = [
    {
      key: 'executedAt',
      header: t('queryHistory.column.executedAt'),
      render: (r) => new Date(r.executedAt).toLocaleString(),
    },
    {
      key: 'queryType',
      header: t('queryHistory.column.queryType'),
      render: (r) => t(`queryHistory.queryType.${r.queryType}`),
    },
    {
      key: 'summary',
      header: t('queryHistory.column.summary'),
      render: (r) => r.savedQueryName ?? r.sql,
    },
    ...(isAdmin && executedByScope === 'ALL'
      ? [
          {
            key: 'executor',
            header: t('queryHistory.column.executor'),
            render: (r: QueryHistoryRecord) => r.executorDisplayName,
          } satisfies TableColumn<QueryHistoryRecord>,
        ]
      : []),
    { key: 'schemaName', header: t('queryHistory.column.schemaName'), render: (r) => r.schemaName },
    { key: 'rowCount', header: t('queryHistory.column.rowCount'), render: (r) => String(r.rowCount) },
    {
      key: 'durationMillis',
      header: t('queryHistory.column.durationMillis'),
      render: (r) => String(r.durationMillis),
    },
  ]

  const onNavigateToExecute = () => {
    if (!selectedRecord) {
      return
    }
    navigate(`/query-execution/${connectionIdNum}`, {
      state: { sql: selectedRecord.sql, schemaName: selectedRecord.schemaName },
    })
  }

  const onNavigateToSave = () => {
    if (!selectedRecord) {
      return
    }
    navigate(`/saved-queries/${connectionIdNum}/new`, {
      state: { sql: selectedRecord.sql, schemaName: selectedRecord.schemaName },
    })
  }

  const onNavigateToBuilder = () => {
    if (!selectedRecord) {
      return
    }
    navigate(`/query-builder/${connectionIdNum}`, {
      state: { sql: selectedRecord.sql, schemaName: selectedRecord.schemaName },
    })
  }

  return (
    <AuthenticatedLayout activeNavKey="queryHistory">
      <PageHeader title={t('queryHistory.title')} />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      <div className={styles.filterList}>
        <div className={styles.filterRow}>
          <label htmlFor={sqlKeywordId} className={styles.filterLabel}>
            {t('queryHistory.filter.sqlKeyword')}
          </label>
          <TextInput
            id={sqlKeywordId}
            value={sqlKeyword}
            onChange={(e) => onFilterChange(() => setSqlKeyword(e.target.value))}
            data-testid="query-history-sql-keyword-input"
          />
        </div>
        <div className={styles.filterRow}>
          <label htmlFor={executedAtFromId} className={styles.filterLabel}>
            {t('queryHistory.filter.executedAtFrom')}
          </label>
          <TextInput
            id={executedAtFromId}
            type="datetime-local"
            value={executedAtFrom}
            onChange={(e) => onFilterChange(() => setExecutedAtFrom(e.target.value))}
          />
        </div>
        <div className={styles.filterRow}>
          <label htmlFor={executedAtToId} className={styles.filterLabel}>
            {t('queryHistory.filter.executedAtTo')}
          </label>
          <TextInput
            id={executedAtToId}
            type="datetime-local"
            value={executedAtTo}
            onChange={(e) => onFilterChange(() => setExecutedAtTo(e.target.value))}
          />
        </div>
        <div className={styles.filterRow}>
          <label htmlFor={schemaNameId} className={styles.filterLabel}>
            {t('queryHistory.filter.schemaName')}
          </label>
          <Select
            id={schemaNameId}
            value={schemaName}
            onChange={(e) => onFilterChange(() => setSchemaName(e.target.value))}
          >
            <option value="">{t('queryHistory.selectPlaceholder')}</option>
            {schemas.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </Select>
        </div>
        {isAdmin ? (
          <div className={styles.filterRow}>
            <label htmlFor={executedByScopeId} className={styles.filterLabel}>
              {t('queryHistory.filter.executedByScope')}
            </label>
            <Select
              id={executedByScopeId}
              value={executedByScope}
              onChange={(e) => onFilterChange(() => setExecutedByScope(e.target.value as ExecutedByScope))}
            >
              <option value="ALL">{t('queryHistory.executedByScope.ALL')}</option>
              <option value="MINE">{t('queryHistory.executedByScope.MINE')}</option>
            </Select>
          </div>
        ) : null}
      </div>
      <DataTable
        columns={columns}
        rows={records}
        rowKey={(r) => String(r.id)}
        loading={loading}
        onRowClick={(r) => setSelectedRecord(r)}
      />
      <Pagination page={page + 1} totalPages={totalPages} onChange={(p) => setPage(p - 1)} />
      {selectedRecord ? (
        <Modal
          open
          title={t('queryHistory.detailTitle')}
          onClose={() => setSelectedRecord(null)}
          footer={
            <>
              <Button onClick={onNavigateToExecute} data-testid="query-history-execute-button">
                {t('queryHistory.executeButton')}
              </Button>
              <Button onClick={onNavigateToSave} data-testid="query-history-save-button">
                {t('queryHistory.saveButton')}
              </Button>
              <Button onClick={onNavigateToBuilder} data-testid="query-history-builder-button">
                {t('queryHistory.builderButton')}
              </Button>
            </>
          }
        >
          <CodeBlock code={selectedRecord.sql} />
        </Modal>
      ) : null}
    </AuthenticatedLayout>
  )
}
