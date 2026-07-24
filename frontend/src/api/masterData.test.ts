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
import { applyBatch, listMasterDataConnections, listMasterDataTables, listRecords } from './masterData'

vi.mock('./http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./http')>()
  return { ...actual, apiFetch: vi.fn() }
})

describe('masterData API client', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('listMasterDataConnections はGETで接続一覧エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])
    await listMasterDataConnections()
    expect(apiFetch).toHaveBeenCalledWith('/api/master-data/connections', { auth: true })
  })

  it('listMasterDataTables はGETでテーブル一覧エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])
    await listMasterDataTables(1)
    expect(apiFetch).toHaveBeenCalledWith('/api/master-data/1/tables', { auth: true })
  })

  it('listRecords はページング・スキーマ/テーブル名をパスに含めてGETを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      columns: [],
      rows: [],
      page: 0,
      pageSize: 20,
      totalCount: 0,
      creatable: false,
      deletable: false,
    })
    await listRecords(1, 'public', 'products', { page: 0, pageSize: 20 })
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/master-data/1/tables/public/products/records?page=0&pageSize=20',
      { auth: true },
    )
  })

  it('listRecords は構造化フィルタをJSON配列としてfilterクエリパラメータに含める', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      columns: [],
      rows: [],
      page: 0,
      pageSize: 20,
      totalCount: 0,
      creatable: false,
      deletable: false,
    })
    await listRecords(1, 'public', 'products', {
      page: 0,
      pageSize: 20,
      filters: [{ columnName: 'status', operator: 'EQ', value: 'active' }],
    })
    const calledPath = vi.mocked(apiFetch).mock.calls[0][0]
    expect(calledPath).toContain(
      `filter=${encodeURIComponent(JSON.stringify([{ columnName: 'status', operator: 'EQ', value: 'active' }]))}`,
    )
  })

  it('listRecords はwhere/orderByをクエリパラメータに含める', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      columns: [],
      rows: [],
      page: 0,
      pageSize: 20,
      totalCount: 0,
      creatable: false,
      deletable: false,
    })
    await listRecords(1, 'public', 'products', { page: 0, pageSize: 20, where: 'id > 1', orderBy: 'id DESC' })
    const calledPath = vi.mocked(apiFetch).mock.calls[0][0]
    expect(calledPath).toContain('where=id+%3E+1')
    expect(calledPath).toContain('orderBy=id+DESC')
  })

  it('applyBatch はPOSTで一括反映エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ success: true, itemResults: [] })
    const operations = [{ operationType: 'DELETE' as const, primaryKeyValues: { id: '1' } }]
    await applyBatch(1, 'public', 'products', operations)
    expect(apiFetch).toHaveBeenCalledWith('/api/master-data/1/tables/public/products/records/batch', {
      method: 'POST',
      auth: true,
      body: { operations },
    })
  })
})
