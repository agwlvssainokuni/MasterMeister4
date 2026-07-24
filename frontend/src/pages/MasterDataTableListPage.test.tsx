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
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import { ApiError } from '../api/http'
import * as masterDataApi from '../api/masterData'
import { MasterDataTableListPage } from './MasterDataTableListPage'

vi.mock('../api/masterData')

function renderPage(connectionId = '1') {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/master-data/${connectionId}`]}>
        <AuthProvider>
          <Routes>
            <Route path="/master-data/:connectionId" element={<MasterDataTableListPage />} />
            <Route path="/master-data" element={<p>接続選択画面</p>} />
            <Route
              path="/master-data/:connectionId/:schemaName/:tableName"
              element={<p>レコード一覧画面</p>}
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('MasterDataTableListPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('アクセス可能なテーブル/ビュー一覧を表示する', async () => {
    vi.mocked(masterDataApi.listMasterDataTables).mockResolvedValueOnce([
      { schemaName: 'public', tableName: 'products', tableType: 'TABLE', creatable: true, deletable: false },
      { schemaName: 'public', tableName: 'v_summary', tableType: 'VIEW', creatable: false, deletable: false },
    ])
    renderPage()

    expect(await screen.findByText('products')).toBeInTheDocument()
    expect(screen.getByText('v_summary')).toBeInTheDocument()
    expect(masterDataApi.listMasterDataTables).toHaveBeenCalledWith(1)
  })

  it('スキーマ未取込の場合、案内メッセージと戻り導線を表示する', async () => {
    vi.mocked(masterDataApi.listMasterDataTables).mockRejectedValueOnce(
      new ApiError('SCHEMA_NOT_IMPORTED', 'スキーマがまだ取り込まれていません', 404),
    )
    renderPage()

    expect(await screen.findByText('スキーマがまだ取り込まれていません')).toBeInTheDocument()
    expect(screen.getByText('接続選択画面へ戻る')).toBeInTheDocument()
  })

  it('行をクリックするとレコード一覧画面へ遷移する', async () => {
    vi.mocked(masterDataApi.listMasterDataTables).mockResolvedValueOnce([
      { schemaName: 'public', tableName: 'products', tableType: 'TABLE', creatable: true, deletable: false },
    ])
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByText('products'))

    expect(await screen.findByText('レコード一覧画面')).toBeInTheDocument()
  })
})
