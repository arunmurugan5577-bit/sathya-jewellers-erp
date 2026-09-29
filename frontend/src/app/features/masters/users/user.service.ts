import { HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService, QueryParams } from '../../../core/services/api.service';
import { Page, PageQuery } from '../../../shared/models/page.model';
import {
  CreateUserRequest,
  ResetPasswordRequest,
  Role,
  UpdateUserRequest,
  User,
  UserPermissions,
} from '../../../shared/models/user.model';

/**
 * User administration API.
 *
 * Does not extend {@code CrudApiService}: users are not a master. Creating one
 * takes a password, editing one must not, and the interesting operations
 * (status, reset, permissions) have no counterpart on the other modules.
 */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly api = inject(ApiService);

  list(query: PageQuery, filters: QueryParams = {}): Observable<Page<User>> {
    return this.api.get<Page<User>>('/users', {
      page: query.page,
      size: query.size,
      sort: query.sort ? `${query.sort},${query.direction ?? 'asc'}` : undefined,
      ...filters,
    });
  }

  get(id: number): Observable<User> {
    return this.api.get<User>(`/users/${id}`);
  }

  create(request: CreateUserRequest, context?: HttpContext): Observable<User> {
    return this.api.post<User>('/users', request, context);
  }

  update(id: number, request: UpdateUserRequest, context?: HttpContext): Observable<User> {
    return this.api.put<User>(`/users/${id}`, request, context);
  }

  setActive(id: number, active: boolean): Observable<User> {
    return this.api.patch<User>(`/users/${id}/status`, { active });
  }

  /** Also revokes every session that user had open. */
  resetPassword(id: number, request: ResetPasswordRequest, context?: HttpContext): Observable<void> {
    return this.api.post<void>(`/users/${id}/reset-password`, request, context);
  }

  delete(id: number): Observable<void> {
    return this.api.delete<void>(`/users/${id}`);
  }

  permissions(id: number): Observable<UserPermissions> {
    return this.api.get<UserPermissions>(`/users/${id}/permissions`);
  }

  /** Sends the complete set of direct grants, not a delta. */
  updatePermissions(id: number, permissionIds: number[]): Observable<UserPermissions> {
    return this.api.put<UserPermissions>(`/users/${id}/permissions`, { permissionIds });
  }

  roles(): Observable<Role[]> {
    return this.api.get<Role[]>('/roles');
  }
}
