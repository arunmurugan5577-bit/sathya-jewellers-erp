export interface LoginRequest {
  username: string;
  password: string;
}

/** The signed-in user, as returned by /api/auth/login and /api/auth/me. */
export interface AuthenticatedUser {
  id: number;
  username: string;
  fullName: string;
  roles: string[];
  /**
   * Effective permission codes. Used to hide actions the user cannot perform.
   * This is presentation only - the server enforces the same set independently.
   */
  permissions: string[];
  mustChangePassword: boolean;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: string;
  refreshToken: string;
  user: AuthenticatedUser;
}
