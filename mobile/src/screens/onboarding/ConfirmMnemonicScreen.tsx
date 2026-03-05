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
import {useNavigation, useRoute} from '@react-navigation/native';
import type {StackNavigationProp, RouteProp} from '@react-navigation/stack';
import type {RootStackParamList} from '@navigation/RootNavigator';
import {useWalletStore} from '@state/walletStore';

type NavProp = StackNavigationProp<RootStackParamList, 'ConfirmMnemonic'>;
type RoutePropType = RouteProp<RootStackParamList, 'ConfirmMnemonic'>;

export default function ConfirmMnemonicScreen() {
  const navigation = useNavigation<NavProp>();
  const route = useRoute<RoutePropType>();
  const {mnemonic} = route.params;
  const createWallet = useWalletStore(s => s.createWallet);

  // Pick 3 random indices to verify
  const [indices] = useState<number[]>(() => {
    const all = Array.from({length: 12}, (_, i) => i);
    const shuffled = all.sort(() => Math.random() - 0.5);
    return shuffled.slice(0, 3).sort((a, b) => a - b);
  });

  const [answers, setAnswers] = useState<string[]>(['', '', '']);
  const [saving, setSaving] = useState(false);

  const handleVerify = async () => {
    const correct = indices.every((idx, i) => answers[i].trim().toLowerCase() === mnemonic[idx]);
    if (!correct) {
      Alert.alert('Incorrect', 'One or more words are wrong. Please check your backup and try again.');
      return;
    }

    setSaving(true);
    try {
      await createWallet(mnemonic);
      navigation.reset({index: 0, routes: [{name: 'Main'}]});
    } catch (err) {
      Alert.alert('Error', 'Failed to create wallet. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Verify Backup</Text>
        <Text style={styles.subtitle}>
          Enter the requested words from your seed phrase to confirm you have it saved.
        </Text>
      </View>

      {indices.map((wordIdx, i) => (
        <View key={wordIdx} style={styles.inputGroup}>
          <Text style={styles.inputLabel}>Word #{wordIdx + 1}</Text>
          <TextInput
            style={styles.input}
            value={answers[i]}
            onChangeText={text => {
              const updated = [...answers];
              updated[i] = text;
              setAnswers(updated);
            }}
            placeholder={`Enter word ${wordIdx + 1}`}
            placeholderTextColor="#6B7280"
            autoCapitalize="none"
            autoCorrect={false}
          />
        </View>
      ))}

      <TouchableOpacity
        style={styles.verifyBtn}
        onPress={handleVerify}
        disabled={saving}
        activeOpacity={0.8}>
        {saving ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.verifyBtnText}>Verify & Create Wallet</Text>
        )}
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {flex: 1, backgroundColor: '#111827'},
  content: {padding: 24, paddingBottom: 48},
  header: {marginBottom: 32},
  backBtn: {marginBottom: 16},
  backText: {color: '#3B82F6', fontSize: 16},
  title: {fontSize: 26, fontWeight: 'bold', color: '#F9FAFB', marginBottom: 8},
  subtitle: {fontSize: 14, color: '#9CA3AF', lineHeight: 20},
  inputGroup: {marginBottom: 20},
  inputLabel: {color: '#9CA3AF', fontSize: 13, marginBottom: 6},
  input: {
    backgroundColor: '#1F2937',
    borderRadius: 10,
    padding: 14,
    color: '#F9FAFB',
    fontSize: 16,
    borderWidth: 1,
    borderColor: '#374151',
  },
  verifyBtn: {backgroundColor: '#3B82F6', padding: 16, borderRadius: 12, alignItems: 'center', marginTop: 8},
  verifyBtnText: {color: '#fff', fontWeight: '600', fontSize: 16},
});
