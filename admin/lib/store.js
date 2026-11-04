// Storage of the app configuration in Vercel Blob.
// config.json is public (the app reads it), admins.json holds Telegram admin IDs.
const { put, list } = require("@vercel/blob");

const CONFIG_PATH = "nectar/config.json";
const ADMINS_PREFIX = "nectar/private/admins-";

const DEFAULT_CONFIG = {
  version: 1,
  updatedAt: 0,
  announcement: { enabled: false, title: "", text: "", url: "", buttonText: "" },
  popup: { id: "", title: "", text: "", url: "", buttonText: "" },
  maintenance: { enabled: false, text: "Ведутся технические работы. Скоро всё заработает." },
  update: { minVersionCode: 0, latestVersionCode: 0, url: "", text: "" },
  links: { bot: "https://t.me/NECTARVPN_xbot", support: "", channel: "", site: "", iphone: "" },
  buttons: [],
  servers: { hide: [], pin: [], rename: {} },
  extraKeys: [],
  defaults: {},
  forceDefaults: false,
  features: { autoVpn: true, splitTunnel: true, addManual: true, customUserAgent: true },
  userAgent: "",
  testUrl: "",
};

function merge(base, over) {
  if (Array.isArray(base) || typeof base !== "object" || base === null) return over === undefined ? base : over;
  const out = { ...base };
  for (const k of Object.keys(over || {})) {
    const b = base[k];
    const o = over[k];
    out[k] = b && typeof b === "object" && !Array.isArray(b) && o && typeof o === "object" && !Array.isArray(o) ? merge(b, o) : o;
  }
  return out;
}

async function readJson(prefix) {
  const { blobs } = await list({ prefix });
  if (!blobs.length) return null;
  blobs.sort((a, b) => new Date(b.uploadedAt) - new Date(a.uploadedAt));
  const r = await fetch(blobs[0].url + "?t=" + Date.now(), { cache: "no-store" });
  if (!r.ok) return null;
  return r.json();
}

async function getConfig() {
  const saved = await readJson(CONFIG_PATH).catch(() => null);
  return merge(DEFAULT_CONFIG, saved || {});
}

async function saveConfig(cfg) {
  const full = merge(DEFAULT_CONFIG, cfg || {});
  full.updatedAt = Date.now();
  await put(CONFIG_PATH, JSON.stringify(full, null, 2), {
    access: "public",
    addRandomSuffix: false,
    allowOverwrite: true,
    contentType: "application/json",
    cacheControlMaxAge: 60,
  });
  return full;
}

async function updateConfig(fn) {
  const cfg = await getConfig();
  const next = (await fn(cfg)) || cfg;
  return saveConfig(next);
}

async function getAdmins() {
  const env = (process.env.TG_ADMINS || "").split(",").map((s) => s.trim()).filter(Boolean);
  const saved = (await readJson(ADMINS_PREFIX).catch(() => null)) || [];
  return Array.from(new Set([...env, ...saved.map(String)]));
}

async function addAdmin(id) {
  const saved = (await readJson(ADMINS_PREFIX).catch(() => null)) || [];
  if (!saved.map(String).includes(String(id))) saved.push(String(id));
  // random suffix: the URL of this file cannot be guessed
  await put(ADMINS_PREFIX + "list.json", JSON.stringify(saved), {
    access: "public",
    addRandomSuffix: true,
    contentType: "application/json",
  });
}

module.exports = { DEFAULT_CONFIG, getConfig, saveConfig, updateConfig, getAdmins, addAdmin, merge };
