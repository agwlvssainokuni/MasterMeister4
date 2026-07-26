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
import { Alert, Button, PageHeader } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { QueryEditorPanel } from './QueryEditorPanel'
import { executeQuery, listQuerySchemas } from '../api/query'
import type { QueryResult } from '../api/query'
import { ApiError } from '../api/http'

const DEFAULT_PAGE_SIZE = 50

// frontend-components.md Flow B-2。ad-hoc実行画面。「名前を付けて保存」で
// Flow AのA-3（新規保存クエリ画面）へ、現在の入力状態をrouter state経由で引き継いで遷移する
// （本画面自体には保存操作を持たない）。
export function QueryExecutionPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { connectionId } = useParams<{ connectionId: string }>()
  const connectionIdNum = Number(connectionId)

  const [schemas, setSchemas] = useState<string[]>([])
  const [schemaName, setSchemaName] = useState('')
  const [sql, setSql] = useState('')
  const [paramValues, setParamValues] = useState<Record<string, string>>({})
  const [pagingEnabled, setPagingEnabled] = useState(false)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<QueryResult | null>(null)
  const [executing, setExecuting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    listQuerySchemas(connectionIdNum)
      .then((list) => setSchemas(list.map((s) => s.schemaName)))
      .catch((error) => setErrorMessage(error instanceof ApiError ? error.message : t('state.error')))
  }, [connectionIdNum, t])

  const onExecute = useCallback(async () => {
    setExecuting(true)
    setErrorMessage(null)
    try {
      const queryResult = await executeQuery(connectionIdNum, {
        sql,
        schemaName,
        params: paramValues,
        pagingEnabled,
        page,
        pageSize,
      })
      setResult(queryResult)
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setExecuting(false)
    }
  }, [connectionIdNum, sql, schemaName, paramValues, pagingEnabled, page, pageSize, t])

  useEffect(() => {
    if (result) {
      void onExecute()
    }
    // ページ番号変更時のみ再実行する（他の入力変更では実行ボタン経由のみ）
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page])

  const onSaveAs = () => {
    navigate(`/saved-queries/${connectionIdNum}/new`, {
      state: { sql, schemaName, paramValues },
    })
  }

  // frontend-components.md「逆遷移・相互遷移の実装方針」1。BR-QUERYBUILDER-12。
  const onEditInBuilder = () => {
    navigate(`/query-builder/${connectionIdNum}`, { state: { sql, schemaName } })
  }

  return (
    <AuthenticatedLayout activeNavKey="queryExecution">
      <PageHeader
        title={t('queryExecution.title')}
        actions={
          <>
            <Button variant="secondary" onClick={onEditInBuilder} data-testid="query-execution-edit-in-builder-button">
              {t('queryBuilder.editInBuilderButton')}
            </Button>
            <Button variant="secondary" onClick={onSaveAs} data-testid="query-execution-save-as-button">
              {t('queryExecution.saveAsButton')}
            </Button>
          </>
        }
      />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      <QueryEditorPanel
        namespace="queryExecution"
        schemas={schemas}
        schemaName={schemaName}
        onSchemaChange={setSchemaName}
        sql={sql}
        onSqlChange={setSql}
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
    </AuthenticatedLayout>
  )
}
