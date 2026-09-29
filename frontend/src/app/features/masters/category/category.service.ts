import { Injectable } from '@angular/core';

import { CrudApiService } from '../../../core/services/crud-api.service';
import { Category, NamedMasterRequest } from '../../../shared/models/master.model';

@Injectable({ providedIn: 'root' })
export class CategoryService extends CrudApiService<Category, NamedMasterRequest> {
  protected readonly resourcePath = '/categories';
}
