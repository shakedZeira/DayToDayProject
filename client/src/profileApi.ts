import { authedFetch } from "./auth";

export interface ProfileRecommendation {
  bmr: number;
  tdee: number;
  maintenance: number;
  weightLoss: number;
  weightGain: number;
  activity: string;
  method: string;
  disclaimer: string;
}

export interface Profile {
  heightCm: number;
  weightKg: number;
  age?: number | null;
  sex?: string | null;
  activity?: string | null;
}

export interface ProfileResponse {
  profile: Profile;
  recommendation: ProfileRecommendation;
}

export interface SaveProfileResponse {
  profile: Profile;
  recommendation: ProfileRecommendation;
  targetApplied: boolean;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getProfile(token: string): Promise<ProfileResponse | null> {
  const res = await authedFetch(token, "/api/profile");
  if (res.status === 404) return null;
  return json<ProfileResponse>(res);
}

export async function saveProfile(
  token: string,
  input: { heightCm: number; weightKg: number; age?: number; sex?: string; activity?: string; applyTarget?: boolean }
): Promise<SaveProfileResponse> {
  const res = await authedFetch(token, "/api/profile", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<SaveProfileResponse>(res);
}
