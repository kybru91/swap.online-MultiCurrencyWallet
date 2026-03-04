import * as React from 'react'
import { Provider } from 'react-redux'
import store, { history } from 'redux/store'
import routes from 'shared/routes'
import { Router } from 'react-router-dom'
import { WagmiProvider } from 'wagmi'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import App from 'containers/App/App'
import IntlProviderContainer from './IntlProviderContainer'

import reducers from 'redux/core/reducers'
import { wagmiConfig } from 'lib/appkit'

const queryClient = new QueryClient()

type RootProps = {
  history: typeof history
  store: typeof store
  routes: typeof routes
}

export default class Root extends React.Component<RootProps> {
  constructor(props) {
    super(props)

    // reset dinamic reducers data
    reducers.user.setIsBalanceFetching({ isBalanceFetching: false })
  }

  render() {
    const { history, store, routes } = this.props

    return (
      <WagmiProvider config={wagmiConfig}>
        <QueryClientProvider client={queryClient}>
          <Provider store={store}>
            <Router history={history}>
              <>
                <IntlProviderContainer>
                  <App history={history}>{routes}</App>
                </IntlProviderContainer>
              </>
            </Router>
          </Provider>
        </QueryClientProvider>
      </WagmiProvider>
    )
  }
}
