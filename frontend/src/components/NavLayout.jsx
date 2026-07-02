import { Box } from "@mui/material";
import MobileBottomNav from "./MobileBottomNav";
import ServerErrorBanner from "./ServerErrorBanner";

export default function NavLayout({ children }) {
  return (
    <>
      <ServerErrorBanner />
      <main>{children}</main>
      <Box sx={{ height: { xs: 56, lg: 0 } }} aria-hidden="true" />
      <MobileBottomNav />
    </>
  );
}
