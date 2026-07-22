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
import { Link } from 'react-router-dom'
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
  PasswordInput,
  Select,
  Spinner,
  TextInput,
} from '../design-system/components'
import type { BadgeTone, TableColumn } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import {
  DEFAULT_PORT_BY_DB_TYPE,
  deleteConnection,
  listConnections,
  refreshSchema,
  registerConnection,
  testConnection,
  testConnectionUnsaved,
  updateConnection,
} from '../api/rdbmsConnections'
import type {
  ConnectionErrorCategory,
  DbType,
  RdbmsConnectionInput,
  RdbmsConnectionSummary,
} from '../api/rdbmsConnections'
import { ApiError } from '../api/http'

interface FormValues {
  displayName: string
  dbType: DbType
  host: string
  port: string
  databaseName: string
  username: string
  password: string
  additionalParams: string
}

const EMPTY_FORM: FormValues = {
  displayName: '',
  dbType: 'MYSQL',
  host: '',
  port: '',
  databaseName: '',
  username: '',
  password: '',
  additionalParams: '',
}

function toInput(values: FormValues): RdbmsConnectionInput {
  return {
    displayName: values.displayName,
    dbType: values.dbType,
    host: values.host,
    port: Number(values.port),
    databaseName: values.databaseName,
    username: values.username,
    password: values.password === '' ? undefined : values.password,
    additionalParams: values.additionalParams === '' ? null : values.additionalParams,
  }
}

