// Admin API used by the web panel. Requires the admin password.
const { getConfig, saveConfig } = require("../lib/store");
const { checkPassword } = require("../lib/auth");

module.exports = async (req, res) => {
  res.setHeader("Cache-Control", "no-store");
  const pw = req.headers["x-admin-password"];
  if (!checkPassword(pw)) return res.status(401).json({ error: "Неверный пароль" });
  try {
    if (req.method === "GET") return res.status(200).json(await getConfig());
    if (req.method === "POST") {
      const body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
      if (!body || typeof body !== "object") return res.status(400).json({ error: "Пустой конфиг" });
      return res.status(200).json(await saveConfig(body));
    }
    res.status(405).json({ error: "Method not allowed" });
  } catch (e) {
    res.status(500).json({ error: String(e.message || e) });
  }
};
