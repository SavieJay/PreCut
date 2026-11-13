import type { SupabaseClient } from "@supabase/supabase-js";
import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";

/**
 * Accounts and cloud projects (Phase 30): who is signed in (a Supabase Auth user, checked with
 * the project's signing keys), their projects and files (the SQL in
 * migrations/20261113130000_accounts.sql), and the stored files in the private `media` bucket.
 */

export interface User {
  id: string;
  email: string | null;
}

/** Checks a Supabase Auth access token; null when it isn't a good one. */
export interface UserVerifier {
  verify(token: string): Promise<User | null>;
}

/**
 * Tokens signed by one of the project's keys (ES256, published as a JWKS), for this project and
 * the `authenticated` role, not expired. Anonymous sign-ins don't count as accounts.
 */
export class JwksUserVerifier implements UserVerifier {
  constructor(
    private readonly keys: JWTVerifyGetKey,
    private readonly issuer: string,
  ) {}

  static forProject(supabaseUrl: string): JwksUserVerifier {
    const base = supabaseUrl.replace(/\/+$/, "");
    return new JwksUserVerifier(
      createRemoteJWKSet(new URL(`${base}/auth/v1/.well-known/jwks.json`)),
      `${base}/auth/v1`,
    );
  }

  async verify(token: string): Promise<User | null> {
    try {
      const { payload } = await jwtVerify(token, this.keys, {
        issuer: this.issuer,
        audience: "authenticated",
        algorithms: ["ES256", "RS256"],
      });
      if (payload.role !== "authenticated" || payload.is_anonymous === true) {
        return null;
      }
      if (typeof payload.sub !== "string" || payload.sub === "") return null;
      return {
        id: payload.sub,
        email: typeof payload.email === "string" ? payload.email : null,
      };
    } catch {
      return null;
    }
  }
}

export interface CloudProject {
  id: string;
  revision: number;
  name: string;
  project: unknown;
  coverSha256: string | null;
  durationMs: number;
  editedAt: string;
  updatedAt: string;
  deleted: boolean;
  media: string[];
}

export interface ProjectChanges {
  projects: CloudProject[];
  next: { since: string; after: string } | null;
  more: boolean;
}

export interface ProjectSave {
  baseRevision: number | null;
  name: string;
  project: Record<string, unknown>;
  media: string[];
  coverSha256: string | null;
  durationMs: number;
  editedAt: string;
}

export type SaveResult =
  | { ok: true; revision: number; updatedAt: string }
  | { ok: false; code: "conflict"; project: CloudProject | null }
  | { ok: false; code: "missing_media"; missing: string[] }
  | { ok: false; code: "too_large" };

export type DeleteResult =
  | { ok: true; revision: number | null }
  | { ok: false; code: "conflict"; project: CloudProject };

export type GrantResult =
  | { ok: true; exists: boolean }
  | {
    ok: false;
    code: "storage_full";
    storageUsed: number;
    storageLimit: number;
  }
  | { ok: false; code: "too_large"; maxFileBytes: number };

export type ConfirmResult =
  | { ok: true }
  | { ok: false; code: "not_found" | "size_mismatch" };

export interface Account {
  pro: boolean;
  storageUsed: number;
  storageLimit: number;
  maxFileBytes: number;
  projects: number;
}

export interface AccountStore {
  changes(
    user: string,
    since: string | null,
    after: string | null,
    limit: number,
  ): Promise<ProjectChanges>;
  save(user: string, id: string, save: ProjectSave): Promise<SaveResult>;
  delete(
    user: string,
    id: string,
    baseRevision: number | null,
  ): Promise<DeleteResult>;
  grant(
    user: string,
    sha256: string,
    size: number,
    mime: string,
  ): Promise<GrantResult>;
  confirm(user: string, sha256: string): Promise<ConfirmResult>;
  ready(user: string, sha256: string): Promise<boolean>;
  account(user: string): Promise<Account>;
  /** Signs [installId] in to [user]; false when the install isn't registered. */
  linkInstall(user: string, installId: string): Promise<boolean>;
  /** Every file the user has, so their stored objects can be removed. */
  files(user: string): Promise<string[]>;
  /** The daily clean-up; answers the files whose stored objects must go. */
  cleanup(): Promise<{ userId: string; sha256: string }[]>;
}

export class PostgresAccountStore implements AccountStore {
  constructor(private readonly client: SupabaseClient) {}

