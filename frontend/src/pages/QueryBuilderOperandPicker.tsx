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

import { useTranslation } from 'react-i18next'
import { Checkbox, Select } from '../design-system/components'
import type { AggregateFunction, ColumnRef } from '../api/queryBuilder'
import styles from './QueryBuilderOperandPicker.module.css'

export interface AvailableColumn {
  tableAlias: string
  columnName: string
  dataTypeCategory: 'NUMERIC' | 'DATETIME' | 'STRING' | 'BOOLEAN'
}

export interface Operand {
  column: ColumnRef | null
  aggregate: { function: AggregateFunction; column: ColumnRef; distinct: boolean } | null
}

const AGGREGATE_FUNCTIONS: AggregateFunction[] = ['COUNT', 'SUM', 'AVG', 'MIN', 'MAX']

/**
 * SELECT/HAVING/ORDER BYタブで共通利用する、列参照または集計関数適用のいずれかを選択する
 * 部品（Part 2実装時の判断。3タブでの重複を避けるため共有コンポーネントとして抽出）。
 * business-logic-model.md §4〜5。
 */
export function QueryBuilderOperandPicker({
  columns,
  allowAggregate,
  value,
  onChange,
}: {
  columns: AvailableColumn[]
  allowAggregate: boolean
  value: Operand
  onChange: (value: Operand) => void
}) {
  const { t } = useTranslation()
  const mode = value.aggregate ? 'aggregate' : 'column'
  const columnKey = (c: ColumnRef) => `${c.tableAlias}.${c.columnName}`

  const onSelectPlainColumn = (key: string) => {
    const [tableAlias, columnName] = key.split('.')
    if (!tableAlias || !columnName) {
      return
    }
    onChange({ column: { tableAlias, columnName }, aggregate: null })
  }

  const onSelectAggregateColumn = (key: string) => {
    const [tableAlias, columnName] = key.split('.')
    if (!tableAlias || !columnName) {
      return
    }
    onChange({
      column: null,
      aggregate: {
        function: value.aggregate?.function ?? 'COUNT',
        column: { tableAlias, columnName },
        distinct: value.aggregate?.distinct ?? false,
      },
    })
  }

  return (
    <span className={styles.picker}>
      {allowAggregate ? (
        <Select
          value={mode}
          onChange={(e) => {
            if (e.target.value === 'aggregate') {
              const first = columns[0]
              onChange({
                column: null,
                aggregate: first
                  ? { function: 'COUNT', column: { tableAlias: first.tableAlias, columnName: first.columnName },
                    distinct: false }
                  : null,
              })
            } else {
              const first = columns[0]
              onChange({ column: first ? { tableAlias: first.tableAlias, columnName: first.columnName } : null,
                aggregate: null })
            }
          }}
        >
          <option value="column">{t('queryBuilder.operand.column')}</option>
          <option value="aggregate">{t('queryBuilder.operand.aggregate')}</option>
        </Select>
      ) : null}
      {mode === 'aggregate' && value.aggregate ? (
        <>
          <Select
            value={value.aggregate.function}
            onChange={(e) =>
              onChange({
                column: null,
                aggregate: { ...value.aggregate!, function: e.target.value as AggregateFunction },
              })
            }
          >
            {AGGREGATE_FUNCTIONS.map((fn) => (
              <option key={fn} value={fn}>
                {fn}
              </option>
            ))}
          </Select>
          <Select value={columnKey(value.aggregate.column)} onChange={(e) => onSelectAggregateColumn(e.target.value)}>
            {columns.map((c) => (
              <option key={columnKey(c)} value={columnKey(c)}>
                {c.tableAlias}.{c.columnName}
              </option>
            ))}
          </Select>
          <Checkbox
            checked={value.aggregate.distinct}
            onChange={(e) => onChange({ column: null, aggregate: { ...value.aggregate!, distinct: e.target.checked } })}
            label="DISTINCT"
          />
        </>
      ) : (
        <Select
          value={value.column ? columnKey(value.column) : ''}
          onChange={(e) => onSelectPlainColumn(e.target.value)}
        >
          <option value="">{t('queryBuilder.selectPlaceholder')}</option>
          {columns.map((c) => (
            <option key={columnKey(c)} value={columnKey(c)}>
              {c.tableAlias}.{c.columnName}
            </option>
          ))}
        </Select>
      )}
    </span>
  )
}
