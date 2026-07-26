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
import { listQueryConnections } from '../api/query'
import type { AccessibleConnection } from '../api/query'
import { ApiError } from '../api/http'

// frontend-components.md 画面1。クエリビルダーの起点となる接続選択画面（UNIT-05/06と同じパターン）。
export function QueryBuilderConnectionListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [connections, setConnections] = useState<AccessibleConnection[]>([])
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    try {
      setConnections(await listQueryConnections())
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    void load()
  }, [load])

  const columns: readonly TableColumn<AccessibleConnection>[] = [
    { key: 'displayName', header: t('queryBuilder.connectionName'), render: (c) => c.displayName },
  ]

  return (
    <AuthenticatedLayout activeNavKey="queryBuilder">
      <PageHeader title={t('queryBuilder.title')} />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      {loading ? (
        <Spinner />
      ) : (
        <DataTable
          columns={columns}
          rows={connections}
          rowKey={(c) => String(c.connectionId)}
          onRowClick={(c) => navigate(`/query-builder/${c.connectionId}`)}
          emptyState={<EmptyState message={t('queryBuilder.connectionListEmpty')} />}
        />
      )}
    </AuthenticatedLayout>
  )
}
