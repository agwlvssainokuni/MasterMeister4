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
import * as masterDataApi from '../api/masterData'
import type { RecordPage } from '../api/masterData'
import { MasterDataRecordListPage } from './MasterDataRecordListPage'

vi.mock('../api/masterData')

function renderPage() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/master-data/1/public/products']}>
        <AuthProvider>
          <Routes>
            <Route path="/master-data/:connectionId/:schemaName/:tableName" element={<MasterDataRecordListPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

const samplePage: RecordPage = {
  columns: [
    { columnName: 'id', dataTypeCategory: 'NUMERIC', primaryKey: true, editable: false },
    { columnName: 'name', dataTypeCategory: 'STRING', primaryKey: false, editable: true },
  ],
  rows: [
    { id: '1', name: 'Alice' },
    { id: '2', name: 'Bob' },
  ],
  page: 0,
  pageSize: 20,
  totalCount: 2,
  creatable: true,
  deletable: true,
}

describe('MasterDataRecordListPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('レコード一覧を表示する', async () => {
    vi.mocked(masterDataApi.listRecords).mockResolvedValueOnce(samplePage)
    renderPage()

    expect(await screen.findByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()
    expect(masterDataApi.listRecords).toHaveBeenCalledWith(1, 'public', 'products', {
      page: 0,
      pageSize: 20,
      filters: [],
      where: undefined,
      orderBy: undefined,
    })
  })

  it('レコードが0件の場合、空状態メッセージを表示する', async () => {
    vi.mocked(masterDataApi.listRecords).mockResolvedValueOnce({ ...samplePage, rows: [], totalCount: 0 })
    renderPage()

    expect(await screen.findByText('レコードがありません')).toBeInTheDocument()
  })

  it('editableなセルをクリックして値を変更し、反映するとapplyBatchがUPDATE操作で呼ばれる', async () => {
    vi.mocked(masterDataApi.listRecords).mockResolvedValue(samplePage)
    vi.mocked(masterDataApi.applyBatch).mockResolvedValueOnce({ success: true, itemResults: [] })
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Alice')
    const aliceRow = screen.getByText('Alice').closest('tr')
    expect(aliceRow).not.toBeNull()
    await user.click(within(aliceRow as HTMLElement).getByTestId('record-cell-name'))
    const input = within(aliceRow as HTMLElement).getByTestId('record-cell-input-name')
    await user.clear(input)
    await user.type(input, 'Alicia')
    await user.tab()

    await user.click(screen.getByTestId('record-apply-button'))

    expect(masterDataApi.applyBatch).toHaveBeenCalledWith(1, 'public', 'products', [
      { operationType: 'UPDATE', primaryKeyValues: { id: '1' }, columnValues: { name: 'Alicia' } },
    ])
    expect(await screen.findByText('変更を反映しました')).toBeInTheDocument()
  })

  it('削除ボタンを押した行はDELETE操作として反映される', async () => {
    vi.mocked(masterDataApi.listRecords).mockResolvedValue(samplePage)
    vi.mocked(masterDataApi.applyBatch).mockResolvedValueOnce({ success: true, itemResults: [] })
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Alice')
    const row = screen.getByText('Alice').closest('tr')
    expect(row).not.toBeNull()
    await user.click(within(row as HTMLElement).getByText('削除'))

    await user.click(screen.getByTestId('record-apply-button'))

    expect(masterDataApi.applyBatch).toHaveBeenCalledWith(1, 'public', 'products', [
      { operationType: 'DELETE', primaryKeyValues: { id: '1' } },
    ])
  })

  it('新規作成モーダルで入力し追加すると、反映時にCREATE操作として送信される', async () => {
    vi.mocked(masterDataApi.listRecords).mockResolvedValue(samplePage)
    vi.mocked(masterDataApi.applyBatch).mockResolvedValueOnce({ success: true, itemResults: [] })
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Alice')
    await user.click(screen.getByTestId('record-add-button'))
    await user.type(screen.getByTestId('create-input-name'), 'Charlie')
    await user.click(screen.getByTestId('create-submit-button'))

    await user.click(screen.getByTestId('record-apply-button'))

    expect(masterDataApi.applyBatch).toHaveBeenCalledWith(1, 'public', 'products', [
      { operationType: 'CREATE', columnValues: { name: 'Charlie' } },
    ])
  })

  it('反映失敗時は行ごとの失敗理由を表示し、保留状態を維持する', async () => {
    vi.mocked(masterDataApi.listRecords).mockResolvedValue(samplePage)
    vi.mocked(masterDataApi.applyBatch).mockResolvedValueOnce({
      success: false,
      itemResults: [{ index: 0, errorCode: 'CONSTRAINT_VIOLATION', errorMessage: '制約違反です' }],
    })
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Alice')
    const row = screen.getByText('Alice').closest('tr')
    await user.click(within(row as HTMLElement).getByText('削除'))
    await user.click(screen.getByTestId('record-apply-button'))

    expect(await screen.findByText(/制約違反です/)).toBeInTheDocument()
    // 失敗時は保留状態を維持するため、取消ボタン(=削除保留中)が表示され続ける
    expect(within(row as HTMLElement).getByText('削除を取消')).toBeInTheDocument()
  })

  it('絞込条件を追加して検索すると、フィルタ条件付きでlistRecordsが呼ばれる', async () => {
    vi.mocked(masterDataApi.listRecords).mockResolvedValue(samplePage)
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Alice')
    await user.click(screen.getByTestId('record-add-filter-button'))
    await user.click(screen.getByTestId('record-search-button'))

    expect(masterDataApi.listRecords).toHaveBeenLastCalledWith(
      1,
      'public',
      'products',
      expect.objectContaining({
        filters: [{ columnName: 'id', operator: 'EQ', value: '', valueTo: undefined }],
      }),
    )
  })
})
