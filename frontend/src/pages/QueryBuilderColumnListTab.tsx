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
import { Button, Select } from '../design-system/components'
import type { ColumnRef } from '../api/queryBuilder'
import type { AvailableColumn } from './QueryBuilderOperandPicker'

// frontend-components.md 画面2 GROUP BYタブ。列参照のみ（集計関数は不可、標準SQLのセマンティクス）。
export function QueryBuilderColumnListTab({
  columns,
  value,
  onChange,
  testIdPrefix,
}: {
  columns: AvailableColumn[]
  value: ColumnRef[]
  onChange: (value: ColumnRef[]) => void
  testIdPrefix: string
}) {
  const { t } = useTranslation()
  const columnKey = (c: ColumnRef) => `${c.tableAlias}.${c.columnName}`

  const addColumn = () => {
    const first = columns[0]
    if (!first) {
      return
    }
    onChange([...value, { tableAlias: first.tableAlias, columnName: first.columnName }])
  }

  const updateColumn = (index: number, ref: ColumnRef) => {
    onChange(value.map((v, i) => (i === index ? ref : v)))
  }

  const removeColumn = (index: number) => {
    onChange(value.filter((_, i) => i !== index))
  }

  return (
    <div>
      {value.map((ref, index) => (
        <div key={index} data-testid={`${testIdPrefix}-${index}`}>
          <Select
            value={columnKey(ref)}
            onChange={(e) => {
              const [tableAlias, columnName] = e.target.value.split('.')
              if (tableAlias && columnName) {
                updateColumn(index, { tableAlias, columnName })
              }
            }}
          >
            {columns.map((c) => (
              <option key={columnKey(c)} value={columnKey(c)}>
                {c.tableAlias}.{c.columnName}
              </option>
            ))}
          </Select>
          <Button variant="ghost" onClick={() => removeColumn(index)} data-testid={`${testIdPrefix}-${index}-remove`}>
            {t('action.remove')}
          </Button>
        </div>
      ))}
      <Button onClick={addColumn} data-testid={`${testIdPrefix}-add`}>
        {t('queryBuilder.groupBy.add')}
      </Button>
    </div>
  )
}
