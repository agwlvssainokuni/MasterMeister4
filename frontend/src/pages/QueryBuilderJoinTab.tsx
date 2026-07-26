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
import type { AccessibleBuilderTable, ColumnRef, JoinClause, JoinType } from '../api/queryBuilder'
import type { AvailableColumn } from './QueryBuilderOperandPicker'
import styles from './QueryBuilderJoinTab.module.css'

const JOIN_TYPES: JoinType[] = ['INNER', 'LEFT', 'RIGHT']

// frontend-components.md 画面2 JOINタブ。0件以上のJOIN、結合種別（INNER/LEFT/RIGHT、
// BR-QUERYBUILDER-02）・結合先テーブル・結合条件（等価結合、複数可、BR-QUERYBUILDER-03）を指定する。
export function QueryBuilderJoinTab({
  schemaName,
  tables,
  columns,
  value,
  onChange,
}: {
  schemaName: string
  tables: AccessibleBuilderTable[]
  columns: AvailableColumn[]
  value: JoinClause[]
  onChange: (value: JoinClause[]) => void
}) {
  const { t } = useTranslation()
  const columnKey = (c: ColumnRef) => `${c.tableAlias}.${c.columnName}`

  const addJoin = () => {
    const first = tables[0]
    if (!first) {
      return
    }
    onChange([
      ...value,
      {
        joinType: 'INNER',
        schemaName,
        tableName: first.tableName,
        alias: first.tableName,
        onConditions: [],
      },
    ])
  }

  const updateJoin = (index: number, join: JoinClause) => {
    onChange(value.map((v, i) => (i === index ? join : v)))
  }

  const removeJoin = (index: number) => {
    onChange(value.filter((_, i) => i !== index))
  }

  const addCondition = (index: number) => {
    const join = value[index]
    const left = columns[0]
    const right = columns[1] ?? columns[0]
    if (!left || !right) {
      return
    }
    updateJoin(index, {
      ...join,
      onConditions: [
        ...join.onConditions,
        {
          leftColumn: { tableAlias: left.tableAlias, columnName: left.columnName },
          rightColumn: { tableAlias: right.tableAlias, columnName: right.columnName },
        },
      ],
    })
  }

  const removeCondition = (joinIndex: number, conditionIndex: number) => {
    const join = value[joinIndex]
    updateJoin(joinIndex, {
      ...join,
      onConditions: join.onConditions.filter((_, i) => i !== conditionIndex),
    })
  }

  return (
    <div>
      {value.map((join, index) => {
        return (
          <div key={index} className={styles.join} data-testid={`query-builder-join-${index}`}>
            <div className={styles.joinRow}>
              <Select
                value={join.joinType}
                onChange={(e) => updateJoin(index, { ...join, joinType: e.target.value as JoinType })}
              >
                {JOIN_TYPES.map((jt) => (
                  <option key={jt} value={jt}>
                    {jt}
                  </option>
                ))}
              </Select>
              <Select
                value={join.tableName}
                onChange={(e) => {
                  const tableName = e.target.value
                  updateJoin(index, { ...join, tableName, alias: tableName, onConditions: [] })
                }}
              >
                {tables.map((tbl) => (
                  <option key={tbl.tableName} value={tbl.tableName}>
                    {tbl.tableName}
                  </option>
                ))}
              </Select>
              <TextInput
                placeholder={t('queryBuilder.join.alias')}
                value={join.alias}
                onChange={(e) => updateJoin(index, { ...join, alias: e.target.value })}
              />
              <Button variant="ghost" onClick={() => removeJoin(index)} data-testid={`query-builder-join-${index}-remove`}>
                {t('action.remove')}
              </Button>
            </div>
            <div className={styles.conditions}>
              {join.onConditions.map((condition, conditionIndex) => (
                <div
                  key={conditionIndex}
                  className={styles.conditionRow}
                  data-testid={`query-builder-join-${index}-condition-${conditionIndex}`}
                >
                  <Select
                    value={columnKey(condition.leftColumn)}
                    onChange={(e) => {
                      const [tableAlias, columnName] = e.target.value.split('.')
                      if (!tableAlias || !columnName) {
                        return
                      }
                      const nextConditions = join.onConditions.map((c, i) =>
                        i === conditionIndex ? { ...c, leftColumn: { tableAlias, columnName } } : c,
                      )
                      updateJoin(index, { ...join, onConditions: nextConditions })
                    }}
                  >
                    {columns.map((c) => (
                      <option key={columnKey(c)} value={columnKey(c)}>
                        {c.tableAlias}.{c.columnName}
                      </option>
                    ))}
                  </Select>
                  <span>=</span>
                  <Select
                    value={columnKey(condition.rightColumn)}
                    onChange={(e) => {
                      const [tableAlias, columnName] = e.target.value.split('.')
                      if (!tableAlias || !columnName) {
                        return
                      }
                      const nextConditions = join.onConditions.map((c, i) =>
                        i === conditionIndex ? { ...c, rightColumn: { tableAlias, columnName } } : c,
                      )
                      updateJoin(index, { ...join, onConditions: nextConditions })
                    }}
                  >
                    {columns.map((c) => (
                      <option key={columnKey(c)} value={columnKey(c)}>
                        {c.tableAlias}.{c.columnName}
                      </option>
                    ))}
                  </Select>
                  <Button
                    variant="ghost"
                    onClick={() => removeCondition(index, conditionIndex)}
                    data-testid={`query-builder-join-${index}-condition-${conditionIndex}-remove`}
                  >
                    {t('action.remove')}
                  </Button>
                </div>
              ))}
              <Button onClick={() => addCondition(index)} data-testid={`query-builder-join-${index}-condition-add`}>
                {t('queryBuilder.join.addCondition')}
              </Button>
            </div>
          </div>
        )
      })}
      <Button onClick={addJoin} data-testid="query-builder-join-add">
        {t('queryBuilder.join.add')}
      </Button>
    </div>
  )
}
