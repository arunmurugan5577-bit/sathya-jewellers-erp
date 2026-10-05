import { HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService, QueryParams } from '../../core/services/api.service';
import { Page, PageQuery } from '../../shared/models/page.model';
import {
  WholesaleBalance,
  WholesaleCalculation,
  WholesaleEstimate,
  WholesaleItemLookup,
  WholesaleRequest,
  WholesaleStatus,
  WholesaleSummary,
} from '../../shared/models/wholesale.model';

function paging(query: PageQuery): QueryParams {
  return {
    page: query.page,
    size: query.size,
    sort: query.sort ? `${query.sort},${query.direction ?? 'desc'}` : undefined,
  };
}

@Injectable({ providedIn: 'root' })
export class WholesaleApiService {
  private readonly api = inject(ApiService);

  list(
    query: PageQuery,
    filters: { fromDate?: string | null; toDate?: string | null; customerId?: number | null; status?: WholesaleStatus | null },
  ): Observable<Page<WholesaleSummary>> {
    return this.api.get<Page<WholesaleSummary>>('/wholesale/estimates', {
      ...paging(query),
      fromDate: filters.fromDate,
      toDate: filters.toDate,
      customerId: filters.customerId,
      status: filters.status,
    });
  }

  findById(id: number): Observable<WholesaleEstimate> {
    return this.api.get<WholesaleEstimate>(`/wholesale/estimates/${id}`);
  }

  /** The barcode on a tag carries the serial, so a scan lands here. */
  lookupItem(serialNumber: string, context?: HttpContext): Observable<WholesaleItemLookup> {
    return this.api.get<WholesaleItemLookup>(
      `/wholesale/estimates/item-lookup/${encodeURIComponent(serialNumber)}`,
      undefined,
      context,
    );
  }

  balance(customerId: number, pureRatePerGram?: number | null): Observable<WholesaleBalance> {
    return this.api.get<WholesaleBalance>(`/wholesale/estimates/balance/${customerId}`, { pureRatePerGram });
  }

  calculate(request: WholesaleRequest, context?: HttpContext): Observable<WholesaleCalculation> {
    return this.api.post<WholesaleCalculation>('/wholesale/estimates/calculate', request, context);
  }

  create(request: WholesaleRequest, context?: HttpContext): Observable<WholesaleEstimate> {
    return this.api.post<WholesaleEstimate>('/wholesale/estimates', request, context);
  }

  cancel(id: number, reason: string): Observable<WholesaleEstimate> {
    return this.api.post<WholesaleEstimate>(`/wholesale/estimates/${id}/cancel`, { reason });
  }
}
