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
import { generateSql, listAccessibleBuilderTables, parseSql } from './queryBuilder'
import type { QueryBuilderState } from './queryBuilder'

vi.mock('./http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./http')>()
  return { ...actual, apiFetch: vi.fn() }
})

describe('queryBuilder API client', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('listAccessibleBuilderTables はGETでテーブル一覧エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([])
    await listAccessibleBuilderTables(1, 'public')
    expect(apiFetch).toHaveBeenCalledWith('/api/query-builder/1/tables?schemaName=public', { auth: true })
  })

  it('generateSql はPOSTでSQL生成エンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({ sql: 'SELECT t1.id FROM items AS t1' })
    const state: QueryBuilderState = {
      from: { schemaName: 'public', tableName: 'items', alias: 't1' },
      joins: [],
      selectItems: [{ column: { tableAlias: 't1', columnName: 'id' }, aggregate: null, alias: null }],
      whereConditions: [],
      groupByColumns: [],
      havingConditions: [],
      orderByItems: [],
      limit: null,
      offset: null,
    }
    await generateSql(1, state)
    expect(apiFetch).toHaveBeenCalledWith('/api/query-builder/1/generate', {
      method: 'POST',
      auth: true,
      body: state,
    })
  })

  it('parseSql はPOSTでリバースエンジニアリングエンドポイントを呼ぶ', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      from: null,
      joins: [],
      selectItems: [],
      whereConditions: [],
      groupByColumns: [],
      havingConditions: [],
      orderByItems: [],
      limit: null,
      offset: null,
    })
    await parseSql(1, 'public', 'SELECT t1.id FROM items t1')
    expect(apiFetch).toHaveBeenCalledWith('/api/query-builder/1/parse', {
      method: 'POST',
      auth: true,
      body: { schemaName: 'public', sql: 'SELECT t1.id FROM items t1' },
    })
  })
})
