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

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { ErrorBoundary, ThemeProvider } from './design-system'
import { CatalogPage } from './design-system/catalog'
import App from './App.tsx'

// No routing library has been introduced yet (deferred to whichever later unit
// first needs multi-screen navigation). Until then, /catalog is served via this
// one-off path check rather than a router dependency.
const page = window.location.pathname === '/catalog' ? <CatalogPage /> : <App />

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <ErrorBoundary>{page}</ErrorBoundary>
    </ThemeProvider>
  </StrictMode>,
)
