import fs from "fs";
import path from "path";
import webpush from "web-push";

export const VAPID_SUBJECT = "mailto:dev@daytoday.local";

const VAPID_FILE = path.join(__dirname, "..", "vapid.json");

export interface VapidKeys {
  publicKey: string;
  privateKey: string;
  subject: string;
}

let cached: VapidKeys | null = null;

export function getVapidKeys(): VapidKeys {
  if (!cached) {
    cached = loadOrCreateKeys();
  }
  return cached;
}

function loadOrCreateKeys(): VapidKeys {
  if (fs.existsSync(VAPID_FILE)) {
    try {
      const raw = JSON.parse(fs.readFileSync(VAPID_FILE, "utf8")) as {
        publicKey: string;
        privateKey: string;
      };
      const keys: VapidKeys = {
        publicKey: String(raw.publicKey),
        privateKey: String(raw.privateKey),
        subject: VAPID_SUBJECT
      };
      webpush.setVapidDetails(keys.subject, keys.publicKey, keys.privateKey);
      return keys;
    } catch (err) {
      console.error(`Failed to read VAPID key file ${VAPID_FILE}, regenerating:`, err);
    }
  }
  const generated = webpush.generateVAPIDKeys();
  fs.writeFileSync(VAPID_FILE, JSON.stringify(generated, null, 2));
  const keys: VapidKeys = {
    publicKey: generated.publicKey,
    privateKey: generated.privateKey,
    subject: VAPID_SUBJECT
  };
  webpush.setVapidDetails(keys.subject, keys.publicKey, keys.privateKey);
  return keys;
}