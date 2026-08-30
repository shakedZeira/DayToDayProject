import { useState } from "react";
import { login, register, type AuthResponse, type AuthUser } from "./auth";

interface Props {
  onAuthed: (token: string, user: AuthUser) => void;
}

export default function Login({ onAuthed }: Props) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [mode, setMode] = useState<"login" | "register">("login");
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const fn = mode === "login" ? login : register;
      const res: AuthResponse = await fn(email, password);
      onAuthed(res.token, res.user);
    } catch (err) {
      setError(err instanceof Error ? err.message : "failed");
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3 w-full max-w-sm">
      <h2 className="text-xl font-semibold text-slate-800">
        {mode === "login" ? "Log in" : "Create account"}
      </h2>
      <input
        className="border rounded px-3 py-2"
        type="email" placeholder="email" value={email}
        onChange={(e) => setEmail(e.target.value)} required
      />
      <input
        className="border rounded px-3 py-2"
        type="password" placeholder="password (min 8)" value={password}
        onChange={(e) => setPassword(e.target.value)} required minLength={8}
      />
      {error && <p className="text-red-600 text-sm">{error}</p>}
      <button className="bg-indigo-600 text-white rounded py-2">{mode === "login" ? "Log in" : "Register"}</button>
      <button type="button" className="text-sm text-indigo-600 underline" onClick={() => setMode(mode === "login" ? "register" : "login")}>
        {mode === "login" ? "Need an account? Register" : "Have an account? Log in"}
      </button>
    </form>
  );
}