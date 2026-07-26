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

export type Visibility = 'PUBLIC' | 'PRIVATE'
export type VisibilityFilter = 'ALL' | 'PUBLIC' | 'PRIVATE'

export interface AccessibleConnection {
  connectionId: number
  displayName: string
}

export interface QueryResult {
  columns: string[]
  rows: Record<string, string | null>[]
  page: number | null
  pageSize: number | null
  totalCount: number | null
  rowCount: number
  durationMillis: number
}

export interface SavedQuerySummary {
  id: number
  name: string
  sql: string
  visibility: Visibility
  createdBy: number
  own: boolean
  retired: boolean
  createdAt: string
  updatedAt: string
}

export interface QueryExecutionParams {
  sql: string
  schemaName: string
  params: Record<string, string>
  pagingEnabled: boolean
  page: number
  pageSize: number
}

export interface SavedQueryExecutionParams {
  schemaName: string
  params: Record<string, string>
  pagingEnabled: boolean
  page: number
  pageSize: number
}

export interface SavedQueryRequest {
  name: string
  sql: string
  visibility: Visibility
}

export function listQueryConnections(): Promise<AccessibleConnection[]> {
  return apiFetch<AccessibleConnection[]>('/api/queries/connections', { auth: true })
}

export function listQuerySchemas(connectionId: number): Promise<{ schemaName: string }[]> {
  return apiFetch<{ schemaName: string }[]>(`/api/queries/${connectionId}/schemas`, { auth: true })
}

export function executeQuery(connectionId: number, request: QueryExecutionParams): Promise<QueryResult> {
  return apiFetch<QueryResult>(`/api/queries/${connectionId}/execute`, {
    method: 'POST',
    auth: true,
    body: request,
  })
}

export function listSavedQueries(
  connectionId: number,
  visibility: VisibilityFilter,
  includeOwnRetired: boolean,
): Promise<SavedQuerySummary[]> {
  const query = new URLSearchParams()
  query.set('visibility', visibility)
  query.set('includeOwnRetired', String(includeOwnRetired))
  return apiFetch<SavedQuerySummary[]>(`/api/queries/${connectionId}/saved?${query.toString()}`, { auth: true })
}

export function createSavedQuery(connectionId: number, request: SavedQueryRequest): Promise<SavedQuerySummary> {
  return apiFetch<SavedQuerySummary>(`/api/queries/${connectionId}/saved`, {
    method: 'POST',
    auth: true,
    body: request,
  })
}

export function getSavedQuery(connectionId: number, savedQueryId: number): Promise<SavedQuerySummary> {
  return apiFetch<SavedQuerySummary>(`/api/queries/${connectionId}/saved/${savedQueryId}`, { auth: true })
}

export function updateSavedQuery(
  connectionId: number,
  savedQueryId: number,
  request: SavedQueryRequest,
): Promise<SavedQuerySummary> {
  return apiFetch<SavedQuerySummary>(`/api/queries/${connectionId}/saved/${savedQueryId}`, {
    method: 'PUT',
    auth: true,
    body: request,
  })
}

export function executeSavedQuery(
  connectionId: number,
  savedQueryId: number,
  request: SavedQueryExecutionParams,
): Promise<QueryResult> {
  return apiFetch<QueryResult>(`/api/queries/${connectionId}/saved/${savedQueryId}/execute`, {
    method: 'POST',
    auth: true,
    body: request,
  })
}

export function retireSavedQuery(connectionId: number, savedQueryId: number): Promise<void> {
  return apiFetch<void>(`/api/queries/${connectionId}/saved/${savedQueryId}/retire`, {
    method: 'POST',
    auth: true,
  })
}
