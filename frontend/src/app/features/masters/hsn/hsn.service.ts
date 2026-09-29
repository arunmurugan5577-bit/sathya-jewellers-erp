import { Injectable } from '@angular/core';

import { CrudApiService } from '../../../core/services/crud-api.service';
import { HsnCode, HsnCodeRequest } from '../../../shared/models/master.model';

@Injectable({ providedIn: 'root' })
export class HsnService extends CrudApiService<HsnCode, HsnCodeRequest> {
  protected readonly resourcePath = '/hsn-codes';
}
