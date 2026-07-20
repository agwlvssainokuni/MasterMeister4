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

import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { RegisterStep1Page } from './pages/RegisterStep1Page'
import { RegisterStep2Page } from './pages/RegisterStep2Page'
import { UserManagementPage } from './pages/UserManagementPage'

// devビルド限定ルート（/mock/*）。lazy()呼び出し自体をimport.meta.env.DEVの
// 三項演算子内に置くことで、本番ビルド時（DEV=falseへ静的に置換される）に
// 到達不能なブランチとしてバンドラーに削除させ、import()のコード分割チャンクごと
// 本番バンドルから排除する（SECURITY-09対応）。JSX側だけを条件分岐させる方式では
// lazy()呼び出し自体は副作用ありとみなされ除去されないため、この方式にしている。
const MockRoutes = import.meta.env.DEV ? lazy(() => import('./mocks/MockRoutes')) : null

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterStep1Page />} />
          <Route path="/register/complete" element={<RegisterStep2Page />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <HomePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/users"
            element={
              <ProtectedRoute>
                <UserManagementPage />
              </ProtectedRoute>
            }
          />
          {MockRoutes ? (
            <Route
              path="/mock/*"
              element={
                <Suspense fallback={null}>
                  <MockRoutes />
                </Suspense>
              }
            />
          ) : null}
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