// frontend-components.md §1。UNIT-01のナビゲーション項目「RDBMS接続設定」（key: connections、
// design-system/components/navigation.tsで予約済み）に対応する画面。
export function RdbmsConnectionListPage() {
  const { t } = useTranslation()
  const [connections, setConnections] = useState<RdbmsConnectionSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(
    null,
  )
  const [formModal, setFormModal] = useState<
    { mode: 'create' | 'edit'; connectionId?: number; values: FormValues } | null
  >(null)
  const [formSubmitting, setFormSubmitting] = useState(false)
  const [formTestResult, setFormTestResult] = useState<{ success: boolean; message: string } | null>(
    null,
  )
  const [formTesting, setFormTesting] = useState(false)
  const [confirmDeleteTarget, setConfirmDeleteTarget] = useState<number | null>(null)
  const [busyConnectionId, setBusyConnectionId] = useState<number | null>(null)

  const loadConnections = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    try {
      setConnections(await listConnections())
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    void loadConnections()
  }, [loadConnections])

  const errorCategoryMessage = (category: ConnectionErrorCategory | null): string => {
    switch (category) {
      case 'CONNECTION_UNREACHABLE':
        return t('connections.testFailureConnectionUnreachable')
      case 'AUTH_ERROR':
        return t('connections.testFailureAuthError')
      case 'TIMEOUT':
        return t('connections.testFailureTimeout')
      default:
        return t('connections.testFailureOther')
    }
  }

  const openCreateForm = () => {
    setFormTestResult(null)
    setFormModal({ mode: 'create', values: EMPTY_FORM })
  }

  const openEditForm = (connection: RdbmsConnectionSummary) => {
    setFormTestResult(null)
    setFormModal({
      mode: 'edit',
      connectionId: connection.id,
      values: {
        displayName: connection.displayName,
        dbType: connection.dbType,
        host: connection.host,
        port: String(connection.port),
        databaseName: connection.databaseName,
        username: connection.username,
        password: '',
        additionalParams: connection.additionalParams ?? '',
      },
    })
  }

  const onDbTypeChange = (dbType: DbType) => {
    setFormModal((current) => {
      if (!current) {
        return current
      }
      // 新規登録時、portが未入力の場合のみデフォルト値を自動入力する（編集時の既存値は上書きしない）
      const shouldFillPort = current.mode === 'create' && current.values.port === ''
      return {
        ...current,
        values: {
          ...current.values,
          dbType,
          port: shouldFillPort ? String(DEFAULT_PORT_BY_DB_TYPE[dbType]) : current.values.port,
        },
      }
    })
  }

  const onFormFieldChange = (field: keyof FormValues, value: string) => {
    setFormModal((current) =>
      current ? { ...current, values: { ...current.values, [field]: value } } : current,
    )
  }

  const onTestInForm = async () => {
    if (!formModal) {
      return
    }
    setFormTesting(true)
    setFormTestResult(null)
    try {
      const result = await testConnectionUnsaved(toInput(formModal.values))
      setFormTestResult({
        success: result.success,
        message: result.success ? t('connections.testSuccess') : errorCategoryMessage(result.errorCategory),
      })
    } catch (error) {
      setFormTestResult({
        success: false,
        message: error instanceof ApiError ? error.message : t('state.error'),
      })
    } finally {
      setFormTesting(false)
    }
  }

  const onFormSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!formModal) {
      return
    }
    setFormSubmitting(true)
    try {
      const input = toInput(formModal.values)
      if (formModal.mode === 'create') {
        await registerConnection(input)
      } else if (formModal.connectionId !== undefined) {
        await updateConnection(formModal.connectionId, input)
      }
      setFormModal(null)
      await loadConnections()
    } catch (error) {
      setFormTestResult({
        success: false,
        message: error instanceof ApiError ? error.message : t('state.error'),
      })
    } finally {
      setFormSubmitting(false)
    }
  }

  const onRowTest = async (connection: RdbmsConnectionSummary) => {
    setBusyConnectionId(connection.id)
    setActionMessage(null)
    try {
      const result = await testConnection(connection.id)
      setActionMessage({
        tone: result.success ? 'success' : 'danger',
        text: result.success ? t('connections.testSuccess') : errorCategoryMessage(result.errorCategory),
      })
    } catch (error) {
      setActionMessage({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : t('state.error'),
      })
    } finally {
      setBusyConnectionId(null)
    }
  }

  const onRowSchemaRefresh = async (connection: RdbmsConnectionSummary) => {
    setBusyConnectionId(connection.id)
    setActionMessage(null)
    try {
      await refreshSchema(connection.id)
      setActionMessage({ tone: 'success', text: t('connections.schemaRefreshSuccess') })
      await loadConnections()
    } catch (error) {
      setActionMessage({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : t('connections.schemaRefreshFailure'),
      })
    } finally {
      setBusyConnectionId(null)
    }
  }

  const onDeleteConfirm = async () => {
    if (confirmDeleteTarget === null) {
      return
    }
    try {
      await deleteConnection(confirmDeleteTarget)
      await loadConnections()
    } catch (error) {
      setActionMessage({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : t('state.error'),
      })
    } finally {
      setConfirmDeleteTarget(null)
    }
  }

  const columns: readonly TableColumn<RdbmsConnectionSummary>[] = [
    { key: 'displayName', header: t('connections.displayName'), render: (c) => c.displayName },
    { key: 'dbType', header: t('connections.dbType'), render: (c) => c.dbType },
    {
      key: 'connectionInfo',
      header: t('connections.connectionInfo'),
      render: (c) => `${c.host}:${c.port}/${c.databaseName}`,
    },
    {
      key: 'schemaStatus',
      header: t('connections.schemaStatus'),
      render: (c) => {
        const tone: BadgeTone = c.schemaImportedAt ? 'success' : 'neutral'
        const label = c.schemaImportedAt
          ? new Date(c.schemaImportedAt).toLocaleString()
          : t('connections.schemaNotImported')
        return <Badge tone={tone}>{label}</Badge>
      },
    },
    {
      key: 'actions',
      header: t('connections.actions'),
      render: (c) => (
        <>
          <Button
            size="sm"
            variant="ghost"
            disabled={busyConnectionId === c.id}
            onClick={() => void onRowTest(c)}
            data-testid={`connections-test-${c.id}`}
          >
            {t('connections.test')}
          </Button>{' '}
          <Button
            size="sm"
            variant="ghost"
            disabled={busyConnectionId === c.id}
            onClick={() => void onRowSchemaRefresh(c)}
            data-testid={`connections-schema-refresh-${c.id}`}
          >
            {t('connections.schemaRefresh')}
          </Button>{' '}
          {c.schemaImportedAt ? (
            <Link to={`/connections/${c.id}/schema`} data-testid={`connections-schema-detail-${c.id}`}>
              {t('connections.schemaDetail')}
            </Link>
          ) : null}{' '}
          <Button
            size="sm"
            variant="ghost"
            onClick={() => openEditForm(c)}
            data-testid={`connections-edit-${c.id}`}
          >
            {t('action.edit')}
          </Button>{' '}
          <Button
            size="sm"
            variant="danger"
            onClick={() => setConfirmDeleteTarget(c.id)}
            data-testid={`connections-delete-${c.id}`}
          >
            {t('action.delete')}
          </Button>
        </>
      ),
    },
  ]

  const additionalParamsHelp =
    formModal?.values.dbType === 'H2'
      ? t('connections.additionalParamsHelpH2')
      : t('connections.additionalParamsHelpDefault')

  return (
    <AuthenticatedLayout activeNavKey="connections">
      <PageHeader
        title={t('connections.title')}
        actions={
          <Button variant="primary" onClick={openCreateForm} data-testid="connections-add-button">
            {t('connections.addButton')}
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
          rows={connections}
          rowKey={(c) => String(c.id)}
          emptyState={<EmptyState message={t('connections.empty')} />}
        />
      )}

      <Modal
        open={formModal !== null}
        title={formModal?.mode === 'create' ? t('connections.formTitleCreate') : t('connections.formTitleEdit')}
        onClose={() => setFormModal(null)}
      >
        {formModal ? (
          <form onSubmit={(event) => void onFormSubmit(event)} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--mm-space-3)' }}>
            {formTestResult ? (
              <Alert tone={formTestResult.success ? 'success' : 'danger'}>{formTestResult.message}</Alert>
            ) : null}
            <FormField label={t('connections.displayName')} required>
              <TextInput
                value={formModal.values.displayName}
                onChange={(event) => onFormFieldChange('displayName', event.target.value)}
                required
                data-testid="connections-form-display-name"
              />
            </FormField>
            <FormField label={t('connections.dbType')} required>
              <Select
                value={formModal.values.dbType}
                onChange={(event) => onDbTypeChange(event.target.value as DbType)}
                data-testid="connections-form-db-type"
              >
                <option value="MYSQL">MySQL</option>
                <option value="MARIADB">MariaDB</option>
                <option value="POSTGRESQL">PostgreSQL</option>
                <option value="H2">H2</option>
              </Select>
            </FormField>
            <FormField label={t('connections.host')} required>
              <TextInput
                value={formModal.values.host}
                onChange={(event) => onFormFieldChange('host', event.target.value)}
                required
                data-testid="connections-form-host"
              />
            </FormField>
            <FormField label={t('connections.port')} required>
              <TextInput
                type="number"
                min={1}
                max={65535}
                value={formModal.values.port}
                onChange={(event) => onFormFieldChange('port', event.target.value)}
                required
                data-testid="connections-form-port"
              />
            </FormField>
            <FormField label={t('connections.databaseName')} required>
              <TextInput
                value={formModal.values.databaseName}
                onChange={(event) => onFormFieldChange('databaseName', event.target.value)}
                required
                data-testid="connections-form-database-name"
              />
            </FormField>
            <FormField label={t('connections.username')} required>
              <TextInput
                value={formModal.values.username}
                onChange={(event) => onFormFieldChange('username', event.target.value)}
                required
                data-testid="connections-form-username"
              />
            </FormField>
            <FormField
              label={t('connections.password')}
              required={formModal.mode === 'create'}
              help={formModal.mode === 'edit' ? t('connections.passwordEditHint') : undefined}
            >
              <PasswordInput
                value={formModal.values.password}
                onChange={(event) => onFormFieldChange('password', event.target.value)}
                required={formModal.mode === 'create'}
                data-testid="connections-form-password"
              />
            </FormField>
            <FormField label={t('connections.additionalParams')} help={additionalParamsHelp}>
              <TextInput
                value={formModal.values.additionalParams}
                onChange={(event) => onFormFieldChange('additionalParams', event.target.value)}
                data-testid="connections-form-additional-params"
              />
            </FormField>
            <Button
              type="button"
              variant="secondary"
              loading={formTesting}
              onClick={() => void onTestInForm()}
              data-testid="connections-form-test-button"
            >
              {t('connections.testButton')}
            </Button>
            <Button type="submit" variant="primary" loading={formSubmitting} data-testid="connections-form-submit">
              {formModal.mode === 'create' ? t('connections.submitCreate') : t('connections.submitEdit')}
            </Button>
          </form>
        ) : null}
      </Modal>

      <ConfirmDialog
        open={confirmDeleteTarget !== null}
        title={t('connections.confirmDeleteTitle')}
        message={t('connections.confirmDeleteMessage')}
        tone="danger"
        confirmLabel={t('action.delete')}
        onConfirm={() => void onDeleteConfirm()}
        onCancel={() => setConfirmDeleteTarget(null)}
      />
    </AuthenticatedLayout>
  )
}
