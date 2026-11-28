export type UserRole =
  | "ROLE_EMPLOYEE"
  | "ROLE_DEPARTMENT_HEAD"
  | "ROLE_PROCUREMENT_ADMIN"
  | "ROLE_AUDITOR";

export interface UserSummary {
  id: number;
  email: string;
  fullName: string;
  role: UserRole;
  departmentId: number | null;
  departmentName: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  departmentId: number;
}

export interface RegisterResponse {
  userId: number;
  email: string;
  fullName: string;
  role: UserRole;
  departmentId: number;
  departmentName: string | null;
  isActive: boolean;
  createdAt: string;
}

export interface AuthTokenResponse {
  tokenType: string;
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface TokenRefreshResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}