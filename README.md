# Nectar VPN — Android клиент

Чёрно-белый Android-клиент для Nectar VPN на ядре **Xray-core**.

Поддерживается: VLESS (TCP / XHTTP / gRPC / WS, REALITY / TLS), Hysteria2, Trojan, VMess, Shadowsocks,
подписки в формате Xray-JSON (как в Happ) и обычные списки ключей (base64 / текст).

## Как скачать APK

1. Откройте вкладку **Releases** репозитория (или **Actions → последний запуск → Artifacts**).
2. Скачайте `NectarVPN_<версия>_arm64-v8a.apk` (для большинства телефонов) или `..._universal.apk`.
3. Установите (разрешите установку из неизвестных источников).

## Как пользоваться

* «+» → вставьте ссылку на подписку `https://…` или ключ `vless://…`.
* Нажмите большую кнопку — VPN подключится.
* Подписка обновляется автоматически при запуске по интервалу из заголовка `profile-update-interval`.
* Ссылки вида `nectarvpn://add/https://…` открывают приложение и добавляют подписку.
* Плитка «Nectar VPN» в шторке быстрых настроек — включение/выключение одним касанием.

## Сборка

APK собирается автоматически GitHub Actions (`.github/workflows/build.yml`) при каждом пуше.
Чтобы новые версии ставились поверх старых без удаления, добавьте в **Settings → Secrets and variables → Actions**
секреты `KEYSTORE_BASE64` (keystore в base64), `KEYSTORE_PASSWORD` и `KEY_ALIAS`. Без них каждая сборка
подписывается новым временным ключом.
