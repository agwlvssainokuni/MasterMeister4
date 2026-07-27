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

import { useCallback, useEffect, useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, DataTable, PageHeader, Pagination, Select, TextInput } from '../design-system/components'
import type { TableColumn } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { AUDIT_EVENT_TYPES, listAuditLog } from '../api/auditLog'
import type { AuditEventType, AuditLogEntry, ResultStatus } from '../api/auditLog'
import { listUsers } from '../api/adminUsers'
import type { UserSummary } from '../api/adminUsers'
import { listConnections } from '../api/rdbmsConnections'
import type { RdbmsConnectionSummary } from '../api/rdbmsConnections'
import { ApiError } from '../api/http'
import styles from './AuditLogPage.module.css'

const DEFAULT_PAGE_SIZE = 50

/**
 * frontend-components.md。単一の監査ログ一覧画面（BR-AUDITVIEW-02）。管理者専用エンドポイント
 * （/api/admin/audit-log）を利用するが、フロントエンド側に独自のロール判定・ガードは設けない
 * （既存のGroupManagementPage等と同じ方針、アクセス制御はバックエンドの403に委ねる）。
 * 画面遷移導線は設けない（BR-AUDITVIEW-11）。
 */
export function AuditLogPage() {
  const { t } = useTranslation()

  const occurredAtFromId = useId()
  const occurredAtToId = useId()
  const eventTypeId = useId()
  const userIdId = useId()
  const connectionIdId = useId()
  const resultStatusId = useId()

  const [occurredAtFrom, setOccurredAtFrom] = useState('')
  const [occurredAtTo, setOccurredAtTo] = useState('')
  const [eventType, setEventType] = useState<AuditEventType | ''>('')
  const [userId, setUserId] = useState('')
  const [connectionId, setConnectionId] = useState('')
  const [resultStatus, setResultStatus] = useState<ResultStatus | ''>('')

  const [users, setUsers] = useState<UserSummary[]>([])
  const [connections, setConnections] = useState<RdbmsConnectionSummary[]>([])

  const [entries, setEntries] = useState<AuditLogEntry[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    listUsers()
      .then(setUsers)
      .catch((error) => setErrorMessage(error instanceof ApiError ? error.message : t('state.error')))
    listConnections()
      .then(setConnections)
      .catch((error) => setErrorMessage(error instanceof ApiError ? error.message : t('state.error')))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    try {
      const result = await listAuditLog({
        occurredAtFrom: occurredAtFrom ? new Date(occurredAtFrom).toISOString() : undefined,
        occurredAtTo: occurredAtTo ? new Date(occurredAtTo).toISOString() : undefined,
        eventType: eventType || undefined,
        userId: userId ? Number(userId) : undefined,
        connectionId: connectionId ? Number(connectionId) : undefined,
        resultStatus: resultStatus || undefined,
        page,
        pageSize: DEFAULT_PAGE_SIZE,
      })
      setEntries(result.content)
      setTotalPages(Math.max(result.totalPages, 1))
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setLoading(false)
    }
  }, [occurredAtFrom, occurredAtTo, eventType, userId, connectionId, resultStatus, page, t])

  useEffect(() => {
    void load()
  }, [load])

  const onFilterChange = (apply: () => void) => {
    apply()
    setPage(0)
  }

  const columns: readonly TableColumn<AuditLogEntry>[] = [
    {
      key: 'occurredAt',
      header: t('auditLog.column.occurredAt'),
      render: (e) => new Date(e.occurredAt).toLocaleString(),
    },
    {
      key: 'eventType',
      header: t('auditLog.column.eventType'),
      render: (e) => t(`auditLog.eventType.${e.eventType}`),
    },
    {
      key: 'userDisplayName',
      header: t('auditLog.column.user'),
      render: (e) => e.userDisplayName ?? '-',
    },
    {
      key: 'connectionDisplayName',
      header: t('auditLog.column.connection'),
      render: (e) => e.connectionDisplayName ?? '-',
    },
    {
      key: 'targetResource',
      header: t('auditLog.column.targetResource'),
      render: (e) => e.targetResource ?? '-',
    },
    {
      key: 'resultStatus',
      header: t('auditLog.column.resultStatus'),
      render: (e) => t(`auditLog.resultStatus.${e.resultStatus}`),
    },
    {
      key: 'detail',
      header: t('auditLog.column.detail'),
      render: (e) => e.detail ?? '-',
    },
  ]

  return (
    <AuthenticatedLayout activeNavKey="auditLog">
      <PageHeader title={t('auditLog.title')} />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      <div className={styles.filterList}>
        <div className={styles.filterRow}>
          <label htmlFor={occurredAtFromId} className={styles.filterLabel}>
            {t('auditLog.filter.occurredAtFrom')}
          </label>
          <TextInput
            id={occurredAtFromId}
            type="datetime-local"
            value={occurredAtFrom}
            onChange={(e) => onFilterChange(() => setOccurredAtFrom(e.target.value))}
          />
        </div>
        <div className={styles.filterRow}>
          <label htmlFor={occurredAtToId} className={styles.filterLabel}>
            {t('auditLog.filter.occurredAtTo')}
          </label>
          <TextInput
            id={occurredAtToId}
            type="datetime-local"
            value={occurredAtTo}
            onChange={(e) => onFilterChange(() => setOccurredAtTo(e.target.value))}
          />
        </div>
        <div className={styles.filterRow}>
          <label htmlFor={eventTypeId} className={styles.filterLabel}>
            {t('auditLog.filter.eventType')}
          </label>
          <Select
            id={eventTypeId}
            value={eventType}
            onChange={(e) => onFilterChange(() => setEventType(e.target.value as AuditEventType | ''))}
          >
            <option value="">{t('auditLog.selectPlaceholder')}</option>
            {AUDIT_EVENT_TYPES.map((et) => (
              <option key={et} value={et}>
                {t(`auditLog.eventType.${et}`)}
              </option>
            ))}
          </Select>
        </div>
        <div className={styles.filterRow}>
          <label htmlFor={userIdId} className={styles.filterLabel}>
            {t('auditLog.filter.user')}
          </label>
          <Select id={userIdId} value={userId} onChange={(e) => onFilterChange(() => setUserId(e.target.value))}>
            <option value="">{t('auditLog.selectPlaceholder')}</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName}
              </option>
            ))}
          </Select>
        </div>
        <div className={styles.filterRow}>
          <label htmlFor={connectionIdId} className={styles.filterLabel}>
            {t('auditLog.filter.connection')}
          </label>
          <Select
            id={connectionIdId}
            value={connectionId}
            onChange={(e) => onFilterChange(() => setConnectionId(e.target.value))}
          >
            <option value="">{t('auditLog.selectPlaceholder')}</option>
            {connections.map((c) => (
              <option key={c.id} value={c.id}>
                {c.displayName}
              </option>
            ))}
          </Select>
        </div>
        <div className={styles.filterRow}>
          <label htmlFor={resultStatusId} className={styles.filterLabel}>
            {t('auditLog.filter.resultStatus')}
          </label>
          <Select
            id={resultStatusId}
            value={resultStatus}
            onChange={(e) => onFilterChange(() => setResultStatus(e.target.value as ResultStatus | ''))}
          >
            <option value="">{t('auditLog.selectPlaceholder')}</option>
            <option value="SUCCESS">{t('auditLog.resultStatus.SUCCESS')}</option>
            <option value="FAILURE">{t('auditLog.resultStatus.FAILURE')}</option>
          </Select>
        </div>
      </div>
      <DataTable columns={columns} rows={entries} rowKey={(e) => String(e.id)} loading={loading} />
      <Pagination page={page + 1} totalPages={totalPages} onChange={(p) => setPage(p - 1)} />
    </AuthenticatedLayout>
  )
}
