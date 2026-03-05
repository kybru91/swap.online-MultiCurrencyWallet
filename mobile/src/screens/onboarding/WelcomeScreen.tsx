import React from 'react';
import {View, Text, TouchableOpacity, StyleSheet} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import type {StackNavigationProp} from '@react-navigation/stack';
import type {RootStackParamList} from '@navigation/RootNavigator';

type WelcomeNavProp = StackNavigationProp<RootStackParamList, 'Welcome'>;

export default function WelcomeScreen() {
  const navigation = useNavigation<WelcomeNavProp>();
  return (
    <View style={styles.container}>
      <Text style={styles.title}>MCW Wallet</Text>
      <Text style={styles.subtitle}>Multi-currency crypto wallet</Text>
      <TouchableOpacity style={styles.btn} onPress={() => navigation.navigate('CreateWallet')}>
        <Text style={styles.btnText}>Create New Wallet</Text>
      </TouchableOpacity>
      <TouchableOpacity style={[styles.btn, styles.btnOutline]} onPress={() => navigation.navigate('ImportWallet')}>
        <Text style={[styles.btnText, styles.btnOutlineText]}>Import Wallet</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#111827', padding: 24},
  title: {fontSize: 32, fontWeight: 'bold', color: '#F9FAFB', marginBottom: 8},
  subtitle: {fontSize: 16, color: '#9CA3AF', marginBottom: 48},
  btn: {width: '100%', backgroundColor: '#3B82F6', padding: 16, borderRadius: 12, alignItems: 'center', marginBottom: 12},
  btnText: {color: '#fff', fontWeight: '600', fontSize: 16},
  btnOutline: {backgroundColor: 'transparent', borderWidth: 1, borderColor: '#3B82F6'},
  btnOutlineText: {color: '#3B82F6'},
});
