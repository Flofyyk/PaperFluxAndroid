import test from "node:test";
import assert from "node:assert/strict";
import { detailLevel, isVerificationWarning, readVerification } from "../src/utils/verification.ts";

test("connected VPN does not make verification or cookie delivery successful", () => {
  for (const message of ["Яндекс: требуется проверка — документ 2, телефон", "Яндекс требует подтверждение доступа на сервере", "Результат передан. Проверяем доступ к Яндексу"]) {
    assert.equal(isVerificationWarning(message), true);
    assert.equal(detailLevel("CONNECTED", message), "warning");
  }
  assert.equal(detailLevel("CONNECTED", "Шифрованный туннель: DNS и TCP подтверждены"), "success");
  assert.equal(detailLevel("ERROR", "Связь прервана"), "error");
});
test("public verification state is validated and never propagates private fields", () => {
  assert.equal(readVerification(null), null);
  assert.equal(readVerification({ carrier: "документ 2", side: "unknown" }), null);
  assert.deepEqual(readVerification({ carrier: "документ 2", side: "телефон", automatic: true, url: "private", jar: "private" }),
    { carrier: "документ 2", side: "телефон", automatic: true });
  assert.equal(readVerification({ carrier: "Volga", side: "VPS", automatic: "false" }).automatic, false);
});
