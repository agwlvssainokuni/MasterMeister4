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
import { RdbmsConnectionListPage } from './pages/RdbmsConnectionListPage'
import { SchemaDetailPage } from './pages/SchemaDetailPage'
import { GroupManagementPage } from './pages/GroupManagementPage'
import { AccessPermissionTreePage } from './pages/AccessPermissionTreePage'
import { MasterDataConnectionListPage } from './pages/MasterDataConnectionListPage'
import { MasterDataTableListPage } from './pages/MasterDataTableListPage'
import { MasterDataRecordListPage } from './pages/MasterDataRecordListPage'
import { SavedQueryConnectionListPage } from './pages/SavedQueryConnectionListPage'
import { SavedQueryListPage } from './pages/SavedQueryListPage'
import { SavedQueryEditorPage } from './pages/SavedQueryEditorPage'
import { QueryExecutionConnectionListPage } from './pages/QueryExecutionConnectionListPage'
import { QueryExecutionPage } from './pages/QueryExecutionPage'
import { QueryBuilderConnectionListPage } from './pages/QueryBuilderConnectionListPage'
import { QueryBuilderPage } from './pages/QueryBuilderPage'
import { QueryHistoryConnectionListPage } from './pages/QueryHistoryConnectionListPage'
import { QueryHistoryPage } from './pages/QueryHistoryPage'

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
          <Route
            path="/connections"
            element={
              <ProtectedRoute>
                <RdbmsConnectionListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/connections/:id/schema"
            element={
              <ProtectedRoute>
                <SchemaDetailPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/groups"
            element={
              <ProtectedRoute>
                <GroupManagementPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/permissions/:connectionId"
            element={
              <ProtectedRoute>
                <AccessPermissionTreePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/master-data"
            element={
              <ProtectedRoute>
                <MasterDataConnectionListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/master-data/:connectionId"
            element={
              <ProtectedRoute>
                <MasterDataTableListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/master-data/:connectionId/:schemaName/:tableName"
            element={
              <ProtectedRoute>
                <MasterDataRecordListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/saved-queries"
            element={
              <ProtectedRoute>
                <SavedQueryConnectionListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/saved-queries/:connectionId"
            element={
              <ProtectedRoute>
                <SavedQueryListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/saved-queries/:connectionId/new"
            element={
              <ProtectedRoute>
                <SavedQueryEditorPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/saved-queries/:connectionId/:savedQueryId"
            element={
              <ProtectedRoute>
                <SavedQueryEditorPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/query-execution"
            element={
              <ProtectedRoute>
                <QueryExecutionConnectionListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/query-execution/:connectionId"
            element={
              <ProtectedRoute>
                <QueryExecutionPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/query-builder"
            element={
              <ProtectedRoute>
                <QueryBuilderConnectionListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/query-builder/:connectionId"
            element={
              <ProtectedRoute>
                <QueryBuilderPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/query-history"
            element={
              <ProtectedRoute>
                <QueryHistoryConnectionListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/query-history/:connectionId"
            element={
              <ProtectedRoute>
                <QueryHistoryPage />
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
