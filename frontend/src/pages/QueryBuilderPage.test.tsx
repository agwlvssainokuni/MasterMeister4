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

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import * as queryApi from '../api/query'
import * as queryBuilderApi from '../api/queryBuilder'
import { QueryBuilderPage } from './QueryBuilderPage'

vi.mock('../api/query')
vi.mock('../api/queryBuilder')

const TABLES = [
  {
    tableName: 'items',
    tableType: 'TABLE' as const,
    columns: [
      { columnName: 'id', dataTypeCategory: 'NUMERIC' as const },
      { columnName: 'name', dataTypeCategory: 'STRING' as const },
    ],
  },
]

const TABLES_WITH_JOIN = [
  ...TABLES,
  {
    tableName: 'categories',
    tableType: 'TABLE' as const,
    columns: [{ columnName: 'id', dataTypeCategory: 'NUMERIC' as const }],
  },
]

function ExecutionTarget() {
  const location = useLocation()
  const state = location.state as { sql?: string; schemaName?: string } | null
  return (
    <p>
      クエリ実行画面 sql={state?.sql} schema={state?.schemaName}
    </p>
  )
}

function SaveTarget() {
  const location = useLocation()
  const state = location.state as { sql?: string; schemaName?: string } | null
  return (
    <p>
      新規保存クエリ画面 sql={state?.sql} schema={state?.schemaName}
    </p>
  )
}

