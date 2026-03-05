import React, {useState} from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import type {StackNavigationProp} from '@react-navigation/stack';
import type {RootStackParamList} from '@navigation/RootNavigator';
import {validateMnemonic} from '@crypto/mnemonic';
import {useWalletStore} from '@state/walletStore';

type NavProp = StackNavigationProp<RootStackParamList, 'ImportWallet'>;

export default function ImportWalletScreen() {
  const navigation = useNavigation<NavProp>();
  const importWallet = useWalletStore(s => s.importWallet);

  const [words, setWords] = useState<string[]>(Array(12).fill(''));
  const [bulkText, setBulkText] = useState('');
  const [bulkMode, setBulkMode] = useState(true); // paste mode by default
  const [loading, setLoading] = useState(false);

  const handleBulkPaste = (text: string) => {
    setBulkText(text);
    const parsed = text.trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (parsed.length <= 12) {
      const padded = [...parsed, ...Array(12 - parsed.length).fill('')];
      setWords(padded.slice(0, 12));
    }
  };

  const handleImport = async () => {
    const filtered = words.map(w => w.trim().toLowerCase()).filter(Boolean);

    try {
      validateMnemonic(filtered.join(' '));
    } catch (err: any) {
      Alert.alert('Invalid Seed Phrase', err.message ?? 'Please check your words and try again.');
      return;
    }

    setLoading(true);
    try {
      await importWallet(filtered);
      navigation.reset({index: 0, routes: [{name: 'Main'}]});
    } catch (err) {
      Alert.alert('Error', 'Failed to import wallet. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Import Wallet</Text>
        <Text style={styles.subtitle}>Enter your 12-word seed phrase to restore your wallet.</Text>
      </View>

      <View style={styles.modeToggle}>
        <TouchableOpacity
          style={[styles.modeBtn, bulkMode && styles.modeBtnActive]}
          onPress={() => setBulkMode(true)}>
          <Text style={[styles.modeBtnText, bulkMode && styles.modeBtnTextActive]}>Paste All</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.modeBtn, !bulkMode && styles.modeBtnActive]}
          onPress={() => setBulkMode(false)}>
          <Text style={[styles.modeBtnText, !bulkMode && styles.modeBtnTextActive]}>Word by Word</Text>
        </TouchableOpacity>
      </View>

      {bulkMode ? (
        <TextInput
          style={styles.bulkInput}
          value={bulkText}
          onChangeText={handleBulkPaste}
          placeholder="Paste your 12-word seed phrase here..."
          placeholderTextColor="#6B7280"
          multiline
          numberOfLines={4}
          autoCapitalize="none"
          autoCorrect={false}
        />
      ) : (
        <View style={styles.wordGrid}>
          {words.map((word, i) => (
            <View key={i} style={styles.wordInputBox}>
              <Text style={styles.wordInputIndex}>{i + 1}</Text>
              <TextInput
                style={styles.wordInput}
                value={word}
                onChangeText={text => {
                  const updated = [...words];
                  updated[i] = text;
                  setWords(updated);
                }}
                placeholder={`Word ${i + 1}`}
                placeholderTextColor="#4B5563"
                autoCapitalize="none"
                autoCorrect={false}
              />
            </View>
          ))}
        </View>
      )}

      <TouchableOpacity
        style={styles.importBtn}
        onPress={handleImport}
        disabled={loading}
        activeOpacity={0.8}>
        {loading ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.importBtnText}>Import Wallet</Text>
        )}
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {flex: 1, backgroundColor: '#111827'},
  content: {padding: 24, paddingBottom: 48},
  header: {marginBottom: 24},
  backBtn: {marginBottom: 16},
  backText: {color: '#3B82F6', fontSize: 16},
  title: {fontSize: 26, fontWeight: 'bold', color: '#F9FAFB', marginBottom: 8},
  subtitle: {fontSize: 14, color: '#9CA3AF', lineHeight: 20},
  modeToggle: {flexDirection: 'row', backgroundColor: '#1F2937', borderRadius: 10, padding: 4, marginBottom: 20},
  modeBtn: {flex: 1, padding: 10, borderRadius: 8, alignItems: 'center'},
  modeBtnActive: {backgroundColor: '#374151'},
  modeBtnText: {color: '#9CA3AF', fontSize: 14},
  modeBtnTextActive: {color: '#F9FAFB', fontWeight: '600'},
  bulkInput: {
    backgroundColor: '#1F2937',
    borderRadius: 12,
    padding: 16,
    color: '#F9FAFB',
    fontSize: 15,
    lineHeight: 22,
    minHeight: 120,
    textAlignVertical: 'top',
    borderWidth: 1,
    borderColor: '#374151',
    marginBottom: 24,
  },
  wordGrid: {flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 24},
  wordInputBox: {
    width: '47%',
    backgroundColor: '#1F2937',
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    borderWidth: 1,
    borderColor: '#374151',
  },
  wordInputIndex: {color: '#6B7280', fontSize: 11, minWidth: 16},
  wordInput: {flex: 1, color: '#F9FAFB', fontSize: 13, padding: 8},
  importBtn: {backgroundColor: '#3B82F6', padding: 16, borderRadius: 12, alignItems: 'center'},
  importBtnText: {color: '#fff', fontWeight: '600', fontSize: 16},
});
