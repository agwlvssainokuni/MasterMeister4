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

import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import * as connectionsApi from '../api/rdbmsConnections'
import type { RdbmsConnectionSummary } from '../api/rdbmsConnections'
import { RdbmsConnectionListPage } from './RdbmsConnectionListPage'
import { renderPage } from '../test/render'

vi.mock('../api/rdbmsConnections')

const mysqlConnection: RdbmsConnectionSummary = {
  id: 1,
  displayName: '本番DB',
  dbType: 'MYSQL',
  host: 'localhost',
  port: 3306,
  databaseName: 'mastermeister',
  username: 'root',
  additionalParams: null,
  schemaImportedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('RdbmsConnectionListPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('接続一覧を取得して表示し、未取込の場合は未取込バッジを表示する', async () => {
    vi.mocked(connectionsApi.listConnections).mockResolvedValueOnce([mysqlConnection])
    renderPage(<RdbmsConnectionListPage />)
    expect(await screen.findByText('本番DB')).toBeInTheDocument()
    expect(screen.getByText('localhost:3306/mastermeister')).toBeInTheDocument()
    expect(screen.getByText('未取込')).toBeInTheDocument()
  })

  it('接続を追加ボタンでフォームを開き、dbType選択でデフォルトポートが自動入力される', async () => {
    vi.mocked(connectionsApi.listConnections).mockResolvedValueOnce([])
    renderPage(<RdbmsConnectionListPage />)
    await screen.findByText('登録済みの接続はありません')

    await userEvent.click(screen.getByTestId('connections-add-button'))
    await userEvent.selectOptions(screen.getByTestId('connections-form-db-type'), 'POSTGRESQL')

    expect(screen.getByTestId('connections-form-port')).toHaveValue(5432)
  })

  it('登録フォーム送信でregisterConnectionを呼び、一覧を再取得する', async () => {
    vi.mocked(connectionsApi.listConnections)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([mysqlConnection])
    vi.mocked(connectionsApi.registerConnection).mockResolvedValueOnce(mysqlConnection)
    renderPage(<RdbmsConnectionListPage />)
    await screen.findByText('登録済みの接続はありません')

    await userEvent.click(screen.getByTestId('connections-add-button'))
    await userEvent.type(screen.getByTestId('connections-form-display-name'), '本番DB')
    await userEvent.type(screen.getByTestId('connections-form-host'), 'localhost')
    await userEvent.type(screen.getByTestId('connections-form-port'), '3306')
    await userEvent.type(screen.getByTestId('connections-form-database-name'), 'mastermeister')
    await userEvent.type(screen.getByTestId('connections-form-username'), 'root')
    await userEvent.type(screen.getByTestId('connections-form-password'), 's3cr3t')
    await userEvent.click(screen.getByTestId('connections-form-submit'))

    await waitFor(() => expect(connectionsApi.registerConnection).toHaveBeenCalledTimes(1))
    expect(connectionsApi.registerConnection).toHaveBeenCalledWith(
      expect.objectContaining({ displayName: '本番DB', host: 'localhost', port: 3306 }),
    )
    expect(await screen.findByText('本番DB')).toBeInTheDocument()
  })

  it('フォーム内接続テストボタンで未保存の値のテストを行い、モーダルは閉じない', async () => {
    vi.mocked(connectionsApi.listConnections).mockResolvedValueOnce([])
    vi.mocked(connectionsApi.testConnectionUnsaved).mockResolvedValueOnce({
      success: false,
      errorCategory: 'AUTH_ERROR',
    })
    renderPage(<RdbmsConnectionListPage />)
    await screen.findByText('登録済みの接続はありません')

    await userEvent.click(screen.getByTestId('connections-add-button'))
    await userEvent.type(screen.getByTestId('connections-form-host'), 'localhost')
    await userEvent.click(screen.getByTestId('connections-form-test-button'))

    expect(await screen.findByText('認証に失敗しました（ユーザ名・パスワードを確認してください）')).toBeInTheDocument()
    expect(screen.getByTestId('connections-form-submit')).toBeInTheDocument()
  })

  it('削除ボタン→確認ダイアログでOKを押すとdeleteConnectionを呼ぶ', async () => {
    vi.mocked(connectionsApi.listConnections)
      .mockResolvedValueOnce([mysqlConnection])
      .mockResolvedValueOnce([])
    vi.mocked(connectionsApi.deleteConnection).mockResolvedValueOnce(undefined)
    renderPage(<RdbmsConnectionListPage />)
    await screen.findByText('本番DB')

    await userEvent.click(screen.getByTestId('connections-delete-1'))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText(/取り込み済みのスキーマ情報もあわせて削除されます/)).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: '削除' }))

    await waitFor(() => expect(connectionsApi.deleteConnection).toHaveBeenCalledWith(1))
  })

  it('スキーマ取込ボタンでrefreshSchemaを呼び、成功メッセージを表示する', async () => {
    vi.mocked(connectionsApi.listConnections)
      .mockResolvedValueOnce([mysqlConnection])
      .mockResolvedValueOnce([{ ...mysqlConnection, schemaImportedAt: '2026-01-05T00:00:00Z' }])
    vi.mocked(connectionsApi.refreshSchema).mockResolvedValueOnce({
      connectionId: 1,
      importedAt: '2026-01-05T00:00:00Z',
      tables: [],
    })
    renderPage(<RdbmsConnectionListPage />)
    await screen.findByText('本番DB')

    await userEvent.click(screen.getByTestId('connections-schema-refresh-1'))

    expect(await screen.findByText('スキーマ取込に成功しました')).toBeInTheDocument()
  })

  it('一覧取得に失敗した場合はエラーメッセージを表示する', async () => {
    vi.mocked(connectionsApi.listConnections).mockRejectedValueOnce(
      new ApiError('INTERNAL_SERVER_ERROR', '予期しないエラーが発生しました', 500),
    )
    renderPage(<RdbmsConnectionListPage />)
    expect(await screen.findByText('予期しないエラーが発生しました')).toBeInTheDocument()
  })
})
