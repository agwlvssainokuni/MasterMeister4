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
import { listAuditLog } from './auditLog'

vi.mock('./http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./http')>()
  return { ...actual, apiFetch: vi.fn() }
})

describe('auditLog API client', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('listAuditLog は絞込条件・ページングをクエリパラメータとして渡す', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      content: [],
      page: 0,
      pageSize: 50,
      totalElements: 0,
      totalPages: 1,
    })
    await listAuditLog({
      eventType: 'LOGIN',
      userId: 1,
      connectionId: 2,
      resultStatus: 'SUCCESS',
      page: 2,
      pageSize: 20,
    })
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/admin/audit-log?eventType=LOGIN&userId=1&connectionId=2&resultStatus=SUCCESS&page=2&pageSize=20',
      { auth: true },
    )
  })

  it('listAuditLog は絞込条件未指定時デフォルト値でリクエストする', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      content: [],
      page: 0,
      pageSize: 50,
      totalElements: 0,
      totalPages: 1,
    })
    await listAuditLog({})
    expect(apiFetch).toHaveBeenCalledWith('/api/admin/audit-log?page=0&pageSize=50', { auth: true })
  })
})
