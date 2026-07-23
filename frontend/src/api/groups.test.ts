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
import { addMember, createGroup, deleteGroup, listGroups, listMembers, removeMember, renameGroup } from './groups'

vi.mock('./http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./http')>()
  return { ...actual, apiFetch: vi.fn() }
})

describe('groups API client', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('listGroups はGETで一覧エンドポイントを呼ぶ', async () => {
    await listGroups()
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/groups', { auth: true })
  })

  it('createGroup はPOSTでnameを送信する', async () => {
    await createGroup('営業')
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/groups', {
      method: 'POST',
      body: { name: '営業' },
      auth: true,
    })
  })

  it('renameGroup はPUTでnameを送信する', async () => {
    await renameGroup(1, '経理')
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/groups/1', {
      method: 'PUT',
      body: { name: '経理' },
      auth: true,
    })
  })

  it('deleteGroup はDELETEを呼ぶ', async () => {
    await deleteGroup(1)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/groups/1', { method: 'DELETE', auth: true })
  })

  it('listMembers はGETで所属ユーザ一覧エンドポイントを呼ぶ', async () => {
    await listMembers(1)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/groups/1/members', { auth: true })
  })

  it('addMember はPOSTでuserIdを送信する', async () => {
    await addMember(1, 42)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/groups/1/members', {
      method: 'POST',
      body: { userId: 42 },
      auth: true,
    })
  })

  it('removeMember はDELETEを呼ぶ', async () => {
    await removeMember(1, 42)
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/groups/1/members/42', { method: 'DELETE', auth: true })
  })
})
