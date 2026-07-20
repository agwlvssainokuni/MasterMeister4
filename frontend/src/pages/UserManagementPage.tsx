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

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert,
  Badge,
  Button,
  ConfirmDialog,
  DataTable,
  EmptyState,
  FilterBar,
  PageHeader,
  Select,
  Spinner,
} from '../design-system/components'
import type { BadgeTone, TableColumn } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { approveUser, disableUser, enableUser, listUsers, rejectUser } from '../api/adminUsers'
import type { UserStatus, UserSummary } from '../api/adminUsers'
import { ApiError } from '../api/http'

type StatusFilter = 'ALL' | UserStatus
type ConfirmAction = 'approve' | 'reject' | 'disable' | 'enable'

const actionByName: Record<ConfirmAction, (id: number) => Promise<void>> = {
  approve: approveUser,
  reject: rejectUser,
  disable: disableUser,
  enable: enableUser,
}

// frontend-components.md §5。承認/却下/無効化/再有効化を単一の画面から扱う（レビュー指摘の反映）。
// 初期表示のステータスフィルタはPENDING。
export function UserManagementPage() {
  const { t } = useTranslation()
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('PENDING')
  const [keyword, setKeyword] = useState('')
  const [users, setUsers] = useState<UserSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [confirmTarget, setConfirmTarget] = useState<{ id: number; action: ConfirmAction } | null>(
    null,
  )

  const statusLabel: Record<UserStatus, string> = {
    PENDING: t('users.statusPending'),
    APPROVED: t('users.statusApproved'),
    REJECTED: t('users.statusRejected'),
    DISABLED: t('users.statusDisabled'),
  }
  const statusTone: Record<UserStatus, BadgeTone> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    DISABLED: 'neutral',
  }
  const confirmCopy: Record<ConfirmAction, { title: string; message: string }> = {
    approve: { title: t('users.confirmApproveTitle'), message: t('users.confirmApproveMessage') },
    reject: { title: t('users.confirmRejectTitle'), message: t('users.confirmRejectMessage') },
    disable: { title: t('users.confirmDisableTitle'), message: t('users.confirmDisableMessage') },
    enable: { title: t('users.confirmEnableTitle'), message: t('users.confirmEnableMessage') },
  }

  const loadUsers = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    try {
      const result = await listUsers(statusFilter === 'ALL' ? undefined : statusFilter)
      setUsers(result)
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setLoading(false)
    }
  }, [statusFilter, t])

  useEffect(() => {
    void loadUsers()
  }, [loadUsers])

  const rows = useMemo(
    () =>
      users.filter(
        (user) =>
          keyword === '' ||
          user.email.toLowerCase().includes(keyword.toLowerCase()) ||
          user.fullName.toLowerCase().includes(keyword.toLowerCase()),
      ),
    [users, keyword],
  )

  const onConfirm = async () => {
    if (!confirmTarget) {
      return
    }
    try {
      await actionByName[confirmTarget.action](confirmTarget.id)
      await loadUsers()
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setConfirmTarget(null)
    }
  }

  const columns: readonly TableColumn<UserSummary>[] = [
    { key: 'fullName', header: t('users.fullName'), render: (user) => user.fullName },
    { key: 'email', header: t('users.email'), render: (user) => user.email },
    {
      key: 'status',
      header: t('users.status'),
      render: (user) => <Badge tone={statusTone[user.status]}>{statusLabel[user.status]}</Badge>,
    },
    {
      key: 'createdAt',
      header: t('users.createdAt'),
      render: (user) => new Date(user.createdAt).toLocaleString(),
    },
    {
      key: 'actions',
      header: t('users.actions'),
      render: (user) => {
        if (user.status === 'PENDING') {
          return (
            <>
              <Button
                size="sm"
                variant="primary"
                onClick={() => setConfirmTarget({ id: user.id, action: 'approve' })}
                data-testid={`users-approve-${user.id}`}
              >
                {t('users.approve')}
              </Button>{' '}
              <Button
                size="sm"
                variant="danger"
                onClick={() => setConfirmTarget({ id: user.id, action: 'reject' })}
                data-testid={`users-reject-${user.id}`}
              >
                {t('users.reject')}
              </Button>
            </>
          )
        }
        if (user.status === 'APPROVED') {
          return (
            <Button
              size="sm"
              variant="danger"
              onClick={() => setConfirmTarget({ id: user.id, action: 'disable' })}
              data-testid={`users-disable-${user.id}`}
            >
              {t('users.disable')}
            </Button>
          )
        }
        if (user.status === 'REJECTED') {
          return (
            <Button
              size="sm"
              variant="primary"
              onClick={() => setConfirmTarget({ id: user.id, action: 'approve' })}
              data-testid={`users-approve-${user.id}`}
            >
              {t('users.approve')}
            </Button>
          )
        }
        return (
          <Button
            size="sm"
            variant="primary"
            onClick={() => setConfirmTarget({ id: user.id, action: 'enable' })}
            data-testid={`users-enable-${user.id}`}
          >
            {t('users.enable')}
          </Button>
        )
      },
    },
  ]

  return (
    <AuthenticatedLayout activeNavKey="users">
      <PageHeader title={t('users.title')} />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      <FilterBar searchValue={keyword} onSearchChange={setKeyword}>
        <Select
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
          data-testid="users-status-filter"
        >
          <option value="ALL">{t('users.filterAll')}</option>
          <option value="PENDING">{t('users.statusPending')}</option>
          <option value="APPROVED">{t('users.statusApproved')}</option>
          <option value="REJECTED">{t('users.statusRejected')}</option>
          <option value="DISABLED">{t('users.statusDisabled')}</option>
        </Select>
      </FilterBar>
      {loading ? (
        <Spinner />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          rowKey={(user) => String(user.id)}
          emptyState={<EmptyState message={t('users.empty')} />}
        />
      )}
      <ConfirmDialog
        open={confirmTarget !== null}
        title={confirmTarget ? confirmCopy[confirmTarget.action].title : ''}
        message={confirmTarget ? confirmCopy[confirmTarget.action].message : ''}
        tone={
          confirmTarget?.action === 'reject' || confirmTarget?.action === 'disable'
            ? 'danger'
            : 'default'
        }
        confirmLabel={confirmTarget ? t(`users.${confirmTarget.action}`) : undefined}
        onConfirm={() => void onConfirm()}
        onCancel={() => setConfirmTarget(null)}
      />
    </AuthenticatedLayout>
  )
}
