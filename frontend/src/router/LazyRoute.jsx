import { Suspense } from "react";

export default function LazyRoute({ children }) {
  return <Suspense fallback={null}>{children}</Suspense>;
}
