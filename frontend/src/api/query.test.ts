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
  createSavedQuery,
  executeQuery,
  executeSavedQuery,
  getSavedQuery,
  listQueryConnections,
  listQuerySchemas,
  listSavedQueries,
  retireSavedQuery,
  updateSavedQuery,
} from './query'

vi.mock('./http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./http')>()
  return { ...actual, apiFetch: vi.fn() }
})

describe('query API client', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('listQueryConnections はGETで接続一覧エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])
    await listQueryConnections()
    expect(apiFetch).toHaveBeenCalledWith('/api/queries/connections', { auth: true })
  })

  it('listQuerySchemas はGETでスキーマ一覧エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])
    await listQuerySchemas(1)
    expect(apiFetch).toHaveBeenCalledWith('/api/queries/1/schemas', { auth: true })
  })

  it('executeQuery はPOSTでad-hoc実行エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      columns: [],
      rows: [],
      page: null,
      pageSize: null,
      totalCount: null,
      rowCount: 0,
      durationMillis: 0,
    })
    await executeQuery(1, { sql: 'SELECT 1', schemaName: 'public', params: {}, pagingEnabled: false, page: 0, pageSize: 0 })
    expect(apiFetch).toHaveBeenCalledWith('/api/queries/1/execute', {
      method: 'POST',
      auth: true,
      body: { sql: 'SELECT 1', schemaName: 'public', params: {}, pagingEnabled: false, page: 0, pageSize: 0 },
    })
  })

  it('listSavedQueries はvisibility/includeOwnRetiredをクエリパラメータに含めてGETを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])
    await listSavedQueries(1, 'PUBLIC', true)
    expect(apiFetch).toHaveBeenCalledWith('/api/queries/1/saved?visibility=PUBLIC&includeOwnRetired=true', {
      auth: true,
    })
  })

  it('createSavedQuery はPOSTで新規保存エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({})
    await createSavedQuery(1, { name: 'クエリA', sql: 'SELECT 1', visibility: 'PUBLIC' })
    expect(apiFetch).toHaveBeenCalledWith('/api/queries/1/saved', {
      method: 'POST',
      auth: true,
      body: { name: 'クエリA', sql: 'SELECT 1', visibility: 'PUBLIC' },
    })
  })

  it('getSavedQuery はGETで個別取得エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({})
    await getSavedQuery(1, 42)
    expect(apiFetch).toHaveBeenCalledWith('/api/queries/1/saved/42', { auth: true })
  })

  it('updateSavedQuery はPUTで更新エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({})
    await updateSavedQuery(1, 42, { name: '改名', sql: 'SELECT 2', visibility: 'PRIVATE' })
    expect(apiFetch).toHaveBeenCalledWith('/api/queries/1/saved/42', {
      method: 'PUT',
      auth: true,
      body: { name: '改名', sql: 'SELECT 2', visibility: 'PRIVATE' },
    })
  })

  it('executeSavedQuery はPOSTで保存クエリ実行エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      columns: [],
      rows: [],
      page: null,
      pageSize: null,
      totalCount: null,
      rowCount: 0,
      durationMillis: 0,
    })
    await executeSavedQuery(1, 42, { schemaName: 'public', params: {}, pagingEnabled: false, page: 0, pageSize: 0 })
    expect(apiFetch).toHaveBeenCalledWith('/api/queries/1/saved/42/execute', {
      method: 'POST',
      auth: true,
      body: { schemaName: 'public', params: {}, pagingEnabled: false, page: 0, pageSize: 0 },
    })
  })

  it('retireSavedQuery はPOSTで非表示化エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(undefined)
    await retireSavedQuery(1, 42)
    expect(apiFetch).toHaveBeenCalledWith('/api/queries/1/saved/42/retire', { method: 'POST', auth: true })
  })
})
