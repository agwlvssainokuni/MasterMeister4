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

import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from './http'
import { exportPermissions, importPermissions, listPermissions, setPermission, unsetPermission } from './permissions'

vi.mock('./http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./http')>()
  return { ...actual, apiFetch: vi.fn() }
})

vi.mock('../auth/tokenStorage', () => ({
  getAccessToken: () => 'test-token',
}))

describe('permissions API client', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('listPermissions はプリンシパル種別・IDをクエリパラメータで送信する', async () => {
    await listPermissions(1, 'USER', 42)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/permissions/1?principalType=USER&principalId=42', {
      auth: true,
    })
  })

  it('setPermission はPUTでリクエストボディを送信する', async () => {
    const input = {
      principalType: 'USER' as const,
      principalId: 42,
      schemaName: 'public',
      tableName: 'products',
      columnName: null,
      primaryPermission: 'READ' as const,
      createPermission: false,
      deletePermission: false,
    }
    await setPermission(1, input)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/permissions/1', { method: 'PUT', body: input, auth: true })
  })

  it('unsetPermission は対象キーをクエリパラメータで送信する（tableName/columnName省略可）', async () => {
    await unsetPermission(1, { principalType: 'USER', principalId: 42, schemaName: 'public' })
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/permissions/1?principalType=USER&principalId=42&schemaName=public', {
      method: 'DELETE',
      auth: true,
    })
  })

  it('unsetPermission はtableName/columnName指定時にクエリへ含める', async () => {
    await unsetPermission(1, {
      principalType: 'USER',
      principalId: 42,
      schemaName: 'public',
      tableName: 'products',
      columnName: 'category_id',
    })
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/admin/permissions/1?principalType=USER&principalId=42&schemaName=public&tableName=products&columnName=category_id',
      { method: 'DELETE', auth: true },
    )
  })

  it('importPermissions はPOSTでyamlを送信する', async () => {
    await importPermissions(1, 'connectionId: 1\npermissions: []')
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/permissions/1/import', {
      method: 'POST',
      body: { yaml: 'connectionId: 1\npermissions: []' },
      auth: true,
    })
  })

  it('exportPermissions は認証ヘッダー付きでYAML本文を取得する', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: () => Promise.resolve('connectionId: 1\npermissions: []\n'),
    })
    vi.stubGlobal('fetch', fetchMock)

    const result = await exportPermissions(1)

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/permissions/1/export', {
      headers: { Authorization: 'Bearer test-token' },
    })
    expect(result).toBe('connectionId: 1\npermissions: []\n')
    vi.unstubAllGlobals()
  })

  it('exportPermissions は失敗時にApiErrorを送出する', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      statusText: 'Not Found',
      json: () => Promise.resolve({ code: 'RDBMS_CONNECTION_NOT_FOUND', message: '接続が見つかりません' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(exportPermissions(999)).rejects.toMatchObject({ code: 'RDBMS_CONNECTION_NOT_FOUND' })
    vi.unstubAllGlobals()
  })
})
