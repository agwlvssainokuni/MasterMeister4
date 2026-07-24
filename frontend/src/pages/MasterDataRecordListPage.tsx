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
import type { CellState } from '../design-system/components'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router-dom'
import {
  Alert,
  Button,
  DataTable,
  EmptyState,
  FormField,
  Modal,
  PageHeader,
  Pagination,
  Select,
  Spinner,
  TextArea,
  TextInput,
} from '../design-system/components'
import type { TableColumn } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { applyBatch, listRecords } from '../api/masterData'
import type {
  BatchOperationItem,
  BatchOperationResult,
  FilterOperator,
  RecordColumn,
  RecordFilter,
  RecordPage,
} from '../api/masterData'
import { ApiError } from '../api/http'

const PAGE_SIZE = 20

interface FilterRow {
  id: string
  columnName: string
  operator: FilterOperator
  value: string
  valueTo: string
}

interface PendingCreateRow {
  id: string
  values: Record<string, string>
}

function operatorsFor(column: RecordColumn | undefined): FilterOperator[] {
  if (!column) {
    return ['EQ']
  }
  switch (column.dataTypeCategory) {
    case 'NUMERIC':
    case 'DATETIME':
      return ['EQ', 'LT', 'LE', 'GT', 'GE', 'BETWEEN']
    case 'STRING':
      return ['EQ', 'STARTS_WITH', 'CONTAINS']
    case 'BOOLEAN':
      return ['EQ']
  }
}

function primaryKeyValuesOf(row: Record<string, string | null>, columns: RecordColumn[]): Record<string, string> {
  const result: Record<string, string> = {}
  for (const column of columns) {
    if (column.primaryKey) {
      result[column.columnName] = row[column.columnName] ?? ''
    }
  }
  return result
}

function rowKeyOf(row: Record<string, string | null>, columns: RecordColumn[]): string {
  return JSON.stringify(primaryKeyValuesOf(row, columns))
}

