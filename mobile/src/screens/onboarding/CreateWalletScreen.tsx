import React, {useEffect, useState} from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import type {StackNavigationProp} from '@react-navigation/stack';
import type {RootStackParamList} from '@navigation/RootNavigator';
import {generateMnemonic} from '@crypto/mnemonic';

type NavProp = StackNavigationProp<RootStackParamList, 'CreateWallet'>;

export default function CreateWalletScreen() {
  const navigation = useNavigation<NavProp>();
  const [mnemonic, setMnemonic] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [confirmed, setConfirmed] = useState(false);

  useEffect(() => {
    const words = generateMnemonic().split(' ');
    setMnemonic(words);
    setLoading(false);
  }, []);

  const handleContinue = () => {
    if (!confirmed) {
      Alert.alert('Please confirm', 'Please check the box confirming you have written down your seed phrase.');
      return;
    }
    navigation.navigate('ConfirmMnemonic', {mnemonic});
  };

  if (loading) {
    return (
      <View style={styles.container}>
        <ActivityIndicator size="large" color="#3B82F6" />
      </View>
    );
  }

  return (
    <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Your Seed Phrase</Text>
        <Text style={styles.subtitle}>
          Write down these 12 words in order and store them safely.{'\n'}
          Anyone with this phrase can access your wallet.
        </Text>
      </View>

      <View style={styles.warningBox}>
        <Text style={styles.warningText}>
          ⚠️ Never share your seed phrase with anyone. MCW will never ask for it.
        </Text>
      </View>

      <View style={styles.mnemonicGrid}>
        {mnemonic.map((word, i) => (
          <View key={i} style={styles.wordBox}>
            <Text style={styles.wordIndex}>{i + 1}</Text>
            <Text style={styles.wordText}>{word}</Text>
          </View>
        ))}
      </View>

      <TouchableOpacity
        style={styles.checkRow}
        onPress={() => setConfirmed(!confirmed)}
        activeOpacity={0.7}>
        <View style={[styles.checkbox, confirmed && styles.checkboxChecked]}>
          {confirmed && <Text style={styles.checkmark}>✓</Text>}
        </View>
        <Text style={styles.checkLabel}>
          I have written down my seed phrase in a safe place
        </Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.continueBtn, !confirmed && styles.continueBtnDisabled]}
        onPress={handleContinue}
        activeOpacity={0.8}>
        <Text style={styles.continueBtnText}>Continue to Verification</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {flex: 1, backgroundColor: '#111827'},
  scrollContent: {padding: 24, paddingBottom: 48},
  container: {flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#111827'},
  header: {marginBottom: 20},
  backBtn: {marginBottom: 16},
  backText: {color: '#3B82F6', fontSize: 16},
  title: {fontSize: 26, fontWeight: 'bold', color: '#F9FAFB', marginBottom: 8},
  subtitle: {fontSize: 14, color: '#9CA3AF', lineHeight: 20},
  warningBox: {backgroundColor: '#451A03', borderRadius: 12, padding: 14, marginBottom: 20},
  warningText: {color: '#FCD34D', fontSize: 13, lineHeight: 18},
  mnemonicGrid: {flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 24},
  wordBox: {
    width: '30%',
    backgroundColor: '#1F2937',
    borderRadius: 8,
    padding: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  wordIndex: {color: '#6B7280', fontSize: 11, minWidth: 16},
  wordText: {color: '#F9FAFB', fontSize: 14, fontWeight: '500'},
  checkRow: {flexDirection: 'row', alignItems: 'flex-start', gap: 12, marginBottom: 24},
  checkbox: {
    width: 22,
    height: 22,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: '#3B82F6',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 1,
  },
  checkboxChecked: {backgroundColor: '#3B82F6'},
  checkmark: {color: '#fff', fontSize: 13, fontWeight: 'bold'},
  checkLabel: {flex: 1, color: '#D1D5DB', fontSize: 14, lineHeight: 20},
  continueBtn: {backgroundColor: '#3B82F6', padding: 16, borderRadius: 12, alignItems: 'center'},
  continueBtnDisabled: {backgroundColor: '#374151'},
  continueBtnText: {color: '#fff', fontWeight: '600', fontSize: 16},
});
