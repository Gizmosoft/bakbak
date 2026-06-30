import { Component, type ErrorInfo, type ReactNode } from 'react';

import { ErrorState } from '@/components/ui/ErrorState';

type Props = {
  children: ReactNode;
};

type State = {
  hasError: boolean;
};

/** Catches SQLite / render failures on the chat screen instead of crashing the app. */
export class ChatErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    if (__DEV__) {
      console.error('[ChatErrorBoundary]', error, info.componentStack);
    }
  }

  private handleRetry = (): void => {
    this.setState({ hasError: false });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <ErrorState
          message="Storage unavailable — restart the app if this persists"
          onRetry={this.handleRetry}
        />
      );
    }

    return this.props.children;
  }
}
