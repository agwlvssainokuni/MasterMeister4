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

export type UserStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'DISABLED'

export interface UserSummary {
  id: number
  email: string
  fullName: string
  status: UserStatus
  createdAt: string
}

export function listUsers(status?: UserStatus): Promise<UserSummary[]> {
  const query = status ? `?status=${status}` : ''
  return apiFetch<UserSummary[]>(`/api/admin/users${query}`, { auth: true })
}

export function approveUser(id: number): Promise<void> {
  return apiFetch<void>(`/api/admin/users/${id}/approve`, { method: 'POST', auth: true })
}

export function rejectUser(id: number): Promise<void> {
  return apiFetch<void>(`/api/admin/users/${id}/reject`, { method: 'POST', auth: true })
}

export function disableUser(id: number): Promise<void> {
  return apiFetch<void>(`/api/admin/users/${id}/disable`, { method: 'POST', auth: true })
}

export function enableUser(id: number): Promise<void> {
  return apiFetch<void>(`/api/admin/users/${id}/enable`, { method: 'POST', auth: true })
}
