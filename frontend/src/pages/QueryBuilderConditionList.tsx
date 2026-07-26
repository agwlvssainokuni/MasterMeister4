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
import { Button, Select, TextInput } from '../design-system/components'
import type { Condition, ColumnDataTypeCategory, ConditionOperator } from '../api/queryBuilder'
import type { AvailableColumn } from './QueryBuilderOperandPicker'
import { QueryBuilderOperandPicker } from './QueryBuilderOperandPicker'
import styles from './QueryBuilderItemRow.module.css'

function resolveCategory(condition: Condition, columns: AvailableColumn[]): ColumnDataTypeCategory {
  if (condition.column) {
    const found = columns.find(
      (c) => c.tableAlias === condition.column!.tableAlias && c.columnName === condition.column!.columnName,
    )
    return found?.dataTypeCategory ?? 'STRING'
  }
  if (condition.aggregate) {
    if (condition.aggregate.function === 'COUNT') {
      return 'NUMERIC'
    }
    const found = columns.find(
      (c) =>
        c.tableAlias === condition.aggregate!.column.tableAlias &&
        c.columnName === condition.aggregate!.column.columnName,
    )
    return found?.dataTypeCategory ?? 'NUMERIC'
  }
  return 'STRING'
}

function operatorsFor(category: ColumnDataTypeCategory): ConditionOperator[] {
  const common: ConditionOperator[] = ['EQ', 'NE', 'IS_NULL', 'IS_NOT_NULL']
  if (category === 'STRING') {
    return [...common, 'STARTS_WITH', 'CONTAINS']
  }
  return [...common, 'LT', 'LE', 'GT', 'GE']
}

// frontend-components.md 画面2 WHERE/HAVINGタブ共通コンポーネント（BR-QUERYBUILDER-04）。
// HAVINGタブでは集計関数適用の結果に対する比較も許容する（allowAggregate=true）。
export function QueryBuilderConditionList({
  columns,
  allowAggregate,
  value,
  onChange,
  testIdPrefix,
}: {
  columns: AvailableColumn[]
  allowAggregate: boolean
  value: Condition[]
  onChange: (value: Condition[]) => void
  testIdPrefix: string
}) {
  const { t } = useTranslation()

  const addCondition = () => {
    const first = columns[0]
    onChange([
      ...value,
      {
        column: first ? { tableAlias: first.tableAlias, columnName: first.columnName } : null,
        aggregate: null,
        operator: 'EQ',
        value: '',
        dataTypeCategory: first?.dataTypeCategory ?? 'STRING',
      },
    ])
  }

  const updateCondition = (index: number, condition: Condition) => {
    onChange(value.map((v, i) => (i === index ? condition : v)))
  }

  const removeCondition = (index: number) => {
    onChange(value.filter((_, i) => i !== index))
  }

  return (
    <div>
      {value.map((condition, index) => {
        const category = resolveCategory(condition, columns)
        const valueless = condition.operator === 'IS_NULL' || condition.operator === 'IS_NOT_NULL'
        return (
          <div key={index} className={styles.row} data-testid={`${testIdPrefix}-${index}`}>
            <QueryBuilderOperandPicker
              columns={columns}
              allowAggregate={allowAggregate}
              value={{ column: condition.column, aggregate: condition.aggregate }}
              onChange={(operand) =>
                updateCondition(index, {
                  ...condition,
                  column: operand.column,
                  aggregate: operand.aggregate,
                  dataTypeCategory: resolveCategory({ ...condition, ...operand }, columns),
                })
              }
            />
            <Select
              value={condition.operator}
              onChange={(e) =>
                updateCondition(index, { ...condition, operator: e.target.value as ConditionOperator })
              }
            >
              {operatorsFor(category).map((op) => (
                <option key={op} value={op}>
                  {t(`queryBuilder.operator.${op}`)}
                </option>
              ))}
            </Select>
            {!valueless ? (
              <TextInput
                value={condition.value ?? ''}
                onChange={(e) => updateCondition(index, { ...condition, value: e.target.value })}
              />
            ) : null}
            <Button variant="ghost" onClick={() => removeCondition(index)} data-testid={`${testIdPrefix}-${index}-remove`}>
              {t('action.remove')}
            </Button>
          </div>
        )
      })}
      <Button onClick={addCondition} data-testid={`${testIdPrefix}-add`}>
        {t('queryBuilder.condition.add')}
      </Button>
    </div>
  )
}
