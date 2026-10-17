// Phone smoke test: run from PC against an adb-forwarded port.
//   adb forward tcp:4000 tcp:4000 && node phone/smoke.mjs
const base = process.env.BASE || "http://127.0.0.1:4000";
const email = `phone_smoke_${Date.now()}@example.com`;
const password = "password123";

const log = (label, status, body) =>
  console.log(`${label.padEnd(22)} ${status}  ${typeof body === "string" ? body.slice(0, 160) : JSON.stringify(body).slice(0, 200)}`);

async function main() {
  const health = await fetch(`${base}/api/health`);
  log("GET /api/health", health.status, await health.text());

  const ready = await fetch(`${base}/api/ready`);
  log("GET /api/ready", ready.status, await ready.text());

  const reg = await fetch(`${base}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  const regBody = await reg.json();
  log("POST /auth/register", reg.status, regBody);
  const token = regBody.token;
  const auth = { Authorization: `Bearer ${token}` };

  const course = await fetch(`${base}/api/italian-path/course`, { headers: auth });
  log("GET /italian-path/course", course.status, await course.text());

  const lesson = await fetch(`${base}/api/italian/lesson/today`, { headers: auth });
  log("GET /italian/lesson/today", lesson.status, await lesson.text());

  const progress = await fetch(`${base}/api/italian/progress`, { headers: auth });
  log("GET /italian/progress", progress.status, await progress.text());

  const root = await fetch(`${base}/`);
  const html = await root.text();
  log("GET / (PWA)", root.status, html.includes('<div id="root"') || html.includes("id=\"root\"") ? "root div present" : html.slice(0, 120));
}

main().catch((e) => {
  console.error("SMOKE FAILED:", e);
  process.exit(1);
});
