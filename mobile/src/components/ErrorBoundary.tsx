import React from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  Clipboard,
  Alert,
} from 'react-native';
import {CrashLogger} from '../utils/crashLogger';

interface Props {
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: React.ErrorInfo | null;
  report: string;
}

/**
 * React ErrorBoundary — catches JS render errors and shows crash report screen.
 *
 * On crash:
 * - Logs to CrashLogger (in-memory + AsyncStorage)
 * - Displays error + stack trace
 * - Allows copying report to clipboard
 * - Allows resetting app to try again
 */
export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {hasError: false, error: null, errorInfo: null, report: ''};
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return {hasError: true, error};
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    const report = CrashLogger.buildReport(error, errorInfo);
    CrashLogger.log(error, errorInfo);
    this.setState({errorInfo, report});
    console.error('[ErrorBoundary] caught:', error.message, errorInfo.componentStack);
  }

  handleCopyReport = () => {
    Clipboard.setString(this.state.report);
    Alert.alert('Copied', 'Crash report copied to clipboard');
  };

  handleReset = () => {
    this.setState({hasError: false, error: null, errorInfo: null, report: ''});
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    const {error, report} = this.state;

    return (
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.title}>💥 App Crashed</Text>
        <Text style={styles.subtitle}>
          An unexpected error occurred. Please copy the report and restart.
        </Text>

        <View style={styles.errorBox}>
          <Text style={styles.errorType}>{error?.name ?? 'Error'}</Text>
          <Text style={styles.errorMessage}>{error?.message ?? 'Unknown error'}</Text>
        </View>

        <Text style={styles.stackTitle}>Stack Trace</Text>
        <ScrollView style={styles.stackBox} horizontal>
          <Text style={styles.stackText}>{error?.stack ?? 'No stack trace'}</Text>
        </ScrollView>

        <View style={styles.actions}>
          <TouchableOpacity style={styles.copyBtn} onPress={this.handleCopyReport}>
            <Text style={styles.copyBtnText}>📋 Copy Report</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.resetBtn} onPress={this.handleReset}>
            <Text style={styles.resetBtnText}>↺ Try Again</Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.debugHint}>
          {'Run: adb logcat | grep -E "(ReactNative|FATAL|Error)"'}
        </Text>
      </ScrollView>
    );
  }
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#0F172A'},
  content: {padding: 24, paddingTop: 60, paddingBottom: 48},
  title: {fontSize: 28, fontWeight: 'bold', color: '#EF4444', marginBottom: 8},
  subtitle: {fontSize: 14, color: '#94A3B8', lineHeight: 20, marginBottom: 24},
  errorBox: {
    backgroundColor: '#1E293B',
    borderRadius: 10,
    padding: 16,
    borderLeftWidth: 4,
    borderLeftColor: '#EF4444',
    marginBottom: 20,
  },
  errorType: {color: '#F87171', fontSize: 13, fontWeight: '700', marginBottom: 4},
  errorMessage: {color: '#E2E8F0', fontSize: 14, lineHeight: 20},
  stackTitle: {color: '#94A3B8', fontSize: 13, fontWeight: '600', marginBottom: 8},
  stackBox: {
    backgroundColor: '#0F172A',
    borderRadius: 8,
    padding: 12,
    maxHeight: 200,
    borderWidth: 1,
    borderColor: '#334155',
    marginBottom: 24,
  },
  stackText: {color: '#64748B', fontSize: 11, fontFamily: 'monospace', lineHeight: 18},
  actions: {flexDirection: 'row', gap: 12, marginBottom: 24},
  copyBtn: {
    flex: 1,
    backgroundColor: '#3B82F6',
    padding: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  copyBtnText: {color: '#fff', fontWeight: '600'},
  resetBtn: {
    flex: 1,
    backgroundColor: '#374151',
    padding: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  resetBtnText: {color: '#D1D5DB', fontWeight: '600'},
  debugHint: {
    color: '#475569',
    fontSize: 11,
    fontFamily: 'monospace',
    textAlign: 'center',
  },
});
