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

import { StrictMode, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import './design-system/tokens/fonts.ts'
import './design-system/tokens/tokens.css'
import './i18n'
import { ThemeProvider } from './design-system/theme/ThemeProvider.tsx'
import { ErrorBoundary } from './design-system/ErrorBoundary.tsx'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <ThemeProvider>
        <Suspense fallback={null}>
          <App />
        </Suspense>
      </ThemeProvider>
    </ErrorBoundary>
  </StrictMode>,
)
