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

import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { Alert, DataTable, EmptyState, PageHeader, Spinner } from '../design-system/components'
import type { TableColumn } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { listQueryHistoryConnections } from '../api/queryHistory'
import type { QueryHistoryConnection } from '../api/queryHistory'
import { ApiError } from '../api/http'

// frontend-components.md 画面1。BR-QUERYHISTORY-11: 履歴実績ベースの接続一覧
// （現在アクセス可能な接続一覧ではない）。
export function QueryHistoryConnectionListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [connections, setConnections] = useState<QueryHistoryConnection[]>([])
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    try {
      setConnections(await listQueryHistoryConnections())
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    void load()
  }, [load])

  const columns: readonly TableColumn<QueryHistoryConnection>[] = [
    { key: 'displayName', header: t('queryHistory.connectionName'), render: (c) => c.displayName },
  ]

  return (
    <AuthenticatedLayout activeNavKey="queryHistory">
      <PageHeader title={t('queryHistory.title')} />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      {loading ? (
        <Spinner />
      ) : (
        <DataTable
          columns={columns}
          rows={connections}
          rowKey={(c) => String(c.connectionId)}
          onRowClick={(c) => navigate(`/query-history/${c.connectionId}`)}
          emptyState={<EmptyState message={t('queryHistory.connectionListEmpty')} />}
        />
      )}
    </AuthenticatedLayout>
  )
}
