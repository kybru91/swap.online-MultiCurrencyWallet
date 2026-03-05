import React from 'react';
import {createBottomTabNavigator} from '@react-navigation/bottom-tabs';
import WalletScreen from '@screens/WalletScreen';
import HistoryScreen from '@screens/HistoryScreen';
import DAppBrowserScreen from '@screens/dapp/DAppBrowserScreen';
import SettingsScreen from '@screens/SettingsScreen';

export type MainTabParamList = {
  Wallet: undefined;
  History: undefined;
  DApps: undefined;
  Settings: undefined;
};

const Tab = createBottomTabNavigator<MainTabParamList>();

/**
 * Main bottom tab navigator: Wallet | History | DApps | Settings
 */
export default function MainNavigator() {
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarStyle: {backgroundColor: '#111827'},
        tabBarActiveTintColor: '#3B82F6',
        tabBarInactiveTintColor: '#9CA3AF',
      }}>
      <Tab.Screen name="Wallet" component={WalletScreen} />
      <Tab.Screen name="History" component={HistoryScreen} />
      <Tab.Screen name="DApps" component={DAppBrowserScreen} />
      <Tab.Screen name="Settings" component={SettingsScreen} />
    </Tab.Navigator>
  );
}
