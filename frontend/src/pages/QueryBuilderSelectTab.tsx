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
import { Button, TextInput } from '../design-system/components'
import type { SelectItem } from '../api/queryBuilder'
import type { AvailableColumn } from './QueryBuilderOperandPicker'
import { QueryBuilderOperandPicker } from './QueryBuilderOperandPicker'
import styles from './QueryBuilderItemRow.module.css'

// frontend-components.md 画面2 SELECTタブ。1件以上の選択項目（列参照または集計関数適用、
// AS別名）を追加・削除する（FR-5.4、BR-QUERYBUILDER-09）。
export function QueryBuilderSelectTab({
  columns,
  value,
  onChange,
}: {
  columns: AvailableColumn[]
  value: SelectItem[]
  onChange: (value: SelectItem[]) => void
}) {
  const { t } = useTranslation()

  const addItem = () => {
    const first = columns[0]
    onChange([
      ...value,
      { column: first ? { tableAlias: first.tableAlias, columnName: first.columnName } : null, aggregate: null,
        alias: null },
    ])
  }

  const updateItem = (index: number, item: SelectItem) => {
    onChange(value.map((v, i) => (i === index ? item : v)))
  }

  const removeItem = (index: number) => {
    onChange(value.filter((_, i) => i !== index))
  }

  return (
    <div>
      {value.map((item, index) => (
        <div key={index} className={styles.row} data-testid={`query-builder-select-item-${index}`}>
          <QueryBuilderOperandPicker
            columns={columns}
            allowAggregate
            value={{ column: item.column, aggregate: item.aggregate }}
            onChange={(operand) => updateItem(index, { ...item, column: operand.column, aggregate: operand.aggregate })}
          />
          <TextInput
            placeholder={t('queryBuilder.select.alias')}
            value={item.alias ?? ''}
            onChange={(e) => updateItem(index, { ...item, alias: e.target.value || null })}
          />
          <Button
            variant="ghost"
            onClick={() => removeItem(index)}
            data-testid={`query-builder-select-item-${index}-remove`}
          >
            {t('action.remove')}
          </Button>
        </div>
      ))}
      <Button onClick={addItem} data-testid="query-builder-select-item-add">
        {t('queryBuilder.select.add')}
      </Button>
    </div>
  )
}
