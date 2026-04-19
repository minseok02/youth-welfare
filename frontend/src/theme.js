import { createTheme } from "@mui/material/styles";

const theme = createTheme({
  palette: {
    primary: {
      main: "#028090",
      dark: "#016070",
      light: "#00A896",
      contrastText: "#ffffff",
    },
    secondary: {
      main: "#00A896",
    },
    background: {
      default: "#F5FAFA",
      paper: "#ffffff",
    },
    text: {
      primary: "#1A2E35",
      secondary: "#64748B",
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
