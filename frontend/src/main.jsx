import React from "react";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "@mui/material/styles";
import CssBaseline from "@mui/material/CssBaseline";
import AuthBootstrap from "./components/AuthBootstrap.jsx";
import theme from "./theme.js";
import router from "./router/index.jsx";

const queryClient = new QueryClient();

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <AuthBootstrap>
          <RouterProvider router={router} />
        </AuthBootstrap>
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>
);
