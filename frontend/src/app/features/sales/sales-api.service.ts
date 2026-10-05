import { HttpContext, HttpResponse } from '@angular/common/http';
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SUPPRESS_ERROR_TOAST } from '../../core/interceptors/error.interceptor';
import { ApiService, QueryParams } from '../../core/services/api.service';
import { Page, PageQuery } from '../../shared/models/page.model';
import {
  Customer,
  CustomerRequest,
  CustomerSummary,
  OldMetalOption,
  OldMetalRequest,
  OldMetalSummary,
  OldMetalTransaction,
  ReportPreview,
  Sale,
  SaleCalculation,
  SaleItemLookup,
  SalePaymentRequest,
  SaleRequest,
  SaleSummary,
} from '../../shared/models/sales.model';

function paging(query: PageQuery): QueryParams {
  return {
    page: query.page,
    size: query.size,
    sort: query.sort ? `${query.sort},${query.direction ?? 'desc'}` : undefined,
  };
}

@Injectable({ providedIn: 'root' })
export class CustomerApiService {
  private readonly api = inject(ApiService);

  list(query: PageQuery, search?: string | null, active?: boolean | null): Observable<Page<Customer>> {
    return this.api.get<Page<Customer>>('/customers', { ...paging(query), search, active });
  }

  lookup(q: string): Observable<CustomerSummary[]> {
    return this.api.get<CustomerSummary[]>('/customers/lookup', { q });
  }

  get(id: number): Observable<Customer> {
    return this.api.get<Customer>(`/customers/${id}`);
  }

  create(request: CustomerRequest, context?: HttpContext): Observable<Customer> {
    return this.api.post<Customer>('/customers', request, context);
  }

  update(id: number, request: CustomerRequest, context?: HttpContext): Observable<Customer> {
    return this.api.put<Customer>(`/customers/${id}`, request, context);
  }

  setActive(id: number, active: boolean): Observable<Customer> {
    return this.api.patch<Customer>(`/customers/${id}/status`, { active });
  }

  delete(id: number): Observable<void> {
    return this.api.delete<void>(`/customers/${id}`);
  }
}

export interface DocumentFilters {
  search?: string | null;
  status?: string | null;
  paymentStatus?: string | null;
  from?: string | null;
  to?: string | null;
}

@Injectable({ providedIn: 'root' })
export class OldMetalApiService {
  private readonly api = inject(ApiService);

  list(query: PageQuery, filters: DocumentFilters = {}): Observable<Page<OldMetalSummary>> {
    return this.api.get<Page<OldMetalSummary>>('/old-metal-transactions', {
      ...paging(query),
      search: filters.search,
      status: filters.status,
      from: filters.from,
      to: filters.to,
    });
  }

  usable(customerId: number): Observable<OldMetalOption[]> {
    return this.api.get<OldMetalOption[]>('/old-metal-transactions/usable', { customerId });
  }

  get(id: number): Observable<OldMetalTransaction> {
    return this.api.get<OldMetalTransaction>(`/old-metal-transactions/${id}`);
  }

  create(request: OldMetalRequest, context?: HttpContext): Observable<OldMetalTransaction> {
    return this.api.post<OldMetalTransaction>('/old-metal-transactions', request, context);
  }

  cancel(id: number, reason: string): Observable<OldMetalTransaction> {
    return this.api.post<OldMetalTransaction>(`/old-metal-transactions/${id}/cancel`, { reason });
  }
}

@Injectable({ providedIn: 'root' })
export class SaleApiService {
  private readonly api = inject(ApiService);

  list(query: PageQuery, filters: DocumentFilters = {}): Observable<Page<SaleSummary>> {
    return this.api.get<Page<SaleSummary>>('/sales', {
      ...paging(query),
      search: filters.search,
      status: filters.status,
      paymentStatus: filters.paymentStatus,
      from: filters.from,
      to: filters.to,
    });
  }

  lookupItem(serialNumber: string, context?: HttpContext): Observable<SaleItemLookup> {
    return this.api.get<SaleItemLookup>(`/sales/item-lookup/${encodeURIComponent(serialNumber)}`, undefined, context);
  }

  calculate(request: SaleRequest, context?: HttpContext): Observable<SaleCalculation> {
    return this.api.post<SaleCalculation>('/sales/calculate', request, context);
  }

  get(id: number): Observable<Sale> {
    return this.api.get<Sale>(`/sales/${id}`);
  }

  create(request: SaleRequest, context?: HttpContext): Observable<Sale> {
    return this.api.post<Sale>('/sales', request, context);
  }

  addPayment(id: number, request: SalePaymentRequest, context?: HttpContext): Observable<Sale> {
    return this.api.post<Sale>(`/sales/${id}/payments`, request, context);
  }

  cancel(id: number, reason: string): Observable<Sale> {
    return this.api.post<Sale>(`/sales/${id}/cancel`, { reason });
  }
}

export type ReportKind = 'stock' | 'sales' | 'wholesale';

@Injectable({ providedIn: 'root' })
export class ReportApiService {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);

  preview(kind: ReportKind, params: QueryParams, context?: HttpContext): Observable<ReportPreview> {
    return this.api.get<ReportPreview>(`/reports/${kind}/preview`, params, context);
  }

  /** The .xlsx as a blob, with headers so the server's file name can be used. */
  export(kind: ReportKind, params: QueryParams): Observable<HttpResponse<Blob>> {
    const clean: Record<string, string> = {};
    for (const [key, value] of Object.entries(params)) {
      if (value !== null && value !== undefined && value !== '') {
        clean[key] = String(value);
      }
    }
    return this.http.get(`${environment.apiUrl}/reports/${kind}/export`, {
      params: clean,
      observe: 'response',
      responseType: 'blob',
      // The page reads the error out of the blob itself; a generic toast would only add noise.
      context: new HttpContext().set(SUPPRESS_ERROR_TOAST, true),
    });
  }
}
