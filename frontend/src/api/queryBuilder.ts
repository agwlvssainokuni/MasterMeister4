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

import { apiFetch } from './http'

export type JoinType = 'INNER' | 'LEFT' | 'RIGHT'
export type AggregateFunction = 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX'
export type ConditionOperator =
  | 'EQ'
  | 'NE'
  | 'LT'
  | 'LE'
  | 'GT'
  | 'GE'
  | 'STARTS_WITH'
  | 'CONTAINS'
  | 'IS_NULL'
  | 'IS_NOT_NULL'
export type ColumnDataTypeCategory = 'NUMERIC' | 'DATETIME' | 'STRING' | 'BOOLEAN'
export type SortDirection = 'ASC' | 'DESC'

export interface ColumnRef {
  tableAlias: string
  columnName: string
}

export interface FromClause {
  schemaName: string
  tableName: string
  alias: string
}

export interface JoinCondition {
  leftColumn: ColumnRef
  rightColumn: ColumnRef
}

export interface JoinClause {
  joinType: JoinType
  schemaName: string
  tableName: string
  alias: string
  onConditions: JoinCondition[]
}

export interface AggregateExpression {
  function: AggregateFunction
  column: ColumnRef
  distinct: boolean
}

export interface SelectItem {
  column: ColumnRef | null
  aggregate: AggregateExpression | null
  alias: string | null
}

export interface Condition {
  column: ColumnRef | null
  aggregate: AggregateExpression | null
  operator: ConditionOperator
  value: string | null
  dataTypeCategory: ColumnDataTypeCategory
}

export interface OrderByItem {
  column: ColumnRef | null
  aggregate: AggregateExpression | null
  direction: SortDirection
}

export interface QueryBuilderState {
  from: FromClause | null
  joins: JoinClause[]
  selectItems: SelectItem[]
  whereConditions: Condition[]
  groupByColumns: ColumnRef[]
  havingConditions: Condition[]
  orderByItems: OrderByItem[]
  limit: number | null
  offset: number | null
}

export interface AccessibleBuilderColumn {
  columnName: string
  dataTypeCategory: ColumnDataTypeCategory
}

export interface AccessibleBuilderTable {
  tableName: string
  tableType: 'TABLE' | 'VIEW'
  columns: AccessibleBuilderColumn[]
}

export function listAccessibleBuilderTables(
  connectionId: number,
  schemaName: string,
): Promise<AccessibleBuilderTable[]> {
  const query = new URLSearchParams({ schemaName })
  return apiFetch<AccessibleBuilderTable[]>(`/api/query-builder/${connectionId}/tables?${query.toString()}`, {
    auth: true,
  })
}

export function generateSql(connectionId: number, state: QueryBuilderState): Promise<{ sql: string }> {
  return apiFetch<{ sql: string }>(`/api/query-builder/${connectionId}/generate`, {
    method: 'POST',
    auth: true,
    body: state,
  })
}

export function parseSql(
  connectionId: number,
  schemaName: string,
  sql: string,
): Promise<QueryBuilderState> {
  return apiFetch<QueryBuilderState>(`/api/query-builder/${connectionId}/parse`, {
    method: 'POST',
    auth: true,
    body: { schemaName, sql },
  })
}
