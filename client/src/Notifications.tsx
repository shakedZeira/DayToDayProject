import { useNotifications } from "./useNotifications";

interface Props {
  token: string;
}

export default function Notifications({ token }: Props) {
  const { registered, error } = useNotifications(token);

  if (error) {
    return <p className="text-xs text-slate-400">{error}</p>;
  }

  if (registered) {
    return <p className="text-xs text-slate-400">🔔 notifications enabled</p>;
  }

  return null;
}
