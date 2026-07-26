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

import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import * as queryApi from '../api/query'
import { SavedQueryEditorPage } from './SavedQueryEditorPage'

vi.mock('../api/query')

function renderPage(initialEntry: string, state?: unknown) {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[{ pathname: initialEntry, state }]}>
        <AuthProvider>
          <Routes>
            <Route path="/saved-queries/:connectionId/new" element={<SavedQueryEditorPage />} />
            <Route path="/saved-queries/:connectionId/:savedQueryId" element={<SavedQueryEditorPage />} />
            <Route path="/saved-queries/:connectionId" element={<p>保存クエリ一覧画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

const EXISTING_QUERY = {
  id: 42,
  name: '既存クエリ',
  sql: 'SELECT * FROM t',
  visibility: 'PRIVATE' as const,
  createdBy: 1,
  own: true,
  retired: false,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('SavedQueryEditorPage - 新規作成モード', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('router stateからSQL・スキーマ・パラメータ値をプレフィルする', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    renderPage('/saved-queries/1/new', { sql: 'SELECT 1', schemaName: 'public', paramValues: {} })

    expect(await screen.findByDisplayValue('SELECT 1')).toBeInTheDocument()
  })

  it('保存ボタンを押すとダイアログが開き、確定すると新規保存APIを呼び作成後の画面へ遷移する', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    const created = { ...EXISTING_QUERY, id: 99, name: 'マイクエリ' }
    vi.mocked(queryApi.createSavedQuery).mockResolvedValueOnce(created)
    vi.mocked(queryApi.getSavedQuery).mockResolvedValueOnce(created)
    const user = userEvent.setup()
    renderPage('/saved-queries/1/new')

    await screen.findByText('public')
    await user.type(screen.getByTestId('query-editor-sql-input'), 'SELECT 1')
    await user.click(screen.getByTestId('saved-query-save-button'))
    await user.type(screen.getByLabelText('クエリ名'), 'マイクエリ')
    const saveDialog = await screen.findByRole('dialog')
    await user.click(within(saveDialog).getByRole('button', { name: '保存' }))

    expect(queryApi.createSavedQuery).toHaveBeenCalledWith(1, {
      name: 'マイクエリ',
      sql: 'SELECT 1',
      visibility: 'PRIVATE',
    })
    expect(await screen.findByRole('heading', { name: 'マイクエリ' })).toBeInTheDocument()
  })
})

describe('SavedQueryEditorPage - 既存クエリ実行モード', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('SQLは既定で読み取り専用で表示される', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryApi.getSavedQuery).mockResolvedValueOnce(EXISTING_QUERY)
    renderPage('/saved-queries/1/42')

    const sqlInput = await screen.findByDisplayValue('SELECT * FROM t')
    expect(sqlInput).toHaveAttribute('readonly')
  })

  it('作成者には編集・非表示化ボタンが表示される', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryApi.getSavedQuery).mockResolvedValueOnce(EXISTING_QUERY)
    renderPage('/saved-queries/1/42')

    expect(await screen.findByTestId('saved-query-edit-button')).toBeInTheDocument()
    expect(screen.getByTestId('saved-query-retire-button')).toBeInTheDocument()
  })

  it('作成者以外には編集・非表示化ボタンを表示しない', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryApi.getSavedQuery).mockResolvedValueOnce({ ...EXISTING_QUERY, own: false })
    renderPage('/saved-queries/1/42')

    await screen.findByDisplayValue('SELECT * FROM t')
    expect(screen.queryByTestId('saved-query-edit-button')).not.toBeInTheDocument()
    expect(screen.queryByTestId('saved-query-retire-button')).not.toBeInTheDocument()
  })

  it('編集ボタンを押すとSQLが編集可能になり、更新できる', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryApi.getSavedQuery).mockResolvedValueOnce(EXISTING_QUERY)
    vi.mocked(queryApi.updateSavedQuery).mockResolvedValueOnce({ ...EXISTING_QUERY, name: '改名' })
    const user = userEvent.setup()
    renderPage('/saved-queries/1/42')

    await user.click(await screen.findByTestId('saved-query-edit-button'))
    const sqlInput = screen.getByDisplayValue('SELECT * FROM t')
    expect(sqlInput).not.toHaveAttribute('readonly')

    await user.click(screen.getByTestId('saved-query-update-button'))
    const updateDialog = await screen.findByRole('dialog')
    await user.click(within(updateDialog).getByRole('button', { name: '保存' }))

    expect(queryApi.updateSavedQuery).toHaveBeenCalledWith(1, 42, {
      name: '既存クエリ',
      sql: 'SELECT * FROM t',
      visibility: 'PRIVATE',
    })
  })

  it('非表示化ボタンを押し確認すると非表示化APIを呼び一覧画面へ戻る', async () => {
    vi.mocked(queryApi.listQuerySchemas).mockResolvedValueOnce([{ schemaName: 'public' }])
    vi.mocked(queryApi.getSavedQuery).mockResolvedValueOnce(EXISTING_QUERY)
    vi.mocked(queryApi.retireSavedQuery).mockResolvedValueOnce(undefined)
    const user = userEvent.setup()
    renderPage('/saved-queries/1/42')

    await user.click(await screen.findByTestId('saved-query-retire-button'))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByText('非表示化'))

    expect(queryApi.retireSavedQuery).toHaveBeenCalledWith(1, 42)
    expect(await screen.findByText('保存クエリ一覧画面')).toBeInTheDocument()
  })
})
