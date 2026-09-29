import { HttpContext } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Lookup } from '../../shared/models/lookup.model';
import { Page, PageQuery } from '../../shared/models/page.model';
import { ApiService, QueryParams } from './api.service';

/**
 * The shape every master module's API follows.
 *
 * All six masters expose the same endpoints under a different path, so the
 * client for each is a two-line subclass. Writing them out longhand five times
 * would guarantee that one of them eventually forgets to send the status
 * parameter, or paginates differently from its neighbours.
 *
 * Modules with extra endpoints (cascading lookups, serial numbers) add methods
 * in their own service rather than widening this base.
 */
export abstract class CrudApiService<TEntity, TRequest> {
  protected readonly api = inject(ApiService);

  /** Resource path, e.g. `/item-types`. */
  protected abstract readonly resourcePath: string;

  list(query: PageQuery, filters: QueryParams = {}, context?: HttpContext): Observable<Page<TEntity>> {
    return this.api.get<Page<TEntity>>(
      this.resourcePath,
      {
        page: query.page,
        size: query.size,
        sort: query.sort ? `${query.sort},${query.direction ?? 'asc'}` : undefined,
        ...filters,
      },
      context,
    );
  }

  /** Active records only - this is what every dropdown binds to. */
  lookup(filters: QueryParams = {}): Observable<Lookup[]> {
    return this.api.get<Lookup[]>(`${this.resourcePath}/lookup`, filters);
  }

  get(id: number): Observable<TEntity> {
    return this.api.get<TEntity>(`${this.resourcePath}/${id}`);
  }

  create(request: TRequest, context?: HttpContext): Observable<TEntity> {
    return this.api.post<TEntity>(this.resourcePath, request, context);
  }

  update(id: number, request: TRequest, context?: HttpContext): Observable<TEntity> {
    return this.api.put<TEntity>(`${this.resourcePath}/${id}`, request, context);
  }

  /** Soft delete. Always available, even for a referenced record. */
  setActive(id: number, active: boolean): Observable<TEntity> {
    return this.api.patch<TEntity>(`${this.resourcePath}/${id}/status`, { active });
  }

  /** Hard delete. Refused by the server once anything references the record. */
  delete(id: number): Observable<void> {
    return this.api.delete<void>(`${this.resourcePath}/${id}`);
  }
}
