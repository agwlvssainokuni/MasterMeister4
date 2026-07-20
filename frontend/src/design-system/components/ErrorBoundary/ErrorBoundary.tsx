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

import { Component, type ErrorInfo, type ReactNode } from 'react'

import { DefaultFallback } from './DefaultFallback'

export interface ErrorBoundaryProps {
  children: ReactNode
  /** Custom fallback UI; defaults to a generic, translated message (no internal error details). */
  fallback?: ReactNode
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string
}

interface ErrorBoundaryState {
  hasError: boolean
}

// One instance is mounted at the application root (DP-UNIT01-1). Individual
// feature units do not add their own nested ErrorBoundary.
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Developer-facing output only (DP-UNIT01-2). Sending this to the audit
    // logging backend is out of scope here; that connection is wired up once
    // UNIT-02 builds the logging infrastructure.
    console.error('ErrorBoundary caught an error', error, errorInfo)
  }

  render() {
    if (this.state.hasError) {
      return <div data-testid={this.props.testId}>{this.props.fallback ?? <DefaultFallback />}</div>
    }
    return this.props.children
  }
}
