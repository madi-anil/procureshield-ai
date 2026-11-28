import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";
import type { TokenRefreshResponse } from "../types/auth";

const ACCESS_TOKEN_KEY = "procureshield_access_token";
const REFRESH_TOKEN_KEY = "procureshield_refresh_token";
const USER_KEY = "procureshield_user";

export const tokenStorage = {
  getAccessToken: () => sessionStorage.getItem(ACCESS_TOKEN_KEY),
  getRefreshToken: () => sessionStorage.getItem(REFRESH_TOKEN_KEY),
  setTokens: (accessToken: string, refreshToken?: string) => {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    if (refreshToken) {
      sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    }
  },
  clear: () => {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
  },
};

export const apiClient = axios.create({
  baseURL: "/api/v1",
  headers: {
    "Content-Type": "application/json",
  },
});

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStorage.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let isRefreshing = false;
let pendingQueue: Array<(token: string | null) => void> = [];

function resolveQueue(token: string | null) {
  pendingQueue.forEach((callback) => callback(token));
  pendingQueue = [];
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as
      | (InternalAxiosRequestConfig & { _retry?: boolean })
      | undefined;

    const isAuthRoute =
      originalRequest?.url?.includes("/auth/login") ||
      originalRequest?.url?.includes("/auth/register") ||
      originalRequest?.url?.includes("/auth/refresh");

    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !isAuthRoute
    ) {
      const refreshToken = tokenStorage.getRefreshToken();
      if (!refreshToken) {
        tokenStorage.clear();
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingQueue.push((newToken) => {
            if (!newToken) {
              reject(error);
              return;
            }
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
            resolve(apiClient(originalRequest));
          });
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshResponse = await axios.post<TokenRefreshResponse>(
          "/api/v1/auth/refresh",
          { refreshToken }
        );
        const { accessToken: nextAccessToken, refreshToken: nextRefreshToken } =
          refreshResponse.data;

        tokenStorage.setTokens(nextAccessToken, nextRefreshToken);
        resolveQueue(nextAccessToken);

        originalRequest.headers.Authorization = `Bearer ${nextAccessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        tokenStorage.clear();
        resolveQueue(null);
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);