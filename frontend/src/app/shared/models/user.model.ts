import { Audited } from './audit.model';

export interface User extends Audited {
  id: number;
  username: string;
  fullName: string;
  email?: string | null;
  mobileNumber?: string | null;
  active: boolean;
  accountLocked: boolean;
  mustChangePassword: boolean;
  lastLoginAt?: string | null;
  roles: string[];
  directPermissionCount: number;
}

export interface CreateUserRequest {
  username: string;
  fullName: string;
  email?: string | null;
  mobileNumber?: string | null;
  password: string;
  roleIds?: number[];
  permissionIds?: number[];
  mustChangePassword?: boolean;
}

export interface UpdateUserRequest {
  fullName: string;
  email?: string | null;
  mobileNumber?: string | null;
  roleIds?: number[];
}

export interface ResetPasswordRequest {
  newPassword: string;
  mustChangePassword?: boolean;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface Role {
  id: number;
  name: string;
  label: string;
  description?: string | null;
  permissionCount: number;
}

export interface Permission {
  id: number;
  code: string;
  module: string;
  action: 'VIEW' | 'CREATE' | 'EDIT' | 'DELETE' | 'EXPORT';
  description?: string | null;
}

export interface ModulePermissions {
  module: string;
  label: string;
  permissions: Permission[];
}

/** Everything the permission matrix screen needs, in one payload. */
export interface UserPermissions {
  userId: number;
  username: string;
  fullName: string;
  roles: string[];
  administrator: boolean;
  modules: ModulePermissions[];
  directPermissionIds: number[];
  rolePermissionCodes: string[];
}