// frontend-components.md §3。BR-MASTER-04〜05, 10, 14, 15。BR-MASTER-06〜09。
// レコード一覧・絞込・インライン編集・一括反映（作成/更新/削除混在）を担う画面。
export function MasterDataRecordListPage() {
  const { t } = useTranslation()
  const { connectionId, schemaName: schemaNameParam, tableName: tableNameParam } = useParams<{
    connectionId: string
    schemaName: string
    tableName: string
  }>()
  const connectionIdNum = Number(connectionId)
  const schemaName = decodeURIComponent(schemaNameParam ?? '')
  const tableName = decodeURIComponent(tableNameParam ?? '')

  const [page, setPage] = useState(0)
  const [recordPage, setRecordPage] = useState<RecordPage | null>(null)
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const [filters, setFilters] = useState<FilterRow[]>([])
  const [rawWhere, setRawWhere] = useState('')
  const [rawOrderBy, setRawOrderBy] = useState('')
  const [rawSectionOpen, setRawSectionOpen] = useState(false)

  const [pendingChanges, setPendingChanges] = useState<Map<string, Record<string, string>>>(new Map())
  const [pendingDeletes, setPendingDeletes] = useState<Set<string>>(new Set())
  const [pendingCreates, setPendingCreates] = useState<PendingCreateRow[]>([])
  const [editingCell, setEditingCell] = useState<{ rowKey: string; columnName: string } | null>(null)
  const [editingValue, setEditingValue] = useState('')
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [createValues, setCreateValues] = useState<Record<string, string>>({})
  const [batchResult, setBatchResult] = useState<BatchOperationResult | null>(null)
  const [applying, setApplying] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    try {
      const requestFilters: RecordFilter[] = filters
        .filter((f) => f.columnName)
        .map((f) => ({
          columnName: f.columnName,
          operator: f.operator,
          value: f.value,
          valueTo: f.operator === 'BETWEEN' ? f.valueTo : undefined,
        }))
      const result = await listRecords(connectionIdNum, schemaName, tableName, {
        page,
        pageSize: PAGE_SIZE,
        filters: requestFilters,
        where: rawWhere || undefined,
        orderBy: rawOrderBy || undefined,
      })
      setRecordPage(result)
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setLoading(false)
    }
  }, [connectionIdNum, schemaName, tableName, page, filters, rawWhere, rawOrderBy, t])

  useEffect(() => {
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionIdNum, schemaName, tableName, page])

  const onSearch = () => {
    setPage(0)
    void load()
  }

  const columns = recordPage?.columns ?? []

  const displayRows = useMemo(() => {
    const existing = recordPage?.rows ?? []
    const created = pendingCreates.map((c) => c.values)
    return [...existing, ...created]
  }, [recordPage, pendingCreates])

  const rowKeyFor = (row: Record<string, string | null>): string => {
    const created = pendingCreates.find((c) => c.values === row)
    if (created) {
      return `create-${created.id}`
    }
    return rowKeyOf(row, columns)
  }

  const addFilterRow = () => {
    setFilters((current) => [
      ...current,
      { id: crypto.randomUUID(), columnName: columns[0]?.columnName ?? '', operator: 'EQ', value: '', valueTo: '' },
    ])
  }

  const updateFilterRow = (id: string, patch: Partial<FilterRow>) => {
    setFilters((current) => current.map((f) => (f.id === id ? { ...f, ...patch } : f)))
  }

  const removeFilterRow = (id: string) => {
    setFilters((current) => current.filter((f) => f.id !== id))
  }

  const startEdit = (rowKey: string, columnName: string, currentValue: string) => {
    setEditingCell({ rowKey, columnName })
    setEditingValue(currentValue)
  }

  const commitEdit = (row: Record<string, string | null>) => {
    if (!editingCell) {
      return
    }
    const key = rowKeyFor(row)
    if (key.startsWith('create-')) {
      const id = key.slice('create-'.length)
      setPendingCreates((current) =>
        current.map((c) => (c.id === id ? { ...c, values: { ...c.values, [editingCell.columnName]: editingValue } } : c)),
      )
    } else {
      setPendingChanges((current) => {
        const next = new Map(current)
        const existing = next.get(key) ?? {}
        next.set(key, { ...existing, [editingCell.columnName]: editingValue })
        return next
      })
    }
    setEditingCell(null)
  }

  const toggleDelete = (row: Record<string, string | null>) => {
    const key = rowKeyFor(row)
    setPendingDeletes((current) => {
      const next = new Set(current)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      return next
    })
  }

  const removePendingCreate = (id: string) => {
    setPendingCreates((current) => current.filter((c) => c.id !== id))
  }

  const openCreateModal = () => {
    const initial: Record<string, string> = {}
    for (const column of columns) {
      if (column.editable) {
        initial[column.columnName] = ''
      }
    }
    setCreateValues(initial)
    setCreateModalOpen(true)
  }

  const submitCreate = () => {
    setPendingCreates((current) => [...current, { id: crypto.randomUUID(), values: { ...createValues } }])
    setCreateModalOpen(false)
  }

  const hasPendingChanges = pendingChanges.size > 0 || pendingDeletes.size > 0 || pendingCreates.length > 0

  const onApply = async () => {
    setApplying(true)
    setBatchResult(null)
    try {
      const operations: BatchOperationItem[] = []
      for (const create of pendingCreates) {
        operations.push({ operationType: 'CREATE', columnValues: create.values })
      }
      for (const row of recordPage?.rows ?? []) {
        const key = rowKeyOf(row, columns)
        if (pendingDeletes.has(key)) {
          operations.push({ operationType: 'DELETE', primaryKeyValues: primaryKeyValuesOf(row, columns) })
        } else if (pendingChanges.has(key)) {
          operations.push({
            operationType: 'UPDATE',
            primaryKeyValues: primaryKeyValuesOf(row, columns),
            columnValues: pendingChanges.get(key),
          })
        }
      }
      const result = await applyBatch(connectionIdNum, schemaName, tableName, operations)
      setBatchResult(result)
      if (result.success) {
        setPendingChanges(new Map())
        setPendingDeletes(new Set())
        setPendingCreates([])
        await load()
      }
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setApplying(false)
    }
  }

  const tableColumns: readonly TableColumn<Record<string, string | null>>[] = [
    ...columns.map((column) => ({
      key: column.columnName,
      header: column.columnName,
      render: (row: Record<string, string | null>) => {
        const key = rowKeyFor(row)
        const isEditing = editingCell?.rowKey === key && editingCell.columnName === column.columnName
        const isDeleted = pendingDeletes.has(key)
        if (isEditing) {
          return (
            <TextInput
              autoFocus
              value={editingValue}
              onChange={(e) => setEditingValue(e.target.value)}
              onBlur={() => commitEdit(row)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  commitEdit(row)
                } else if (e.key === 'Escape') {
                  setEditingCell(null)
                }
              }}
              data-testid={`record-cell-input-${column.columnName}`}
            />
          )
        }
        const displayValue =
          (key.startsWith('create-')
            ? row[column.columnName]
            : (pendingChanges.get(key)?.[column.columnName] ?? row[column.columnName])) ?? ''
        return column.editable && !isDeleted ? (
          <button
            type="button"
            onClick={() => startEdit(key, column.columnName, displayValue)}
            data-testid={`record-cell-${column.columnName}`}
            style={{ background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left', width: '100%' }}
          >
            {displayValue}
          </button>
        ) : (
          <span>{displayValue}</span>
        )
      },
    })),
    {
      key: 'rowActions',
      header: t('masterData.rowActions'),
      render: (row: Record<string, string | null>) => {
        const key = rowKeyFor(row)
        if (key.startsWith('create-')) {
          return (
            <Button size="sm" variant="ghost" onClick={() => removePendingCreate(key.slice('create-'.length))}>
              {t('action.delete')}
            </Button>
          )
        }
        return recordPage?.deletable ? (
          <Button size="sm" variant={pendingDeletes.has(key) ? 'secondary' : 'danger'} onClick={() => toggleDelete(row)}>
            {pendingDeletes.has(key) ? t('masterData.undoDelete') : t('action.delete')}
          </Button>
        ) : null
      },
    },
  ]

  const cellStates: Record<string, Record<string, CellState>> = {}
  for (const [key, values] of pendingChanges.entries()) {
    cellStates[key] = {}
    for (const columnName of Object.keys(values)) {
      cellStates[key][columnName] = 'edited'
    }
  }

  const rowStates: Record<string, 'added' | 'removed'> = {}
  for (const row of recordPage?.rows ?? []) {
    const key = rowKeyOf(row, columns)
    if (pendingDeletes.has(key)) {
      rowStates[key] = 'removed'
    }
  }
  for (const create of pendingCreates) {
    rowStates[`create-${create.id}`] = 'added'
  }

  const totalPages = recordPage ? Math.max(1, Math.ceil(recordPage.totalCount / PAGE_SIZE)) : 1

  return (
    <AuthenticatedLayout activeNavKey="masterData">
      <PageHeader
        title={tableName}
        actions={
          <>
            {recordPage?.creatable ? (
              <Button variant="secondary" onClick={openCreateModal} data-testid="record-add-button">
                {t('masterData.addRow')}
              </Button>
            ) : null}{' '}
            <Button
              variant="primary"
              disabled={!hasPendingChanges}
              loading={applying}
              onClick={() => void onApply()}
              data-testid="record-apply-button"
            >
              {t('masterData.applyButton')}
            </Button>
          </>
        }
      />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      {batchResult ? (
        <Alert tone={batchResult.success ? 'success' : 'danger'}>
          {batchResult.success
            ? t('masterData.applySuccess')
            : batchResult.itemResults.map((r) => `#${r.index}: ${r.errorMessage}`).join(', ')}
        </Alert>
      ) : null}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--mm-space-2)' }}>
        {filters.map((filterRow) => {
          const column = columns.find((c) => c.columnName === filterRow.columnName)
          const ops = operatorsFor(column)
          return (
            <div key={filterRow.id} style={{ display: 'flex', gap: 'var(--mm-space-2)' }}>
              <Select
                value={filterRow.columnName}
                onChange={(e) => updateFilterRow(filterRow.id, { columnName: e.target.value })}
              >
                {columns.map((c) => (
                  <option key={c.columnName} value={c.columnName}>
                    {c.columnName}
                  </option>
                ))}
              </Select>
              <Select
                value={filterRow.operator}
                onChange={(e) => updateFilterRow(filterRow.id, { operator: e.target.value as FilterOperator })}
              >
                {ops.map((op) => (
                  <option key={op} value={op}>
                    {op}
                  </option>
                ))}
              </Select>
              <TextInput
                value={filterRow.value}
                onChange={(e) => updateFilterRow(filterRow.id, { value: e.target.value })}
              />
              {filterRow.operator === 'BETWEEN' ? (
                <TextInput
                  value={filterRow.valueTo}
                  onChange={(e) => updateFilterRow(filterRow.id, { valueTo: e.target.value })}
                />
              ) : null}
              <Button size="sm" variant="ghost" onClick={() => removeFilterRow(filterRow.id)}>
                {t('action.delete')}
              </Button>
            </div>
          )
        })}
        <div>
          <Button size="sm" variant="ghost" onClick={addFilterRow} data-testid="record-add-filter-button">
            {t('masterData.addFilterCondition')}
          </Button>{' '}
          <Button size="sm" variant="ghost" onClick={() => setRawSectionOpen((open) => !open)}>
            {t('masterData.rawQueryToggle')}
          </Button>{' '}
          <Button size="sm" variant="primary" onClick={onSearch} data-testid="record-search-button">
            {t('action.search')}
          </Button>
        </div>
        {rawSectionOpen ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--mm-space-2)' }}>
            <FormField label={t('masterData.rawWhere')}>
              <TextArea value={rawWhere} onChange={(e) => setRawWhere(e.target.value)} rows={2} />
            </FormField>
            <FormField label={t('masterData.rawOrderBy')}>
              <TextArea value={rawOrderBy} onChange={(e) => setRawOrderBy(e.target.value)} rows={1} />
            </FormField>
          </div>
        ) : null}
      </div>

      {loading ? (
        <Spinner />
      ) : (
        <DataTable
          columns={tableColumns}
          rows={displayRows}
          rowKey={(row) => rowKeyFor(row)}
          cellStates={cellStates}
          rowStates={rowStates}
          emptyState={<EmptyState message={t('masterData.recordListEmpty')} />}
        />
      )}

      {recordPage ? (
        <Pagination page={page + 1} totalPages={totalPages} onChange={(p) => setPage(p - 1)} />
      ) : null}

      <Modal
        open={createModalOpen}
        title={t('masterData.createTitle')}
        onClose={() => setCreateModalOpen(false)}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--mm-space-3)' }}>
          {columns
            .filter((c) => c.editable)
            .map((column) => (
              <FormField key={column.columnName} label={column.columnName}>
                <TextInput
                  value={createValues[column.columnName] ?? ''}
                  onChange={(e) =>
                    setCreateValues((current) => ({ ...current, [column.columnName]: e.target.value }))
                  }
                  data-testid={`create-input-${column.columnName}`}
                />
              </FormField>
            ))}
          <Button variant="primary" onClick={submitCreate} data-testid="create-submit-button">
            {t('action.add')}
          </Button>
        </div>
      </Modal>
    </AuthenticatedLayout>
  )
}
