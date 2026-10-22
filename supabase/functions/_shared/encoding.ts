/** Byte helpers shared by the install token, Play Integrity and request-hash code. */

/** Bytes in an ordinary (not shared) buffer, what Web Crypto and Blob take. */
export type Bytes = Uint8Array<ArrayBuffer>;

const encoder = new TextEncoder();

export function utf8(text: string): Bytes {
  return encoder.encode(text);
}

export async function sha256(data: Bytes | string): Promise<Bytes> {
  const bytes = typeof data === "string" ? utf8(data) : data;
  return new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
}

export function hex(bytes: Uint8Array): string {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/** URL-safe base64 without padding (what Play Integrity uses for hashes and certificate digests). */
export function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(
    /=+$/,
    "",
  );
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** Whether [value] is a lower-case UUID, the form install IDs take. */
export function isUuid(value: unknown): value is string {
  return typeof value === "string" && UUID.test(value);
}
