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

export type DbType = 'MYSQL' | 'MARIADB' | 'POSTGRESQL' | 'H2'
export type ConnectionErrorCategory = 'CONNECTION_UNREACHABLE' | 'AUTH_ERROR' | 'TIMEOUT' | 'OTHER'
export type NormalizedType = 'STRING' | 'NUMBER' | 'DATE_TIME' | 'BOOLEAN' | 'BINARY' | 'OTHER'
export type ConstraintType = 'PRIMARY_KEY' | 'FOREIGN_KEY' | 'UNIQUE' | 'INDEX'
export type TableType = 'TABLE' | 'VIEW'

export interface RdbmsConnectionSummary {
  id: number
  displayName: string
  dbType: DbType
  host: string
  port: number
  databaseName: string
  username: string
  additionalParams: string | null
  schemaImportedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface RdbmsConnectionInput {
  displayName: string
  dbType: DbType
  host: string
  port: number
  databaseName: string
  username: string
  password?: string
  additionalParams?: string | null
}

export interface ConnectionTestResult {
  success: boolean
  errorCategory: ConnectionErrorCategory | null
}

export interface SchemaConstraintDetail {
  constraintType: ConstraintType
  constraintName: string
  columnNames: string[]
  referencedTable: string | null
  referencedColumns: string[]
}

export interface SchemaColumnDetail {
  columnName: string
  ordinalPosition: number
  comment: string | null
  nativeType: string
  normalizedType: NormalizedType
  nullable: boolean
  defaultValue: string | null
}

export interface SchemaTableDetail {
  schemaName: string
  tableName: string
  tableType: TableType
  comment: string | null
  columns: SchemaColumnDetail[]
  constraints: SchemaConstraintDetail[]
}

export interface SchemaSnapshotDetail {
  connectionId: number
  importedAt: string
  tables: SchemaTableDetail[]
}

export function listConnections(): Promise<RdbmsConnectionSummary[]> {
  return apiFetch<RdbmsConnectionSummary[]>('/api/admin/rdbms-connections', { auth: true })
}

export function registerConnection(input: RdbmsConnectionInput): Promise<RdbmsConnectionSummary> {
  return apiFetch<RdbmsConnectionSummary>('/api/admin/rdbms-connections', {
    method: 'POST',
    auth: true,
    body: input,
  })
}

export function updateConnection(
  id: number,
  input: RdbmsConnectionInput,
): Promise<RdbmsConnectionSummary> {
  return apiFetch<RdbmsConnectionSummary>(`/api/admin/rdbms-connections/${id}`, {
    method: 'PUT',
    auth: true,
    body: input,
  })
}

export function deleteConnection(id: number): Promise<void> {
  return apiFetch<void>(`/api/admin/rdbms-connections/${id}`, { method: 'DELETE', auth: true })
}

export function testConnection(id: number): Promise<ConnectionTestResult> {
  return apiFetch<ConnectionTestResult>(`/api/admin/rdbms-connections/${id}/test`, {
    method: 'POST',
    auth: true,
  })
}

export function testConnectionUnsaved(input: RdbmsConnectionInput): Promise<ConnectionTestResult> {
  return apiFetch<ConnectionTestResult>('/api/admin/rdbms-connections/test', {
    method: 'POST',
    auth: true,
    body: input,
  })
}

export function refreshSchema(id: number): Promise<SchemaSnapshotDetail> {
  return apiFetch<SchemaSnapshotDetail>(`/api/admin/rdbms-connections/${id}/schema-refresh`, {
    method: 'POST',
    auth: true,
  })
}

export function getSchema(id: number): Promise<SchemaSnapshotDetail> {
  return apiFetch<SchemaSnapshotDetail>(`/api/admin/rdbms-connections/${id}/schema`, { auth: true })
}

export const DEFAULT_PORT_BY_DB_TYPE: Record<DbType, number> = {
  MYSQL: 3306,
  MARIADB: 3306,
  POSTGRESQL: 5432,
  H2: 9092,
}
