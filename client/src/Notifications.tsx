import { useEffect, useState } from "react";
import { useNotifications } from "./useNotifications";
import { getSettings, putSetting } from "./settingsApi";

interface Props {
  token: string;
}

const browserTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";

export default function Notifications({ token }: Props) {
  const { registered, error } = useNotifications(token);
  const [enabled, setEnabled] = useState(true);
  const [quietStart, setQuietStart] = useState("");
  const [quietEnd, setQuietEnd] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    getSettings(token)
      .then(({ settings }) => {
        setEnabled(settings["notify.enabled"] !== "false");
        setQuietStart(settings["notify.quietStart"] ?? "");
        setQuietEnd(settings["notify.quietEnd"] ?? "");
        setLoaded(true);
      })
      .catch(() => setLoaded(true));
  }, [token]);

  // Quiet hours are evaluated on the server in this zone, so keep it in sync with the device.
  const save = async (key: string, value: string) => {
    setSaveError(null);
    try {
      await putSetting(token, key, value);
      await putSetting(token, "notify.timeZone", browserTimeZone());
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : "failed to save");
    }
  };

  return (
    <div className="flex flex-col gap-2">
      {error ? (
        <p className="text-xs text-slate-400">{error}</p>
      ) : (
        registered && <p className="text-xs text-slate-400">🔔 notifications enabled</p>
      )}

      {loaded && (
        <div className="flex flex-col gap-2 text-sm text-slate-700">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => {
                setEnabled(e.target.checked);
                save("notify.enabled", String(e.target.checked));
              }}
            />
            Task reminders
          </label>
          <div className="flex items-center gap-2">
            <span>Quiet hours</span>
            <input
              type="time"
              value={quietStart}
              aria-label="Quiet hours start"
              onChange={(e) => {
                setQuietStart(e.target.value);
                if (e.target.value) save("notify.quietStart", e.target.value);
              }}
              className="border border-slate-300 rounded px-1"
            />
            <span>to</span>
            <input
              type="time"
              value={quietEnd}
              aria-label="Quiet hours end"
              onChange={(e) => {
                setQuietEnd(e.target.value);
                if (e.target.value) save("notify.quietEnd", e.target.value);
              }}
              className="border border-slate-300 rounded px-1"
            />
          </div>
          {saveError && <p className="text-xs text-red-600">{saveError}</p>}
        </div>
      )}
    </div>
  );
}
