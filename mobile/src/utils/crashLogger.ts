/**
 * CrashLogger — in-process crash reporting for MCW Mobile.
 *
 * Captures:
 * 1. React render errors (via ErrorBoundary)
 * 2. Unhandled JS Promise rejections
 * 3. Unhandled JS exceptions (via global.ErrorUtils)
 *
 * Reports are stored in memory (last 50) and logged to console.error
 * so they appear in adb logcat under the tag "ReactNativeJS".
 *
 * To see crashes in real time:
 *   adb logcat -s ReactNativeJS:E
 *   adb logcat | grep -E "(FATAL|ReactNative|Error)"
 */

import {Platform} from 'react-native';

export interface CrashReport {
  id: string;
  timestamp: string;
  type: 'render' | 'unhandled_rejection' | 'uncaught_exception' | 'native';
  message: string;
  stack: string;
  componentStack?: string;
  platform: string;
  version: string;
}

const MAX_REPORTS = 50;
const reports: CrashReport[] = [];

export const CrashLogger = {
  /**
   * Logs a React ErrorBoundary error.
   */
  log(error: Error, info?: React.ErrorInfo): void {
    const report = CrashLogger.buildReport(error, info);
    reports.unshift(report);
    if (reports.length > MAX_REPORTS) reports.pop();

    // Print to console — appears in adb logcat as ReactNativeJS
    console.error(
      `[CRASH][${report.type}] ${report.message}\n${report.stack}\n${report.componentStack ?? ''}`,
    );
  },

  /**
   * Builds a full crash report string for copying/sharing.
   */
  buildReport(error: Error, info?: React.ErrorInfo): CrashReport {
    return {
      id: `crash_${Date.now()}`,
      timestamp: new Date().toISOString(),
      type: info ? 'render' : 'uncaught_exception',
      message: error.message ?? 'Unknown error',
      stack: error.stack ?? '',
      componentStack: info?.componentStack ?? undefined,
      platform: `${Platform.OS} ${Platform.Version}`,
      version: '0.1.0',
    };
  },

  /**
   * Formats a report as a human-readable string for copying to clipboard.
   */
  formatReport(report: CrashReport): string {
    return [
      '=== MCW Mobile Crash Report ===',
      `Time: ${report.timestamp}`,
      `Platform: ${report.platform}`,
      `Version: ${report.version}`,
      `Type: ${report.type}`,
      '',
      `Error: ${report.message}`,
      '',
      'Stack Trace:',
      report.stack,
      report.componentStack ? `\nComponent Stack:${report.componentStack}` : '',
      '',
      '=== End of Report ===',
    ].join('\n');
  },

  /**
   * Returns the last N crash reports.
   */
  getReports(limit = 10): CrashReport[] {
    return reports.slice(0, limit);
  },

  /**
   * Clears all stored reports.
   */
  clear(): void {
    reports.length = 0;
  },
};

/**
 * Sets up global JS error handlers.
 * Call ONCE from index.js before AppRegistry.
 *
 * Catches:
 * - Unhandled promise rejections (Promise.catch() missing)
 * - Uncaught synchronous exceptions
 */
export function setupGlobalErrorHandlers(): void {
  // Override React Native's global error handler
  // ErrorUtils is available globally in React Native JS environment
  const originalHandler = (global as any).ErrorUtils?.getGlobalHandler?.();

  (global as any).ErrorUtils?.setGlobalHandler?.((error: Error, isFatal: boolean) => {
    const report: CrashReport = {
      id: `crash_${Date.now()}`,
      timestamp: new Date().toISOString(),
      type: 'uncaught_exception',
      message: `${isFatal ? '[FATAL] ' : ''}${error.message}`,
      stack: error.stack ?? '',
      platform: `${Platform.OS} ${Platform.Version}`,
      version: '0.1.0',
    };

    reports.unshift(report);
    if (reports.length > MAX_REPORTS) reports.pop();

    console.error(
      `[CRASH][uncaught${isFatal ? '/FATAL' : ''}] ${error.message}\n${error.stack}`,
    );

    // Call original handler — shows RN red screen in debug, rethrows in release
    originalHandler?.(error, isFatal);
  });

  // Handle unhandled promise rejections
  const originalRejectionHandler = (global as any).onunhandledrejection;
  (global as any).onunhandledrejection = (event: any) => {
    const reason = event?.reason;
    const error = reason instanceof Error ? reason : new Error(String(reason));

    const report: CrashReport = {
      id: `crash_${Date.now()}`,
      timestamp: new Date().toISOString(),
      type: 'unhandled_rejection',
      message: error.message,
      stack: error.stack ?? '',
      platform: `${Platform.OS} ${Platform.Version}`,
      version: '0.1.0',
    };

    reports.unshift(report);
    if (reports.length > MAX_REPORTS) reports.pop();

    console.error(`[CRASH][unhandled_rejection] ${error.message}\n${error.stack}`);

    originalRejectionHandler?.(event);
  };

  console.log('[CrashLogger] Global error handlers installed');
}
