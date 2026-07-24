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
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Badge, DataTable, EmptyState, PageHeader, Spinner, Alert } from '../design-system/components'
import type { TableColumn } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { listMasterDataTables } from '../api/masterData'
import type { AccessibleTable } from '../api/masterData'
import { ApiError } from '../api/http'

function tableKey(table: AccessibleTable): string {
  return `${table.schemaName}.${table.tableName}`
}

// frontend-components.md §2。BR-MASTER-01〜02。アクセス可能なテーブル/ビュー一覧画面。
export function MasterDataTableListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { connectionId } = useParams<{ connectionId: string }>()
  const connectionIdNum = Number(connectionId)

  const [tables, setTables] = useState<AccessibleTable[]>([])
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [notImported, setNotImported] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    setNotImported(false)
    try {
      setTables(await listMasterDataTables(connectionIdNum))
    } catch (error) {
      if (error instanceof ApiError && error.code === 'SCHEMA_NOT_IMPORTED') {
        setNotImported(true)
      } else {
        setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
      }
    } finally {
      setLoading(false)
    }
  }, [connectionIdNum, t])

  useEffect(() => {
    void load()
  }, [load])

  const columns: readonly TableColumn<AccessibleTable>[] = [
    { key: 'schemaName', header: t('connections.schemaName'), render: (table) => table.schemaName },
    { key: 'tableName', header: t('connections.tableName'), render: (table) => table.tableName },
    {
      key: 'tableType',
      header: t('connections.tableType'),
      render: (table) => (
        <Badge tone={table.tableType === 'VIEW' ? 'primary' : 'neutral'}>{table.tableType}</Badge>
      ),
    },
  ]

  return (
    <AuthenticatedLayout activeNavKey="masterData">
      <PageHeader title={t('masterData.tableListTitle')} />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      {notImported ? (
        <EmptyState
          message={t('connections.notImported')}
          action={<Link to="/master-data">{t('masterData.backToConnections')}</Link>}
        />
      ) : null}
      {loading ? (
        <Spinner />
      ) : notImported ? null : (
        <DataTable
          columns={columns}
          rows={tables}
          rowKey={(table) => tableKey(table)}
          onRowClick={(table) =>
            navigate(
              `/master-data/${connectionIdNum}/${encodeURIComponent(table.schemaName)}/${encodeURIComponent(table.tableName)}`,
            )
          }
          emptyState={<EmptyState message={t('masterData.tableListEmpty')} />}
        />
      )}
    </AuthenticatedLayout>
  )
}
