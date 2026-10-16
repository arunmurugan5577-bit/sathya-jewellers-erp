import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { OldMetalTransaction } from '../../shared/models/sales.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';

/**
 * The PURCHASE BILL as printed: seller header, bill number and date, the
 * customer the metal came from, the lines with their weights, and the total.
 *
 * <p>Its own component for the same reason the tax invoice has one: the single
 * bill screen and a printed run of them show the same document, and a second
 * copy of the design would drift from the first the moment either changed.
 *
 * <p>The sheet is a whole A4 page, laid out exactly as the invoice is, so the
 * two go in one file without looking like different shapes.
 */
@Component({
  selector: 'app-old-metal-bill',
  standalone: true,
  imports: [DatePipe, InrPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let b = bill();
    <article class="bill">
      @if (b.status === 'CANCELLED') {
        <div class="bill__void">CANCELLED</div>
      }
      <header class="bill__seller">
        <img class="bill__logo" src="assets/brand/logo-icon.png" alt="" width="384" height="384" />
        <div>
          <h1 class="bill__shop">{{ b.sellerName }}</h1>
          @if (b.sellerAddress) { <p>{{ b.sellerAddress }}</p> }
          <p>
            @if (b.sellerMobile) { <span>Cell: {{ b.sellerMobile }}</span> }
            @if (b.sellerGstin) { <span class="gstin">GSTIN: {{ b.sellerGstin }}</span> }
          </p>
        </div>
      </header>

      <div class="bill__title">PURCHASE BILL</div>

      <section class="bill__meta">
        <div>
          <div><span class="label">From:</span> <strong>{{ b.customerName }}</strong></div>
          @if (b.customerAddress) { <div>{{ b.customerAddress }}</div> }
          @if (b.customerMobile) { <div>Cell: {{ b.customerMobile }}</div> }
        </div>
        <div class="right">
          <div><span class="label">No.</span> <strong>{{ b.transactionNumber }}</strong></div>
          <div><span class="label">Date:</span> {{ b.transactionDate | date: 'dd-MM-yyyy' }}</div>
        </div>
      </section>

      <table class="bill__lines">
        <thead>
          <tr>
            <th>S.No</th><th class="left">Particulars</th><th>HSN</th>
            <th>Net Wt</th><th>Gross Wt</th><th>Rate</th><th>Amount</th>
          </tr>
        </thead>
        <tbody>
          @for (item of b.items; track item.lineNumber) {
            <tr>
              <td class="center">{{ item.lineNumber }}</td>
              <td class="left">
                {{ item.particulars }}
                <div class="sub">{{ item.itemTypeName }} {{ item.purityName || '' }}</div>
              </td>
              <td class="center">{{ item.hsnCode }}</td>
              <td>{{ item.netWeightGrams.toFixed(3) }}</td>
              <td>{{ item.grossWeightGrams != null ? item.grossWeightGrams.toFixed(3) : '-' }}</td>
              <td>{{ item.ratePerGram | inr: 'whole' }}</td>
              <td class="strong">{{ item.amount | inr: 'whole' }}</td>
            </tr>
          }
        </tbody>
        <tfoot>
          <tr class="grand"><td colspan="6" class="right">TOTAL</td><td>{{ b.totalAmount | inr }}</td></tr>
        </tfoot>
      </table>

      <p class="bill__words"><span class="label">Rupees:</span> {{ b.amountInWords }}</p>

      <!-- Takes up whatever is left of the sheet, so the signatures land at the
           foot of the page rather than under the last figure. -->
      <div class="bill__fill"></div>

      <footer class="bill__sign">
        <!-- Both sign: the customer sold the metal and was paid for it. -->
        <div>Seller's signature</div>
        <div>For {{ b.sellerName }}</div>
      </footer>
    </article>
  `,
  styles: [
    `
      :host { display: block; overflow-x: auto; }

      /*
       * The same sheet the tax invoice uses: a whole A4 page, 210 x 297 mm,
       * with the 10 mm margin @page sets as padding, so the content box is
       * 190 mm on screen and 190 mm on paper. A purchase bill and an invoice
       * go in the same file and should not be two different shapes.
       */
      .bill {
        position: relative;
        width: 210mm; min-height: 297mm; margin: 0 auto; padding: 10mm;
        box-sizing: border-box;
        display: flex; flex-direction: column;
        background: #fff; color: #1a1a1a; border: 1px solid #d8cfc0;
        font-size: 12.5px; line-height: 1.45;
      }
      .bill p { margin: 0; }
      .bill__void {
        position: absolute; top: 40%; left: 50%; transform: translate(-50%, -50%) rotate(-20deg);
        font-size: 64px; font-weight: 800; color: rgba(180, 0, 0, 0.18); pointer-events: none;
      }
      .bill__seller {
        display: flex; gap: 14px; align-items: center; justify-content: center; text-align: center;
        border-bottom: 2px solid #7a1f2b; margin-top: 12mm; padding-bottom: 5mm;
      }
      .bill__logo { width: 72px; height: 72px; border-radius: 50%; flex-shrink: 0; }
      .bill__shop { margin: 0; font-size: 25px; color: #7a1f2b; text-transform: uppercase; letter-spacing: 0.04em; }
      /* On its own line: a GSTIN is 15 characters and crowds the phone number. */
      .gstin { display: block; margin-top: 2px; font-weight: 600; letter-spacing: 0.03em; }
      .bill__title {
        text-align: center; margin: 7mm auto; font-weight: 700; letter-spacing: 0.2em;
        background: #7a1f2b; color: #fff; width: fit-content; padding: 4px 18px; border-radius: 3px;
      }
      .bill__meta { display: flex; justify-content: space-between; gap: 16px; margin: 0 0 7mm; }
      .label { color: #6b5f50; }
      .bill__lines { width: 100%; border-collapse: collapse; }
      .bill__lines th, .bill__lines td {
        border: 1px solid #bfb3a0; padding: 2.2mm 2mm; text-align: right; font-variant-numeric: tabular-nums;
      }
      .bill__lines th { background: #f3ece0; text-align: center; font-weight: 600; }
      .left { text-align: left !important; }
      .center { text-align: center !important; }
      .right { text-align: right; }
      .strong { font-weight: 600; }
      .sub { font-size: 10px; color: #6b5f50; }
      .grand td { font-weight: 800; background: #f3ece0; }
      .bill__words { margin: 7mm 0 0; }
      .bill__fill { flex: 1 1 auto; min-height: 22mm; }
      .bill__sign { display: flex; justify-content: space-between; font-weight: 600; }
      .bill__sign > div { border-top: 1px solid #bfb3a0; padding-top: 4px; min-width: 55mm; text-align: center; }

      /*
       * "screen and", and it matters: a width query is measured against the
       * page when printing, and A4 is about 794 px, so an unscoped one hands
       * the printer the phone layout.
       */
      @media screen and (max-width: 900px) {
        .bill { width: auto; min-height: 0; padding: 12px; }
        .bill__meta { flex-direction: column; }
        .bill__fill { display: none; }
      }

      /* Paper states its own layout rather than relying on another block not matching. */
      @media print {
        .bill { border: 0; width: auto; min-height: 100%; padding: 0; }
        .bill__meta { display: flex; flex-direction: row; justify-content: space-between; }
        .bill__fill { display: block; }
        .bill__lines { display: table; width: 100%; }
        .bill__lines thead { display: table-header-group; }
        .bill__lines tr { break-inside: avoid; page-break-inside: avoid; }
        .bill__lines tfoot { break-inside: avoid; page-break-inside: avoid; }
      }
    `,
  ],
})
export class OldMetalBillComponent {
  readonly bill = input.required<OldMetalTransaction>();
}
