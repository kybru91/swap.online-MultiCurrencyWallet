# TICK.md — MCW Periodic Maintenance

Периодические задачи технического обслуживания. Выполняй последовательно.

---

## 1. RPC Liveness Audit

Проверяем что все 10 EVM-нод живы. Dead chains удаляем из конфига.

```bash
cd /root/MultiCurrencyWallet

INFURA_KEY="fdd4494101ed4a28b41bb66d7fe9c692"
PAYLOAD='{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'

check_rpc() {
  local name="$1" url="$2"
  resp=$(curl -sf --max-time 10 -X POST "$url" -H 'Content-Type: application/json' -d "$PAYLOAD" 2>&1) \
    && echo "$resp" | grep -q '"result"' \
    && echo "✅ $name" \
    || echo "❌ $name  ← DEAD ($url)"
}

check_rpc "ETH"    "https://mainnet.infura.io/v3/$INFURA_KEY"
check_rpc "BNB"    "https://bsc-dataseed.binance.org/"
check_rpc "MATIC"  "https://rpc.ankr.com/polygon/afbbed6c16c518a252ca00b02d488e4ce457fb930b5f2aff5437a3a5191fb731"
check_rpc "ARBETH" "https://arb1.arbitrum.io/rpc"
check_rpc "AURETH" "https://mainnet.aurora.dev"
check_rpc "XDAI"   "https://rpc.gnosischain.com"
check_rpc "AVAX"   "https://api.avax.network/ext/bc/C/rpc"
check_rpc "MOVR"   "https://rpc.moonriver.moonbeam.network"
check_rpc "ONE"    "https://api.harmony.one"
check_rpc "AME"    "https://node1.amechain.io"
```

**Если нашли dead chain:** удали из 4 файлов:
- `src/front/config/mainnet/evmNetworks.js`
- `src/front/config/mainnet/web3.js`
- `src/front/config/mainnet/link.js`
- `src/front/config/mainnet/api.js`

И аналогичные в `testnet/`.

---

## 2. CI Status Check

```bash
cd /root/MultiCurrencyWallet
gh run list --limit 10
```

Все `deploymaster`, `deploy` (WP plugin), `appsSmoke` должны быть ✅.
Если нет — смотреть логи: `gh run view <id> --log-failed`

---

## 3. dApp Catalog Smoke Check

Убеждаемся, что все внешние URL в каталоге приложений доступны:

```bash
for url in \
  "https://dex.onout.org" \
  "https://polyfactory.wpmix.net" \
  "https://farm.wpmix.net" \
  "https://ido.onout.org" \
  "https://lottery.onout.org"; do
  code=$(curl -sf --max-time 8 -o /dev/null -w "%{http_code}" "$url")
  [ "$code" = "200" ] && echo "✅ $url" || echo "❌ $url  (HTTP $code)"
done
```

---

## 4. Dependencies Audit

```bash
cd /root/MultiCurrencyWallet
npm audit --audit-level=critical
```

Critical уязвимости — фиксить сразу. High — оценивать контекст (atomic swap libs часто имеют теоретические уязвимости в неиспользуемых путях).

---

## 5. TICK.md Cleanup

Удалять из этого файла:
- Устаревшие URL/команды (если chain/сервис убран из проекта)
- Блоки, которые больше не актуальны

Добавлять новые секции при:
- Добавлении новых EVM chains в конфиг
- Добавлении новых dApp в каталог

---

## Workflow Summary

| Шаг | Команда | Ожидаемый результат |
|-----|---------|---------------------|
| 1 | bash RPC check block | Все 10 endpoints ✅ |
| 2 | `gh run list --limit 10` | Все workflows ✅ |
| 3 | bash dApp URL check | Все 5 URL HTTP 200 |
| 4 | `npm audit --audit-level=critical` | 0 critical |
| 5 | Edit TICK.md | Актуальный файл |

**CI автоматизация:** `.github/workflows/rpc-healthcheck.yml` запускается каждые 6 часов (cron).

**Обновлено:** 2026-03-04
