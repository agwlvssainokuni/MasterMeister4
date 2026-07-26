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

import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Button,
  Checkbox,
  DataTable,
  EmptyState,
  FormField,
  Pagination,
  Select,
  TextArea,
  TextInput,
} from '../design-system/components'
import type { TableColumn } from '../design-system/components'
import type { QueryResult } from '../api/query'

// frontend-components.md 共通コンポーネント節。A-3/A-4/B-2で共通利用するSQL入力欄
// （パラメータ自動検出付き）・スキーマセレクタ・ページング設定・結果表を1コンポーネントに集約する。
// パラメータ検出はクライアント側の簡易正規表現によるUIヒントであり（文字列リテラル内の
// `:`を誤検出しうる)、実際の安全性検証（AST走査）はバックエンドのQuerySqlAnalyzerが担う。
export interface QueryEditorPanelProps {
  namespace: 'savedQuery' | 'queryExecution'
  schemas: string[]
  schemaName: string
  onSchemaChange: (schema: string) => void
  sql: string
  onSqlChange: (sql: string) => void
  sqlReadOnly?: boolean
  paramValues: Record<string, string>
  onParamValuesChange: (values: Record<string, string>) => void
  pagingEnabled: boolean
  onPagingEnabledChange: (enabled: boolean) => void
  pageSize: number
  onPageSizeChange: (pageSize: number) => void
  onExecute: () => void
  executing: boolean
  result: QueryResult | null
  page: number
  onPageChange: (page: number) => void
}

function detectParamNames(sql: string): string[] {
  const matches = sql.matchAll(/:([a-zA-Z_][a-zA-Z0-9_]*)/g)
  const names: string[] = []
  for (const match of matches) {
    if (!names.includes(match[1])) {
      names.push(match[1])
    }
  }
  return names
}

export function QueryEditorPanel({
  namespace,
  schemas,
  schemaName,
  onSchemaChange,
  sql,
  onSqlChange,
  sqlReadOnly = false,
  paramValues,
  onParamValuesChange,
  pagingEnabled,
  onPagingEnabledChange,
  pageSize,
  onPageSizeChange,
  onExecute,
  executing,
  result,
  page,
  onPageChange,
}: QueryEditorPanelProps) {
  const { t } = useTranslation()
  const paramNames = useMemo(() => detectParamNames(sql), [sql])

  const resultColumns: readonly TableColumn<Record<string, string | null>>[] = useMemo(
    () => (result?.columns ?? []).map((column) => ({ key: column, header: column, render: (row) => row[column] })),
    [result],
  )

  const totalPages = result?.totalCount != null && result.pageSize ? Math.ceil(result.totalCount / result.pageSize) : 1

  return (
    <div>
      <FormField label={t(`${namespace}.schemaLabel`)}>
        <Select value={schemaName} onChange={(event) => onSchemaChange(event.target.value)}>
          <option value="" disabled>
            {t('state.required')}
          </option>
          {schemas.map((schema) => (
            <option key={schema} value={schema}>
              {schema}
            </option>
          ))}
        </Select>
      </FormField>
      <FormField label={t(`${namespace}.sqlLabel`)}>
        <TextArea
          value={sql}
          readOnly={sqlReadOnly}
          onChange={(event) => onSqlChange(event.target.value)}
          rows={8}
          data-testid="query-editor-sql-input"
        />
      </FormField>
      {paramNames.length > 0 ? (
        <div>
          {paramNames.map((name) => (
            <FormField key={name} label={name}>
              <TextInput
                value={paramValues[name] ?? ''}
                onChange={(event) => onParamValuesChange({ ...paramValues, [name]: event.target.value })}
                data-testid={`query-editor-param-${name}`}
              />
            </FormField>
          ))}
        </div>
      ) : null}
      <Checkbox
        label={t(`${namespace}.pagingLabel`)}
        checked={pagingEnabled}
        onChange={(event) => onPagingEnabledChange(event.target.checked)}
      />
      {pagingEnabled ? (
        <FormField label={t(`${namespace}.pageSizeLabel`)}>
          <TextInput
            type="number"
            min={1}
            value={pageSize}
            onChange={(event) => onPageSizeChange(Number(event.target.value))}
          />
        </FormField>
      ) : null}
      <Button
        variant="primary"
        disabled={!schemaName || executing}
        loading={executing}
        onClick={onExecute}
        data-testid="query-editor-execute-button"
      >
        {t(`${namespace}.executeButton`)}
      </Button>

      {result ? (
        <>
          <DataTable
            columns={resultColumns}
            rows={result.rows}
            rowKey={(row) => JSON.stringify(row)}
            emptyState={<EmptyState message={t(`${namespace}.resultEmpty`)} />}
          />
          {pagingEnabled && result.totalCount != null ? (
            <Pagination page={page} totalPages={totalPages} onChange={onPageChange} />
          ) : null}
        </>
      ) : null}
    </div>
  )
}
