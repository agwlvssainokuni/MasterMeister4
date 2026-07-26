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
import type { OrderByItem, SortDirection } from '../api/queryBuilder'
import type { AvailableColumn } from './QueryBuilderOperandPicker'
import { QueryBuilderOperandPicker } from './QueryBuilderOperandPicker'

// frontend-components.md 画面2 ORDER BYタブ。列参照または集計関数適用の結果＋昇順/降順。
export function QueryBuilderOrderByTab({
  columns,
  value,
  onChange,
}: {
  columns: AvailableColumn[]
  value: OrderByItem[]
  onChange: (value: OrderByItem[]) => void
}) {
  const { t } = useTranslation()

  const addItem = () => {
    const first = columns[0]
    onChange([
      ...value,
      { column: first ? { tableAlias: first.tableAlias, columnName: first.columnName } : null, aggregate: null,
        direction: 'ASC' },
    ])
  }

  const updateItem = (index: number, item: OrderByItem) => {
    onChange(value.map((v, i) => (i === index ? item : v)))
  }

  const removeItem = (index: number) => {
    onChange(value.filter((_, i) => i !== index))
  }

  return (
    <div>
      {value.map((item, index) => (
        <div key={index} data-testid={`query-builder-orderby-${index}`}>
          <QueryBuilderOperandPicker
            columns={columns}
            allowAggregate
            value={{ column: item.column, aggregate: item.aggregate }}
            onChange={(operand) => updateItem(index, { ...item, column: operand.column, aggregate: operand.aggregate })}
          />
          <Select
            value={item.direction}
            onChange={(e) => updateItem(index, { ...item, direction: e.target.value as SortDirection })}
          >
            <option value="ASC">{t('queryBuilder.orderBy.asc')}</option>
            <option value="DESC">{t('queryBuilder.orderBy.desc')}</option>
          </Select>
          <Button variant="ghost" onClick={() => removeItem(index)} data-testid={`query-builder-orderby-${index}-remove`}>
            {t('action.remove')}
          </Button>
        </div>
      ))}
      <Button onClick={addItem} data-testid="query-builder-orderby-add">
        {t('queryBuilder.orderBy.add')}
      </Button>
    </div>
  )
}
