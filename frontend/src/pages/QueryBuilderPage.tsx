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

import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { Alert, Button, CodeBlock, PageHeader, Select, Tabs } from '../design-system/components'
import type { TabItem } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { listQuerySchemas } from '../api/query'
import {
  generateSql,
  listAccessibleBuilderTables,
  parseSql,
} from '../api/queryBuilder'
import type { AccessibleBuilderTable, QueryBuilderState } from '../api/queryBuilder'
import { ApiError } from '../api/http'
import { QueryBuilderFromTab } from './QueryBuilderFromTab'
import { QueryBuilderJoinTab } from './QueryBuilderJoinTab'
import { QueryBuilderSelectTab } from './QueryBuilderSelectTab'
import { QueryBuilderConditionList } from './QueryBuilderConditionList'
import { QueryBuilderColumnListTab } from './QueryBuilderColumnListTab'
import { QueryBuilderOrderByTab } from './QueryBuilderOrderByTab'
import { QueryBuilderLimitOffsetTab } from './QueryBuilderLimitOffsetTab'
import type { AvailableColumn } from './QueryBuilderOperandPicker'

const EMPTY_STATE: QueryBuilderState = {
  from: null,
  joins: [],
  selectItems: [],
  whereConditions: [],
  groupByColumns: [],
  havingConditions: [],
  orderByItems: [],
  limit: null,
  offset: null,
}

interface QueryBuilderPrefill {
  sql?: string
  schemaName?: string
  editMode?: boolean
  savedQueryId?: number
}

