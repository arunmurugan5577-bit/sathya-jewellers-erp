import { Injectable } from '@angular/core';

import { CrudApiService } from '../../../core/services/crud-api.service';
import { ItemType, NamedMasterRequest } from '../../../shared/models/master.model';

@Injectable({ providedIn: 'root' })
export class ItemTypeService extends CrudApiService<ItemType, NamedMasterRequest> {
  protected readonly resourcePath = '/item-types';
}
