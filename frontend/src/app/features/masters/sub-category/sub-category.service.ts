import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { CrudApiService } from '../../../core/services/crud-api.service';
import { Lookup } from '../../../shared/models/lookup.model';
import { SubCategory, SubCategoryRequest } from '../../../shared/models/master.model';

@Injectable({ providedIn: 'root' })
export class SubCategoryService extends CrudApiService<SubCategory, SubCategoryRequest> {
  protected readonly resourcePath = '/sub-categories';

  /**
   * The cascading dropdown source.
   *
   * Scoped to a category by design: offering every sub category in the shop and
   * letting the server reject the mismatch would be a worse experience than not
   * offering the wrong ones at all.
   */
  lookupByCategory(categoryId: number): Observable<Lookup[]> {
    return this.lookup({ categoryId });
  }
}
