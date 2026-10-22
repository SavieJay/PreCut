import * as jose from "jose";
import { type Bytes, isUuid } from "./encoding.ts";

/**
 * An install's token: proof that it registered with a genuine app on a genuine phone, so later
 * calls needn't spend a Play Integrity check. HS256 with the INSTALL_TOKEN_SECRET; it says only
 * which install it is.
 */

const AUDIENCE = "precut-cloud";
const DAY_SECONDS = 24 * 60 * 60;
export const INSTALL_TOKEN_DAYS = 180;
/** A token with less left than this is replaced in the next response. */
export const RENEW_WITHIN_DAYS = 30;

export async function signInstallToken(
  installId: string,
  secret: Bytes,
  now: Date,
): Promise<string> {
  const issuedAt = Math.floor(now.getTime() / 1000);
  return await new jose.SignJWT({})
    .setProtectedHeader({ alg: "HS256" })
    .setSubject(installId)
    .setAudience(AUDIENCE)
    .setIssuedAt(issuedAt)
    .setExpirationTime(issuedAt + INSTALL_TOKEN_DAYS * DAY_SECONDS)
    .sign(secret);
}

export interface VerifiedInstall {
  installId: string;
  /** Whether the token should be replaced: it runs out within [RENEW_WITHIN_DAYS]. */
  renew: boolean;
}

/** The install a valid, unexpired token names; null for anything else. */
export async function verifyInstallToken(
  token: string,
  secret: Bytes,
  now: Date,
): Promise<VerifiedInstall | null> {
  try {
    const { payload } = await jose.jwtVerify(token, secret, {
      audience: AUDIENCE,
      algorithms: ["HS256"],
      currentDate: now,
    });
    if (!isUuid(payload.sub) || payload.exp === undefined) return null;
    const left = payload.exp - now.getTime() / 1000;
    return {
      installId: payload.sub,
      renew: left < RENEW_WITHIN_DAYS * DAY_SECONDS,
    };
  } catch {
    return null;
  }
}
