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

import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert,
  Badge,
  Button,
  ConfirmDialog,
  DataTable,
  EmptyState,
  FormField,
  Modal,
  PageHeader,
  Select,
  Spinner,
  TextInput,
} from '../design-system/components'
import type { TableColumn } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import {
  addMember,
  createGroup,
  deleteGroup,
  listGroups,
  listMembers,
  removeMember,
  renameGroup,
} from '../api/groups'
import type { GroupMember, GroupSummary } from '../api/groups'
import { listUsers } from '../api/adminUsers'
import type { UserSummary } from '../api/adminUsers'
import { ApiError } from '../api/http'

// frontend-components.md §1。/groups（UserManagementPage同様の「ナビ項目＝管理対象エンティティ名」
// 規約に合わせたパス）。
export function GroupManagementPage() {
  const { t } = useTranslation()
  const [groups, setGroups] = useState<GroupSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(
    null,
  )
  const [formModal, setFormModal] = useState<{ mode: 'create' | 'rename'; groupId?: number; name: string } | null>(
    null,
  )
  const [formSubmitting, setFormSubmitting] = useState(false)
  const [membershipModal, setMembershipModal] = useState<{ groupId: number; groupName: string } | null>(null)
  const [members, setMembers] = useState<GroupMember[]>([])
  const [membersLoading, setMembersLoading] = useState(false)
  const [candidateUsers, setCandidateUsers] = useState<UserSummary[]>([])
  const [selectedUserId, setSelectedUserId] = useState<string>('')
  const [confirmDeleteTarget, setConfirmDeleteTarget] = useState<number | null>(null)

  const loadGroups = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    try {
      setGroups(await listGroups())
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    void loadGroups()
  }, [loadGroups])

  const openCreateForm = () => setFormModal({ mode: 'create', name: '' })
  const openRenameForm = (group: GroupSummary) => setFormModal({ mode: 'rename', groupId: group.id, name: group.name })

  const onFormSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!formModal) {
      return
    }
    setFormSubmitting(true)
    try {
      if (formModal.mode === 'create') {
        await createGroup(formModal.name)
      } else if (formModal.groupId !== undefined) {
        await renameGroup(formModal.groupId, formModal.name)
      }
      setFormModal(null)
      await loadGroups()
    } catch (error) {
      setActionMessage({ tone: 'danger', text: error instanceof ApiError ? error.message : t('state.error') })
    } finally {
      setFormSubmitting(false)
    }
  }

  const onDeleteConfirm = async () => {
    if (confirmDeleteTarget === null) {
      return
    }
    try {
      await deleteGroup(confirmDeleteTarget)
      await loadGroups()
    } catch (error) {
      setActionMessage({ tone: 'danger', text: error instanceof ApiError ? error.message : t('state.error') })
    } finally {
      setConfirmDeleteTarget(null)
    }
  }

  const openMembershipModal = async (group: GroupSummary) => {
    setMembershipModal({ groupId: group.id, groupName: group.name })
    setMembersLoading(true)
    try {
      const [memberList, userList] = await Promise.all([listMembers(group.id), listUsers('APPROVED')])
      setMembers(memberList)
      setCandidateUsers(userList)
    } catch (error) {
      setActionMessage({ tone: 'danger', text: error instanceof ApiError ? error.message : t('state.error') })
    } finally {
      setMembersLoading(false)
    }
  }

  const onAddMember = async () => {
    if (!membershipModal || selectedUserId === '') {
      return
    }
    try {
      await addMember(membershipModal.groupId, Number(selectedUserId))
      setMembers(await listMembers(membershipModal.groupId))
      setSelectedUserId('')
      await loadGroups()
    } catch (error) {
      setActionMessage({ tone: 'danger', text: error instanceof ApiError ? error.message : t('state.error') })
    }
  }

  const onRemoveMember = async (userId: number) => {
    if (!membershipModal) {
      return
    }
    try {
      await removeMember(membershipModal.groupId, userId)
      setMembers(await listMembers(membershipModal.groupId))
      await loadGroups()
    } catch (error) {
      setActionMessage({ tone: 'danger', text: error instanceof ApiError ? error.message : t('state.error') })
    }
  }

  const availableCandidates = candidateUsers.filter(
    (user) => !members.some((member) => member.userId === user.id),
  )

  const columns: readonly TableColumn<GroupSummary>[] = [
    { key: 'name', header: t('groups.name'), render: (g) => g.name },
    {
      key: 'memberCount',
      header: t('groups.memberCount'),
      render: (g) => <Badge tone="neutral">{g.memberCount}</Badge>,
    },
    {
      key: 'actions',
      header: t('groups.actions'),
      render: (g) => (
        <>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => void openMembershipModal(g)}
            data-testid={`groups-members-${g.id}`}
          >
            {t('groups.manageMembers')}
          </Button>{' '}
          <Button
            size="sm"
            variant="ghost"
            onClick={() => openRenameForm(g)}
            data-testid={`groups-rename-${g.id}`}
          >
            {t('action.edit')}
          </Button>{' '}
          <Button
            size="sm"
            variant="danger"
            onClick={() => setConfirmDeleteTarget(g.id)}
            data-testid={`groups-delete-${g.id}`}
          >
            {t('action.delete')}
          </Button>
        </>
      ),
    },
  ]

  return (
    <AuthenticatedLayout activeNavKey="groups">
      <PageHeader
        title={t('groups.title')}
        actions={
          <Button variant="primary" onClick={openCreateForm} data-testid="groups-add-button">
            {t('groups.addButton')}
          </Button>
        }
      />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      {actionMessage ? <Alert tone={actionMessage.tone}>{actionMessage.text}</Alert> : null}
      {loading ? (
        <Spinner />
      ) : (
        <DataTable
          columns={columns}
          rows={groups}
          rowKey={(g) => String(g.id)}
          emptyState={<EmptyState message={t('groups.empty')} />}
        />
      )}

      <Modal
        open={formModal !== null}
        title={formModal?.mode === 'create' ? t('groups.formTitleCreate') : t('groups.formTitleRename')}
        onClose={() => setFormModal(null)}
      >
        {formModal ? (
          <form
            onSubmit={(event) => void onFormSubmit(event)}
            style={{ display: 'flex', flexDirection: 'column', gap: 'var(--mm-space-3)' }}
          >
            <FormField label={t('groups.name')} required>
              <TextInput
                value={formModal.name}
                onChange={(event) => setFormModal({ ...formModal, name: event.target.value })}
                required
                data-testid="groups-form-name"
              />
            </FormField>
            <Button type="submit" variant="primary" loading={formSubmitting} data-testid="groups-form-submit">
              {formModal.mode === 'create' ? t('groups.submitCreate') : t('groups.submitRename')}
            </Button>
          </form>
        ) : null}
      </Modal>

      <Modal
        open={membershipModal !== null}
        title={t('groups.membersTitle', { name: membershipModal?.groupName ?? '' })}
        onClose={() => setMembershipModal(null)}
      >
        {membershipModal ? (
          membersLoading ? (
            <Spinner />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--mm-space-3)' }}>
              <ul>
                {members.map((member) => (
                  <li key={member.userId}>
                    {member.fullName}（{member.email}）{' '}
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => void onRemoveMember(member.userId)}
                      data-testid={`groups-member-remove-${member.userId}`}
                    >
                      {t('action.delete')}
                    </Button>
                  </li>
                ))}
              </ul>
              <FormField label={t('groups.addMember')}>
                <Select
                  value={selectedUserId}
                  onChange={(event) => setSelectedUserId(event.target.value)}
                  data-testid="groups-member-select"
                >
                  <option value="">{t('groups.selectUser')}</option>
                  {availableCandidates.map((user) => (
                    <option key={user.id} value={user.id}>
                      {user.fullName}（{user.email}）
                    </option>
                  ))}
                </Select>
              </FormField>
              <Button
                variant="primary"
                disabled={selectedUserId === ''}
                onClick={() => void onAddMember()}
                data-testid="groups-member-add-button"
              >
                {t('action.add')}
              </Button>
            </div>
          )
        ) : null}
      </Modal>

      <ConfirmDialog
        open={confirmDeleteTarget !== null}
        title={t('groups.confirmDeleteTitle')}
        message={t('groups.confirmDeleteMessage')}
        tone="danger"
        confirmLabel={t('action.delete')}
        onConfirm={() => void onDeleteConfirm()}
        onCancel={() => setConfirmDeleteTarget(null)}
      />
    </AuthenticatedLayout>
  )
}
