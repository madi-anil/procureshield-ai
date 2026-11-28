import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { apiClient, tokenStorage } from "../api/apiClient";
import type {
  AuthTokenResponse,
  LoginRequest,
  RegisterRequest,
  RegisterResponse,
  UserSummary,
} from "../types/auth";

const USER_STORAGE_KEY = "procureshield_user";

interface AuthContextValue {
  user: UserSummary | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (payload: LoginRequest) => Promise<UserSummary>;
  register: (payload: RegisterRequest) => Promise<RegisterResponse>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function loadStoredUser(): UserSummary | null {
  try {
    const raw = sessionStorage.getItem(USER_STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as UserSummary;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(() => loadStoredUser());
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    const handleStorageChange = () => {
      if (!tokenStorage.getAccessToken()) {
        setUser(null);
      }
    };
    window.addEventListener("storage", handleStorageChange);
    return () => window.removeEventListener("storage", handleStorageChange);
  }, []);

  const login = useCallback(async (payload: LoginRequest): Promise<UserSummary> => {
    setIsLoading(true);
    try {
      const response = await apiClient.post<AuthTokenResponse>(
        "/auth/login",
        payload
      );
      const { accessToken, refreshToken, user: loggedInUser } = response.data;
      tokenStorage.setTokens(accessToken, refreshToken);
      sessionStorage.setItem(USER_STORAGE_KEY, JSON.stringify(loggedInUser));
      setUser(loggedInUser);
      return loggedInUser;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const register = useCallback(
    async (payload: RegisterRequest): Promise<RegisterResponse> => {
      setIsLoading(true);
      try {
        const response = await apiClient.post<RegisterResponse>(
          "/auth/register",
          payload
        );
        return response.data;
      } finally {
        setIsLoading(false);
      }
    },
    []
  );

  const logout = useCallback(() => {
    tokenStorage.clear();
    sessionStorage.removeItem(USER_STORAGE_KEY);
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: Boolean(user && tokenStorage.getAccessToken()),
      isLoading,
      login,
      register,
      logout,
    }),
    [user, isLoading, login, register, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used inside an AuthProvider");
  }
  return ctx;
}