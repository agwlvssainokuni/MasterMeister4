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

import { apiFetch } from './http'
import type { TableType } from './rdbmsConnections'

export type ColumnDataTypeCategory = 'NUMERIC' | 'DATETIME' | 'STRING' | 'BOOLEAN'
export type FilterOperator = 'EQ' | 'LT' | 'LE' | 'GT' | 'GE' | 'BETWEEN' | 'STARTS_WITH' | 'CONTAINS'
export type OperationType = 'CREATE' | 'UPDATE' | 'DELETE'

export interface AccessibleConnection {
  connectionId: number
  displayName: string
}

export interface AccessibleTable {
  schemaName: string
  tableName: string
  tableType: TableType
  creatable: boolean
  deletable: boolean
}

export interface RecordColumn {
  columnName: string
  dataTypeCategory: ColumnDataTypeCategory
  primaryKey: boolean
  editable: boolean
}

export interface RecordFilter {
  columnName: string
  operator: FilterOperator
  value?: string
  valueTo?: string
}

export interface RecordPage {
  columns: RecordColumn[]
  rows: Record<string, string | null>[]
  page: number
  pageSize: number
  totalCount: number
  creatable: boolean
  deletable: boolean
}

export interface BatchOperationItem {
  operationType: OperationType
  primaryKeyValues?: Record<string, string>
  columnValues?: Record<string, string>
}

export interface BatchOperationItemResult {
  index: number
  errorCode: string
  errorMessage: string
}

export interface BatchOperationResult {
  success: boolean
  itemResults: BatchOperationItemResult[]
}

export function listMasterDataConnections(): Promise<AccessibleConnection[]> {
  return apiFetch<AccessibleConnection[]>('/api/master-data/connections', { auth: true })
}

export function listMasterDataTables(connectionId: number): Promise<AccessibleTable[]> {
  return apiFetch<AccessibleTable[]>(`/api/master-data/${connectionId}/tables`, { auth: true })
}

export interface ListRecordsParams {
  page: number
  pageSize: number
  filters?: RecordFilter[]
  where?: string
  orderBy?: string
}

export function listRecords(
  connectionId: number,
  schemaName: string,
  tableName: string,
  params: ListRecordsParams,
): Promise<RecordPage> {
  const query = new URLSearchParams()
  query.set('page', String(params.page))
  query.set('pageSize', String(params.pageSize))
  if (params.filters && params.filters.length > 0) {
    query.set('filter', JSON.stringify(params.filters))
  }
  if (params.where) {
    query.set('where', params.where)
  }
  if (params.orderBy) {
    query.set('orderBy', params.orderBy)
  }
  const path = `/api/master-data/${connectionId}/tables/${encodeURIComponent(schemaName)}/${encodeURIComponent(tableName)}/records?${query.toString()}`
  return apiFetch<RecordPage>(path, { auth: true })
}

export function applyBatch(
  connectionId: number,
  schemaName: string,
  tableName: string,
  operations: BatchOperationItem[],
): Promise<BatchOperationResult> {
  const path = `/api/master-data/${connectionId}/tables/${encodeURIComponent(schemaName)}/${encodeURIComponent(tableName)}/records/batch`
  return apiFetch<BatchOperationResult>(path, {
    method: 'POST',
    auth: true,
    body: { operations },
  })
}
