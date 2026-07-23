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
import * as groupsApi from '../api/groups'
import type { GroupSummary, GroupMember } from '../api/groups'
import * as adminUsersApi from '../api/adminUsers'
import type { UserSummary } from '../api/adminUsers'
import { GroupManagementPage } from './GroupManagementPage'
import { renderPage } from '../test/render'

vi.mock('../api/groups')
vi.mock('../api/adminUsers')

const salesGroup: GroupSummary = { id: 1, name: '営業', memberCount: 1, createdAt: '2026-01-01T00:00:00Z' }

const alice: GroupMember = { userId: 10, email: 'alice@example.com', fullName: 'Alice' }
const approvedBob: UserSummary = {
  id: 20,
  email: 'bob@example.com',
  fullName: 'Bob',
  status: 'APPROVED',
  createdAt: '2026-01-01T00:00:00Z',
}

describe('GroupManagementPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('グループ一覧を表示する', async () => {
    vi.mocked(groupsApi.listGroups).mockResolvedValueOnce([salesGroup])
    renderPage(<GroupManagementPage />)
    expect(await screen.findByText('営業')).toBeInTheDocument()
  })

  it('グループを作成できる', async () => {
    vi.mocked(groupsApi.listGroups).mockResolvedValueOnce([]).mockResolvedValueOnce([salesGroup])
    vi.mocked(groupsApi.createGroup).mockResolvedValueOnce(salesGroup)
    renderPage(<GroupManagementPage />)
    await waitFor(() => expect(groupsApi.listGroups).toHaveBeenCalledTimes(1))

    await userEvent.click(screen.getByTestId('groups-add-button'))
    await userEvent.type(screen.getByTestId('groups-form-name'), '営業')
    await userEvent.click(screen.getByTestId('groups-form-submit'))

    expect(groupsApi.createGroup).toHaveBeenCalledWith('営業')
    await waitFor(() => expect(groupsApi.listGroups).toHaveBeenCalledTimes(2))
  })

  it('削除確認ダイアログ経由でグループを削除できる', async () => {
    vi.mocked(groupsApi.listGroups).mockResolvedValueOnce([salesGroup]).mockResolvedValueOnce([])
    vi.mocked(groupsApi.deleteGroup).mockResolvedValueOnce(undefined)
    renderPage(<GroupManagementPage />)
    await screen.findByText('営業')

    await userEvent.click(screen.getByTestId('groups-delete-1'))
    await userEvent.click(within(screen.getByRole('dialog')).getByText('削除'))

    expect(groupsApi.deleteGroup).toHaveBeenCalledWith(1)
  })

  it('所属ユーザ管理モーダルでユーザの追加・削除ができる', async () => {
    vi.mocked(groupsApi.listGroups).mockResolvedValue([salesGroup])
    vi.mocked(groupsApi.listMembers).mockResolvedValue([alice])
    vi.mocked(adminUsersApi.listUsers).mockResolvedValue([approvedBob])
    vi.mocked(groupsApi.addMember).mockResolvedValueOnce(undefined)
    vi.mocked(groupsApi.removeMember).mockResolvedValueOnce(undefined)

    renderPage(<GroupManagementPage />)
    await screen.findByText('営業')

    await userEvent.click(screen.getByTestId('groups-members-1'))
    expect(await screen.findByText(/Alice/)).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByTestId('groups-member-select'), '20')
    await userEvent.click(screen.getByTestId('groups-member-add-button'))
    expect(groupsApi.addMember).toHaveBeenCalledWith(1, 20)

    await userEvent.click(screen.getByTestId('groups-member-remove-10'))
    expect(groupsApi.removeMember).toHaveBeenCalledWith(1, 10)
  })
})
