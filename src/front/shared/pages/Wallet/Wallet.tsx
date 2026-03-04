import { useState, useEffect, useRef } from 'react'
import { useHistory, useRouteMatch, useLocation } from 'react-router-dom'
import { FormattedMessage, useIntl } from 'react-intl'
import { connect } from 'redaction'
import cssModules from 'react-css-modules'
import { isMobile } from 'react-device-detect'
import { BigNumber } from 'bignumber.js'
import moment from 'moment'
import { useAppKitAccount, useAppKitNetwork } from '@reown/appkit/react'

import appConfig from 'app-config'
import actions from 'redux/actions'
import { links, constants, stats, user, routing } from 'helpers'
import { localisedUrl } from 'helpers/locale'
import config from 'helpers/externalConfig'
import metamask from 'helpers/metamask'
import wpLogoutModal from 'helpers/wpLogoutModal'
import feedback from 'helpers/feedback'

import InvoicesList from 'pages/Invoices/InvoicesList'
import History from 'pages/History/History'
import DashboardLayout from 'components/layout/DashboardLayout/DashboardLayout'
import BalanceForm from 'components/BalanceForm/BalanceForm'
import CurrenciesList from './CurrenciesList'
import styles from './Wallet.scss'

const host = window.location.hostname || document.location.host
const isWidgetBuild = config && config.isWidget

