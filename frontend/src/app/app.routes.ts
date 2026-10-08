import { Routes } from '@angular/router';

import { Permissions } from './core/auth/permissions';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { passwordChangeGuard } from './core/guards/password-change.guard';
import { permissionGuard } from './core/guards/permission.guard';

/**
 * Application routes.
 *
 * Everything authenticated hangs off the shell route, so the chrome is built
 * once. Each feature is lazily loaded: a user who only ever opens the inventory
 * list never downloads the user administration screens.
 *
 * Guards compose in order - authenticated, then password change enforced, then
 * the specific permission the screen needs.
 */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in - Jewellery ERP',
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'change-password',
    canActivate: [authGuard],
    title: 'Change password - Jewellery ERP',
    loadComponent: () =>
      import('./features/auth/change-password/change-password.component').then(
        (m) => m.ChangePasswordComponent,
      ),
  },
  {
    path: '',
    canActivate: [authGuard, passwordChangeGuard],
    canActivateChild: [authGuard, passwordChangeGuard],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },

      {
        path: 'dashboard',
        title: 'Dashboard - Jewellery ERP',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },

      // --- Sales -----------------------------------------------------------
      {
        path: 'sales/new',
        title: 'New sale - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.SALES_CREATE)],
        loadComponent: () => import('./features/sales/sale-editor.component').then((m) => m.SaleEditorComponent),
      },
      {
        path: 'sales',
        title: 'Sales - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.SALES_VIEW)],
        loadComponent: () => import('./features/sales/sale-list.component').then((m) => m.SaleListComponent),
      },
      {
        path: 'sales/:id',
        title: 'Invoice - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.SALES_VIEW)],
        loadComponent: () => import('./features/sales/sale-detail.component').then((m) => m.SaleDetailComponent),
      },
      // --- Wholesale -------------------------------------------------------
      {
        path: 'wholesale/new',
        title: 'New wholesale estimate - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.WHOLESALE_CREATE)],
        loadComponent: () =>
          import('./features/wholesale/wholesale-editor.component').then((m) => m.WholesaleEditorComponent),
      },
      {
        path: 'wholesale',
        title: 'Wholesale - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.WHOLESALE_VIEW)],
        loadComponent: () =>
          import('./features/wholesale/wholesale-list.component').then((m) => m.WholesaleListComponent),
      },
      {
        path: 'wholesale/:id',
        title: 'Wholesale estimate - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.WHOLESALE_VIEW)],
        loadComponent: () =>
          import('./features/wholesale/wholesale-detail.component').then((m) => m.WholesaleDetailComponent),
      },
      {
        path: 'old-metal/new',
        title: 'Old gold purchase - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.OLD_METAL_CREATE)],
        loadComponent: () => import('./features/old-metal/old-metal-form.component').then((m) => m.OldMetalFormComponent),
      },
      {
        path: 'old-metal',
        title: 'Old gold / silver - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.OLD_METAL_VIEW)],
        loadComponent: () => import('./features/old-metal/old-metal-list.component').then((m) => m.OldMetalListComponent),
      },
      {
        path: 'old-metal/:id',
        title: 'Purchase bill - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.OLD_METAL_VIEW)],
        loadComponent: () => import('./features/old-metal/old-metal-detail.component').then((m) => m.OldMetalDetailComponent),
      },
      {
        path: 'customers',
        title: 'Customers - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.CUSTOMER_VIEW)],
        loadComponent: () => import('./features/customers/customer-list.component').then((m) => m.CustomerListComponent),
      },

      // --- Labels ----------------------------------------------------------
      {
        path: 'labels',
        title: 'Label printing - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.LABEL_VIEW)],
        loadComponent: () =>
          import('./features/labels/label-print.component').then((m) => m.LabelPrintComponent),
      },

      // --- Reports ---------------------------------------------------------
      {
        path: 'reports',
        title: 'Reports - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.REPORT_SALES_VIEW, Permissions.REPORT_STOCK_VIEW)],
        loadComponent: () => import('./features/reports/reports.component').then((m) => m.ReportsComponent),
      },

      // --- Masters ---------------------------------------------------------
      {
        path: 'masters/item-types',
        title: 'Item Types - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.ITEM_TYPE_VIEW)],
        loadComponent: () =>
          import('./features/masters/item-type/item-type-list.component').then(
            (m) => m.ItemTypeListComponent,
          ),
      },
      {
        path: 'masters/categories',
        title: 'Categories - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.CATEGORY_VIEW)],
        loadComponent: () =>
          import('./features/masters/category/category-list.component').then(
            (m) => m.CategoryListComponent,
          ),
      },
      {
        path: 'masters/sub-categories',
        title: 'Sub Categories - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.SUB_CATEGORY_VIEW)],
        loadComponent: () =>
          import('./features/masters/sub-category/sub-category-list.component').then(
            (m) => m.SubCategoryListComponent,
          ),
      },
      {
        path: 'masters/hsn-codes',
        title: 'HSN Codes - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.HSN_VIEW)],
        loadComponent: () =>
          import('./features/masters/hsn/hsn-list.component').then((m) => m.HsnListComponent),
      },
      {
        path: 'masters/purities',
        title: 'Purities - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.PURITY_VIEW)],
        loadComponent: () =>
          import('./features/masters/purity/purity-list.component').then((m) => m.PurityListComponent),
      },

      // --- Users -----------------------------------------------------------
      {
        path: 'masters/serial-numbering',
        title: 'Serial numbering - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.INVENTORY_SERIAL_EDIT)],
        loadComponent: () =>
          import('./features/masters/serial-numbering/serial-numbering.component').then(
            (m) => m.SerialNumberingComponent,
          ),
      },
      {
        path: 'masters/users',
        title: 'Users - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.USER_VIEW)],
        loadComponent: () =>
          import('./features/masters/users/user-list.component').then((m) => m.UserListComponent),
      },
      {
        path: 'masters/users/:id/permissions',
        title: 'User permissions - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.USER_VIEW)],
        loadComponent: () =>
          import('./features/masters/users/user-permissions.component').then(
            (m) => m.UserPermissionsComponent,
          ),
      },

      // --- Inventory -------------------------------------------------------
      {
        path: 'inventory',
        title: 'Inventory - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.INVENTORY_VIEW)],
        loadComponent: () =>
          import('./features/inventory/item-list.component').then((m) => m.ItemListComponent),
      },
      {
        path: 'inventory/new',
        title: 'Add item - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.INVENTORY_CREATE)],
        loadComponent: () =>
          import('./features/inventory/item-form.component').then((m) => m.ItemFormComponent),
      },
      {
        path: 'inventory/:id',
        title: 'Item - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.INVENTORY_VIEW)],
        loadComponent: () =>
          import('./features/inventory/item-detail.component').then((m) => m.ItemDetailComponent),
      },
      {
        path: 'inventory/:id/edit',
        title: 'Edit item - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.INVENTORY_EDIT)],
        loadComponent: () =>
          import('./features/inventory/item-form.component').then((m) => m.ItemFormComponent),
      },

      // --- Settings --------------------------------------------------------
      {
        path: 'settings/shop',
        title: 'Shop details - Jewellery ERP',
        canActivate: [permissionGuard(Permissions.SHOP_SETTINGS_VIEW)],
        loadComponent: () =>
          import('./features/shop-settings/shop-settings.component').then(
            (m) => m.ShopSettingsComponent,
          ),
      },
    ],
  },

  { path: '**', redirectTo: '' },
];
