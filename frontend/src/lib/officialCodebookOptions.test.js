import assert from "node:assert/strict";
import { describe, test } from "node:test";
import {
  PROFILE_NOT_APPLICABLE_CODE,
  normalizeHousingProfileCodes,
} from "./officialCodebookOptions.js";

describe("official codebook profile helpers", () => {
  test("keeps housing type not applicable when house tenure is not applicable", () => {
    assert.deepEqual(
      normalizeHousingProfileCodes({
        houseTenureCode: PROFILE_NOT_APPLICABLE_CODE,
        housingTypeCode: "4",
      }),
      {
        houseTenureCode: PROFILE_NOT_APPLICABLE_CODE,
        housingTypeCode: PROFILE_NOT_APPLICABLE_CODE,
      },
    );
  });

  test("preserves housing type for concrete house tenure values", () => {
    assert.deepEqual(
      normalizeHousingProfileCodes({
        houseTenureCode: "3",
        housingTypeCode: "4",
      }),
      {
        houseTenureCode: "3",
        housingTypeCode: "4",
      },
    );
  });
});
