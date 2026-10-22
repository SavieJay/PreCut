import { assertEquals } from "@std/assert";
import { utf8 } from "../_shared/encoding.ts";
import {
  signInstallToken,
  verifyInstallToken,
} from "../_shared/install_token.ts";
import { INSTALL, NOW, SECRET } from "./helpers.ts";

const DAY = 24 * 3600 * 1000;

Deno.test("an install token names its install for 180 days and asks to be renewed in the last 30", async () => {
  const token = await signInstallToken(INSTALL, SECRET, NOW);
  assertEquals(await verifyInstallToken(token, SECRET, NOW), {
    installId: INSTALL,
    renew: false,
  });
  assertEquals(
    await verifyInstallToken(
      token,
      SECRET,
      new Date(NOW.getTime() + 149 * DAY),
    ),
    { installId: INSTALL, renew: false },
  );
  assertEquals(
    await verifyInstallToken(
      token,
      SECRET,
      new Date(NOW.getTime() + 151 * DAY),
    ),
    { installId: INSTALL, renew: true },
  );
  assertEquals(
    await verifyInstallToken(
      token,
      SECRET,
      new Date(NOW.getTime() + 181 * DAY),
    ),
    null,
  );
});

Deno.test("a token signed with another secret, altered or not a token is refused", async () => {
  const token = await signInstallToken(INSTALL, SECRET, NOW);
  assertEquals(
    await verifyInstallToken(
      token,
      utf8("another-secret-another-secret-123"),
      NOW,
    ),
    null,
  );
  const [header, payload, signature] = token.split(".");
  const altered = btoa(
    atob(payload.replace(/-/g, "+").replace(/_/g, "/")).replace(
      INSTALL,
      "00000000-0000-0000-0000-000000000000",
    ),
  )
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  assertEquals(
    await verifyInstallToken(`${header}.${altered}.${signature}`, SECRET, NOW),
    null,
  );
  assertEquals(await verifyInstallToken("not-a-token", SECRET, NOW), null);
});
