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
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  ConfirmDialog,
  DataTable,
  EmptyState,
  FilterBar,
  PageHeader,
  Select,
  Spinner,
} from '../design-system/components'
import type { TableColumn } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { listSavedQueries, retireSavedQuery } from '../api/query'
import type { SavedQuerySummary, VisibilityFilter } from '../api/query'
import { ApiError } from '../api/http'

// frontend-components.md Flow A-2。対象接続に紐づく保存クエリ一覧・非表示化を担う画面。
export function SavedQueryListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { connectionId } = useParams<{ connectionId: string }>()
  const connectionIdNum = Number(connectionId)

  const [queries, setQueries] = useState<SavedQuerySummary[]>([])
  const [visibility, setVisibility] = useState<VisibilityFilter>('ALL')
  const [includeOwnRetired, setIncludeOwnRetired] = useState(false)
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [retireTarget, setRetireTarget] = useState<SavedQuerySummary | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(null)
    try {
      setQueries(await listSavedQueries(connectionIdNum, visibility, includeOwnRetired))
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setLoading(false)
    }
  }, [connectionIdNum, visibility, includeOwnRetired, t])

  useEffect(() => {
    void load()
  }, [load])

  const onRetireConfirm = async () => {
    if (!retireTarget) {
      return
    }
    try {
      await retireSavedQuery(connectionIdNum, retireTarget.id)
      setRetireTarget(null)
      await load()
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
      setRetireTarget(null)
    }
  }

  const columns: readonly TableColumn<SavedQuerySummary>[] = [
    {
      key: 'name',
      header: t('savedQuery.nameColumn'),
      render: (q) => (
        <Button
          variant="ghost"
          onClick={() => navigate(`/saved-queries/${connectionIdNum}/${q.id}`)}
          data-testid={`saved-query-name-${q.id}`}
        >
          {q.name}
        </Button>
      ),
    },
    {
      key: 'visibility',
      header: t('savedQuery.visibilityColumn'),
      render: (q) => (
        <Badge tone={q.visibility === 'PUBLIC' ? 'primary' : 'neutral'}>
          {q.visibility === 'PUBLIC' ? t('savedQuery.visibilityPublic') : t('savedQuery.visibilityPrivate')}
        </Badge>
      ),
    },
    {
      key: 'owner',
      header: t('savedQuery.ownerColumn'),
      render: (q) => (q.own ? t('savedQuery.ownerSelf') : t('savedQuery.ownerOther')),
    },
    {
      key: 'actions',
      header: '',
      render: (q) =>
        q.own && !q.retired ? (
          <Button
            variant="danger"
            size="sm"
            onClick={() => setRetireTarget(q)}
            data-testid={`saved-query-retire-${q.id}`}
          >
            {t('savedQuery.retireAction')}
          </Button>
        ) : null,
    },
  ]

  return (
    <AuthenticatedLayout activeNavKey="savedQueries">
      <PageHeader
        title={t('savedQuery.listTitleSuffix')}
        actions={
          <Button
            variant="primary"
            onClick={() => navigate(`/saved-queries/${connectionIdNum}/new`)}
            data-testid="saved-query-add-button"
          >
            {t('savedQuery.addButton')}
          </Button>
        }
      />
      <FilterBar>
        <Select
          value={visibility}
          onChange={(event) => setVisibility(event.target.value as VisibilityFilter)}
          aria-label={t('savedQuery.visibilityColumn')}
        >
          <option value="ALL">{t('savedQuery.visibilityAll')}</option>
          <option value="PUBLIC">{t('savedQuery.visibilityPublic')}</option>
          <option value="PRIVATE">{t('savedQuery.visibilityPrivate')}</option>
        </Select>
        <Checkbox
          label={t('savedQuery.includeOwnRetired')}
          checked={includeOwnRetired}
          onChange={(event) => setIncludeOwnRetired(event.target.checked)}
        />
      </FilterBar>
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      {loading ? (
        <Spinner />
      ) : (
        <DataTable
          columns={columns}
          rows={queries}
          rowKey={(q) => String(q.id)}
          emptyState={<EmptyState message={t('savedQuery.listEmpty')} />}
        />
      )}
      <ConfirmDialog
        open={retireTarget !== null}
        title={t('savedQuery.confirmRetireTitle')}
        message={t('savedQuery.confirmRetireMessage')}
        tone="danger"
        confirmLabel={t('savedQuery.retireAction')}
        onConfirm={() => void onRetireConfirm()}
        onCancel={() => setRetireTarget(null)}
      />
    </AuthenticatedLayout>
  )
}
