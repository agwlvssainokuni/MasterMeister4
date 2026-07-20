import { Component, type ErrorInfo, type ReactNode } from 'react';

import { DefaultFallback } from './DefaultFallback';

export interface ErrorBoundaryProps {
  children: ReactNode;
  /** Custom fallback UI; defaults to a generic, translated message (no internal error details). */
  fallback?: ReactNode;
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

// One instance is mounted at the application root (DP-UNIT01-1). Individual
// feature units do not add their own nested ErrorBoundary.
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Developer-facing output only (DP-UNIT01-2). Sending this to the audit
    // logging backend is out of scope here; that connection is wired up once
    // UNIT-02 builds the logging infrastructure.
    console.error('ErrorBoundary caught an error', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return <div data-testid={this.props.testId}>{this.props.fallback ?? <DefaultFallback />}</div>;
    }
    return this.props.children;
  }
}
