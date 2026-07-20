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

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  AppShell,
  Badge,
  Button,
  Checkbox,
  DataTable,
  EmptyState,
  FormField,
  Modal,
  PageHeader,
  Select,
  ToastProvider,
  useDefaultNavItems,
  useToast,
} from '../../design-system/components'
import type { TableColumn } from '../../design-system/components'
import { samplePermissions } from '../data/sample'
import type { SamplePermission } from '../data/sample'

// 権限設定画面モック。UNIT-04で正式設計するアクセス権限モデル（主権限・補助権限）は
// 先取りせず、一般的な設定画面レイアウト（一覧テーブル＋編集フォーム）として表現する（Q8=A）。
function PermissionsMockInner() {
  const { t } = useTranslation('design-system')
  const { showToast } = useToast()
  const navItems = useDefaultNavItems('accessControl')
  const [editing, setEditing] = useState<SamplePermission | null>(null)
  const [rows, setRows] = useState<readonly SamplePermission[]>(samplePermissions)

  const columns: readonly TableColumn<SamplePermission>[] = [
    { key: 'principal', header: t('mock.permissions.principal'), render: (row) => row.principal },
    { key: 'resource', header: t('mock.permissions.resource'), render: (row) => row.resource },
    {
      key: 'primary',
      header: t('mock.permissions.primary'),
      render: (row) => (
        <Badge tone={row.primary === 'UPDATE' ? 'primary' : 'neutral'}>{row.primary}</Badge>
      ),
    },
    {
      key: 'auxiliary',
      header: t('mock.permissions.auxiliary'),
      render: (row) =>
        row.auxiliary.length > 0 ? (
          <>
            {row.auxiliary.map((a) => (
              <Badge key={a} tone="success">
                {a}
              </Badge>
            ))}
          </>
        ) : (
          '-'
        ),
    },
    {
      key: 'actions',
      header: t('mock.permissions.edit'),
      render: (row) => (
        <Button
          size="sm"
          onClick={() => setEditing(row)}
          data-testid={`permissions-mock-edit-${row.id}`}
        >
          {t('action.edit', { ns: 'common' })}
        </Button>
      ),
    },
  ]

  const onSave = () => {
    if (!editing) {
      return
    }
    setRows((current) => current.map((row) => (row.id === editing.id ? editing : row)))
    setEditing(null)
    showToast('success', t('mock.permissions.saved'))
  }

  return (
    <AppShell navItems={navItems}>
      <PageHeader title={t('mock.permissions.title')} />
      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(row) => row.id}
        emptyState={<EmptyState />}
      />
      <Modal
        open={editing !== null}
        title={t('mock.permissions.editTitle')}
        onClose={() => setEditing(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)}>
              {t('action.cancel', { ns: 'common' })}
            </Button>
            <Button variant="primary" onClick={onSave} data-testid="permissions-mock-save-button">
              {t('action.save', { ns: 'common' })}
            </Button>
          </>
        }
      >
        {editing ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--mm-space-3)' }}>
            <FormField label={t('mock.permissions.primary')}>
              <Select
                value={editing.primary}
                onChange={(event) =>
                  setEditing({
                    ...editing,
                    primary: event.target.value as SamplePermission['primary'],
                  })
                }
              >
                <option value="NONE">NONE</option>
                <option value="READ">READ</option>
                <option value="UPDATE">UPDATE</option>
              </Select>
            </FormField>
            <FormField label={t('mock.permissions.auxiliary')}>
              <div style={{ display: 'flex', gap: 'var(--mm-space-4)' }}>
                <Checkbox
                  label="CREATE"
                  checked={editing.auxiliary.includes('CREATE')}
                  onChange={(event) =>
                    setEditing({
                      ...editing,
                      auxiliary: event.target.checked
                        ? [...editing.auxiliary, 'CREATE']
                        : editing.auxiliary.filter((a) => a !== 'CREATE'),
                    })
                  }
                />
                <Checkbox
                  label="DELETE"
                  checked={editing.auxiliary.includes('DELETE')}
                  onChange={(event) =>
                    setEditing({
                      ...editing,
                      auxiliary: event.target.checked
                        ? [...editing.auxiliary, 'DELETE']
                        : editing.auxiliary.filter((a) => a !== 'DELETE'),
                    })
                  }
                />
              </div>
            </FormField>
          </div>
        ) : null}
      </Modal>
    </AppShell>
  )
}

export function PermissionsMock() {
  return (
    <ToastProvider>
      <PermissionsMockInner />
    </ToastProvider>
  )
}
