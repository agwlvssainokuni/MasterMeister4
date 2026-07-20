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

// 画面モック用の静的サンプルデータ（実データ風の日本語サンプル。Q3=A）

export type UserStatus = 'pending' | 'active' | 'disabled'

export interface SampleUser {
  id: string
  email: string
  displayName: string
  status: UserStatus
  registeredAt: string
}

export const sampleUsers: readonly SampleUser[] = [
  {
    id: '1',
    email: 'suzuki@example.com',
    displayName: '鈴木 一郎',
    status: 'active',
    registeredAt: '2026-07-01 09:12:44',
  },
  {
    id: '2',
    email: 'sato@example.com',
    displayName: '佐藤 花子',
    status: 'pending',
    registeredAt: '2026-07-15 18:03:21',
  },
  {
    id: '3',
    email: 'tanaka@example.com',
    displayName: '田中 太郎',
    status: 'active',
    registeredAt: '2026-06-28 10:45:02',
  },
  {
    id: '4',
    email: 'watanabe@example.com',
    displayName: '渡辺 美咲',
    status: 'disabled',
    registeredAt: '2026-05-11 14:22:10',
  },
  {
    id: '5',
    email: 'kobayashi@example.com',
    displayName: '小林 健',
    status: 'pending',
    registeredAt: '2026-07-18 08:55:37',
  },
]

export interface SampleColumnMeta {
  name: string
  type: string
  permission: 'READ' | 'UPDATE'
  primaryKey?: boolean
}

// マスタメンテナンス画面モック: 顧客マスタ
export const customerColumns: readonly SampleColumnMeta[] = [
  { name: 'id', type: 'BIGINT', permission: 'READ', primaryKey: true },
  { name: 'name', type: 'VARCHAR(100)', permission: 'UPDATE' },
  { name: 'kana', type: 'VARCHAR(100)', permission: 'UPDATE' },
  { name: 'credit_limit', type: 'DECIMAL(12,0)', permission: 'UPDATE' },
  { name: 'status', type: 'VARCHAR(20)', permission: 'UPDATE' },
  { name: 'updated_at', type: 'TIMESTAMP', permission: 'READ' },
]

export type CustomerRow = Record<string, string>

export const customerRows: readonly CustomerRow[] = [
  {
    id: '1001',
    name: '株式会社アカツキ商事',
    kana: 'アカツキショウジ',
    credit_limit: '5000000',
    status: 'ACTIVE',
    updated_at: '2026-07-10 11:20:33',
  },
  {
    id: '1002',
    name: '有限会社きらら食品',
    kana: 'キララショクヒン',
    credit_limit: '1200000',
    status: 'ACTIVE',
    updated_at: '2026-07-12 16:44:01',
  },
  {
    id: '1003',
    name: 'サンライズ工業株式会社',
    kana: 'サンライズコウギョウ',
    credit_limit: '8000000',
    status: 'HOLD',
    updated_at: '2026-07-01 09:05:18',
  },
]

export interface SamplePermission {
  id: string
  principal: string
  resource: string
  primary: 'READ' | 'UPDATE' | 'NONE'
  auxiliary: readonly ('CREATE' | 'DELETE')[]
}

// 権限設定画面モック（汎用レイアウト。詳細な権限モデルはUNIT-04で正式設計）
export const samplePermissions: readonly SamplePermission[] = [
  {
    id: '1',
    principal: '営業部グループ',
    resource: 'sales.customers',
    primary: 'UPDATE',
    auxiliary: ['CREATE'],
  },
  {
    id: '2',
    principal: '経理部グループ',
    resource: 'sales.orders',
    primary: 'READ',
    auxiliary: [],
  },
  {
    id: '3',
    principal: '鈴木 一郎',
    resource: 'sales.customers',
    primary: 'READ',
    auxiliary: [],
  },
]
