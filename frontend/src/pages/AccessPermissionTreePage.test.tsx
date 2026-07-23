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

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import { ApiError } from '../api/http'
import * as connectionsApi from '../api/rdbmsConnections'
import type { RdbmsConnectionSummary, SchemaSnapshotDetail } from '../api/rdbmsConnections'
import * as groupsApi from '../api/groups'
import * as adminUsersApi from '../api/adminUsers'
import * as permissionsApi from '../api/permissions'
import type { PermissionEntry } from '../api/permissions'
import { AccessPermissionTreePage } from './AccessPermissionTreePage'

vi.mock('../api/rdbmsConnections')
vi.mock('../api/groups')
vi.mock('../api/adminUsers')
vi.mock('../api/permissions')

function renderTreePage(connectionId = '1') {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/permissions/${connectionId}`]}>
        <AuthProvider>
          <Routes>
            <Route path="/permissions/:connectionId" element={<AccessPermissionTreePage />} />
            <Route path="/connections" element={<p>接続一覧画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

const sampleConnection: RdbmsConnectionSummary = {
  id: 1,
  displayName: '本番DB',
  dbType: 'MYSQL',
  host: 'localhost',
  port: 3306,
  databaseName: 'mastermeister',
  username: 'root',
  additionalParams: null,
  schemaImportedAt: '2026-01-01T00:00:00Z',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const sampleSnapshot: SchemaSnapshotDetail = {
  connectionId: 1,
  importedAt: '2026-01-01T00:00:00Z',
  tables: [
    {
      schemaName: 'public',
      tableName: 'products',
      tableType: 'TABLE',
      comment: null,
      columns: [
        {
          columnName: 'category_id',
          ordinalPosition: 1,
          comment: null,
          nativeType: 'INT',
          normalizedType: 'NUMBER',
          nullable: false,
          defaultValue: null,
        },
      ],
      constraints: [],
    },
  ],
}

function setupCommonMocks() {
  vi.mocked(connectionsApi.listConnections).mockResolvedValue([sampleConnection])
  vi.mocked(connectionsApi.getSchema).mockResolvedValue(sampleSnapshot)
  vi.mocked(adminUsersApi.listUsers).mockResolvedValue([
    { id: 42, email: 'alice@example.com', fullName: 'Alice', status: 'APPROVED', createdAt: '2026-01-01T00:00:00Z' },
  ])
  vi.mocked(groupsApi.listGroups).mockResolvedValue([])
}

describe('AccessPermissionTreePage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('スキーマ未取込の場合は案内メッセージを表示する', async () => {
    vi.mocked(connectionsApi.listConnections).mockResolvedValue([sampleConnection])
    vi.mocked(connectionsApi.getSchema).mockRejectedValue(new ApiError('SCHEMA_NOT_IMPORTED', 'not imported', 404))
    vi.mocked(adminUsersApi.listUsers).mockResolvedValue([])
    vi.mocked(groupsApi.listGroups).mockResolvedValue([])

    renderTreePage()

    expect(await screen.findByText('スキーマがまだ取り込まれていません')).toBeInTheDocument()
  })

  it('プリンシパル未選択時はツリーを表示せず選択を促す', async () => {
    setupCommonMocks()
    vi.mocked(permissionsApi.listPermissions).mockResolvedValue([])

    renderTreePage()

    expect(await screen.findByText('対象のユーザまたはグループを選択してください')).toBeInTheDocument()
  })

  it('プリンシパル選択後、ツリーを表示し主権限を設定できる', async () => {
    setupCommonMocks()
    vi.mocked(permissionsApi.listPermissions).mockResolvedValue([])
    const savedEntry: PermissionEntry = {
      principalType: 'USER',
      principalId: 42,
      schemaName: 'public',
      tableName: 'products',
      columnName: null,
      primaryPermission: 'READ',
      createPermission: false,
      deletePermission: false,
      updatedAt: '2026-01-01T00:00:00Z',
      updatedBy: 99,
    }
    vi.mocked(permissionsApi.setPermission).mockResolvedValueOnce(savedEntry)

    renderTreePage()
    await userEvent.selectOptions(await screen.findByTestId('permissions-principal-select'), '42')

    await userEvent.click(screen.getByTestId('permissions-schema-toggle-public'))
    await screen.findByTestId('permissions-primary-public.products')

    await userEvent.selectOptions(screen.getByTestId('permissions-primary-public.products'), 'READ')

    await waitFor(() =>
      expect(permissionsApi.setPermission).toHaveBeenCalledWith(1, {
        principalType: 'USER',
        principalId: 42,
        schemaName: 'public',
        tableName: 'products',
        columnName: null,
        primaryPermission: 'READ',
        createPermission: false,
        deletePermission: false,
      }),
    )
  })

  it('「未設定」を選択すると解除APIを呼び出す', async () => {
    setupCommonMocks()
    const existingEntry: PermissionEntry = {
      principalType: 'USER',
      principalId: 42,
      schemaName: 'public',
      tableName: 'products',
      columnName: null,
      primaryPermission: 'READ',
      createPermission: false,
      deletePermission: false,
      updatedAt: '2026-01-01T00:00:00Z',
      updatedBy: 99,
    }
    vi.mocked(permissionsApi.listPermissions).mockResolvedValue([existingEntry])
    vi.mocked(permissionsApi.unsetPermission).mockResolvedValueOnce(undefined)

    renderTreePage()
    await userEvent.selectOptions(await screen.findByTestId('permissions-principal-select'), '42')
    await userEvent.click(screen.getByTestId('permissions-schema-toggle-public'))
    await screen.findByTestId('permissions-primary-public.products')

    await userEvent.selectOptions(screen.getByTestId('permissions-primary-public.products'), '')

    await waitFor(() =>
      expect(permissionsApi.unsetPermission).toHaveBeenCalledWith(1, {
        principalType: 'USER',
        principalId: 42,
        schemaName: 'public',
        tableName: 'products',
        columnName: null,
      }),
    )
  })

  it('YAMLエクスポートボタンでexportPermissionsを呼び出す', async () => {
    setupCommonMocks()
    vi.mocked(permissionsApi.listPermissions).mockResolvedValue([])
    vi.mocked(permissionsApi.exportPermissions).mockResolvedValueOnce('connectionId: 1\npermissions: []\n')
    // jsdomはURL.createObjectURL/anchor.click()を実装していないため、テスト内でスタブする
    URL.createObjectURL = vi.fn().mockReturnValue('blob:mock')
    URL.revokeObjectURL = vi.fn()
    HTMLAnchorElement.prototype.click = vi.fn()

    renderTreePage()
    await screen.findByTestId('permissions-export-button')
    await userEvent.click(screen.getByTestId('permissions-export-button'))

    await waitFor(() => expect(permissionsApi.exportPermissions).toHaveBeenCalledWith(1))
  })
})
