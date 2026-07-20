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

import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { AuthProvider } from './AuthContext'
import { ProtectedRoute } from './ProtectedRoute'
import { clearTokens, setTokens } from './tokenStorage'

function renderWithRoutes() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>ログイン画面</p>} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <p>保護されたコンテンツ</p>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute', () => {
  afterEach(() => {
    clearTokens()
  })

  it('未認証の場合は/loginへリダイレクトする', () => {
    renderWithRoutes()
    expect(screen.getByText('ログイン画面')).toBeInTheDocument()
    expect(screen.queryByText('保護されたコンテンツ')).not.toBeInTheDocument()
  })

  it('認証済みの場合はchildrenを表示する', () => {
    setTokens('access-1', 'refresh-1')
    renderWithRoutes()
    expect(screen.getByText('保護されたコンテンツ')).toBeInTheDocument()
  })
})
