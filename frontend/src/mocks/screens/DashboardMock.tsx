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

import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  AppShell,
  Badge,
  Button,
  ConfirmDialog,
  DataTable,
  EmptyState,
  FilterBar,
  PageHeader,
  Pagination,
  Select,
  ToastProvider,
  useDefaultNavItems,
  useToast,
} from '../../design-system/components'
import type { TableColumn } from '../../design-system/components'
import { sampleUsers } from '../data/sample'
import type { SampleUser, UserStatus } from '../data/sample'

const statusTone = { pending: 'warning', active: 'success', disabled: 'neutral' } as const

function DashboardMockInner() {
  const { t } = useTranslation('design-system')
  const { showToast } = useToast()
  const navItems = useDefaultNavItems('dashboard')
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | UserStatus>('all')
  const [confirm, setConfirm] = useState<{ user: SampleUser; kind: 'approve' | 'reject' } | null>(
    null,
  )
  const [page, setPage] = useState(1)

  const statusLabel: Record<UserStatus, string> = {
    pending: t('mock.dashboard.statusPending'),
    active: t('mock.dashboard.statusActive'),
    disabled: t('mock.dashboard.statusDisabled'),
  }

  const rows = useMemo(
    () =>
      sampleUsers.filter(
        (user) =>
          (statusFilter === 'all' || user.status === statusFilter) &&
          (keyword === '' || user.email.includes(keyword) || user.displayName.includes(keyword)),
      ),
    [keyword, statusFilter],
  )

  const columns: readonly TableColumn<SampleUser>[] = [
    {
      key: 'email',
      header: t('mock.dashboard.email'),
      render: (user) => user.email,
      sortable: true,
    },
    {
      key: 'displayName',
      header: t('mock.dashboard.displayName'),
      render: (user) => user.displayName,
    },
    {
      key: 'status',
      header: t('mock.dashboard.status'),
      render: (user) => <Badge tone={statusTone[user.status]}>{statusLabel[user.status]}</Badge>,
    },
    {
      key: 'registeredAt',
      header: t('mock.dashboard.registeredAt'),
      render: (user) => user.registeredAt,
      sortable: true,
    },
    {
      key: 'actions',
      header: t('mock.dashboard.actions'),
      render: (user) =>
        user.status === 'pending' ? (
          <>
            <Button
              size="sm"
              variant="primary"
              onClick={() => setConfirm({ user, kind: 'approve' })}
              data-testid={`dashboard-mock-approve-${user.id}`}
            >
              {t('action.approve', { ns: 'common' })}
            </Button>{' '}
            <Button
              size="sm"
              variant="danger"
              onClick={() => setConfirm({ user, kind: 'reject' })}
              data-testid={`dashboard-mock-reject-${user.id}`}
            >
              {t('action.reject', { ns: 'common' })}
            </Button>
          </>
        ) : null,
    },
  ]

  return (
    <AppShell navItems={navItems}>
      <PageHeader title={t('mock.dashboard.title')} />
      <FilterBar searchValue={keyword} onSearchChange={setKeyword}>
        <Select
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as 'all' | UserStatus)}
        >
          <option value="all">{t('mock.dashboard.statusAll')}</option>
          <option value="pending">{t('mock.dashboard.statusPending')}</option>
          <option value="active">{t('mock.dashboard.statusActive')}</option>
          <option value="disabled">{t('mock.dashboard.statusDisabled')}</option>
        </Select>
      </FilterBar>
      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(user) => user.id}
        emptyState={
          <EmptyState
            action={
              <Button
                size="sm"
                onClick={() => {
                  setKeyword('')
                  setStatusFilter('all')
                }}
              >
                {t('action.clear', { ns: 'common' })}
              </Button>
            }
          />
        }
      />
      <div style={{ marginTop: 'var(--mm-space-3)', display: 'flex', justifyContent: 'flex-end' }}>
        <Pagination page={page} totalPages={2} onChange={setPage} />
      </div>
      <ConfirmDialog
        open={confirm !== null}
        title={
          confirm?.kind === 'approve'
            ? t('mock.dashboard.approveTitle')
            : t('mock.dashboard.rejectTitle')
        }
        message={`${confirm?.user.displayName ?? ''}（${confirm?.user.email ?? ''}）${
          confirm?.kind === 'approve'
            ? t('mock.dashboard.approveMessage')
            : t('mock.dashboard.rejectMessage')
        }`}
        tone={confirm?.kind === 'reject' ? 'danger' : 'default'}
        confirmLabel={
          confirm?.kind === 'approve'
            ? t('action.approve', { ns: 'common' })
            : t('action.reject', { ns: 'common' })
        }
        onConfirm={() => {
          if (confirm) {
            showToast(
              confirm.kind === 'approve' ? 'success' : 'info',
              `${confirm.user.displayName}${
                confirm.kind === 'approve'
                  ? t('mock.dashboard.approved')
                  : t('mock.dashboard.rejected')
              }`,
            )
          }
          setConfirm(null)
        }}
        onCancel={() => setConfirm(null)}
      />
    </AppShell>
  )
}

export function DashboardMock() {
  return (
    <ToastProvider>
      <DashboardMockInner />
    </ToastProvider>
  )
}
