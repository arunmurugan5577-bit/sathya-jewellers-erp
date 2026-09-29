import { HttpClient, HttpContext, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ApiService } from '../../core/services/api.service';
import {
  LabelPreview,
  LabelPrintResult,
  LabelPrinters,
  LabelRequest,
  LabelSettings,
  LabelSettingsRequest,
} from '../../shared/models/label.model';

/**
 * Label printing API.
 *
 * The two document calls return HTML rather than JSON - the page the printer
 * gets - so they go through HttpClient directly with `responseType: 'text'`.
 */
@Injectable({ providedIn: 'root' })
export class LabelsApiService {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);

  settings(): Observable<LabelSettings> {
    return this.api.get<LabelSettings>('/labels/settings');
  }

  saveSettings(request: LabelSettingsRequest, context?: HttpContext): Observable<LabelSettings> {
    return this.api.put<LabelSettings>('/labels/settings', request, context);
  }

  /** Printers the server's machine can reach. */
  printers(): Observable<LabelPrinters> {
    return this.api.get<LabelPrinters>('/labels/printers');
  }

  /** Sends the labels straight to the label printer. No browser dialog. */
  print(request: LabelRequest, context?: HttpContext): Observable<LabelPrintResult> {
    return this.api.post<LabelPrintResult>('/labels/print', request, context);
  }

  /** Checks a selection: what each label will say, and anything unprintable. */
  preview(request: LabelRequest, context?: HttpContext): Observable<LabelPreview> {
    return this.api.post<LabelPreview>('/labels/preview', request, context);
  }

  /** The label document at true size, for the on-screen preview. Records nothing. */
  previewDocument(request: LabelRequest, context?: HttpContext): Observable<string> {
    return this.document('/labels/preview-document', request, context);
  }

  /** The document to hand to the printer. The run is recorded server-side. */
  printDocument(request: LabelRequest, context?: HttpContext): Observable<string> {
    return this.document('/labels/print-document', request, context);
  }

  private document(path: string, request: LabelRequest, context?: HttpContext): Observable<string> {
    return this.http.post(`${environment.apiUrl}${path}`, request, {
      responseType: 'text',
      // JSON too: a rejected selection comes back as the standard error body.
      headers: new HttpHeaders({ Accept: 'text/html, application/json' }),
      context,
    });
  }
}
