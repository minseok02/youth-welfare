import { createTheme } from "@mui/material/styles";

const theme = createTheme({
  palette: {
    primary: {
      main: "#2563eb",
      dark: "#1d4ed8",
      light: "#3b82f6",
      contrastText: "#ffffff",
    },
    secondary: {
      main: "#3b82f6",
    },
    background: {
      default: "#f7f8fc",
      paper: "#ffffff",
    },
    text: {
      primary: "#11131a",
      secondary: "#4a4f5c",
    },
  },
  typography: {
    fontFamily: "'Noto Sans KR', 'Roboto', sans-serif",
    button: {
      fontWeight: 600,
      textTransform: "none",
    },
  },
  shape: {
    borderRadius: 10,
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          boxShadow: "none",
          "&:hover": { boxShadow: "none" },
        },
      },
    },
    MuiTextField: {
      defaultProps: { size: "small" },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: 8,
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 12,
          boxShadow: "0 2px 12px rgba(2,128,144,0.08)",
          border: "1px solid #E8F4F5",
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { borderRadius: 20 },
      },
    },
  },
});

export default theme;
