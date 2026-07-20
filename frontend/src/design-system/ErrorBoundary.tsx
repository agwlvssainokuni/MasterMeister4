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

import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'
import { withTranslation } from 'react-i18next'
import type { WithTranslation } from 'react-i18next'

interface ErrorBoundaryProps extends WithTranslation {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

// SECURITY-15: 描画エラー時にアプリ全体をクラッシュさせず、汎用フォールバックを表示する。
// バックエンドへのエラーレポート送信APIは未実装のため、現時点ではコンソール出力のみ
// 行う（NFR Design Q5=A）。
class ErrorBoundaryBase extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // eslint-disable-next-line no-console
    console.error('[MasterMeister] Unhandled rendering error', error, errorInfo)
  }

  render() {
    if (this.state.hasError) {
      const { t } = this.props
      return (
        <div role="alert" data-testid="error-boundary-fallback">
          {t('state.error')}
        </div>
      )
    }
    return this.props.children
  }
}

export const ErrorBoundary = withTranslation()(ErrorBoundaryBase)
