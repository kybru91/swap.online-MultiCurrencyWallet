import React, { useEffect, useMemo, useRef } from 'react'
import CSSModules from 'react-css-modules'
import { withRouter } from 'react-router-dom'
import { FormattedMessage, injectIntl } from 'react-intl'
import { links } from 'helpers'
import { localisedUrl } from 'helpers/locale'
import { connect } from 'redaction'

import styles from './Apps.scss'
import {
  walletAppsCatalog,
  getWalletAppById,
  isAllowedWalletAppUrl,
  resolveWalletAppUrl,
} from './appsCatalog'
import { createWalletAppsBridge } from './walletBridge'

type AppsProps = {
  history: any
  match: {
    params: {
      appId?: string
    }
  }
  intl: any
  ethData: {
    address: string
    currency: string
  } | null
}

const Apps = (props: AppsProps) => {
  const iframeRef = useRef<HTMLIFrameElement | null>(null)
  const bridgeRef = useRef<any>(null)

  const {
    history,
    intl: { locale },
    match: {
      params: { appId: routeAppId },
    },
    ethData,
  } = props

  const selectedApp = useMemo(() => {
    if (!routeAppId) {
      return undefined
    }

    return getWalletAppById(routeAppId)
  }, [routeAppId])

  useEffect(() => {
    if (routeAppId && !getWalletAppById(routeAppId)) {
      history.replace(localisedUrl(locale, links.apps))
    }
  }, [routeAppId, history, locale])

  const appUrl = useMemo(() => {
    if (!selectedApp) return ''
    const base = resolveWalletAppUrl(selectedApp)
    if (selectedApp.isInternal) return base
    const rawScheme = document.body.dataset.scheme || 'default'
    const scheme = rawScheme === 'dark' ? 'dark' : 'light'
    const sep = base.includes('?') ? '&' : '?'
    return `${base}${sep}theme=${scheme}`
  }, [selectedApp])
  const isAllowedAppUrl = selectedApp ? isAllowedWalletAppUrl(appUrl) : false
  const needsBridge = selectedApp?.walletBridge === 'eip1193'

  useEffect(() => {
    if (bridgeRef.current) {
      bridgeRef.current.destroy()
      bridgeRef.current = null
    }

    if (!needsBridge || !isAllowedAppUrl || !iframeRef.current) {
      return
    }

    bridgeRef.current = createWalletAppsBridge({
      iframe: iframeRef.current,
      appUrl,
      internalWallet: ethData,
    })

    return () => {
      if (bridgeRef.current) {
        bridgeRef.current.destroy()
        bridgeRef.current = null
      }
    }
  }, [needsBridge, appUrl, isAllowedAppUrl, ethData])

  const handleOpenApp = (id: string) => {
    history.push(localisedUrl(locale, `${links.apps}/${id}`))
  }

  const handleAppFrameLoad = () => {
    if (bridgeRef.current) {
      bridgeRef.current.sendReady()
    }
  }

  if (selectedApp) {
    return (
      <section styleName="appsPageFull">
        {!isAllowedAppUrl && (
          <div className="container">
            <div styleName="securityNotice">
              <FormattedMessage
                id="Apps_SecurityNotice"
                defaultMessage="Blocked by allowlist policy. Add app host to allowlist before embedding."
              />
            </div>
          </div>
        )}

        {isAllowedAppUrl && (
          <iframe
            key={selectedApp.id}
            ref={iframeRef}
            title={selectedApp.title}
            src={appUrl}
            onLoad={handleAppFrameLoad}
            styleName="appFrame"
            sandbox="allow-forms allow-same-origin allow-scripts allow-popups allow-popups-to-escape-sandbox"
            allow="clipboard-read; clipboard-write"
          />
        )}
      </section>
    )
  }

  return (
    <div className="container">
      <section styleName="appsPage">
        <header styleName="header">
          <h1 styleName="title">
            <FormattedMessage id="Apps_Title" defaultMessage="Wallet Apps" />
          </h1>
          <p styleName="description">
            <FormattedMessage
              id="Apps_Description"
              defaultMessage="Open integrated dApps inside wallet UI for seamless flow."
            />
          </p>
        </header>

        <section styleName="appsCatalogGrid">
          {walletAppsCatalog.map((app) => (
            <button
              key={app.id}
              type="button"
              styleName="appTile"
              onClick={() => handleOpenApp(app.id)}
            >
              {app.cardImage ? (
                <div styleName="appCardWrap">
                  <img src={app.cardImage} alt={app.title} styleName="appCardImage" />
                </div>
              ) : (
                <div styleName="appIconWrap">
                  <span styleName="appIconFallback">{app.iconSymbol || app.title.charAt(0)}</span>
                </div>
              )}
              <div styleName="appTileTitle">{app.title}</div>
              {app.isInternal && (
                <span styleName="appLabel">
                  <FormattedMessage id="Apps_Internal" defaultMessage="Internal" />
                </span>
              )}
            </button>
          ))}
        </section>
      </section>
    </div>
  )
}

export default withRouter(
  connect({
    ethData: 'user.ethData',
  })(injectIntl(CSSModules(Apps, styles, { allowMultiple: true })))
)
