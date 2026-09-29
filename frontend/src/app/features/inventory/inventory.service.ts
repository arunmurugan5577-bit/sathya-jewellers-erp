import { HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApiService } from '../../core/services/api.service';
import { InventoryFilters, InventoryItem, InventoryItemRequest } from '../../shared/models/inventory.model';
import { Page, PageQuery } from '../../shared/models/page.model';

/** Inventory API client. */
@Injectable({ providedIn: 'root' })
export class InventoryService {
  private readonly api = inject(ApiService);

  list(query: PageQuery, filters: InventoryFilters = {}): Observable<Page<InventoryItem>> {
    return this.api.get<Page<InventoryItem>>('/inventory/items', {
      page: query.page,
      size: query.size,
      sort: query.sort ? `${query.sort},${query.direction ?? 'desc'}` : undefined,
      search: filters.search ?? undefined,
      serialNumber: filters.serialNumber ?? undefined,
      itemTypeId: filters.itemTypeId ?? undefined,
      purityId: filters.purityId ?? undefined,
      categoryId: filters.categoryId ?? undefined,
      subCategoryId: filters.subCategoryId ?? undefined,
      status: filters.status ?? undefined,
      active: filters.active === null || filters.active === undefined ? undefined : filters.active,
    });
  }

  get(id: number): Observable<InventoryItem> {
    return this.api.get<InventoryItem>(`/inventory/items/${id}`);
  }

  create(request: InventoryItemRequest, context?: HttpContext): Observable<InventoryItem> {
    return this.api.post<InventoryItem>('/inventory/items', request, context);
  }

  update(id: number, request: InventoryItemRequest, context?: HttpContext): Observable<InventoryItem> {
    return this.api.put<InventoryItem>(`/inventory/items/${id}`, request, context);
  }

  setActive(id: number, active: boolean): Observable<InventoryItem> {
    return this.api.patch<InventoryItem>(`/inventory/items/${id}/status`, { active });
  }

  delete(id: number): Observable<void> {
    return this.api.delete<void>(`/inventory/items/${id}`);
  }

  /**
   * The next unused serial number, as a suggestion.
   *
   * Not a reservation: if two people open the form at the same moment they both
   * see the same number, and the second save is rejected with a clear message.
   * Reserving numbers would leave permanent gaps every time a form was
   * abandoned, which a physical serial sequence cannot have.
   */
  nextSerialNumber(): Observable<string> {
    return this.api
      .get<{ serialNumber: string }>('/inventory/items/next-serial')
      .pipe(map((response) => response.serialNumber));
  }
}
