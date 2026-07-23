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
import type { ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import {
  Alert,
  Button,
  Checkbox,
  EmptyState,
  FormField,
  Icon,
  Modal,
  PageHeader,
  Select,
  Spinner,
} from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { listConnections, getSchema } from '../api/rdbmsConnections'
import type { SchemaSnapshotDetail } from '../api/rdbmsConnections'
import { listGroups } from '../api/groups'
import type { GroupSummary } from '../api/groups'
import { listUsers } from '../api/adminUsers'
import type { UserSummary } from '../api/adminUsers'
import {
  exportPermissions,
  importPermissions,
  listPermissions,
  setPermission,
  unsetPermission,
} from '../api/permissions'
import type { PermissionEntry, PrimaryPermission, PrincipalType } from '../api/permissions'
import { ApiError } from '../api/http'

function tableKey(schemaName: string, tableName: string): string {
  return `${schemaName}.${tableName}`
}

function findEntry(
  permissions: readonly PermissionEntry[],
  schemaName: string,
  tableName: string | null,
  columnName: string | null,
): PermissionEntry | null {
  return (
    permissions.find(
      (p) => p.schemaName === schemaName && p.tableName === tableName && p.columnName === columnName,
    ) ?? null
  )
}

// frontend-components.md §2。/permissions/:connectionId。プリンシパル主体（ツリー型）。
export function AccessPermissionTreePage() {
  const { t } = useTranslation()
  const { connectionId: connectionIdParam } = useParams<{ connectionId: string }>()
  const connectionId = Number(connectionIdParam)

  const [connectionDisplayName, setConnectionDisplayName] = useState('')
  const [schema, setSchema] = useState<SchemaSnapshotDetail | null>(null)
  const [notImported, setNotImported] = useState(false)
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(
    null,
  )

  const [principalType, setPrincipalType] = useState<PrincipalType>('USER')
  const [principalId, setPrincipalId] = useState<string>('')
  const [users, setUsers] = useState<UserSummary[]>([])
  const [groups, setGroups] = useState<GroupSummary[]>([])
  const [permissions, setPermissions] = useState<PermissionEntry[]>([])

  const [expandedSchemas, setExpandedSchemas] = useState<Set<string>>(new Set())
  const [expandedTables, setExpandedTables] = useState<Set<string>>(new Set())
  const [importModal, setImportModal] = useState<{ file: File | null } | null>(null)
  const [importing, setImporting] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    setNotImported(false)
    try {
      const [connections, schemaResult, userList, groupList] = await Promise.all([
        listConnections(),
        getSchema(connectionId),
        listUsers('APPROVED'),
        listGroups(),
      ])
      setConnectionDisplayName(connections.find((c) => c.id === connectionId)?.displayName ?? '')
      setSchema(schemaResult)
      setUsers(userList)
      setGroups(groupList)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'SCHEMA_NOT_IMPORTED') {
        setNotImported(true)
      } else {
        setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
      }
    } finally {
      setLoading(false)
    }
  }, [connectionId, t])

  useEffect(() => {
    void load()
  }, [load])

  const loadPermissions = useCallback(async () => {
    if (principalId === '') {
      setPermissions([])
      return
    }
    try {
      setPermissions(await listPermissions(connectionId, principalType, Number(principalId)))
    } catch (error) {
      setActionMessage({ tone: 'danger', text: error instanceof ApiError ? error.message : t('state.error') })
    }
  }, [connectionId, principalType, principalId, t])

  useEffect(() => {
    void loadPermissions()
  }, [loadPermissions])

  const schemasByName = useMemo(() => {
    const map = new Map<string, typeof schema extends null ? never : NonNullable<typeof schema>['tables']>()
    for (const table of schema?.tables ?? []) {
      const list = map.get(table.schemaName) ?? []
      list.push(table)
      map.set(table.schemaName, list)
    }
    return map
  }, [schema])

  const toggleSchema = (schemaName: string) => {
    setExpandedSchemas((current) => {
      const next = new Set(current)
      if (next.has(schemaName)) {
        next.delete(schemaName)
      } else {
        next.add(schemaName)
      }
      return next
    })
  }

  const toggleTable = (key: string) => {
    setExpandedTables((current) => {
      const next = new Set(current)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      return next
    })
  }

  const applyPrimaryChange = async (
    schemaName: string,
    tableName: string | null,
    columnName: string | null,
    value: string,
  ) => {
    if (principalId === '') {
      return
    }
    const existing = findEntry(permissions, schemaName, tableName, columnName)
    try {
      if (value === '') {
        await unsetPermission(connectionId, {
          principalType,
          principalId: Number(principalId),
          schemaName,
          tableName,
          columnName,
        })
      } else {
        await setPermission(connectionId, {
          principalType,
          principalId: Number(principalId),
          schemaName,
          tableName,
          columnName,
          primaryPermission: value as PrimaryPermission,
          createPermission: existing?.createPermission ?? false,
          deletePermission: existing?.deletePermission ?? false,
        })
      }
      await loadPermissions()
    } catch (error) {
      setActionMessage({ tone: 'danger', text: error instanceof ApiError ? error.message : t('state.error') })
    }
  }

  const applyAuxiliaryChange = async (
    schemaName: string,
    tableName: string | null,
    kind: 'create' | 'delete',
    checked: boolean,
  ) => {
    if (principalId === '') {
      return
    }
    const existing = findEntry(permissions, schemaName, tableName, null)
    try {
      await setPermission(connectionId, {
        principalType,
        principalId: Number(principalId),
        schemaName,
        tableName,
        columnName: null,
        primaryPermission: existing?.primaryPermission ?? 'NONE',
        createPermission: kind === 'create' ? checked : (existing?.createPermission ?? false),
        deletePermission: kind === 'delete' ? checked : (existing?.deletePermission ?? false),
      })
      await loadPermissions()
    } catch (error) {
      setActionMessage({ tone: 'danger', text: error instanceof ApiError ? error.message : t('state.error') })
    }
  }

  const onExport = async () => {
    try {
      const yaml = await exportPermissions(connectionId)
      const blob = new Blob([yaml], { type: 'application/x-yaml' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `permissions-${connectionId}.yaml`
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      setActionMessage({ tone: 'danger', text: error instanceof ApiError ? error.message : t('state.error') })
    }
  }

  const onImport = async () => {
    if (!importModal?.file) {
      return
    }
    setImporting(true)
    try {
      const yaml = await importModal.file.text()
      await importPermissions(connectionId, yaml)
      setImportModal(null)
      setActionMessage({ tone: 'success', text: t('permissions.importSuccess') })
      await loadPermissions()
    } catch (error) {
      setActionMessage({ tone: 'danger', text: error instanceof ApiError ? error.message : t('state.error') })
    } finally {
      setImporting(false)
    }
  }

  const onImportFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    setImportModal({ file: event.target.files?.[0] ?? null })
  }

  const primaryOptions: { value: '' | PrimaryPermission; labelKey: string }[] = [
    { value: '', labelKey: 'permissions.notSet' },
    { value: 'NONE', labelKey: 'permissions.none' },
    { value: 'READ', labelKey: 'permissions.read' },
    { value: 'UPDATE', labelKey: 'permissions.update' },
  ]

  return (
    <AuthenticatedLayout>
      <PageHeader
        title={`${connectionDisplayName} - ${t('permissions.title')}`}
        actions={
          <>
            <Button variant="secondary" onClick={() => void onExport()} data-testid="permissions-export-button">
              {t('permissions.exportButton')}
            </Button>{' '}
            <Button
              variant="secondary"
              onClick={() => setImportModal({ file: null })}
              data-testid="permissions-import-button"
            >
              {t('permissions.importButton')}
            </Button>
          </>
        }
      />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      {actionMessage ? <Alert tone={actionMessage.tone}>{actionMessage.text}</Alert> : null}
      {loading ? <Spinner /> : null}
      {notImported ? (
        <EmptyState
          message={t('connections.notImported')}
          action={<Link to="/connections">{t('connections.backToList')}</Link>}
        />
      ) : null}

      {schema ? (
        <>
          <div style={{ display: 'flex', gap: 'var(--mm-space-3)', marginBottom: 'var(--mm-space-3)' }}>
            <FormField label={t('permissions.principalType')}>
              <Select
                value={principalType}
                onChange={(event) => {
                  setPrincipalType(event.target.value as PrincipalType)
                  setPrincipalId('')
                }}
                data-testid="permissions-principal-type"
              >
                <option value="USER">{t('permissions.principalUser')}</option>
                <option value="GROUP">{t('permissions.principalGroup')}</option>
              </Select>
            </FormField>
            <FormField label={t('permissions.principal')}>
              <Select
                value={principalId}
                onChange={(event) => setPrincipalId(event.target.value)}
                data-testid="permissions-principal-select"
              >
                <option value="">{t('permissions.selectPrincipal')}</option>
                {(principalType === 'USER' ? users : groups).map((entity) => (
                  <option key={entity.id} value={entity.id}>
                    {'email' in entity ? `${entity.fullName}（${entity.email}）` : entity.name}
                  </option>
                ))}
              </Select>
            </FormField>
          </div>

          {principalId === '' ? (
            <EmptyState message={t('permissions.selectPrincipalPrompt')} />
          ) : (
            <div data-testid="permissions-tree">
              {[...schemasByName.entries()].map(([schemaName, tables]) => {
                const schemaEntry = findEntry(permissions, schemaName, null, null)
                const schemaExpanded = expandedSchemas.has(schemaName)
                return (
                  <div key={schemaName} style={{ marginBottom: 'var(--mm-space-2)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--mm-space-2)' }}>
                      <button
                        type="button"
                        onClick={() => toggleSchema(schemaName)}
                        data-testid={`permissions-schema-toggle-${schemaName}`}
                        aria-label={schemaExpanded ? t('action.collapse') : t('action.expand')}
                      >
                        <Icon name={schemaExpanded ? 'chevron-down' : 'chevron-right'} />
                      </button>
                      <strong>{schemaName}</strong>
                      <Select
                        value={schemaEntry?.primaryPermission ?? ''}
                        onChange={(event) => void applyPrimaryChange(schemaName, null, null, event.target.value)}
                        data-testid={`permissions-primary-${schemaName}`}
                      >
                        {primaryOptions.map((option) => (
                          <option key={option.value} value={option.value}>
                            {t(option.labelKey)}
                          </option>
                        ))}
                      </Select>
                      <Checkbox
                        label={t('permissions.create')}
                        checked={schemaEntry?.createPermission ?? false}
                        onChange={(event) => void applyAuxiliaryChange(schemaName, null, 'create', event.target.checked)}
                        data-testid={`permissions-create-${schemaName}`}
                      />
                      <Checkbox
                        label={t('permissions.delete')}
                        checked={schemaEntry?.deletePermission ?? false}
                        onChange={(event) => void applyAuxiliaryChange(schemaName, null, 'delete', event.target.checked)}
                        data-testid={`permissions-delete-${schemaName}`}
                      />
                    </div>

                    {schemaExpanded
                      ? tables.map((table) => {
                          const key = tableKey(schemaName, table.tableName)
                          const tableEntry = findEntry(permissions, schemaName, table.tableName, null)
                          const tableExpanded = expandedTables.has(key)
                          return (
                            <div key={key} style={{ marginLeft: 'var(--mm-space-5)' }}>
                              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--mm-space-2)' }}>
                                <button
                                  type="button"
                                  onClick={() => toggleTable(key)}
                                  data-testid={`permissions-table-toggle-${key}`}
                                  aria-label={tableExpanded ? t('action.collapse') : t('action.expand')}
                                >
                                  <Icon name={tableExpanded ? 'chevron-down' : 'chevron-right'} />
                                </button>
                                <span>{table.tableName}</span>
                                <Select
                                  value={tableEntry?.primaryPermission ?? ''}
                                  onChange={(event) =>
                                    void applyPrimaryChange(schemaName, table.tableName, null, event.target.value)
                                  }
                                  data-testid={`permissions-primary-${key}`}
                                >
                                  {primaryOptions.map((option) => (
                                    <option key={option.value} value={option.value}>
                                      {t(option.labelKey)}
                                    </option>
                                  ))}
                                </Select>
                                <Checkbox
                                  label={t('permissions.create')}
                                  checked={tableEntry?.createPermission ?? false}
                                  onChange={(event) =>
                                    void applyAuxiliaryChange(schemaName, table.tableName, 'create', event.target.checked)
                                  }
                                  data-testid={`permissions-create-${key}`}
                                />
                                <Checkbox
                                  label={t('permissions.delete')}
                                  checked={tableEntry?.deletePermission ?? false}
                                  onChange={(event) =>
                                    void applyAuxiliaryChange(schemaName, table.tableName, 'delete', event.target.checked)
                                  }
                                  data-testid={`permissions-delete-${key}`}
                                />
                              </div>

                              {tableExpanded
                                ? table.columns.map((column) => {
                                    const columnEntry = findEntry(
                                      permissions,
                                      schemaName,
                                      table.tableName,
                                      column.columnName,
                                    )
                                    return (
                                      <div
                                        key={column.columnName}
                                        style={{
                                          marginLeft: 'var(--mm-space-5)',
                                          display: 'flex',
                                          alignItems: 'center',
                                          gap: 'var(--mm-space-2)',
                                        }}
                                      >
                                        <span>{column.columnName}</span>
                                        <Select
                                          value={columnEntry?.primaryPermission ?? ''}
                                          onChange={(event) =>
                                            void applyPrimaryChange(
                                              schemaName,
                                              table.tableName,
                                              column.columnName,
                                              event.target.value,
                                            )
                                          }
                                          data-testid={`permissions-primary-${key}.${column.columnName}`}
                                        >
                                          {primaryOptions.map((option) => (
                                            <option key={option.value} value={option.value}>
                                              {t(option.labelKey)}
                                            </option>
                                          ))}
                                        </Select>
                                      </div>
                                    )
                                  })
                                : null}
                            </div>
                          )
                        })
                      : null}
                  </div>
                )
              })}
            </div>
          )}
        </>
      ) : null}

      <Modal
        open={importModal !== null}
        title={t('permissions.importButton')}
        onClose={() => setImportModal(null)}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--mm-space-3)' }}>
          <input type="file" accept=".yaml,.yml" onChange={onImportFileChange} data-testid="permissions-import-file" />
          <Button
            variant="primary"
            disabled={!importModal?.file}
            loading={importing}
            onClick={() => void onImport()}
            data-testid="permissions-import-execute"
          >
            {t('permissions.importExecute')}
          </Button>
        </div>
      </Modal>
    </AuthenticatedLayout>
  )
}
