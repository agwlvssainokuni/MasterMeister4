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
import { FormField, Select, TextInput } from '../design-system/components'
import type { AccessibleBuilderTable, FromClause } from '../api/queryBuilder'
import styles from './QueryBuilderFromTab.module.css'

interface QueryBuilderFromTabProps {
  schemaName: string
  tables: AccessibleBuilderTable[]
  value: FromClause | null
  onChange: (value: FromClause) => void
}

// frontend-components.md 画面2 FROMタブ。起点テーブル1件（必須）を選択する。
// エイリアス未指定時はテーブル名を初期値として自動採用する（business-logic-model.md §4）。
export function QueryBuilderFromTab({ schemaName, tables, value, onChange }: QueryBuilderFromTabProps) {
  const { t } = useTranslation()

  const onSelectTable = (tableName: string) => {
    if (!tableName) {
      return
    }
    onChange({ schemaName, tableName, alias: value?.tableName === tableName ? value.alias : tableName })
  }

  return (
    <div className={styles.row}>
      <FormField label={t('queryBuilder.from.table')}>
        <Select value={value?.tableName ?? ''} onChange={(e) => onSelectTable(e.target.value)}>
          <option value="">{t('queryBuilder.selectPlaceholder')}</option>
          {tables.map((table) => (
            <option key={table.tableName} value={table.tableName}>
              {table.tableName}
            </option>
          ))}
        </Select>
      </FormField>
      {value ? (
        <FormField label={t('queryBuilder.from.alias')}>
          <TextInput
            value={value.alias}
            onChange={(e) => onChange({ ...value, alias: e.target.value })}
          />
        </FormField>
      ) : null}
    </div>
  )
}
