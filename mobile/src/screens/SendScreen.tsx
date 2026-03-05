import React, {useState} from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  ActivityIndicator,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {useWalletStore} from '@state/walletStore';
import {useAuthStore} from '@state/authStore';

type Currency = 'BTC' | 'ETH';

export default function SendScreen() {
  const navigation = useNavigation<any>();
  const {btcAddress, ethAddress, balances, activeChainId} = useWalletStore();
  const onUserInteraction = useAuthStore(s => s.onUserInteraction);

  const [currency, setCurrency] = useState<Currency>('ETH');
  const [toAddress, setToAddress] = useState('');
  const [amount, setAmount] = useState('');
  const [sending, setSending] = useState(false);

  const balance = currency === 'BTC'
    ? `${balances.btcBalance ?? '0'} BTC`
    : `${balances.ethBalance ?? '0'} ETH`;

  const handleSend = async () => {
    onUserInteraction();

    if (!toAddress.trim()) {
      Alert.alert('Error', 'Please enter a recipient address');
      return;
    }
    if (!amount.trim() || isNaN(parseFloat(amount))) {
      Alert.alert('Error', 'Please enter a valid amount');
      return;
    }

    Alert.alert(
      'Confirm Send',
      `Send ${amount} ${currency} to\n${toAddress.slice(0, 20)}...${toAddress.slice(-8)}?`,
      [
        {text: 'Cancel', style: 'cancel'},
        {
          text: 'Send',
          style: 'destructive',
          onPress: async () => {
            setSending(true);
            // TODO: Implement actual signing + broadcast in future sprint
            await new Promise(r => setTimeout(r, 1500));
            setSending(false);
            Alert.alert(
              'Transaction Submitted',
              'Your transaction has been submitted to the network.',
              [{text: 'OK', onPress: () => navigation.goBack()}],
            );
          },
        },
      ],
    );
  };

  const handleMaxAmount = () => {
    onUserInteraction();
    const bal = currency === 'BTC' ? balances.btcBalance : balances.ethBalance;
    if (bal) setAmount(bal);
  };

  return (
    <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Send</Text>
      </View>

      {/* Currency selector */}
      <Text style={styles.label}>Currency</Text>
      <View style={styles.currencyRow}>
        {(['ETH', 'BTC'] as Currency[]).map(c => (
          <TouchableOpacity
            key={c}
            style={[styles.currencyBtn, currency === c && styles.currencyBtnActive]}
            onPress={() => {
              setCurrency(c);
              onUserInteraction();
            }}>
            <Text style={[styles.currencyBtnText, currency === c && styles.currencyBtnTextActive]}>
              {c}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <Text style={styles.balanceText}>Available: {balance}</Text>

      {/* From address */}
      <Text style={styles.label}>From</Text>
      <View style={styles.addressDisplay}>
        <Text style={styles.addressText} numberOfLines={1}>
          {currency === 'BTC' ? btcAddress : ethAddress}
        </Text>
      </View>

      {/* To address */}
      <Text style={styles.label}>To Address</Text>
      <TextInput
        style={styles.input}
        value={toAddress}
        onChangeText={t => { setToAddress(t); onUserInteraction(); }}
        placeholder={currency === 'BTC' ? '1A1zP1eP...' : '0x...'}
        placeholderTextColor="#6B7280"
        autoCapitalize="none"
        autoCorrect={false}
      />

      {/* Amount */}
      <Text style={styles.label}>Amount ({currency})</Text>
      <View style={styles.amountRow}>
        <TextInput
          style={[styles.input, {flex: 1}]}
          value={amount}
          onChangeText={t => { setAmount(t); onUserInteraction(); }}
          placeholder="0.00"
          placeholderTextColor="#6B7280"
          keyboardType="decimal-pad"
        />
        <TouchableOpacity style={styles.maxBtn} onPress={handleMaxAmount}>
          <Text style={styles.maxBtnText}>MAX</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity
        style={[styles.sendBtn, sending && styles.sendBtnDisabled]}
        onPress={handleSend}
        disabled={sending}
        activeOpacity={0.8}>
        {sending ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.sendBtnText}>Send {currency}</Text>
        )}
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {flex: 1, backgroundColor: '#111827'},
  content: {padding: 24, paddingBottom: 48},
  header: {marginBottom: 24},
  backBtn: {marginBottom: 12},
  backText: {color: '#3B82F6', fontSize: 16},
  title: {fontSize: 26, fontWeight: 'bold', color: '#F9FAFB'},
  label: {color: '#9CA3AF', fontSize: 13, marginBottom: 6, marginTop: 16},
  currencyRow: {flexDirection: 'row', gap: 8},
  currencyBtn: {
    flex: 1,
    padding: 12,
    borderRadius: 10,
    alignItems: 'center',
    backgroundColor: '#1F2937',
    borderWidth: 1,
    borderColor: '#374151',
  },
  currencyBtnActive: {borderColor: '#3B82F6', backgroundColor: '#1E3A5F'},
  currencyBtnText: {color: '#9CA3AF', fontWeight: '600'},
  currencyBtnTextActive: {color: '#3B82F6'},
  balanceText: {color: '#6B7280', fontSize: 12, marginTop: 6},
  addressDisplay: {
    backgroundColor: '#1F2937',
    borderRadius: 10,
    padding: 14,
    borderWidth: 1,
    borderColor: '#374151',
  },
  addressText: {color: '#9CA3AF', fontSize: 13},
  input: {
    backgroundColor: '#1F2937',
    borderRadius: 10,
    padding: 14,
    color: '#F9FAFB',
    fontSize: 15,
    borderWidth: 1,
    borderColor: '#374151',
  },
  amountRow: {flexDirection: 'row', gap: 8, alignItems: 'center'},
  maxBtn: {
    backgroundColor: '#374151',
    padding: 14,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#4B5563',
  },
  maxBtnText: {color: '#9CA3AF', fontWeight: '600', fontSize: 13},
  sendBtn: {
    backgroundColor: '#3B82F6',
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: 24,
  },
  sendBtnDisabled: {backgroundColor: '#374151'},
  sendBtnText: {color: '#fff', fontWeight: '600', fontSize: 16},
});
