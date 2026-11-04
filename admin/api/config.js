// Public: the Nectar VPN app downloads its remote configuration from here.
const { getConfig } = require("../lib/store");

module.exports = async (req, res) => {
  try {
    const cfg = await getConfig();
    res.setHeader("Cache-Control", "public, s-maxage=30, stale-while-revalidate=300");
    res.status(200).json(cfg);
  } catch (e) {
    res.status(500).json({ error: String(e.message || e) });
  }
};
