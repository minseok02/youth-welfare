import { Box } from "@mui/material";
import MobileBottomNav from "./MobileBottomNav";

export default function NavLayout({ children }) {
  return (
    <>
      {children}
      <Box sx={{ height: { xs: 56, lg: 0 } }} aria-hidden="true" />
      <MobileBottomNav />
    </>
  );
}