function renderPage(initialEntry = '/query-builder/1', state?: unknown) {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[{ pathname: initialEntry, state }]}>
        <AuthProvider>
          <Routes>
            <Route path="/query-builder/:connectionId" element={<QueryBuilderPage />} />
            <Route path="/query-execution/:connectionId" element={<ExecutionTarget />} />
            <Route path="/saved-queries/:connectionId/new" element={<SaveTarget />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('QueryBuilderPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('スキーマ・アクセス可能テーブル一覧を読み込む', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryBuilderApi.listAccessibleBuilderTables).mockResolvedValueOnce(TABLES)
    renderPage()

    await screen.findByText('public')
    await waitFor(() => expect(queryBuilderApi.listAccessibleBuilderTables).toHaveBeenCalledWith(1, 'public'))
  })

  it('FROM・SELECTタブを指定するとSQLプレビューが表示される', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryBuilderApi.listAccessibleBuilderTables).mockResolvedValueOnce(TABLES)
    vi.mocked(queryBuilderApi.generateSql).mockResolvedValueOnce({ sql: 'SELECT t1.id FROM items AS t1' })
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('public')
    await user.click(screen.getByRole('tab', { name: 'FROM' }))
    await user.selectOptions(screen.getByLabelText('テーブル'), 'items')

    await user.click(screen.getByRole('tab', { name: 'SELECT' }))
    await user.click(screen.getByTestId('query-builder-select-item-add'))

    expect(await screen.findByText('SELECT t1.id FROM items AS t1')).toBeInTheDocument()
  })

  it('JOIN条件の右辺にもFROM句・JOIN済みテーブルの列を選択できる', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryBuilderApi.listAccessibleBuilderTables).mockResolvedValueOnce(TABLES_WITH_JOIN)
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('public')
    await user.click(screen.getByRole('tab', { name: 'FROM' }))
    await user.selectOptions(screen.getByLabelText('テーブル'), 'items')
    await user.click(screen.getByTestId('query-builder-join-add'))

    const joinRow = screen.getByTestId('query-builder-join-0')
    const joinTableSelect = within(joinRow).getAllByRole('combobox')[1]
    await user.selectOptions(joinTableSelect, 'categories')
    await user.click(screen.getByTestId('query-builder-join-0-condition-add'))

    const conditionRow = screen.getByTestId('query-builder-join-0-condition-0')
    const rightSelect = within(conditionRow).getAllByRole('combobox')[1]
    expect(within(rightSelect).getByRole('option', { name: 'items.id' })).toBeInTheDocument()
    expect(within(rightSelect).getByRole('option', { name: 'items.name' })).toBeInTheDocument()
    expect(within(rightSelect).getByRole('option', { name: 'categories.id' })).toBeInTheDocument()
  })

  it('生成後に「実行へ」ボタンでクエリ実行画面へ遷移する', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryBuilderApi.listAccessibleBuilderTables).mockResolvedValueOnce(TABLES)
    vi.mocked(queryBuilderApi.generateSql).mockResolvedValueOnce({ sql: 'SELECT t1.id FROM items AS t1' })
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('public')
    await user.click(screen.getByRole('tab', { name: 'FROM' }))
    await user.selectOptions(screen.getByLabelText('テーブル'), 'items')
    await user.click(screen.getByRole('tab', { name: 'SELECT' }))
    await user.click(screen.getByTestId('query-builder-select-item-add'))
    await screen.findByText('SELECT t1.id FROM items AS t1')

    await user.click(screen.getByTestId('query-builder-execute-button'))

    expect(await screen.findByText(/クエリ実行画面/)).toBeInTheDocument()
    expect(screen.getByText(/sql=SELECT t1\.id FROM items AS t1/)).toBeInTheDocument()
  })

  it('生成後に「保存へ」ボタンで新規保存クエリ画面へ遷移する', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryBuilderApi.listAccessibleBuilderTables).mockResolvedValueOnce(TABLES)
    vi.mocked(queryBuilderApi.generateSql).mockResolvedValueOnce({ sql: 'SELECT t1.id FROM items AS t1' })
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('public')
    await user.click(screen.getByRole('tab', { name: 'FROM' }))
    await user.selectOptions(screen.getByLabelText('テーブル'), 'items')
    await user.click(screen.getByRole('tab', { name: 'SELECT' }))
    await user.click(screen.getByTestId('query-builder-select-item-add'))
    await screen.findByText('SELECT t1.id FROM items AS t1')

    await user.click(screen.getByTestId('query-builder-save-button'))

    expect(await screen.findByText(/新規保存クエリ画面/)).toBeInTheDocument()
  })

  it('router state経由でSQLを受け取るとリバースエンジニアリングしてFROMタブに反映する', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryBuilderApi.listAccessibleBuilderTables).mockResolvedValueOnce(TABLES)
    vi.mocked(queryBuilderApi.parseSql).mockResolvedValueOnce({
      from: { schemaName: 'public', tableName: 'items', alias: 't1' },
      joins: [],
      selectItems: [{ column: { tableAlias: 't1', columnName: 'id' }, aggregate: null, alias: null }],
      whereConditions: [],
      groupByColumns: [],
      havingConditions: [],
      orderByItems: [],
      limit: null,
      offset: null,
    })
    const user = userEvent.setup()
    renderPage('/query-builder/1', { sql: 'SELECT t1.id FROM items t1', schemaName: 'public' })

    await screen.findByText('public')
    await waitFor(() => expect(queryBuilderApi.parseSql).toHaveBeenCalledWith(1, 'public', 'SELECT t1.id FROM items t1'))

    await user.click(screen.getByRole('tab', { name: 'FROM' }))
    expect(await screen.findByDisplayValue('items')).toBeInTheDocument()
  })

  it('リバースエンジニアリング失敗時はエラーメッセージを表示する', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryBuilderApi.listAccessibleBuilderTables).mockResolvedValueOnce(TABLES)
    vi.mocked(queryBuilderApi.parseSql).mockRejectedValueOnce(new Error('failed'))
    renderPage('/query-builder/1', { sql: 'SELECT id FROM t1 UNION SELECT id FROM t2', schemaName: 'public' })

    await screen.findByText('public')
    expect(await screen.findByText('このSQLはクエリビルダーに反映できませんでした')).toBeInTheDocument()
  })
})
