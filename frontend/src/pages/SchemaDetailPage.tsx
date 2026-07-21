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

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { Alert, Badge, DataTable, EmptyState, PageHeader, Spinner } from '../design-system/components'
import type { TableColumn } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { getSchema } from '../api/rdbmsConnections'
import type { SchemaColumnDetail, SchemaSnapshotDetail, SchemaTableDetail } from '../api/rdbmsConnections'
import { ApiError } from '../api/http'

// frontend-components.md §2。取込済みのテーブル・カラム・制約情報を確認する読取専用画面。
export function SchemaDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const connectionId = Number(id)

  const [schema, setSchema] = useState<SchemaSnapshotDetail | null>(null)
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [notImported, setNotImported] = useState(false)
  const [selectedTableName, setSelectedTableName] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    setNotImported(false)
    try {
      const result = await getSchema(connectionId)
      setSchema(result)
      setSelectedTableName(result.tables[0]?.tableName ?? null)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'SCHEMA_NOT_IMPORTED') {
        setNotImported(true)
      } else {
        setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
      }
    } finally {
      setLoading(false)
    }
  }, [connectionId, t])

  useEffect(() => {
    void load()
  }, [load])

  const selectedTable = useMemo<SchemaTableDetail | null>(
    () => schema?.tables.find((table) => table.tableName === selectedTableName) ?? null,
    [schema, selectedTableName],
  )

  const tableColumns: readonly TableColumn<SchemaTableDetail>[] = [
    { key: 'tableName', header: t('connections.tableName'), render: (table) => table.tableName },
    {
      key: 'tableType',
      header: t('connections.tableType'),
      render: (table) => <Badge tone={table.tableType === 'VIEW' ? 'primary' : 'neutral'}>{table.tableType}</Badge>,
    },
    { key: 'comment', header: t('connections.comment'), render: (table) => table.comment ?? '' },
    { key: 'columnCount', header: t('connections.columnCount'), render: (table) => String(table.columns.length) },
  ]

  const constraintBadges = (column: SchemaColumnDetail, table: SchemaTableDetail) =>
    table.constraints
      .filter((constraint) => constraint.columnNames.includes(column.columnName))
      .map((constraint) => (
        <Badge key={constraint.constraintName} tone={constraint.constraintType === 'PRIMARY_KEY' ? 'success' : 'neutral'}>
          {constraint.constraintType}
        </Badge>
      ))

  const columnColumns: readonly TableColumn<SchemaColumnDetail>[] = selectedTable
    ? [
        { key: 'columnName', header: t('connections.columnName'), render: (c) => c.columnName },
        { key: 'nativeType', header: t('connections.nativeType'), render: (c) => c.nativeType },
        { key: 'normalizedType', header: t('connections.normalizedType'), render: (c) => c.normalizedType },
        {
          key: 'nullable',
          header: t('connections.nullable'),
          render: (c) => (c.nullable ? t('action.ok') : '-'),
        },
        {
          key: 'constraints',
          header: t('connections.constraints'),
          render: (c) => constraintBadges(c, selectedTable),
        },
      ]
    : []

  return (
    <AuthenticatedLayout activeNavKey="connections">
      <PageHeader
        title={t('connections.schemaDetailTitle')}
      />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      {loading ? <Spinner /> : null}
      {notImported ? (
        <EmptyState
          message={t('connections.notImported')}
          action={<Link to="/connections">{t('connections.backToList')}</Link>}
        />
      ) : null}
      {schema ? (
        <>
          <p>
            {t('connections.importedAt')}: {new Date(schema.importedAt).toLocaleString()}
          </p>
          <DataTable
            columns={tableColumns}
            rows={schema.tables}
            rowKey={(table) => table.tableName}
            emptyState={<EmptyState message={t('state.empty')} />}
          />
          {selectedTable ? (
            <DataTable
              columns={columnColumns}
              rows={selectedTable.columns}
              rowKey={(c) => c.columnName}
              emptyState={<EmptyState message={t('state.empty')} />}
            />
          ) : null}
        </>
      ) : null}
    </AuthenticatedLayout>
  )
}
