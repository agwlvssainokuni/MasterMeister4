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

import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'

// モック画面はPublicLayout/AppShell経由でThemeToggle（要ThemeProvider）と
// ナビゲーション（要Router）に依存するため、テスト用に共通ラップを提供する。
export function renderMock(ui: ReactElement) {
  return render(
    <ThemeProvider>
      <MemoryRouter>{ui}</MemoryRouter>
    </ThemeProvider>,
  )
}

// UNIT-02の画面はAuthContext（useAuth）にも依存するため、AuthProviderを含めた
// ラップを提供する。initialEntriesでRegisterStep2Pageの`?token=`等を指定できる。
export function renderPage(ui: ReactElement, options?: { initialEntries?: string[] }) {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={options?.initialEntries}>
        <AuthProvider>{ui}</AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}
