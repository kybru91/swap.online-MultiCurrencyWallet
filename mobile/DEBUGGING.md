# MCW Mobile — Crash Debugging Guide

## Архитектура crash detection

```
index.js
  └── setupGlobalErrorHandlers()     ← ловит unhandled promise rejections + JS exceptions
        App.tsx
          └── <ErrorBoundary>        ← ловит React render crashes
                └── NavigationContainer → screens
```

---

## Инструменты дебага

### 1. adb logcat (основной инструмент)

```bash
# Все логи React Native (JS errors, warnings)
adb logcat -s ReactNativeJS

# Native crashes (SIGSEGV, OutOfMemory, ANR)
adb logcat | grep -E "(FATAL EXCEPTION|AndroidRuntime|Signal 11)"

# Только ошибки и выше
adb logcat *:E

# Комбо — всё важное
adb logcat | grep -E "(ReactNative|FATAL|crash|Error|Exception)" --color

# Сохранить лог с момента старта
adb logcat -c && adb logcat > /tmp/mcw_crash.txt

# Смотреть только наши логи (MCW prefix)
adb logcat | grep -E "\[CRASH\]|\[MCW\]"
```

### 2. Metro bundler (JS source maps + hot reload)

```bash
# Запустить Metro с verbose логированием
cd mobile && npx react-native start --verbose

# Если порт занят
npx react-native start --port 8082
```

### 3. Flipper (visual debugger)

Flipper встроен в React Native 0.73. Скачать: https://fbflipper.com/

Плагины для установки в Flipper:
- **React DevTools** — дерево компонентов, props, state
- **Network** — все HTTP запросы (axios, fetch)
- **Logs** — console.log / console.error / console.warn
- **Layout Inspector** — визуальный layout

```bash
# Flipper подключается автоматически при adb-соединении
# Просто открой Flipper desktop и запусти app на устройстве/эмуляторе
```

### 4. Chrome DevTools (JS debugging)

```bash
# 1. Запусти Metro: npx react-native start
# 2. В приложении: встряхни телефон → "Debug JS Remotely"
# 3. Откроется: http://localhost:8081/debugger-ui/
# 4. Открой Chrome DevTools (F12) → Sources → breakpoints
```

### 5. Hermes debugger (production-like)

React Native 0.73+ использует Hermes engine по умолчанию.

```bash
# Подключение через Chrome DevTools Protocol
# chrome://inspect → Remote Target → найди "Hermes React Native"
```

---

## Типы крашей и как их диагностировать

### JS Crash (render error)
**Симптом:** красный экран "App Crashed" с ErrorBoundary

**Что делать:**
1. Нажать "Copy Report" → отправить в Slack/Telegram
2. `adb logcat | grep "\[CRASH\]"` — полный stack trace
3. Найти строку в source map: `npx react-native symbolicatestack`

### JS Exception (unhandled)
**Симптом:** в release — тихий краш. В debug — red screen.

**Что делать:**
```bash
adb logcat | grep -E "ReactNativeJS.*Error"
```

### Native crash (SIGSEGV/ANR)
**Симптом:** app вылетает мгновенно, без JS ошибок

**Что делать:**
```bash
# 1. Смотреть tombstone файлы
adb shell ls /data/tombstones/
adb pull /data/tombstones/tombstone_00 /tmp/

# 2. Native crash лог
adb logcat | grep -E "(FATAL EXCEPTION|signal 11|libc)"

# 3. ANR traces
adb pull /data/anr/anr_XX /tmp/
```

### OutOfMemory
**Симптом:** "Application may be doing too much work on its main thread"

```bash
adb logcat | grep -E "(OutOfMemory|GC|low memory)"

# Heap dump
adb shell am dumpheap <pid> /sdcard/heap.hprof
adb pull /sdcard/heap.hprof /tmp/
# Открыть в Android Studio → Profiler
```

### Startup crash (app не открывается)
**Симптом:** app сразу закрывается после открытия

**Диагностика:**
```bash
# 1. Смотреть ВСЕ логи с самого запуска
adb logcat -c  # очистить
# запустить app
adb logcat | head -200  # первые 200 строк

# 2. Проверить, что Metro запущен
curl http://localhost:8081/status

# 3. Проверить bundler ошибки
curl http://localhost:8081/index.bundle?platform=android&dev=true 2>&1 | head -50

# 4. Удалить и переустановить
adb uninstall org.multicurrencywallet.mobile
cd mobile/android && ./gradlew installDebug

# 5. Проверить permission errors
adb logcat | grep -E "(Permission denied|SecurityException)"
```

---

## Debug режим vs Release

### Debug APK
```bash
cd mobile/android && ./gradlew installDebug
```
- Показывает JS red screen при ошибках
- Metro bundler с hot reload
- Flipper интеграция
- Source maps доступны

### Release APK (имитация production)
```bash
cd mobile/android && ./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release-unsigned.apk
```
- ProGuard/R8 минификация
- Hermes bytecode
- Нужны source maps для symbolication

---

## Полезные команды

```bash
# Узнать PID приложения
adb shell pidof org.multicurrencywallet.mobile

# Смотреть только логи нашего процесса
adb logcat --pid=$(adb shell pidof org.multicurrencywallet.mobile)

# Принудительно вызвать краш (для теста)
# В Settings Screen есть кнопка "Force Crash" в DEV режиме

# Сбросить Metro кэш (если странные ошибки)
cd mobile && npx react-native start --reset-cache

# Очистить gradle кэш
cd mobile/android && ./gradlew clean

# Посмотреть память
adb shell dumpsys meminfo org.multicurrencywallet.mobile
```

---

## Crash reports из ErrorBoundary

Когда приложение крашится через ErrorBoundary, экран показывает:
1. **Тип и сообщение ошибки** — красным
2. **Stack trace** — прокручиваемый
3. **Кнопка "Copy Report"** → отправить разработчику
4. **Кнопка "Try Again"** → сброс ErrorBoundary без перезапуска

Все крашы логируются через `adb logcat` с тегом `[CRASH]`.

---

## Что делать при crash on startup

1. `adb logcat -c && открой app && adb logcat > /tmp/crash.txt`
2. Ищи в логе: `grep -E "(FATAL|Exception|Error)" /tmp/crash.txt`
3. Проверь Metro: `curl http://localhost:8081/status`
4. Проверь нет ли missing native module: `grep "Native module" /tmp/crash.txt`
5. Попробуй `cd mobile && npx react-native start --reset-cache`
