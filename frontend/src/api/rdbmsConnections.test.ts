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
import {
  deleteConnection,
  getSchema,
  listConnections,
  refreshSchema,
  registerConnection,
  testConnection,
  testConnectionUnsaved,
  updateConnection,
} from './rdbmsConnections'

vi.mock('./http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./http')>()
  return { ...actual, apiFetch: vi.fn() }
})

const input = {
  displayName: '本番DB',
  dbType: 'MYSQL' as const,
  host: 'localhost',
  port: 3306,
  databaseName: 'mastermeister',
  username: 'root',
  password: 's3cr3t',
}

describe('rdbmsConnections API client', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('listConnections はGETで一覧エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])
    await listConnections()
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/rdbms-connections', { auth: true })
  })

  it('registerConnection はPOSTで登録エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({})
    await registerConnection(input)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/rdbms-connections', {
      method: 'POST',
      auth: true,
      body: input,
    })
  })

  it('updateConnection はPUTで更新エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({})
    await updateConnection(1, input)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/rdbms-connections/1', {
      method: 'PUT',
      auth: true,
      body: input,
    })
  })

  it('deleteConnection はDELETEで削除エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(undefined)
    await deleteConnection(1)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/rdbms-connections/1', {
      method: 'DELETE',
      auth: true,
    })
  })

  it('testConnection は保存済み接続テストエンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ success: true, errorCategory: null })
    await testConnection(1)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/rdbms-connections/1/test', {
      method: 'POST',
      auth: true,
    })
  })

  it('testConnectionUnsaved は対象IDなしの未保存テストエンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ success: true, errorCategory: null })
    await testConnectionUnsaved(input)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/rdbms-connections/test', {
      method: 'POST',
      auth: true,
      body: input,
    })
  })

  it('refreshSchema はスキーマ取込エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ connectionId: 1, importedAt: '', tables: [] })
    await refreshSchema(1)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/rdbms-connections/1/schema-refresh', {
      method: 'POST',
      auth: true,
    })
  })

  it('getSchema はスキーマ詳細取得エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ connectionId: 1, importedAt: '', tables: [] })
    await getSchema(1)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/rdbms-connections/1/schema', { auth: true })
  })
})
