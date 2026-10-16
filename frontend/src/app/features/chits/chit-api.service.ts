import { HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService, QueryParams } from '../../core/services/api.service';
import {
  ChitAccount,
  ChitPaymentRequest,
  ChitScheme,
  ChitSchemeType,
  ChitStatus,
  ChitSummary,
  MatureChitRequest,
  OpenChitRequest,
  WithdrawChitRequest,
} from '../../shared/models/chit.model';
import { Page, PageQuery } from '../../shared/models/page.model';

function paging(query: PageQuery): QueryParams {
  return {
    page: query.page,
    size: query.size,
    sort: query.sort ? `${query.sort},${query.direction ?? 'desc'}` : undefined,
  };
}

@Injectable({ providedIn: 'root' })
export class ChitApiService {
  private readonly api = inject(ApiService);

  /** The schemes the shop offers, cheapest first. */
  schemes(): Observable<ChitScheme[]> {
    return this.api.get<ChitScheme[]>('/chits/schemes');
  }

  list(
    query: PageQuery,
    filters: {
      search?: string | null;
      status?: ChitStatus | null;
      schemeType?: ChitSchemeType | null;
      customerId?: number | null;
    },
  ): Observable<Page<ChitSummary>> {
    return this.api.get<Page<ChitSummary>>('/chits', {
      ...paging(query),
      search: filters.search,
      status: filters.status,
      schemeType: filters.schemeType,
      customerId: filters.customerId,
    });
  }

  findById(id: number): Observable<ChitAccount> {
    return this.api.get<ChitAccount>(`/chits/${id}`);
  }

  open(request: OpenChitRequest, context?: HttpContext): Observable<ChitAccount> {
    return this.api.post<ChitAccount>('/chits', request, context);
  }

  /** One instalment. The amount is the scheme's, so it is not sent. */
  pay(id: number, request: ChitPaymentRequest, context?: HttpContext): Observable<ChitAccount> {
    return this.api.post<ChitAccount>(`/chits/${id}/payments`, request, context);
  }

  /** Adds the shop's twelfth month and hands the balance to the customer. */
  mature(id: number, request: MatureChitRequest, context?: HttpContext): Observable<ChitAccount> {
    return this.api.post<ChitAccount>(`/chits/${id}/mature`, request, context);
  }

  /** Pays the chit out. The whole balance goes and the chit closes. */
  withdraw(id: number, request: WithdrawChitRequest, context?: HttpContext): Observable<ChitAccount> {
    return this.api.post<ChitAccount>(`/chits/${id}/withdraw`, request, context);
  }

  cancel(id: number, reason: string): Observable<ChitAccount> {
    return this.api.post<ChitAccount>(`/chits/${id}/cancel`, { reason });
  }

  /** Erases the chit. The number is freed when it was the last one issued. */
  delete(id: number): Observable<void> {
    return this.api.delete<void>(`/chits/${id}`);
  }
}