  changes(
    user: string,
    since: string | null,
    after: string | null,
    limit: number,
  ) {
    return this.call<ProjectChanges>("cloud_project_changes", {
      p_user: user,
      p_since: since,
      p_after: after,
      p_limit: limit,
    });
  }
  save(user: string, id: string, save: ProjectSave) {
    return this.call<SaveResult>("cloud_project_save", {
      p_user: user,
      p_id: id,
      p_base: save.baseRevision,
      p_name: save.name,
      p_project: save.project,
      p_media: save.media,
      p_cover: save.coverSha256,
      p_duration: save.durationMs,
      p_edited_at: save.editedAt,
    });
  }
  delete(user: string, id: string, baseRevision: number | null) {
    return this.call<DeleteResult>("cloud_project_delete", {
      p_user: user,
      p_id: id,
      p_base: baseRevision,
    });
  }
  grant(user: string, sha256: string, size: number, mime: string) {
    return this.call<GrantResult>("cloud_media_grant", {
      p_user: user,
      p_sha256: sha256,
      p_size: size,
      p_mime: mime,
    });
  }
  confirm(user: string, sha256: string) {
    return this.call<ConfirmResult>("cloud_media_confirm", {
      p_user: user,
      p_sha256: sha256,
    });
  }
  ready(user: string, sha256: string) {
    return this.call<boolean>("cloud_media_ready", {
      p_user: user,
      p_sha256: sha256,
    });
  }
  account(user: string) {
    return this.call<Account>("cloud_account", { p_user: user });
  }
  linkInstall(user: string, installId: string) {
    return this.call<boolean>("cloud_link_install", {
      p_user: user,
      p_install: installId,
    });
  }
  files(user: string) {
    return this.call<string[]>("cloud_account_media", { p_user: user });
  }
  cleanup() {
    return this.call<{ userId: string; sha256: string }[]>("cloud_cleanup", {});
  }

  private async call<T>(
    name: string,
    args: Record<string, unknown>,
  ): Promise<T> {
    const { data, error } = await this.client.rpc(name, args);
    if (error) throw new Error(`${name}: ${error.message}`);
    return data as T;
  }
}

/** The private `media` bucket, where files live at `<user id>/<sha256>`. */
export interface MediaStorage {
  /** Where the phone sends a resumable (TUS) upload. */
  readonly uploadUrl: string;
  /** A link that downloads [path] for [seconds]. */
  signedUrl(path: string, seconds: number): Promise<string | null>;
  remove(paths: string[]): Promise<void>;
}

export const MEDIA_BUCKET = "media";

export class SupabaseMediaStorage implements MediaStorage {
  readonly uploadUrl: string;

  constructor(private readonly client: SupabaseClient, supabaseUrl: string) {
    this.uploadUrl = resumableUploadUrl(supabaseUrl);
  }

  async signedUrl(path: string, seconds: number): Promise<string | null> {
    const { data, error } = await this.client.storage.from(MEDIA_BUCKET)
      .createSignedUrl(path, seconds);
    if (error) return null;
    return data?.signedUrl ?? null;
  }

  async remove(paths: string[]): Promise<void> {
    // Storage removes at most 1,000 objects a call.
    for (let i = 0; i < paths.length; i += 1000) {
      const { error } = await this.client.storage.from(MEDIA_BUCKET).remove(
        paths.slice(i, i + 1000),
      );
      if (error) throw new Error(`storage remove: ${error.message}`);
    }
  }
}

/**
 * Storage's own host for resumable uploads (`<ref>.storage.supabase.co`), which Supabase advises
 * for large files; a local stack uses its one address.
 */
export function resumableUploadUrl(supabaseUrl: string): string {
  const base = supabaseUrl.replace(/\/+$/, "");
  const hosted = base.match(/^https:\/\/([a-z0-9]+)\.supabase\.co$/);
  const host = hosted ? `https://${hosted[1]}.storage.supabase.co` : base;
  return `${host}/storage/v1/upload/resumable`;
}

/** Deletes Auth users (DELETE /account). */
export interface UserAdmin {
  deleteUser(id: string): Promise<void>;
}

export class SupabaseUserAdmin implements UserAdmin {
  constructor(private readonly client: SupabaseClient) {}

  async deleteUser(id: string): Promise<void> {
    const { error } = await this.client.auth.admin.deleteUser(id);
    if (error) throw new Error(`delete user: ${error.message}`);
  }
}
