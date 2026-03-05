import React, {useEffect, useCallback} from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {useWalletStore} from '@state/walletStore';
import {useAuthStore} from '@state/authStore';

export default function WalletScreen() {
  const navigation = useNavigation<any>();
  const {
    btcAddress,
    ethAddress,
    balances,
    balancesLoading,
    balancesError,
    refreshBalances,
  } = useWalletStore();

  const onUserInteraction = useAuthStore(s => s.onUserInteraction);

  useEffect(() => {
    refreshBalances();
  }, []);

  const handleRefresh = useCallback(() => {
    onUserInteraction();
    refreshBalances();
  }, []);

  const btcUsdValue =
    balances.btcBalance && balances.btcUsd
      ? (parseFloat(balances.btcBalance) * balances.btcUsd).toFixed(2)
      : null;

  const ethUsdValue =
    balances.ethBalance && balances.ethUsd
      ? (parseFloat(balances.ethBalance) * balances.ethUsd).toFixed(2)
      : null;

  const totalUsd =
    btcUsdValue && ethUsdValue
      ? (parseFloat(btcUsdValue) + parseFloat(ethUsdValue)).toFixed(2)
      : null;

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl refreshing={balancesLoading} onRefresh={handleRefresh} tintColor="#3B82F6" />
      }>
      {/* Total balance header */}
      <View style={styles.totalCard}>
        <Text style={styles.totalLabel}>Total Portfolio</Text>
        {balancesLoading && !totalUsd ? (
          <ActivityIndicator color="#3B82F6" style={{marginVertical: 8}} />
        ) : (
          <Text style={styles.totalValue}>
            {totalUsd ? `$${totalUsd}` : '—'}
          </Text>
        )}
      </View>

      {/* BTC card */}
      <View style={styles.assetCard}>
        <View style={styles.assetIconCircle}>
          <Text style={styles.assetIconText}>₿</Text>
        </View>
        <View style={styles.assetInfo}>
          <Text style={styles.assetName}>Bitcoin</Text>
          <Text style={styles.assetAddress} numberOfLines={1}>
            {btcAddress ? `${btcAddress.slice(0, 12)}...${btcAddress.slice(-6)}` : '—'}
          </Text>
        </View>
        <View style={styles.assetAmounts}>
          <Text style={styles.assetBalance}>
            {balances.btcBalance ?? '—'} BTC
          </Text>
          <Text style={styles.assetUsd}>
            {btcUsdValue ? `$${btcUsdValue}` : ''}
          </Text>
        </View>
      </View>

      {/* ETH card */}
      <View style={styles.assetCard}>
        <View style={[styles.assetIconCircle, {backgroundColor: '#7C3AED'}]}>
          <Text style={styles.assetIconText}>Ξ</Text>
        </View>
        <View style={styles.assetInfo}>
          <Text style={styles.assetName}>Ethereum</Text>
          <Text style={styles.assetAddress} numberOfLines={1}>
            {ethAddress ? `${ethAddress.slice(0, 12)}...${ethAddress.slice(-6)}` : '—'}
          </Text>
        </View>
        <View style={styles.assetAmounts}>
          <Text style={styles.assetBalance}>
            {balances.ethBalance ?? '—'} ETH
          </Text>
          <Text style={styles.assetUsd}>
            {ethUsdValue ? `$${ethUsdValue}` : ''}
          </Text>
        </View>
      </View>

      {balancesError && (
        <Text style={styles.errorText}>{balancesError}</Text>
      )}

      {/* Action buttons */}
      <View style={styles.actions}>
        <TouchableOpacity
          style={styles.actionBtn}
          onPress={() => navigation.navigate('Send')}
          activeOpacity={0.8}>
          <Text style={styles.actionBtnText}>↑ Send</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.actionBtn, styles.actionBtnSecondary]}
          onPress={handleRefresh}
          activeOpacity={0.8}>
          <Text style={[styles.actionBtnText, styles.actionBtnSecondaryText]}>↻ Refresh</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {flex: 1, backgroundColor: '#111827'},
  content: {padding: 20, paddingBottom: 40},
  totalCard: {
    backgroundColor: '#1D4ED8',
    borderRadius: 16,
    padding: 24,
    marginBottom: 20,
    alignItems: 'center',
  },
  totalLabel: {color: '#93C5FD', fontSize: 14, marginBottom: 4},
  totalValue: {color: '#fff', fontSize: 36, fontWeight: 'bold'},
  assetCard: {
    backgroundColor: '#1F2937',
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  assetIconCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#F59E0B',
    justifyContent: 'center',
    alignItems: 'center',
  },
  assetIconText: {color: '#fff', fontSize: 20, fontWeight: 'bold'},
  assetInfo: {flex: 1},
  assetName: {color: '#F9FAFB', fontSize: 16, fontWeight: '600'},
  assetAddress: {color: '#6B7280', fontSize: 12, marginTop: 2},
  assetAmounts: {alignItems: 'flex-end'},
  assetBalance: {color: '#F9FAFB', fontSize: 15, fontWeight: '600'},
  assetUsd: {color: '#9CA3AF', fontSize: 12, marginTop: 2},
  errorText: {color: '#EF4444', textAlign: 'center', marginTop: 8},
  actions: {flexDirection: 'row', gap: 12, marginTop: 12},
  actionBtn: {
    flex: 1,
    backgroundColor: '#3B82F6',
    padding: 14,
    borderRadius: 12,
    alignItems: 'center',
  },
  actionBtnSecondary: {backgroundColor: '#374151'},
  actionBtnText: {color: '#fff', fontWeight: '600', fontSize: 15},
  actionBtnSecondaryText: {color: '#D1D5DB'},
});
