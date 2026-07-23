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

export interface GroupSummary {
  id: number
  name: string
  memberCount: number
  createdAt: string
}

export interface GroupMember {
  userId: number
  email: string
  fullName: string
}

export function listGroups(): Promise<GroupSummary[]> {
  return apiFetch<GroupSummary[]>('/api/admin/groups', { auth: true })
}

export function createGroup(name: string): Promise<GroupSummary> {
  return apiFetch<GroupSummary>('/api/admin/groups', { method: 'POST', body: { name }, auth: true })
}

export function renameGroup(id: number, name: string): Promise<GroupSummary> {
  return apiFetch<GroupSummary>(`/api/admin/groups/${id}`, { method: 'PUT', body: { name }, auth: true })
}

export function deleteGroup(id: number): Promise<void> {
  return apiFetch<void>(`/api/admin/groups/${id}`, { method: 'DELETE', auth: true })
}

export function listMembers(id: number): Promise<GroupMember[]> {
  return apiFetch<GroupMember[]>(`/api/admin/groups/${id}/members`, { auth: true })
}

export function addMember(id: number, userId: number): Promise<void> {
  return apiFetch<void>(`/api/admin/groups/${id}/members`, {
    method: 'POST',
    body: { userId },
    auth: true,
  })
}

export function removeMember(id: number, userId: number): Promise<void> {
  return apiFetch<void>(`/api/admin/groups/${id}/members/${userId}`, { method: 'DELETE', auth: true })
}
