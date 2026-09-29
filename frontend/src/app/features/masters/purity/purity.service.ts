import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { CrudApiService } from '../../../core/services/crud-api.service';
import { Lookup } from '../../../shared/models/lookup.model';
import { Purity, PurityRequest } from '../../../shared/models/master.model';

@Injectable({ providedIn: 'root' })
export class PurityService extends CrudApiService<Purity, PurityRequest> {
  protected readonly resourcePath = '/purities';

  /** Active purities of one item type - the cascading dropdown source. */
  lookupByItemType(itemTypeId: number): Observable<Lookup[]> {
    return this.lookup({ itemTypeId });
  }
}
