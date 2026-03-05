import React, {useEffect, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
  Alert,
  Switch,
} from 'react-native';
import {useSettingsStore} from '@state/settingsStore';
import {useAuthStore} from '@state/authStore';
import {SUPPORTED_CHAINS} from '@network/RpcConfig';

export default function SettingsScreen() {
  const {activeChainId, customRpcUrls, setActiveChain, setCustomRpc, clearCustomRpc} =
    useSettingsStore();
  const {biometricAvailability, lock} = useAuthStore();

  const [customRpcInput, setCustomRpcInput] = useState('');
  const [editingChainId, setEditingChainId] = useState<number | null>(null);

  const activeChain = SUPPORTED_CHAINS.find(c => c.chainId === activeChainId);

  const handleSaveCustomRpc = async () => {
    if (!editingChainId) return;
    const url = customRpcInput.trim();
    if (url && !url.startsWith('http')) {
      Alert.alert('Invalid URL', 'RPC URL must start with http:// or https://');
      return;
    }
    if (url) {
      await setCustomRpc(editingChainId, url);
      Alert.alert('Saved', 'Custom RPC URL saved successfully.');
    } else {
      await clearCustomRpc(editingChainId);
      Alert.alert('Cleared', 'Custom RPC URL removed. Using default.');
    }
    setEditingChainId(null);
    setCustomRpcInput('');
  };

  const handleLockWallet = () => {
    Alert.alert('Lock Wallet', 'Are you sure you want to lock the wallet?', [
      {text: 'Cancel', style: 'cancel'},
      {text: 'Lock', style: 'destructive', onPress: lock},
    ]);
  };

  return (
    <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
      <Text style={styles.pageTitle}>Settings</Text>

      {/* Network Selection */}
      <Text style={styles.sectionTitle}>Network</Text>
      <View style={styles.card}>
        {SUPPORTED_CHAINS.map(chain => (
          <TouchableOpacity
            key={chain.chainId}
            style={[
              styles.chainRow,
              activeChainId === chain.chainId && styles.chainRowActive,
            ]}
            onPress={() => setActiveChain(chain.chainId)}
            activeOpacity={0.7}>
            <View>
              <Text style={styles.chainName}>{chain.name}</Text>
              <Text style={styles.chainId}>Chain ID: {chain.chainId}</Text>
            </View>
            {activeChainId === chain.chainId && (
              <Text style={styles.checkmark}>✓</Text>
            )}
          </TouchableOpacity>
        ))}
      </View>

      {/* Custom RPC */}
      <Text style={styles.sectionTitle}>Custom RPC</Text>
      <View style={styles.card}>
        {SUPPORTED_CHAINS.map(chain => (
          <View key={chain.chainId} style={styles.rpcRow}>
            <View style={styles.rpcInfo}>
              <Text style={styles.rpcChainName}>{chain.name}</Text>
              <Text style={styles.rpcUrl} numberOfLines={1}>
                {customRpcUrls[chain.chainId] ?? chain.rpcUrls[0]}
              </Text>
            </View>
            <TouchableOpacity
              style={styles.editBtn}
              onPress={() => {
                setEditingChainId(chain.chainId);
                setCustomRpcInput(customRpcUrls[chain.chainId] ?? '');
              }}>
              <Text style={styles.editBtnText}>Edit</Text>
            </TouchableOpacity>
          </View>
        ))}

        {editingChainId !== null && (
          <View style={styles.rpcEditBox}>
            <Text style={styles.rpcEditLabel}>
              Custom RPC for {SUPPORTED_CHAINS.find(c => c.chainId === editingChainId)?.name}
            </Text>
            <TextInput
              style={styles.rpcInput}
              value={customRpcInput}
              onChangeText={setCustomRpcInput}
              placeholder="https://... (leave empty to reset)"
              placeholderTextColor="#6B7280"
              autoCapitalize="none"
              autoCorrect={false}
            />
            <View style={styles.rpcEditActions}>
              <TouchableOpacity
                style={styles.rpcSaveBtn}
                onPress={handleSaveCustomRpc}>
                <Text style={styles.rpcSaveBtnText}>Save</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.rpcCancelBtn}
                onPress={() => { setEditingChainId(null); setCustomRpcInput(''); }}>
                <Text style={styles.rpcCancelBtnText}>Cancel</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
      </View>

      {/* Security */}
      <Text style={styles.sectionTitle}>Security</Text>
      <View style={styles.card}>
        <View style={styles.settingRow}>
          <Text style={styles.settingLabel}>Biometric Auth</Text>
          <Text style={styles.settingValue}>
            {biometricAvailability === 'available' ? 'Available' : 'Not available'}
          </Text>
        </View>
        <TouchableOpacity style={styles.dangerRow} onPress={handleLockWallet}>
          <Text style={styles.dangerText}>🔒 Lock Wallet</Text>
        </TouchableOpacity>
      </View>

      {/* App Info */}
      <Text style={styles.sectionTitle}>About</Text>
      <View style={styles.card}>
        <View style={styles.settingRow}>
          <Text style={styles.settingLabel}>Version</Text>
          <Text style={styles.settingValue}>0.1.0</Text>
        </View>
        <View style={styles.settingRow}>
          <Text style={styles.settingLabel}>Network</Text>
          <Text style={styles.settingValue}>{activeChain?.name ?? 'Unknown'}</Text>
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {flex: 1, backgroundColor: '#111827'},
  content: {padding: 20, paddingBottom: 48},
  pageTitle: {fontSize: 28, fontWeight: 'bold', color: '#F9FAFB', marginBottom: 24},
  sectionTitle: {color: '#9CA3AF', fontSize: 13, fontWeight: '600', marginBottom: 8, marginTop: 16, textTransform: 'uppercase', letterSpacing: 0.5},
  card: {backgroundColor: '#1F2937', borderRadius: 14, overflow: 'hidden', marginBottom: 8},
  chainRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#374151',
  },
  chainRowActive: {backgroundColor: '#1E3A5F'},
  chainName: {color: '#F9FAFB', fontSize: 15, fontWeight: '600'},
  chainId: {color: '#6B7280', fontSize: 12, marginTop: 2},
  checkmark: {color: '#3B82F6', fontSize: 18, fontWeight: 'bold'},
  rpcRow: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#374151',
    gap: 12,
  },
  rpcInfo: {flex: 1},
  rpcChainName: {color: '#F9FAFB', fontSize: 14, fontWeight: '500'},
  rpcUrl: {color: '#6B7280', fontSize: 11, marginTop: 2},
  editBtn: {backgroundColor: '#374151', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 6},
  editBtnText: {color: '#9CA3AF', fontSize: 13},
  rpcEditBox: {padding: 14, borderTopWidth: 1, borderTopColor: '#374151'},
  rpcEditLabel: {color: '#9CA3AF', fontSize: 13, marginBottom: 8},
  rpcInput: {
    backgroundColor: '#111827',
    borderRadius: 8,
    padding: 12,
    color: '#F9FAFB',
    fontSize: 13,
    borderWidth: 1,
    borderColor: '#374151',
    marginBottom: 10,
  },
  rpcEditActions: {flexDirection: 'row', gap: 8},
  rpcSaveBtn: {flex: 1, backgroundColor: '#3B82F6', padding: 10, borderRadius: 8, alignItems: 'center'},
  rpcSaveBtnText: {color: '#fff', fontWeight: '600'},
  rpcCancelBtn: {flex: 1, backgroundColor: '#374151', padding: 10, borderRadius: 8, alignItems: 'center'},
  rpcCancelBtnText: {color: '#9CA3AF', fontWeight: '600'},
  settingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#374151',
  },
  settingLabel: {color: '#D1D5DB', fontSize: 15},
  settingValue: {color: '#9CA3AF', fontSize: 14},
  dangerRow: {padding: 16},
  dangerText: {color: '#EF4444', fontSize: 15, fontWeight: '500'},
});
