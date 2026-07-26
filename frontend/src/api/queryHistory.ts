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

export type ExecutedByScope = 'ALL' | 'MINE'
export type QueryType = 'SAVED' | 'AD_HOC'

export interface QueryHistoryConnection {
  connectionId: number
  displayName: string
}

export interface QueryHistoryRecord {
  id: number
  executedBy: number
  executorDisplayName: string
  connectionId: number
  schemaName: string
  sql: string
  savedQueryId: number | null
  savedQueryName: string | null
  queryType: QueryType
  rowCount: number
  durationMillis: number
  executedAt: string
}

export interface QueryHistoryPage {
  content: QueryHistoryRecord[]
  page: number
  pageSize: number
  totalElements: number
  totalPages: number
}

export interface QueryHistorySearchParams {
  executedByScope?: ExecutedByScope
  executedAtFrom?: string
  executedAtTo?: string
  schemaName?: string
  sqlKeyword?: string
  page?: number
  pageSize?: number
}

export function listQueryHistoryConnections(
  executedByScope: ExecutedByScope = 'ALL',
): Promise<QueryHistoryConnection[]> {
  const query = new URLSearchParams({ executedByScope })
  return apiFetch<QueryHistoryConnection[]>(`/api/query-history/connections?${query.toString()}`, { auth: true })
}

export function listQueryHistorySchemas(
  connectionId: number,
  executedByScope: ExecutedByScope = 'ALL',
): Promise<string[]> {
  const query = new URLSearchParams({ executedByScope })
  return apiFetch<string[]>(`/api/query-history/${connectionId}/schemas?${query.toString()}`, { auth: true })
}

export function listQueryHistory(
  connectionId: number,
  params: QueryHistorySearchParams,
): Promise<QueryHistoryPage> {
  const query = new URLSearchParams()
  query.set('executedByScope', params.executedByScope ?? 'ALL')
  if (params.executedAtFrom) {
    query.set('executedAtFrom', params.executedAtFrom)
  }
  if (params.executedAtTo) {
    query.set('executedAtTo', params.executedAtTo)
  }
  if (params.schemaName) {
    query.set('schemaName', params.schemaName)
  }
  if (params.sqlKeyword) {
    query.set('sqlKeyword', params.sqlKeyword)
  }
  query.set('page', String(params.page ?? 0))
  query.set('pageSize', String(params.pageSize ?? 50))
  return apiFetch<QueryHistoryPage>(`/api/query-history/${connectionId}?${query.toString()}`, { auth: true })
}
