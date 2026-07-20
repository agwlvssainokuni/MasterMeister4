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

import { Navigate, Route, Routes } from 'react-router-dom'
import { CatalogPage } from './CatalogPage'
import { DashboardMock } from './screens/DashboardMock'
import { LoginMock } from './screens/LoginMock'
import { MasterDataMock } from './screens/MasterDataMock'
import { PermissionsMock } from './screens/PermissionsMock'
import { RegisterMock } from './screens/RegisterMock'

// /mock/* 配下のルーティング。パス割当はfrontend-components.mdで確定済み。
export default function MockRoutes() {
  return (
    <Routes>
      <Route index element={<Navigate to="catalog" replace />} />
      <Route path="catalog" element={<CatalogPage />} />
      <Route path="login" element={<LoginMock />} />
      <Route path="register" element={<RegisterMock />} />
      <Route path="dashboard" element={<DashboardMock />} />
      <Route path="master-data" element={<MasterDataMock />} />
      <Route path="permissions" element={<PermissionsMock />} />
    </Routes>
  )
}
