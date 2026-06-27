import assert from "node:assert/strict";
import { describe, test } from "node:test";
import {
  PROFILE_NOT_APPLICABLE_CODE,
  resolveStandardProfileCodeCompletion,
} from "./profileStandardCodes.js";

describe("profile standard code completion", () => {
  test("does not count housing type as a separate required item when house tenure is not applicable", () => {
    assert.deepEqual(
      resolveStandardProfileCodeCompletion({
        houseTenureCode: PROFILE_NOT_APPLICABLE_CODE,
        housingTypeCode: PROFILE_NOT_APPLICABLE_CODE,
        basicLivingRecipientTypeCode: "",
      }),
      {
        totalCount: 2,
        missingCount: 1,
        filledCount: 1,
        missingLabels: ["복지 수급 정보"],
        includesSensitive: false,
      },
    );
  });

  test("keeps housing type in the completion target for concrete house tenure values", () => {
    assert.deepEqual(
      resolveStandardProfileCodeCompletion({
        houseTenureCode: "3",
        housingTypeCode: "",
        basicLivingRecipientTypeCode: "NONE",
      }),
      {
        totalCount: 3,
        missingCount: 1,
        filledCount: 2,
        missingLabels: ["주택유형"],
        includesSensitive: false,
      },
    );
  });
});
