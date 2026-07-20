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
import * as adminUsersApi from '../api/adminUsers'
import type { UserSummary } from '../api/adminUsers'
import { UserManagementPage } from './UserManagementPage'
import { renderPage } from '../test/render'

vi.mock('../api/adminUsers')

const pendingUser: UserSummary = {
  id: 1,
  email: 'pending@example.com',
  fullName: '保留 太郎',
  status: 'PENDING',
  createdAt: '2026-01-01T00:00:00Z',
}

const approvedUser: UserSummary = {
  id: 2,
  email: 'approved@example.com',
  fullName: '承認 花子',
  status: 'APPROVED',
  createdAt: '2026-01-02T00:00:00Z',
}

describe('UserManagementPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('初期表示はPENDINGフィルタでユーザ一覧を取得し、承認・却下ボタンを表示する', async () => {
    vi.mocked(adminUsersApi.listUsers).mockResolvedValueOnce([pendingUser])
    renderPage(<UserManagementPage />)
    expect(await screen.findByText('保留 太郎')).toBeInTheDocument()
    expect(adminUsersApi.listUsers).toHaveBeenCalledWith('PENDING')
    expect(screen.getByTestId('users-approve-1')).toBeInTheDocument()
    expect(screen.getByTestId('users-reject-1')).toBeInTheDocument()
  })

  it('ステータスフィルタを変更すると再取得し、APPROVEDでは無効化ボタンを表示する', async () => {
    vi.mocked(adminUsersApi.listUsers)
      .mockResolvedValueOnce([pendingUser])
      .mockResolvedValueOnce([approvedUser])
    renderPage(<UserManagementPage />)
    await screen.findByText('保留 太郎')

    await userEvent.selectOptions(screen.getByTestId('users-status-filter'), 'APPROVED')
    expect(await screen.findByText('承認 花子')).toBeInTheDocument()
    expect(adminUsersApi.listUsers).toHaveBeenCalledWith('APPROVED')
    expect(screen.getByTestId('users-disable-2')).toBeInTheDocument()
  })

  it('キーワード検索で氏名・メールアドレスにより表示行を絞り込む', async () => {
    vi.mocked(adminUsersApi.listUsers).mockResolvedValueOnce([pendingUser, approvedUser])
    renderPage(<UserManagementPage />)
    await screen.findByText('保留 太郎')
    expect(screen.getByText('承認 花子')).toBeInTheDocument()

    await userEvent.type(screen.getByTestId('filter-bar-search-input'), '保留')
    expect(screen.getByText('保留 太郎')).toBeInTheDocument()
    expect(screen.queryByText('承認 花子')).not.toBeInTheDocument()
  })

  it('承認ボタン押下→確認ダイアログでOK押下でapproveUserを呼び、一覧を再取得する', async () => {
    vi.mocked(adminUsersApi.listUsers)
      .mockResolvedValueOnce([pendingUser])
      .mockResolvedValueOnce([{ ...pendingUser, status: 'APPROVED' }])
    vi.mocked(adminUsersApi.approveUser).mockResolvedValueOnce(undefined)
    renderPage(<UserManagementPage />)
    await screen.findByText('保留 太郎')

    await userEvent.click(screen.getByTestId('users-approve-1'))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('このユーザを承認しますか？')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: '承認' }))

    await waitFor(() => expect(adminUsersApi.approveUser).toHaveBeenCalledWith(1))
    expect(adminUsersApi.listUsers).toHaveBeenCalledTimes(2)
  })

  it('確認ダイアログでキャンセルした場合はAPIを呼ばない', async () => {
    vi.mocked(adminUsersApi.listUsers).mockResolvedValueOnce([pendingUser])
    renderPage(<UserManagementPage />)
    await screen.findByText('保留 太郎')

    await userEvent.click(screen.getByTestId('users-reject-1'))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'キャンセル' }))

    expect(adminUsersApi.rejectUser).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('一覧取得に失敗した場合はエラーメッセージを表示する', async () => {
    vi.mocked(adminUsersApi.listUsers).mockRejectedValueOnce(
      new ApiError('AUTH_TOKEN_EXPIRED', 'セッションの有効期限が切れました', 401),
    )
    renderPage(<UserManagementPage />)
    expect(await screen.findByText('セッションの有効期限が切れました')).toBeInTheDocument()
  })
})
