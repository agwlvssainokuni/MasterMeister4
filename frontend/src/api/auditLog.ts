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

export type ResultStatus = 'SUCCESS' | 'FAILURE'

export type AuditEventType =
  | 'LOGIN'
  | 'LOGOUT'
  | 'LOGIN_FAILURE'
  | 'REGISTRATION_REQUESTED'
  | 'REGISTRATION_COMPLETED'
  | 'USER_APPROVED'
  | 'USER_REJECTED'
  | 'USER_DISABLED'
  | 'USER_ENABLED'
  | 'TOKEN_REUSE_DETECTED'
  | 'CONNECTION_REGISTERED'
  | 'CONNECTION_UPDATED'
  | 'CONNECTION_DELETED'
  | 'SCHEMA_IMPORTED'
  | 'PERMISSION_CHANGED'
  | 'GROUP_CREATED'
  | 'GROUP_RENAMED'
  | 'GROUP_DELETED'
  | 'GROUP_MEMBER_ADDED'
  | 'GROUP_MEMBER_REMOVED'
  | 'PERMISSION_YAML_EXPORTED'
  | 'PERMISSION_YAML_IMPORTED'
  | 'MASTER_DATA_BULK_ACCESSED'
  | 'MASTER_DATA_BATCH_APPLIED'
  | 'QUERY_EXECUTED'
  | 'QUERY_SAVED'
  | 'QUERY_UPDATED'
  | 'QUERY_RETIRED'

export const AUDIT_EVENT_TYPES: AuditEventType[] = [
  'LOGIN',
  'LOGOUT',
  'LOGIN_FAILURE',
  'REGISTRATION_REQUESTED',
  'REGISTRATION_COMPLETED',
  'USER_APPROVED',
  'USER_REJECTED',
  'USER_DISABLED',
  'USER_ENABLED',
  'TOKEN_REUSE_DETECTED',
  'CONNECTION_REGISTERED',
  'CONNECTION_UPDATED',
  'CONNECTION_DELETED',
  'SCHEMA_IMPORTED',
  'PERMISSION_CHANGED',
  'GROUP_CREATED',
  'GROUP_RENAMED',
  'GROUP_DELETED',
  'GROUP_MEMBER_ADDED',
  'GROUP_MEMBER_REMOVED',
  'PERMISSION_YAML_EXPORTED',
  'PERMISSION_YAML_IMPORTED',
  'MASTER_DATA_BULK_ACCESSED',
  'MASTER_DATA_BATCH_APPLIED',
  'QUERY_EXECUTED',
  'QUERY_SAVED',
  'QUERY_UPDATED',
  'QUERY_RETIRED',
]

export interface AuditLogEntry {
  id: number
  occurredAt: string
  userId: number | null
  userDisplayName: string | null
  connectionId: number | null
  connectionDisplayName: string | null
  eventType: AuditEventType
  targetResource: string | null
  resultStatus: ResultStatus
  detail: string | null
}

export interface AuditLogPage {
  content: AuditLogEntry[]
  page: number
  pageSize: number
  totalElements: number
  totalPages: number
}

export interface AuditLogSearchParams {
  occurredAtFrom?: string
  occurredAtTo?: string
  eventType?: AuditEventType
  userId?: number
  connectionId?: number
  resultStatus?: ResultStatus
  page?: number
  pageSize?: number
}

export function listAuditLog(params: AuditLogSearchParams): Promise<AuditLogPage> {
  const query = new URLSearchParams()
  if (params.occurredAtFrom) {
    query.set('occurredAtFrom', params.occurredAtFrom)
  }
  if (params.occurredAtTo) {
    query.set('occurredAtTo', params.occurredAtTo)
  }
  if (params.eventType) {
    query.set('eventType', params.eventType)
  }
  if (params.userId !== undefined) {
    query.set('userId', String(params.userId))
  }
  if (params.connectionId !== undefined) {
    query.set('connectionId', String(params.connectionId))
  }
  if (params.resultStatus) {
    query.set('resultStatus', params.resultStatus)
  }
  query.set('page', String(params.page ?? 0))
  query.set('pageSize', String(params.pageSize ?? 50))
  return apiFetch<AuditLogPage>(`/api/admin/audit-log?${query.toString()}`, { auth: true })
}
