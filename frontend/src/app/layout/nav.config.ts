import { Permissions } from '../core/auth/permissions';

/**
 * One entry in the sidebar.
 *
 * `permissions` lists the codes that make the entry relevant; holding any one of
 * them is enough. An entry with no permissions is visible to every signed-in
 * user.
 */
export interface NavItem {
  label: string;
  route: string;
  /** SVG path data for the 20x20 icon. */
  icon: string;
  permissions?: readonly string[];
}

export interface NavSection {
  label?: string;
  items: NavItem[];
}

/* A small hand-picked icon set, inlined as path data. Pulling in an icon font or
 * a library for eleven glyphs would cost more to load than the whole sidebar. */
const icons = {
  dashboard: 'M3 3h7v7H3V3zm11 0h7v4h-7V3zM3 14h7v7H3v-7zm11-3h7v10h-7V11z',
  tag: 'M3 3h8l10 10-8 8L3 11V3zm3.5 3.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z',
  layers: 'M12 2l9 5-9 5-9-5 9-5zm9 10l-9 5-9-5m18 5l-9 5-9-5',
  branch: 'M6 3v10a4 4 0 0 0 4 4h8M6 3a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm12 12a2 2 0 1 1 0 4 2 2 0 0 1 0-4z',
  receipt: 'M5 3h14v18l-3-2-2 2-2-2-2 2-2-2-3 2V3zm3 5h8M8 12h8M8 16h5',
  gauge: 'M12 21a9 9 0 1 1 9-9M12 12l5-3',
  box: 'M3 7l9-4 9 4v10l-9 4-9-4V7zm9 4l9-4m-9 4L3 7m9 4v10',
  plus: 'M12 5v14M5 12h14',
  cart: 'M3 3h2l2.4 12.2a2 2 0 0 0 2 1.8h8.2a2 2 0 0 0 2-1.6L21 8H6M9 21h.01M18 21h.01',
  invoice: 'M6 2h9l5 5v15H6V2zm9 0v5h5M9 13h8M9 17h6',
  coins: 'M12 3c4.4 0 8 1.3 8 3s-3.6 3-8 3-8-1.3-8-3 3.6-3 8-3zM4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6',
  person: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  chart: 'M3 3v18h18M7 15l4-4 3 3 6-6',
  barcode: 'M3 5v14M6 5v14M9 5v10M12 5v14M15 5v10M18 5v14M21 5v14',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm13 10v-2a4 4 0 0 0-3-3.87',
  settings:
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm8.4-3a8.4 8.4 0 0 0-.13-1.4l2-1.5-2-3.4-2.3 1a8.5 8.5 0 0 0-2.4-1.4L15.2 2h-4l-.37 2.3a8.5 8.5 0 0 0-2.4 1.4l-2.3-1-2 3.4 2 1.5a8.4 8.4 0 0 0 0 2.8l-2 1.5 2 3.4 2.3-1a8.5 8.5 0 0 0 2.4 1.4l.37 2.3h4l.37-2.3a8.5 8.5 0 0 0 2.4-1.4l2.3 1 2-3.4-2-1.5c.09-.46.13-.93.13-1.4z',
} as const;

/**
 * The navigation tree.
 *
 * Sections are hidden entirely when a user can see none of their entries, so a
 * staff member with inventory access only sees "Inventory" - not an empty
 * "Masters" heading advertising screens they cannot open.
 */
export const NAV_SECTIONS: readonly NavSection[] = [
  {
    items: [{ label: 'Dashboard', route: '/dashboard', icon: icons.dashboard }],
  },
  {
    label: 'Sales',
    items: [
      { label: 'New Sale', route: '/sales/new', icon: icons.cart, permissions: [Permissions.SALES_CREATE] },
      { label: 'Invoices', route: '/sales', icon: icons.invoice, permissions: [Permissions.SALES_VIEW] },
      {
        label: 'Wholesale',
        route: '/wholesale',
        icon: icons.invoice,
        permissions: [Permissions.WHOLESALE_VIEW],
      },
      { label: 'Old Gold / Silver', route: '/old-metal', icon: icons.coins, permissions: [Permissions.OLD_METAL_VIEW] },
      { label: 'Customers', route: '/customers', icon: icons.person, permissions: [Permissions.CUSTOMER_VIEW] },
    ],
  },
  {
    label: 'Reports',
    items: [
      {
        label: 'Sales & Stock',
        route: '/reports',
        icon: icons.chart,
        permissions: [
          Permissions.REPORT_SALES_VIEW,
          Permissions.REPORT_STOCK_VIEW,
          Permissions.REPORT_WHOLESALE_VIEW,
        ],
      },
    ],
  },
  {
    label: 'Masters',
    items: [
      {
        label: 'Item Types',
        route: '/masters/item-types',
        icon: icons.tag,
        permissions: [Permissions.ITEM_TYPE_VIEW],
      },
      {
        label: 'Categories',
        route: '/masters/categories',
        icon: icons.layers,
        permissions: [Permissions.CATEGORY_VIEW],
      },
      {
        label: 'Sub Categories',
        route: '/masters/sub-categories',
        icon: icons.branch,
        permissions: [Permissions.SUB_CATEGORY_VIEW],
      },
      {
        label: 'HSN Codes',
        route: '/masters/hsn-codes',
        icon: icons.receipt,
        permissions: [Permissions.HSN_VIEW],
      },
      {
        label: 'Purities',
        route: '/masters/purities',
        icon: icons.gauge,
        permissions: [Permissions.PURITY_VIEW],
      },
      {
        label: 'Serial Numbering',
        route: '/masters/serial-numbering',
        icon: icons.barcode,
        permissions: [Permissions.INVENTORY_SERIAL_EDIT],
      },
      {
        label: 'Users',
        route: '/masters/users',
        icon: icons.users,
        permissions: [Permissions.USER_VIEW],
      },
    ],
  },
  {
    label: 'Inventory',
    items: [
      {
        label: 'Add Item',
        route: '/inventory/new',
        icon: icons.plus,
        permissions: [Permissions.INVENTORY_CREATE],
      },
      {
        label: 'Item List',
        route: '/inventory',
        icon: icons.box,
        permissions: [Permissions.INVENTORY_VIEW],
      },
      {
        label: 'Label Printing',
        route: '/labels',
        icon: icons.barcode,
        permissions: [Permissions.LABEL_VIEW],
      },
    ],
  },
  {
    label: 'Settings',
    items: [
      {
        label: 'Shop Details',
        route: '/settings/shop',
        icon: icons.settings,
        permissions: [Permissions.SHOP_SETTINGS_VIEW],
      },
    ],
  },
];