function Wallet(props) {
  const {
    hiddenCoinsList,
    isBalanceFetching,
    activeFiat,
    activeCurrency,
    currencies,
    multisigPendingCount: multisigPendingCountProp,
    coinsData,
  } = props

  const history = useHistory()
  const match = useRouteMatch<{ page?: string }>()
  const location = useLocation()
  const intl = useIntl()
  const { locale } = intl

  const { address: appKitAddress, isConnected: appKitIsConnected } = useAppKitAccount()
  const { caipNetwork } = useAppKitNetwork()

  const appKitNetworkVersion = caipNetwork?.id
  const appKitUnknownNetwork = !metamask.isAvailableNetwork()

  const page = match?.params?.page ?? null

  const getInitialActiveComponentNum = () => {
    if (page === 'history' && !isMobile) return 1
    if (page === 'invoices') return 2
    return 0
  }

  const [activeComponentNum, setActiveComponentNum] = useState(getInitialActiveComponentNum)
  const [multisigPendingCount, setMultisigPendingCount] = useState(multisigPendingCountProp)

  const syncTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const handleConnectWallet = () => {
    if (metamask.isConnected()) {
      history.push(localisedUrl(locale, links.home))
      return
    }
    setTimeout(() => {
      metamask.connect({})
    }, 100)
  }

  const getInfoAboutCurrency = async () => {
    const currencyNames = currencies.map(({ value, name }) => value || name)
    await actions.user.getInfoAboutCurrency(currencyNames)
  }

  const handleWithdraw = (params) => {
    const userCurrencyData = actions.core.getWallets({})
    const { address, amount } = params
    const item = userCurrencyData.find(
      ({ currency }) => currency.toLowerCase() === params.currency.toLowerCase(),
    )

    actions.modals.open(constants.modals.Withdraw, {
      ...item,
      toAddress: address,
      amount,
    })
  }

  useEffect(() => {
    if (location.pathname.toLowerCase() === links.connectWallet.toLowerCase()) {
      handleConnectWallet()
    }

    actions.user.getBalances()
    actions.user.fetchMultisigStatus()

    const { params, url } = match
    if (url.includes('send')) {
      handleWithdraw(params)
    }

    if (page === 'exit') {
      wpLogoutModal(() => {
        history.push(localisedUrl(locale, links.home))
      }, intl)
    }

    getInfoAboutCurrency()
    setMultisigPendingCount(multisigPendingCountProp)
  }, [])

  useEffect(() => {
    if (location.pathname.toLowerCase() === links.connectWallet.toLowerCase()) {
      handleConnectWallet()
    }
  }, [location.pathname])

  useEffect(() => {
    let newActiveComponentNum = 0
    if (page === 'history' && !isMobile) newActiveComponentNum = 1
    if (page === 'invoices') newActiveComponentNum = 2

    if (page === 'exit') {
      wpLogoutModal(() => {
        history.push(localisedUrl(locale, links.home))
      }, intl)
    }

    setActiveComponentNum(newActiveComponentNum)
    setMultisigPendingCount(multisigPendingCountProp)

    if (syncTimer.current) clearTimeout(syncTimer.current)
  }, [page, multisigPendingCountProp])

  const goToСreateWallet = () => {
    feedback.wallet.pressedAddCurrency()
    history.push(localisedUrl(locale, links.createWallet))
  }

  const handleReceive = (context) => {
    const widgetCurrencies = user.getWidgetCurrencies()
    const filteredCurrencies = user.filterUserCurrencyData(actions.core.getWallets())

    const availableWallets = filteredCurrencies.filter((item) => {
      const { isMetamask, isConnected, currency, balance } = item

      return (
        (context !== 'Send' || balance)
        && (!isMetamask || (isMetamask && isConnected))
        && (!isWidgetBuild || widgetCurrencies.includes(currency))
      )
    })

    actions.modals.open(constants.modals.CurrencyAction, {
      currencies: availableWallets,
      context,
    })
  }

  const showNoWalletsNotification = () => {
    actions.notifications.show(
      constants.notifications.Message,
      { message: (
        <FormattedMessage
          id="WalletEmptyBalance"
          defaultMessage="No wallets available"
        />
      ) },
    )
  }

  const handleWithdrawFirstAsset = () => {
    const userCurrencyData = actions.core.getWallets({})
    const availableWallets = user.filterUserCurrencyData(userCurrencyData)

    if (
      !Object.keys(availableWallets).length
      || (Object.keys(availableWallets).length === 1 && !user.isCorrectWalletToShow(availableWallets[0]))
    ) {
      showNoWalletsNotification()
      return
    }

    const firstAvailableWallet = availableWallets.find((wallet) => (
      user.isCorrectWalletToShow(wallet)
        && !wallet.balanceError
        && new BigNumber(wallet.balance).isPositive()
    ))

    if (!firstAvailableWallet) {
      showNoWalletsNotification()
      return
    }

    const { currency, address, tokenKey } = firstAvailableWallet
    let targetCurrency = currency

    switch (currency.toLowerCase()) {
      case 'btc (multisig)':
      case 'btc (sms-protected)':
      case 'btc (pin-protected)':
        targetCurrency = 'btc'
    }

    const firstUrlPart = tokenKey ? `/token/${tokenKey}` : `/${targetCurrency}`

    history.push(
      localisedUrl(locale, `${firstUrlPart}/${address}/send`),
    )
  }

  const returnFiatBalanceByWallet = (wallet) => {
    const hasFiatPrice = wallet.balance > 0 && wallet.infoAboutCurrency?.price_fiat

    if (hasFiatPrice) {
      return new BigNumber(wallet.balance)
        .multipliedBy(wallet.infoAboutCurrency.price_fiat)
        .dp(2, BigNumber.ROUND_FLOOR)
        .toNumber()
    }

    return 0
  }

  // expose for testing (preserves original window.testSaveShamirsSecrets API)
  ;(window as any).testSaveShamirsSecrets = () => {
    actions.modals.open(constants.modals.ShamirsSecretSave)
  }

  const addFiatBalanceInUserCurrencyData = (currencyData) => {
    currencyData.forEach((wallet) => {
      wallet.fiatBalance = returnFiatBalanceByWallet(wallet)
    })
    return currencyData
  }

  const returnBalanceInBtc = (wallet) => {
    const widgetCurrencies = user.getWidgetCurrencies()
    const name = wallet.isToken
      ? wallet.tokenKey.toUpperCase()
      : (wallet.currency || wallet.name)

    if (
      (!isWidgetBuild || widgetCurrencies.includes(name))
      && !wallet.balanceError
      && wallet.infoAboutCurrency?.price_btc
      && wallet.balance > 0
    ) {
      return wallet.balance * wallet.infoAboutCurrency.price_btc
    }

    return 0
  }

  const returnTotalBalanceInBtc = (currencyData) => {
    let balance = new BigNumber(0)
    currencyData.forEach((wallet) => {
      balance = balance.plus(returnBalanceInBtc(wallet))
    })
    return balance.toNumber()
  }

  const returnTotalFiatBalance = (currencyData) => {
    let balance = new BigNumber(0)
    currencyData.forEach((wallet) => {
      balance = balance.plus(wallet.fiatBalance)
    })
    return balance.toNumber()
  }

  const syncData = () => {
    // that is for noxon, dont delete it :)
    const now = moment().format('HH:mm:ss DD/MM/YYYY')
    const lastCheck = localStorage.getItem(constants.localStorage.lastCheckBalance) || now
    const lastCheckMoment = moment(lastCheck, 'HH:mm:ss DD/MM/YYYY')

    const isFirstCheck = moment(now, 'HH:mm:ss DD/MM/YYYY').isSame(lastCheckMoment)
    const isOneHourAfter = moment(now, 'HH:mm:ss DD/MM/YYYY').isAfter(
      lastCheckMoment.add(1, 'hours'),
    )

    // coinsData.ethData holds the ETH wallet used for stats reporting
    const ethData = coinsData?.ethData

    // Build metamaskData entry from Reown AppKit hooks instead of Redux metamaskData
    const metamaskWalletEntry = {
      currency: 'ETH Metamask',
      address: appKitAddress ?? '',
      isConnected: appKitIsConnected,
      networkVersion: appKitNetworkVersion,
      unknownNetwork: appKitUnknownNetwork,
    }

    const coinsDataWithMetamask = {
      ...coinsData,
      metamaskData: metamaskWalletEntry,
    }

    syncTimer.current = setTimeout(async () => {
      if (host === 'localhost' || config?.entry !== 'mainnet' || !metamask.isCorrectNetwork()) {
        return
      }

      if (isOneHourAfter || isFirstCheck) {
        localStorage.setItem(constants.localStorage.lastCheckBalance, now)
        try {
          const ipInfo = await stats.getIPInfo()

          const registrationData: {
            locale: string
            ip: string
            widget_url?: string
            wallets?: IUniversalObj[]
          } = {
            locale:
              ipInfo.locale
              || (navigator.userLanguage || navigator.language || 'en-gb').split('-')[0],
            ip: ipInfo.ip,
          }

          let widgetUrl
          if (appConfig.isWidget) {
            widgetUrl = routing.getTopLocation().origin
            registrationData.widget_url = widgetUrl
          }

          const tokensArray: any[] = Object.values(coinsDataWithMetamask)

          const wallets = tokensArray.map((item) => ({
            symbol: item && item.currency ? item.currency.split(' ')[0] : '',
            type: item && item.currency ? item.currency.split(' ')[1] || 'common' : '',
            address: item && item.address ? item.address : '',
            balance: item && item.balance ? new BigNumber(item.balance).toNumber() : 0,
            public_key: item && item.publicKey ? item.publicKey.toString('Hex') : '',
            entry: config?.entry ? config.entry : 'testnet:undefined',
          }))

          registrationData.wallets = wallets

          await stats.updateUser(ethData.address, routing.getTopLocation().host, registrationData)
        } catch (error) {
          console.group('wallet >%c syncData', 'color: red;')
          console.error(`Sync error in wallet: ${error}`)
          console.groupEnd()
        }
      }
    }, 2000)
  }

  if (!config.isWidget || (window as any)?.STATISTICS_ENABLED) {
    syncData()
  }

  let userWallets = user.filterUserCurrencyData(actions.core.getWallets({}))
  userWallets = addFiatBalanceInUserCurrencyData(userWallets)

  const balanceInBtc = returnTotalBalanceInBtc(userWallets)
  const allFiatBalance = returnTotalFiatBalance(userWallets)

  return (
    <DashboardLayout
      page={page as any}
      BalanceForm={(
        <BalanceForm
          activeFiat={activeFiat}
          fiatBalance={allFiatBalance}
          currencyBalance={balanceInBtc}
          activeCurrency={activeCurrency}
          handleReceive={handleReceive}
          handleWithdraw={handleWithdrawFirstAsset}
          isFetching={isBalanceFetching}
          type="wallet"
          currency="btc"
          multisigPendingCount={multisigPendingCount}
        />
      )}
    >
      {activeComponentNum === 0 && (
        <CurrenciesList
          tableRows={userWallets}
          hiddenCoinsList={hiddenCoinsList}
          goToСreateWallet={goToСreateWallet}
          multisigPendingCount={multisigPendingCount}
        />
      )}
      {activeComponentNum === 1 && <History {...props} />}
      {activeComponentNum === 2 && <InvoicesList {...props} onlyTable />}
    </DashboardLayout>
  )
}

