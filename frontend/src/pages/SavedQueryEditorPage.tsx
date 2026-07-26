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
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  Alert,
  Button,
  ConfirmDialog,
  FormField,
  Modal,
  PageHeader,
  Select,
  TextInput,
} from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { QueryEditorPanel } from './QueryEditorPanel'
import {
  createSavedQuery,
  executeQuery,
  executeSavedQuery,
  getSavedQuery,
  listQuerySchemas,
  retireSavedQuery,
  updateSavedQuery,
} from '../api/query'
import type { QueryResult, SavedQuerySummary, Visibility } from '../api/query'
import { ApiError } from '../api/http'

const DEFAULT_PAGE_SIZE = 50

interface PrefillState {
  sql?: string
  schemaName?: string
  paramValues?: Record<string, string>
}

// frontend-components.md Flow A-3/A-4。新規保存クエリ画面（mode='new'）と
// 既存保存クエリ実行画面（mode='existing'）を共通のコンポーネントで実装する。
export function SavedQueryEditorPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const { connectionId, savedQueryId } = useParams<{ connectionId: string; savedQueryId?: string }>()
  const connectionIdNum = Number(connectionId)
  const savedQueryIdNum = savedQueryId ? Number(savedQueryId) : null
  const mode: 'new' | 'existing' = savedQueryIdNum ? 'existing' : 'new'

  const prefill = (location.state as PrefillState | null) ?? null

  const [savedQuery, setSavedQuery] = useState<SavedQuerySummary | null>(null)
  const [editing, setEditing] = useState(false)
  const [schemas, setSchemas] = useState<string[]>([])
  const [schemaName, setSchemaName] = useState(prefill?.schemaName ?? '')
  const [sql, setSql] = useState(prefill?.sql ?? '')
  const [paramValues, setParamValues] = useState<Record<string, string>>(prefill?.paramValues ?? {})
  const [pagingEnabled, setPagingEnabled] = useState(false)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<QueryResult | null>(null)
  const [executing, setExecuting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const [saveDialogOpen, setSaveDialogOpen] = useState(false)
  const [saveName, setSaveName] = useState('')
  const [saveVisibility, setSaveVisibility] = useState<Visibility>('PRIVATE')
  const [retireConfirmOpen, setRetireConfirmOpen] = useState(false)

  useEffect(() => {
    listQuerySchemas(connectionIdNum)
      .then((list) => setSchemas(list.map((s) => s.schemaName)))
      .catch((error) => setErrorMessage(error instanceof ApiError ? error.message : t('state.error')))
  }, [connectionIdNum, t])

  useEffect(() => {
    if (mode !== 'existing' || !savedQueryIdNum) {
      return
    }
    getSavedQuery(connectionIdNum, savedQueryIdNum)
      .then((q) => {
        setSavedQuery(q)
        setSql(q.sql)
        setSaveName(q.name)
        setSaveVisibility(q.visibility)
      })
      .catch((error) => setErrorMessage(error instanceof ApiError ? error.message : t('state.error')))
  }, [mode, connectionIdNum, savedQueryIdNum, t])

  // frontend-components.md「逆遷移・相互遷移の実装方針」3。クエリビルダー画面から
  // 「保存へ」で戻ってきた場合、router state経由のSQLで上書きし編集モードへ入る
  // （location.keyはナビゲーションごとに変わるため、同一パスへの再遷移でも確実に反応する）。
  useEffect(() => {
    if (!prefill?.sql) {
      return
    }
    setSql(prefill.sql)
    if (prefill.schemaName) {
      setSchemaName(prefill.schemaName)
    }
    if (mode === 'existing') {
      setEditing(true)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.key])

  const onExecute = useCallback(async () => {
    setExecuting(true)
    setErrorMessage(null)
    try {
      const queryResult =
        mode === 'existing' && savedQueryIdNum
          ? await executeSavedQuery(connectionIdNum, savedQueryIdNum, {
              schemaName,
              params: paramValues,
              pagingEnabled,
              page,
              pageSize,
            })
          : await executeQuery(connectionIdNum, { sql, schemaName, params: paramValues, pagingEnabled, page, pageSize })
      setResult(queryResult)
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setExecuting(false)
    }
  }, [mode, connectionIdNum, savedQueryIdNum, sql, schemaName, paramValues, pagingEnabled, page, pageSize, t])

  useEffect(() => {
    if (result) {
      void onExecute()
    }
    // ページ番号変更時のみ再実行する（他の入力変更では実行ボタン経由のみ）
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page])

  const onSaveNew = async () => {
    try {
      const created = await createSavedQuery(connectionIdNum, { name: saveName, sql, visibility: saveVisibility })
      setSaveDialogOpen(false)
      navigate(`/saved-queries/${connectionIdNum}/${created.id}`, { replace: true })
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    }
  }

  const onUpdate = async () => {
    if (!savedQueryIdNum) {
      return
    }
    try {
      const updated = await updateSavedQuery(connectionIdNum, savedQueryIdNum, {
        name: saveName,
        sql,
        visibility: saveVisibility,
      })
      setSavedQuery(updated)
      setEditing(false)
      setSaveDialogOpen(false)
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    }
  }

  const onRetireConfirm = async () => {
    if (!savedQueryIdNum) {
      return
    }
    try {
      await retireSavedQuery(connectionIdNum, savedQueryIdNum)
      setRetireConfirmOpen(false)
      navigate(`/saved-queries/${connectionIdNum}`)
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
      setRetireConfirmOpen(false)
    }
  }

  const title = mode === 'new' ? t('savedQuery.newTitle') : (savedQuery?.name ?? '')

  // frontend-components.md「逆遷移・相互遷移の実装方針」2〜3。BR-QUERYBUILDER-12。
  const onEditInBuilder = () => {
    navigate(`/query-builder/${connectionIdNum}`, {
      state:
        mode === 'existing' && savedQueryIdNum
          ? { sql, schemaName, editMode: true, savedQueryId: savedQueryIdNum }
          : { sql, schemaName },
    })
  }

  return (
    <AuthenticatedLayout activeNavKey="savedQueries">
      <PageHeader
        title={title}
        actions={
          mode === 'new' ? (
            <>
              <Button
                variant="secondary"
                onClick={onEditInBuilder}
                data-testid="saved-query-edit-in-builder-button"
              >
                {t('queryBuilder.editInBuilderButton')}
              </Button>
              <Button
                variant="primary"
                disabled={!sql.trim()}
                onClick={() => setSaveDialogOpen(true)}
                data-testid="saved-query-save-button"
              >
                {t('savedQuery.saveButton')}
              </Button>
            </>
          ) : savedQuery?.own ? (
            <>
              {!editing ? (
                <Button variant="secondary" onClick={() => setEditing(true)} data-testid="saved-query-edit-button">
                  {t('savedQuery.editButton')}
                </Button>
              ) : (
                <>
                  <Button
                    variant="secondary"
                    onClick={onEditInBuilder}
                    data-testid="saved-query-edit-in-builder-button"
                  >
                    {t('queryBuilder.editInBuilderButton')}
                  </Button>
                  <Button
                    variant="primary"
                    onClick={() => setSaveDialogOpen(true)}
                    data-testid="saved-query-update-button"
                  >
                    {t('savedQuery.updateButton')}
                  </Button>
                </>
              )}
              <Button
                variant="danger"
                onClick={() => setRetireConfirmOpen(true)}
                data-testid="saved-query-retire-button"
              >
                {t('savedQuery.retireAction')}
              </Button>
            </>
          ) : null
        }
      />
      {mode === 'existing' && !editing ? <Alert tone="info">{t('savedQuery.readOnlyNotice')}</Alert> : null}
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      <QueryEditorPanel
        namespace="savedQuery"
        schemas={schemas}
        schemaName={schemaName}
        onSchemaChange={setSchemaName}
        sql={sql}
        onSqlChange={setSql}
        sqlReadOnly={mode === 'existing' && !editing}
        paramValues={paramValues}
        onParamValuesChange={setParamValues}
        pagingEnabled={pagingEnabled}
        onPagingEnabledChange={setPagingEnabled}
        pageSize={pageSize}
        onPageSizeChange={setPageSize}
        onExecute={() => void onExecute()}
        executing={executing}
        result={result}
        page={page}
        onPageChange={setPage}
      />

      <Modal
        open={saveDialogOpen}
        title={t('savedQuery.saveDialogTitle')}
        onClose={() => setSaveDialogOpen(false)}
        footer={
          <Button variant="primary" onClick={() => void (mode === 'new' ? onSaveNew() : onUpdate())}>
            {t('action.save')}
          </Button>
        }
      >
        <FormField label={t('savedQuery.nameLabel')}>
          <TextInput value={saveName} onChange={(event) => setSaveName(event.target.value)} />
        </FormField>
        <FormField label={t('savedQuery.visibilityLabel')}>
          <Select value={saveVisibility} onChange={(event) => setSaveVisibility(event.target.value as Visibility)}>
            <option value="PRIVATE">{t('savedQuery.visibilityPrivate')}</option>
            <option value="PUBLIC">{t('savedQuery.visibilityPublic')}</option>
          </Select>
        </FormField>
      </Modal>

      <ConfirmDialog
        open={retireConfirmOpen}
        title={t('savedQuery.confirmRetireTitle')}
        message={t('savedQuery.confirmRetireMessage')}
        tone="danger"
        confirmLabel={t('savedQuery.retireAction')}
        onConfirm={() => void onRetireConfirm()}
        onCancel={() => setRetireConfirmOpen(false)}
      />
    </AuthenticatedLayout>
  )
}
