import { base64Url, type Bytes, hex, sha256 } from "./encoding.ts";

/**
 * The request hashes the app passes to Play Integrity, binding each check to one request so a
 * verdict can't be reused for another (docs/CLOUD_API.md). The app computes the same values.
 */

/** Registration: the install ID being registered. */
export async function registerHash(installId: string): Promise<string> {
  return base64Url(await sha256(`precut-register:${installId}`));
}

/** An upload: the install and the exact audio bytes. */
export async function transcribeHash(
  installId: string,
  audio: Bytes,
): Promise<string> {
  return base64Url(
    await sha256(`precut-transcribe:${installId}:${hex(await sha256(audio))}`),
  );
}

/** An AI edit step: the install and the exact request body (the conversation so far). */
export async function editHash(
  installId: string,
  body: Bytes,
): Promise<string> {
  return base64Url(
    await sha256(`precut-edit:${installId}:${hex(await sha256(body))}`),
  );
}

/** A purchase sent for checking: the install and the purchase token. */
export async function purchaseHash(
  installId: string,
  purchaseToken: string,
): Promise<string> {
  return base64Url(
    await sha256(
      `precut-purchase:${installId}:${hex(await sha256(purchaseToken))}`,
    ),
  );
}
