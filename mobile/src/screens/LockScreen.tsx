import React, {useEffect, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TextInput,
  ActivityIndicator,
  Alert,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {useAuthStore} from '@state/authStore';
import {useWalletStore} from '@state/walletStore';

export default function LockScreen() {
  const navigation = useNavigation<any>();
  const {
    lockState,
    biometricAvailability,
    unlockWithBiometric,
    unlockWithPassword,
  } = useAuthStore();
  const initWallet = useWalletStore(s => s.initWallet);

  const [showPassword, setShowPassword] = useState(false);
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  // Try biometric on mount
  useEffect(() => {
    if (biometricAvailability === 'available') {
      handleBiometric();
    } else {
      setShowPassword(true);
    }
  }, []);

  // Navigate to main when unlocked
  useEffect(() => {
    if (lockState.type === 'unlocked') {
      initWallet().then(() => {
        navigation.replace('Main');
      });
    }
  }, [lockState]);

  const handleBiometric = async () => {
    setLoading(true);
    const success = await unlockWithBiometric();
    setLoading(false);
    if (!success) {
      setShowPassword(true);
    }
  };

  const handlePasswordSubmit = async () => {
    if (!password) return;
    setLoading(true);
    const result = await unlockWithPassword(password);
    setLoading(false);
    if (!result.success) {
      Alert.alert('Authentication Failed', result.error ?? 'Wrong password');
      setPassword('');
    }
  };

  const isLockedOut = lockState.type === 'locked_out';
  const lockoutSeconds = isLockedOut
    ? Math.max(0, Math.ceil((lockState.lockoutUntil - Date.now()) / 1000))
    : 0;

  return (
    <View style={styles.container}>
      <Text style={styles.logo}>MCW</Text>
      <Text style={styles.title}>Wallet Locked</Text>

      {isLockedOut ? (
        <View style={styles.lockoutBox}>
          <Text style={styles.lockoutText}>
            Too many failed attempts.{'\n'}
            Try again in {lockoutSeconds} seconds.
          </Text>
        </View>
      ) : (
        <>
          {biometricAvailability === 'available' && !showPassword && (
            <TouchableOpacity
              style={styles.biometricBtn}
              onPress={handleBiometric}
              disabled={loading}>
              {loading ? (
                <ActivityIndicator color="#3B82F6" />
              ) : (
                <>
                  <Text style={styles.biometricIcon}>🔏</Text>
                  <Text style={styles.biometricText}>Use Biometric</Text>
                </>
              )}
            </TouchableOpacity>
          )}

          {(showPassword || biometricAvailability !== 'available') && (
            <View style={styles.passwordForm}>
              <Text style={styles.passwordLabel}>Enter Password</Text>
              <TextInput
                style={styles.passwordInput}
                value={password}
                onChangeText={setPassword}
                placeholder="Password"
                placeholderTextColor="#6B7280"
                secureTextEntry
                onSubmitEditing={handlePasswordSubmit}
                returnKeyType="done"
              />
              <TouchableOpacity
                style={styles.unlockBtn}
                onPress={handlePasswordSubmit}
                disabled={loading || !password}>
                {loading ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <Text style={styles.unlockBtnText}>Unlock</Text>
                )}
              </TouchableOpacity>
            </View>
          )}

          {biometricAvailability === 'available' && showPassword && (
            <TouchableOpacity
              style={styles.switchBtn}
              onPress={() => { setShowPassword(false); handleBiometric(); }}>
              <Text style={styles.switchBtnText}>Use Biometric Instead</Text>
            </TouchableOpacity>
          )}
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#111827', padding: 32},
  logo: {fontSize: 48, fontWeight: 'bold', color: '#3B82F6', marginBottom: 8},
  title: {fontSize: 22, color: '#D1D5DB', marginBottom: 40},
  lockoutBox: {backgroundColor: '#450A0A', borderRadius: 12, padding: 20, marginBottom: 24, alignItems: 'center'},
  lockoutText: {color: '#FCA5A5', fontSize: 15, textAlign: 'center', lineHeight: 22},
  biometricBtn: {
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: '#1E3A5F',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#3B82F6',
    marginBottom: 24,
  },
  biometricIcon: {fontSize: 40},
  biometricText: {color: '#93C5FD', fontSize: 12, marginTop: 4},
  passwordForm: {width: '100%', marginBottom: 16},
  passwordLabel: {color: '#9CA3AF', fontSize: 13, marginBottom: 8},
  passwordInput: {
    backgroundColor: '#1F2937',
    borderRadius: 10,
    padding: 14,
    color: '#F9FAFB',
    fontSize: 16,
    borderWidth: 1,
    borderColor: '#374151',
    marginBottom: 12,
  },
  unlockBtn: {
    backgroundColor: '#3B82F6',
    padding: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  unlockBtnText: {color: '#fff', fontWeight: '600', fontSize: 16},
  switchBtn: {marginTop: 8},
  switchBtnText: {color: '#3B82F6', fontSize: 14},
});
