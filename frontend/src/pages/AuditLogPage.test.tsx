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
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import * as auditLogApi from '../api/auditLog'
import * as adminUsersApi from '../api/adminUsers'
import * as rdbmsConnectionsApi from '../api/rdbmsConnections'
import { AuditLogPage } from './AuditLogPage'

vi.mock('../api/auditLog', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/auditLog')>()
  return { ...actual, listAuditLog: vi.fn() }
})
vi.mock('../api/adminUsers')
vi.mock('../api/rdbmsConnections')

function renderPage() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/audit-log']}>
        <AuthProvider>
          <AuditLogPage />
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

const ENTRY = {
  id: 1,
  occurredAt: '2026-07-27T00:00:00Z',
  userId: 1,
  userDisplayName: '山田太郎',
  connectionId: 1,
  connectionDisplayName: '接続A',
  eventType: 'LOGIN' as const,
  targetResource: null,
  resultStatus: 'SUCCESS' as const,
  detail: null,
}

describe('AuditLogPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  function mockDefaults() {
    vi.mocked(adminUsersApi.listUsers).mockResolvedValue([
      { id: 1, email: 'yamada@example.com', fullName: '山田太郎', status: 'APPROVED', createdAt: '2026-01-01T00:00:00Z' },
    ])
    vi.mocked(rdbmsConnectionsApi.listConnections).mockResolvedValue([
      {
        id: 1,
        displayName: '接続A',
        dbType: 'POSTGRESQL',
        host: 'localhost',
        port: 5432,
        databaseName: 'db',
        username: 'user',
        additionalParams: null,
        schemaImportedAt: null,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ])
    vi.mocked(auditLogApi.listAuditLog).mockResolvedValue({
      content: [ENTRY],
      page: 0,
      pageSize: 50,
      totalElements: 1,
      totalPages: 1,
    })
  }

  it('監査ログ一覧を表示する', async () => {
    mockDefaults()
    renderPage()

    expect(await screen.findByRole('cell', { name: '山田太郎' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '接続A' })).toBeInTheDocument()
  })

  it('イベント種別を絞込条件として指定すると再取得する', async () => {
    mockDefaults()
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: '山田太郎' })

    await user.selectOptions(screen.getByLabelText('イベント種別'), 'LOGIN')

    expect(auditLogApi.listAuditLog).toHaveBeenLastCalledWith(
      expect.objectContaining({ eventType: 'LOGIN' }),
    )
  })

  it('対象ユーザ・対象接続のセレクタ選択肢は既存APIから取得する', async () => {
    mockDefaults()
    renderPage()

    await screen.findByRole('cell', { name: '山田太郎' })

    expect(adminUsersApi.listUsers).toHaveBeenCalled()
    expect(rdbmsConnectionsApi.listConnections).toHaveBeenCalled()
  })
})
