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
import { listQueryHistory, listQueryHistoryConnections, listQueryHistorySchemas } from './queryHistory'

vi.mock('./http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./http')>()
  return { ...actual, apiFetch: vi.fn() }
})

describe('queryHistory API client', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('listQueryHistoryConnections はGETで接続一覧エンドポイントを呼ぶ（デフォルトALL）', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])
    await listQueryHistoryConnections()
    expect(apiFetch).toHaveBeenCalledWith('/api/query-history/connections?executedByScope=ALL', { auth: true })
  })

  it('listQueryHistoryConnections はexecutedByScopeを指定できる', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])
    await listQueryHistoryConnections('MINE')
    expect(apiFetch).toHaveBeenCalledWith('/api/query-history/connections?executedByScope=MINE', { auth: true })
  })

  it('listQueryHistorySchemas はGETでスキーマ名一覧エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])
    await listQueryHistorySchemas(1, 'ALL')
    expect(apiFetch).toHaveBeenCalledWith('/api/query-history/1/schemas?executedByScope=ALL', { auth: true })
  })

  it('listQueryHistory は絞込条件・ページングをクエリパラメータとして渡す', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      content: [],
      page: 0,
      pageSize: 50,
      totalElements: 0,
      totalPages: 1,
    })
    await listQueryHistory(1, {
      executedByScope: 'MINE',
      schemaName: 'public',
      sqlKeyword: 'SELECT',
      page: 2,
      pageSize: 20,
    })
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/query-history/1?executedByScope=MINE&schemaName=public&sqlKeyword=SELECT&page=2&pageSize=20',
      { auth: true },
    )
  })

  it('listQueryHistory は絞込条件未指定時デフォルト値でリクエストする', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      content: [],
      page: 0,
      pageSize: 50,
      totalElements: 0,
      totalPages: 1,
    })
    await listQueryHistory(1, {})
    expect(apiFetch).toHaveBeenCalledWith('/api/query-history/1?executedByScope=ALL&page=0&pageSize=50', {
      auth: true,
    })
  })
})
