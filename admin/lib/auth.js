const crypto = require("crypto");

/** Constant-time password check against the ADMIN_PASSWORD env variable. */
function checkPassword(pw) {
  const real = process.env.ADMIN_PASSWORD || "";
  if (!real || typeof pw !== "string") return false;
  const a = crypto.createHash("sha256").update(pw).digest();
  const b = crypto.createHash("sha256").update(real).digest();
  return crypto.timingSafeEqual(a, b);
}

module.exports = { checkPassword };