const mapStateToProps = ({
  core: { hiddenCoinsList },
  user,
  user: {
    activeFiat,
    ethData,
    bnbData,
    maticData,
    arbethData,
    aurethData,
    xdaiData,
    ftmData,
    movrData,
    oneData,
    ameData,
    avaxData,
    btcData,
    ghostData,
    nextData,
    phi_v1Data,
    phiData,
    fkwData,
    phpxData,
    tokensData,
    btcMultisigSMSData,
    btcMultisigUserData,
    btcMultisigUserDataList,
    isBalanceFetching,
    multisigPendingCount,
    activeCurrency,
  },
  currencies: { items: currencies },
  modals,
}: any) => {
  const userCurrencyData = [
    ethData,
    bnbData,
    maticData,
    arbethData,
    aurethData,
    xdaiData,
    ftmData,
    avaxData,
    movrData,
    oneData,
    ameData,
    btcData,
    ghostData,
    nextData,
    phi_v1Data,
    phiData,
    fkwData,
    phpxData,
    ...Object.keys(tokensData).map((k) => tokensData[k]),
  ]

  return {
    userCurrencyData,
    currencies,
    isBalanceFetching,
    multisigPendingCount,
    hiddenCoinsList,
    user,
    activeCurrency,
    activeFiat,
    coinsData: {
      ethData,
      bnbData,
      maticData,
      arbethData,
      aurethData,
      xdaiData,
      ftmData,
      avaxData,
      movrData,
      oneData,
      ameData,
      phi_v1Data,
      phiData,
      fkwData,
      phpxData,
      btcData,
      ghostData,
      nextData,
      btcMultisigSMSData,
      btcMultisigUserData,
      btcMultisigUserDataList,
    },
    modals,
  }
}

export default cssModules(connect(mapStateToProps)(Wallet), styles, { allowMultiple: true })
