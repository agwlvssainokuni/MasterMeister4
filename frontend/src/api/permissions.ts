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

import { ApiError, apiFetch } from './http'
import { getAccessToken } from '../auth/tokenStorage'

export type PrincipalType = 'USER' | 'GROUP'
export type PrimaryPermission = 'NONE' | 'READ' | 'UPDATE'

export interface PermissionEntry {
  principalType: PrincipalType
  principalId: number
  schemaName: string
  tableName: string | null
  columnName: string | null
  primaryPermission: PrimaryPermission
  createPermission: boolean
  deletePermission: boolean
  updatedAt: string
  updatedBy: number
}

export interface PermissionEntryInput {
  principalType: PrincipalType
  principalId: number
  schemaName: string
  tableName?: string | null
  columnName?: string | null
  primaryPermission: PrimaryPermission
  createPermission: boolean
  deletePermission: boolean
}

export interface PermissionKey {
  principalType: PrincipalType
  principalId: number
  schemaName: string
  tableName?: string | null
  columnName?: string | null
}

export function listPermissions(
  connectionId: number,
  principalType: PrincipalType,
  principalId: number,
): Promise<PermissionEntry[]> {
  const params = new URLSearchParams({ principalType, principalId: String(principalId) })
  return apiFetch<PermissionEntry[]>(`/api/admin/permissions/${connectionId}?${params}`, { auth: true })
}

export function setPermission(connectionId: number, input: PermissionEntryInput): Promise<PermissionEntry> {
  return apiFetch<PermissionEntry>(`/api/admin/permissions/${connectionId}`, {
    method: 'PUT',
    body: input,
    auth: true,
  })
}

export function unsetPermission(connectionId: number, key: PermissionKey): Promise<void> {
  const params = new URLSearchParams({
    principalType: key.principalType,
    principalId: String(key.principalId),
    schemaName: key.schemaName,
  })
  if (key.tableName) {
    params.set('tableName', key.tableName)
  }
  if (key.columnName) {
    params.set('columnName', key.columnName)
  }
  return apiFetch<void>(`/api/admin/permissions/${connectionId}?${params}`, { method: 'DELETE', auth: true })
}

// exportエンドポイントはYAML本文をそのまま返す（JSONではない）ため、apiFetchの
// JSONパース前提のラッパーは使わず、認証ヘッダーのみ再現した直接fetchで取得する。
export async function exportPermissions(connectionId: number): Promise<string> {
  const token = getAccessToken()
  const response = await fetch(`/api/admin/permissions/${connectionId}/export`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    const errorBody = (await response.json().catch(() => null)) as { code?: string; message?: string } | null
    throw new ApiError(errorBody?.code ?? 'UNKNOWN_ERROR', errorBody?.message ?? response.statusText, response.status)
  }
  return response.text()
}

export function importPermissions(connectionId: number, yaml: string): Promise<void> {
  return apiFetch<void>(`/api/admin/permissions/${connectionId}/import`, {
    method: 'POST',
    body: { yaml },
    auth: true,
  })
}
