// Webhook of the separate *admin* Telegram bot ("Nectar Control").
// It never touches the main @NECTARVPN_xbot: it only edits the app configuration.
//
// Setup: create a bot in @BotFather, put its token into TG_TOKEN, then open
//   https://<domain>/api/telegram?setup=1&password=<ADMIN_PASSWORD>
const { getConfig, updateConfig, getAdmins, addAdmin } = require("../lib/store");
const { checkPassword } = require("../lib/auth");

const TOKEN = () => process.env.TG_TOKEN || "";

async function tg(method, payload) {
  const r = await fetch(`https://api.telegram.org/bot${TOKEN()}/${method}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return r.json();
}

const send = (chat, text, extra = {}) =>
  tg("sendMessage", { chat_id: chat, text, parse_mode: "HTML", disable_web_page_preview: true, ...extra });

const esc = (s) => String(s ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

const MENU = {
  inline_keyboard: [
    [{ text: "📊 Статус", callback_data: "status" }, { text: "🔗 Ссылки", callback_data: "links" }],
    [{ text: "🛠 Тех. работы ВКЛ", callback_data: "maint_on" }, { text: "✅ Тех. работы ВЫКЛ", callback_data: "maint_off" }],
    [{ text: "📢 Убрать объявление", callback_data: "ann_off" }, { text: "🔕 Убрать сообщение", callback_data: "popup_off" }],
    [{ text: "❓ Все команды", callback_data: "help" }],
  ],
};

const HELP = `<b>Nectar Control — управление приложением</b>

<b>Общее</b>
/status — текущие настройки
/panel — ссылка на веб-админку
/menu — кнопки быстрого управления

<b>Сообщения пользователям</b>
/announce Текст — баннер на главном экране
/announce off — убрать баннер
/announce_link https://… Текст кнопки — кнопка в баннере
/popup Текст — разовое окно всем (покажется 1 раз)
/popup off — отменить

<b>Тех. работы и обновления</b>
/maintenance on Текст — заблокировать подключение с сообщением
/maintenance off — снять блокировку
/update 5 https://… — предложить обновиться до версии 5
/forceupdate 5 https://… — обязательное обновление (ниже 5 — не пустит)

<b>Ссылки и кнопки</b>
/bot https://t.me/… — кнопка «Получить подписку»
/support https://t.me/… — поддержка
/channel https://t.me/… — канал
/iphone https://… — инструкция для iPhone
/button Название | https://… — добавить кнопку на главный экран
/buttons clear — удалить все кнопки

<b>Серверы</b>
/hide Норвегия — скрыть серверы с этим словом
/unhide Норвегия — вернуть
/pin Германия — закрепить сверху
/rename Старое | Новое — переименовать
/addkey vless://… — добавить сервер всем пользователям
/keys — список добавленных ключей
/delkey 2 — удалить ключ №2

<b>Настройки по умолчанию</b>
/dns 1.1.1.1 — DNS по умолчанию
/ua Happ/3.0.0 — User-Agent для подписки
/default autoReconnect true — любая настройка приложения
/forcedefaults on|off — применять настройки принудительно

<b>Подключение из основного бота</b>
/link https://sub… — ссылка «Открыть в Nectar VPN» для пользователя`;

function summary(c) {
  const on = (b) => (b ? "🟢 вкл" : "⚪ выкл");
  return [
    `<b>Статус приложения</b>`,
    `Тех. работы: ${on(c.maintenance.enabled)}${c.maintenance.enabled ? " — " + esc(c.maintenance.text) : ""}`,
    `Объявление: ${on(c.announcement.enabled)}${c.announcement.enabled ? " — " + esc(c.announcement.text) : ""}`,
    `Разовое сообщение: ${c.popup.id ? esc(c.popup.text) : "нет"}`,
    `Мин. версия: ${c.update.minVersionCode || "—"}, последняя: ${c.update.latestVersionCode || "—"}`,
    `Бот: ${esc(c.links.bot)}`,
    `Кнопок: ${c.buttons.length}, доп. ключей: ${c.extraKeys.length}`,
    `Скрыто: ${c.servers.hide.join(", ") || "—"}; закреплено: ${c.servers.pin.join(", ") || "—"}`,
    `Обновлено: ${c.updatedAt ? new Date(c.updatedAt).toLocaleString("ru-RU", { timeZone: "Europe/Moscow" }) : "никогда"}`,
  ].join("\n");
}

function origin(req) {
  const host = req.headers["x-forwarded-host"] || req.headers.host;
  return `https://${host}`;
}

async function handleCommand(chat, text, req) {
  const [cmdRaw, ...rest] = text.trim().split(/\s+/);
  const cmd = cmdRaw.split("@")[0].toLowerCase();
  const arg = text.trim().slice(cmdRaw.length).trim();
  const first = (rest[0] || "").toLowerCase();
  const saved = async (fn, msg) => {
    const c = await updateConfig(fn);
    await send(chat, `✅ ${msg}\n\n${summary(c)}`);
  };
  const needArg = () => send(chat, "Не хватает параметра. /help — все команды");

  switch (cmd) {
    case "/start":
    case "/menu":
      return send(chat, "Панель управления Nectar VPN", { reply_markup: MENU });
    case "/help":
      return send(chat, HELP);
    case "/status":
      return send(chat, summary(await getConfig()));
    case "/panel":
      return send(chat, `Веб-админка: ${origin(req)}/admin`);

    case "/announce":
      if (!arg) return needArg();
      if (first === "off") return saved((c) => { c.announcement.enabled = false; }, "Объявление убрано");
      return saved((c) => { c.announcement = { ...c.announcement, enabled: true, text: arg }; }, "Объявление опубликовано");
    case "/announce_link": {
      const [url, ...t] = rest;
      if (!url) return needArg();
      return saved((c) => { c.announcement.url = url; c.announcement.buttonText = t.join(" ") || "Подробнее"; }, "Кнопка объявления сохранена");
    }
    case "/popup":
      if (!arg) return needArg();
      if (first === "off") return saved((c) => { c.popup = { id: "", title: "", text: "", url: "", buttonText: "" }; }, "Разовое сообщение отменено");
      return saved((c) => { c.popup = { ...c.popup, id: String(Date.now()), text: arg }; }, "Сообщение покажется всем пользователям один раз");

    case "/maintenance":
      if (first === "on") return saved((c) => { c.maintenance.enabled = true; if (rest.length > 1) c.maintenance.text = rest.slice(1).join(" "); }, "Тех. работы включены — подключение заблокировано");
      if (first === "off") return saved((c) => { c.maintenance.enabled = false; }, "Тех. работы выключены");
      return needArg();

    case "/update":
    case "/forceupdate": {
      const v = parseInt(rest[0], 10);
      if (!v) return needArg();
      return saved((c) => {
        c.update.latestVersionCode = v;
        if (rest[1]) c.update.url = rest[1];
        if (rest.length > 2) c.update.text = rest.slice(2).join(" ");
        if (cmd === "/forceupdate") c.update.minVersionCode = v;
      }, cmd === "/forceupdate" ? `Обязательное обновление до версии ${v}` : `Предложено обновление до версии ${v}`);
    }

    case "/bot": case "/support": case "/channel": case "/iphone": {
      if (!rest[0]) return needArg();
      const key = cmd.slice(1);
      return saved((c) => { c.links[key] = rest[0]; }, "Ссылка сохранена");
    }
    case "/button": {
      const [title, url] = arg.split("|").map((s) => s.trim());
      if (!title || !url) return send(chat, "Формат: /button Название | https://…");
      return saved((c) => { c.buttons.push({ title, url }); }, "Кнопка добавлена");
    }
    case "/buttons":
      if (first === "clear") return saved((c) => { c.buttons = []; }, "Кнопки удалены");
      return send(chat, (await getConfig()).buttons.map((b, i) => `${i + 1}. ${esc(b.title)} — ${esc(b.url)}`).join("\n") || "Кнопок нет");

    case "/hide": case "/unhide": case "/pin": {
      if (!arg) return needArg();
      return saved((c) => {
        if (cmd === "/hide") c.servers.hide = Array.from(new Set([...c.servers.hide, arg]));
        if (cmd === "/unhide") { c.servers.hide = c.servers.hide.filter((x) => x !== arg); c.servers.pin = c.servers.pin.filter((x) => x !== arg); }
        if (cmd === "/pin") c.servers.pin = Array.from(new Set([...c.servers.pin, arg]));
      }, "Серверы обновлены");
    }
    case "/rename": {
      const [from, to] = arg.split("|").map((s) => s.trim());
      if (!from || !to) return send(chat, "Формат: /rename Старое | Новое");
      return saved((c) => { c.servers.rename[from] = to; }, "Переименовано");
    }
    case "/addkey":
      if (!/^[a-z0-9]+:\/\//i.test(arg)) return send(chat, "Пришлите ключ вида vless://… или hysteria2://…");
      return saved((c) => { c.extraKeys.push(arg); }, "Ключ добавлен всем пользователям");
    case "/keys": {
      const c = await getConfig();
      return send(chat, c.extraKeys.map((k, i) => `${i + 1}. <code>${esc(k.slice(0, 60))}…</code>`).join("\n") || "Дополнительных ключей нет");
    }
    case "/delkey": {
      const n = parseInt(rest[0], 10);
      if (!n) return needArg();
      return saved((c) => { c.extraKeys.splice(n - 1, 1); }, `Ключ №${n} удалён`);
    }

    case "/dns":
      if (!rest[0]) return needArg();
      return saved((c) => { c.defaults.dns = rest[0]; }, "DNS по умолчанию сохранён");
    case "/ua":
      if (!arg) return needArg();
      return saved((c) => { c.userAgent = arg; }, "User-Agent сохранён");
    case "/default": {
      const [key, ...vals] = rest;
      if (!key || !vals.length) return send(chat, "Формат: /default ключ значение");
      const raw = vals.join(" ");
      let val = raw;
      if (raw === "true" || raw === "false") val = raw === "true";
      else if (/^-?\d+$/.test(raw)) val = parseInt(raw, 10);
      return saved((c) => { c.defaults[key] = val; }, `Настройка ${esc(key)} = ${esc(raw)}`);
    }
    case "/forcedefaults":
      return saved((c) => { c.forceDefaults = first === "on"; }, `Принудительные настройки: ${first === "on" ? "вкл" : "выкл"}`);

    case "/link": {
      if (!/^https?:\/\//i.test(arg)) return send(chat, "Формат: /link https://ссылка-на-подписку");
      const link = `${origin(req)}/open/${encodeURIComponent(arg)}`;
      return send(chat, `Ссылка для пользователя (можно ставить кнопкой в основном боте):\n${esc(link)}`);
    }
    default:
      return send(chat, "Неизвестная команда. /help — все команды", { reply_markup: MENU });
  }
}

async function handleCallback(q, req) {
  const chat = q.message.chat.id;
  await tg("answerCallbackQuery", { callback_query_id: q.id });
  const map = {
    status: "/status",
    help: "/help",
    maint_on: "/maintenance on",
    maint_off: "/maintenance off",
    ann_off: "/announce off",
    popup_off: "/popup off",
  };
  if (q.data === "links") {
    const c = await getConfig();
    return send(chat, `Бот: ${esc(c.links.bot)}\nПоддержка: ${esc(c.links.support) || "—"}\nКанал: ${esc(c.links.channel) || "—"}\niPhone: ${esc(c.links.iphone) || "—"}\nПанель: ${origin(req)}/admin`);
  }
  if (map[q.data]) return handleCommand(chat, map[q.data], req);
}

module.exports = async (req, res) => {
  try {
    // one-time webhook setup
    if (req.method === "GET") {
      if (req.query.setup && checkPassword(req.query.password)) {
        if (!TOKEN()) return res.status(400).send("TG_TOKEN не задан");
        const secret = require("crypto").createHash("sha256").update(TOKEN()).digest("hex").slice(0, 32);
        const r = await tg("setWebhook", { url: `${origin(req)}/api/telegram`, secret_token: secret, allowed_updates: ["message", "callback_query"] });
        await tg("setMyCommands", {
          commands: [
            { command: "menu", description: "Кнопки управления" },
            { command: "status", description: "Статус приложения" },
            { command: "help", description: "Все команды" },
            { command: "panel", description: "Веб-админка" },
          ],
        });
        return res.status(200).json(r);
      }
      return res.status(200).send("Nectar Control bot webhook");
    }

    const secret = require("crypto").createHash("sha256").update(TOKEN()).digest("hex").slice(0, 32);
    if (req.headers["x-telegram-bot-api-secret-token"] !== secret) return res.status(401).end();

    const update = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
    const from = update.message?.from?.id ?? update.callback_query?.from?.id;
    const chat = update.message?.chat?.id ?? update.callback_query?.message?.chat?.id;
    const text = update.message?.text || "";
    if (!from || !chat) return res.status(200).end();

    const admins = await getAdmins();
    const isAdmin = admins.includes(String(from));

    if (!isAdmin) {
      if (text.startsWith("/login ") && checkPassword(text.slice(7).trim())) {
        await addAdmin(from);
        await send(chat, "✅ Вы администратор Nectar VPN.", { reply_markup: MENU });
      } else {
        await send(chat, "⛔ Доступ закрыт. Отправьте: /login пароль");
      }
      return res.status(200).end();
    }

    if (update.callback_query) await handleCallback(update.callback_query, req);
    else if (text.startsWith("/")) await handleCommand(chat, text, req);
    else await send(chat, "Отправьте команду. /help — список", { reply_markup: MENU });
    res.status(200).end();
  } catch (e) {
    console.error(e);
    res.status(200).end(); // never make Telegram retry forever
  }
};
