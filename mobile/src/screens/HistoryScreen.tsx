import React, {useEffect, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  RefreshControl,
  ActivityIndicator,
} from 'react-native';
import {useWalletStore} from '@state/walletStore';
import {useAuthStore} from '@state/authStore';
import type {TransactionRecord} from '@network/BtcApi';

type Tab = 'BTC' | 'ETH';

export default function HistoryScreen() {
  const {btcHistory, ethHistory, historyLoading, refreshHistory} = useWalletStore();
  const onUserInteraction = useAuthStore(s => s.onUserInteraction);
  const [tab, setTab] = useState<Tab>('ETH');

  useEffect(() => {
    refreshHistory();
  }, []);

  const txs: TransactionRecord[] = tab === 'BTC' ? (btcHistory ?? []) : (ethHistory ?? []);

  const formatDate = (ts: number) => {
    if (!ts) return '—';
    return new Date(ts * 1000).toLocaleDateString('en-US', {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  };

  const renderItem = ({item}: {item: TransactionRecord}) => {
    const isIn = item.direction === 'in';
    const isSelf = item.direction === 'self';
    return (
      <View style={styles.txRow}>
        <View style={[styles.txIcon, isIn ? styles.txIconIn : isSelf ? styles.txIconSelf : styles.txIconOut]}>
          <Text style={styles.txIconText}>{isSelf ? '↕' : isIn ? '↓' : '↑'}</Text>
        </View>
        <View style={styles.txInfo}>
          <Text style={styles.txType}>
            {isSelf ? 'Self Transfer' : isIn ? 'Received' : 'Sent'}
          </Text>
          <Text style={styles.txDate}>{formatDate(item.timestamp)}</Text>
          <Text style={styles.txHash} numberOfLines={1}>
            {item.hash.slice(0, 20)}...
          </Text>
        </View>
        <View style={styles.txAmount}>
          <Text style={[styles.txAmountText, isIn ? styles.amountIn : styles.amountOut]}>
            {isIn ? '+' : '-'}{item.amount.toFixed(8)} {item.currency}
          </Text>
          <Text style={styles.txConf}>
            {item.confirmations > 0 ? `${item.confirmations} conf.` : 'Unconfirmed'}
          </Text>
        </View>
      </View>
    );
  };

  return (
    <View style={styles.container}>
      {/* Tab selector */}
      <View style={styles.tabs}>
        {(['ETH', 'BTC'] as Tab[]).map(t => (
          <TouchableOpacity
            key={t}
            style={[styles.tab, tab === t && styles.tabActive]}
            onPress={() => { setTab(t); onUserInteraction(); }}>
            <Text style={[styles.tabText, tab === t && styles.tabTextActive]}>{t}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {historyLoading && txs.length === 0 ? (
        <ActivityIndicator color="#3B82F6" style={{marginTop: 40}} />
      ) : txs.length === 0 ? (
        <View style={styles.emptyState}>
          <Text style={styles.emptyText}>No transactions yet</Text>
          <Text style={styles.emptySubtext}>Your {tab} transactions will appear here</Text>
        </View>
      ) : (
        <FlatList
          data={txs}
          keyExtractor={item => item.hash}
          renderItem={renderItem}
          refreshControl={
            <RefreshControl
              refreshing={historyLoading}
              onRefresh={refreshHistory}
              tintColor="#3B82F6"
            />
          }
          contentContainerStyle={styles.listContent}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#111827'},
  tabs: {
    flexDirection: 'row',
    backgroundColor: '#1F2937',
    margin: 16,
    borderRadius: 10,
    padding: 4,
  },
  tab: {flex: 1, padding: 10, borderRadius: 8, alignItems: 'center'},
  tabActive: {backgroundColor: '#374151'},
  tabText: {color: '#9CA3AF', fontWeight: '600'},
  tabTextActive: {color: '#F9FAFB'},
  listContent: {paddingHorizontal: 16},
  txRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1F2937',
    borderRadius: 12,
    padding: 14,
    marginBottom: 8,
    gap: 12,
  },
  txIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
  },
  txIconIn: {backgroundColor: '#064E3B'},
  txIconOut: {backgroundColor: '#450A0A'},
  txIconSelf: {backgroundColor: '#1E3A5F'},
  txIconText: {fontSize: 18},
  txInfo: {flex: 1},
  txType: {color: '#F9FAFB', fontWeight: '600', fontSize: 14},
  txDate: {color: '#9CA3AF', fontSize: 12, marginTop: 2},
  txHash: {color: '#6B7280', fontSize: 11, marginTop: 2},
  txAmount: {alignItems: 'flex-end'},
  txAmountText: {fontSize: 13, fontWeight: '600'},
  amountIn: {color: '#10B981'},
  amountOut: {color: '#EF4444'},
  txConf: {color: '#6B7280', fontSize: 11, marginTop: 2},
  emptyState: {flex: 1, justifyContent: 'center', alignItems: 'center', marginTop: 80},
  emptyText: {color: '#F9FAFB', fontSize: 18, fontWeight: '600'},
  emptySubtext: {color: '#9CA3AF', fontSize: 14, marginTop: 8},
});