// frontend-components.md 画面2。クエリビルダー画面。BR-QUERYBUILDER-12の逆遷移・相互遷移に
// 対応するため、遷移元のSQL・スキーマをrouter state経由で受け取り、自動的にリバース
// エンジニアリングを試行する。
export function QueryBuilderPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const { connectionId } = useParams<{ connectionId: string }>()
  const connectionIdNum = Number(connectionId)
  const prefill = (location.state as QueryBuilderPrefill | null) ?? null

  const [schemas, setSchemas] = useState<string[]>([])
  const [schemaName, setSchemaName] = useState(prefill?.schemaName ?? '')
  const [accessibleTables, setAccessibleTables] = useState<AccessibleBuilderTable[]>([])
  const [builderState, setBuilderState] = useState<QueryBuilderState>(EMPTY_STATE)
  const [activeTab, setActiveTab] = useState('select')
  const [generatedSql, setGeneratedSql] = useState<string | null>(null)
  const [generating, setGenerating] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    listQuerySchemas(connectionIdNum)
      .then((list) => {
        const names = list.map((s) => s.schemaName)
        setSchemas(names)
        if (!schemaName && names.length > 0) {
          setSchemaName(names[0])
        }
      })
      .catch((error) => setErrorMessage(error instanceof ApiError ? error.message : t('state.error')))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionIdNum])

  useEffect(() => {
    if (!schemaName) {
      return
    }
    listAccessibleBuilderTables(connectionIdNum, schemaName)
      .then(setAccessibleTables)
      .catch((error) => setErrorMessage(error instanceof ApiError ? error.message : t('state.error')))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionIdNum, schemaName])

  // BR-QUERYBUILDER-12: 遷移元のSQLをリバースエンジニアリングして初期反映する。
  // 失敗時はタブを初期状態のまま表示しエラーのみ表示する（フェイルクローズ）。
  useEffect(() => {
    if (!prefill?.sql || !schemaName) {
      return
    }
    parseSql(connectionIdNum, schemaName, prefill.sql)
      .then(setBuilderState)
      .catch(() => setErrorMessage(t('queryBuilder.reverseEngineeringFailed')))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionIdNum, schemaName])

  const availableColumns: AvailableColumn[] = useMemo(() => {
    const result: AvailableColumn[] = []
    const addForAlias = (tableName: string, alias: string) => {
      const table = accessibleTables.find((t) => t.tableName === tableName)
      table?.columns.forEach((c) =>
        result.push({ tableAlias: alias, columnName: c.columnName, dataTypeCategory: c.dataTypeCategory }),
      )
    }
    if (builderState.from) {
      addForAlias(builderState.from.tableName, builderState.from.alias)
    }
    builderState.joins.forEach((j) => addForAlias(j.tableName, j.alias))
    return result
  }, [accessibleTables, builderState.from, builderState.joins])

  useEffect(() => {
    if (!builderState.from || builderState.selectItems.length === 0) {
      setGeneratedSql(null)
      return
    }
    const timer = setTimeout(() => {
      setGenerating(true)
      generateSql(connectionIdNum, builderState)
        .then((res) => {
          setGeneratedSql(res.sql)
          setErrorMessage(null)
        })
        .catch((error) => {
          setGeneratedSql(null)
          setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
        })
        .finally(() => setGenerating(false))
    }, 400)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionIdNum, builderState])

  const onNavigateToSave = () => {
    if (!generatedSql) {
      return
    }
    if (prefill?.editMode && prefill.savedQueryId) {
      navigate(`/saved-queries/${connectionIdNum}/${prefill.savedQueryId}`, {
        state: { sql: generatedSql, schemaName },
      })
      return
    }
    navigate(`/saved-queries/${connectionIdNum}/new`, { state: { sql: generatedSql, schemaName } })
  }

  const onNavigateToExecute = () => {
    if (!generatedSql) {
      return
    }
    navigate(`/query-execution/${connectionIdNum}`, { state: { sql: generatedSql, schemaName } })
  }

  const tabs: readonly TabItem[] = [
    {
      key: 'select',
      label: t('queryBuilder.tab.select'),
      content: (
        <QueryBuilderSelectTab
          columns={availableColumns}
          value={builderState.selectItems}
          onChange={(selectItems) => setBuilderState({ ...builderState, selectItems })}
        />
      ),
    },
    {
      key: 'from',
      label: t('queryBuilder.tab.from'),
      content: (
        <QueryBuilderFromTab
          schemaName={schemaName}
          tables={accessibleTables}
          value={builderState.from}
          onChange={(from) => setBuilderState({ ...builderState, from })}
        />
      ),
    },
    {
      key: 'join',
      label: t('queryBuilder.tab.join'),
      content: (
        <QueryBuilderJoinTab
          schemaName={schemaName}
          tables={accessibleTables}
          leftColumns={availableColumns}
          value={builderState.joins}
          onChange={(joins) => setBuilderState({ ...builderState, joins })}
        />
      ),
    },
    {
      key: 'where',
      label: t('queryBuilder.tab.where'),
      content: (
        <QueryBuilderConditionList
          columns={availableColumns}
          allowAggregate={false}
          value={builderState.whereConditions}
          onChange={(whereConditions) => setBuilderState({ ...builderState, whereConditions })}
          testIdPrefix="query-builder-where"
        />
      ),
    },
    {
      key: 'groupBy',
      label: t('queryBuilder.tab.groupBy'),
      content: (
        <QueryBuilderColumnListTab
          columns={availableColumns}
          value={builderState.groupByColumns}
          onChange={(groupByColumns) => setBuilderState({ ...builderState, groupByColumns })}
          testIdPrefix="query-builder-groupby"
        />
      ),
    },
    {
      key: 'having',
      label: t('queryBuilder.tab.having'),
      content: (
        <QueryBuilderConditionList
          columns={availableColumns}
          allowAggregate
          value={builderState.havingConditions}
          onChange={(havingConditions) => setBuilderState({ ...builderState, havingConditions })}
          testIdPrefix="query-builder-having"
        />
      ),
    },
    {
      key: 'orderBy',
      label: t('queryBuilder.tab.orderBy'),
      content: (
        <QueryBuilderOrderByTab
          columns={availableColumns}
          value={builderState.orderByItems}
          onChange={(orderByItems) => setBuilderState({ ...builderState, orderByItems })}
        />
      ),
    },
    {
      key: 'limitOffset',
      label: t('queryBuilder.tab.limitOffset'),
      content: (
        <QueryBuilderLimitOffsetTab
          limit={builderState.limit}
          offset={builderState.offset}
          onChangeLimit={(limit) => setBuilderState({ ...builderState, limit })}
          onChangeOffset={(offset) => setBuilderState({ ...builderState, offset })}
        />
      ),
    },
  ]

  return (
    <AuthenticatedLayout activeNavKey="queryBuilder">
      <PageHeader title={t('queryBuilder.title')} />
      {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
      <Select value={schemaName} onChange={(e) => setSchemaName(e.target.value)} data-testid="query-builder-schema-select">
        <option value="">{t('queryBuilder.selectPlaceholder')}</option>
        {schemas.map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </Select>
      <Tabs items={tabs} activeKey={activeTab} onChange={setActiveTab} />
      <div>
        {generating ? <span>{t('state.loading')}</span> : null}
        {generatedSql ? <CodeBlock code={generatedSql} /> : null}
      </div>
      <Button onClick={onNavigateToSave} disabled={!generatedSql} data-testid="query-builder-save-button">
        {t('queryBuilder.saveButton')}
      </Button>
      <Button onClick={onNavigateToExecute} disabled={!generatedSql} data-testid="query-builder-execute-button">
        {t('queryBuilder.executeButton')}
      </Button>
    </AuthenticatedLayout>
  )
}
