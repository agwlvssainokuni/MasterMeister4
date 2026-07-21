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

import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import { ApiError } from '../api/http'
import * as connectionsApi from '../api/rdbmsConnections'
import type { SchemaSnapshotDetail } from '../api/rdbmsConnections'
import { SchemaDetailPage } from './SchemaDetailPage'

vi.mock('../api/rdbmsConnections')

function renderSchemaDetailPage(connectionId = '1') {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/connections/${connectionId}/schema`]}>
        <AuthProvider>
          <Routes>
            <Route path="/connections/:id/schema" element={<SchemaDetailPage />} />
            <Route path="/connections" element={<p>接続一覧画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

const sampleSnapshot: SchemaSnapshotDetail = {
  connectionId: 1,
  importedAt: '2026-01-05T00:00:00Z',
  tables: [
    {
      tableName: 'products',
      tableType: 'TABLE',
      comment: null,
      columns: [
        {
          columnName: 'product_id',
          ordinalPosition: 1,
          comment: null,
          nativeType: 'INT',
          normalizedType: 'NUMBER',
          nullable: false,
          defaultValue: null,
        },
        {
          columnName: 'category_id',
          ordinalPosition: 2,
          comment: null,
          nativeType: 'INT',
          normalizedType: 'NUMBER',
          nullable: false,
          defaultValue: null,
        },
      ],
      constraints: [
        {
          constraintType: 'PRIMARY_KEY',
          constraintName: 'pk_products',
          columnNames: ['product_id'],
          referencedTable: null,
          referencedColumns: [],
        },
        {
          constraintType: 'FOREIGN_KEY',
          constraintName: 'fk_products_category',
          columnNames: ['category_id'],
          referencedTable: 'categories',
          referencedColumns: ['category_id'],
        },
      ],
    },
  ],
}

describe('SchemaDetailPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('スキーマ取込済みの場合、テーブル一覧とカラム一覧を表示する', async () => {
    vi.mocked(connectionsApi.getSchema).mockResolvedValueOnce(sampleSnapshot)
    renderSchemaDetailPage()

    expect(await screen.findByText('products')).toBeInTheDocument()
    expect(screen.getByText('product_id')).toBeInTheDocument()
    expect(screen.getByText('PRIMARY_KEY')).toBeInTheDocument()
    expect(screen.getByText('FOREIGN_KEY')).toBeInTheDocument()
    expect(connectionsApi.getSchema).toHaveBeenCalledWith(1)
  })

  it('スキーマ未取込の場合、案内メッセージと一覧への戻り導線を表示する', async () => {
    vi.mocked(connectionsApi.getSchema).mockRejectedValueOnce(
      new ApiError('SCHEMA_NOT_IMPORTED', 'スキーマがまだ取り込まれていません', 404),
    )
    renderSchemaDetailPage()

    expect(await screen.findByText('スキーマがまだ取り込まれていません')).toBeInTheDocument()
    expect(screen.getByText('接続一覧へ戻る')).toBeInTheDocument()
  })
})
