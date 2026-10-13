import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Sale } from '../../shared/models/sales.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';

/**
 * The TAX INVOICE as printed, laid out like the shop's paper receipt book:
 * seller header, invoice number and date, customer, particulars with weights,
 * TOTAL, CGST / SGST, discount, GRAND TOTAL and the amount in words.
 *
 * Every figure comes from the saved invoice. Shop details are the snapshot taken
 * when the invoice was issued, so a later change of address does not rewrite
 * history.
 */
@Component({
  selector: 'app-invoice-print',
  standalone: true,
  imports: [InrPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let s = sale();
    <article class="invoice">
      @if (s.status === 'CANCELLED') {
        <div class="invoice__void">CANCELLED</div>
      }
      <header class="invoice__seller">
        <img class="invoice__logo" src="assets/brand/logo-icon.png" alt="" width="384" height="384" />
        <div>
          <h1 class="invoice__shop">{{ s.sellerName }}</h1>
          @if (s.sellerAddress) { <p>{{ s.sellerAddress }}</p> }
          <p>
            @if (s.sellerMobile) { <span>Cell: {{ s.sellerMobile }}</span> }
            @if (s.sellerGstin) { <span class="gstin">GSTIN: {{ s.sellerGstin }}</span> }
          </p>
        </div>
      </header>

      <div class="invoice__title">TAX INVOICE</div>

      <section class="invoice__meta">
        <div class="invoice__to">
          <div><span class="label">To:</span> <strong>{{ s.customerName }}</strong></div>
          @if (s.customerAddress) { <div>{{ s.customerAddress }}</div> }
          @if (s.customerMobile) { <div>Cell: {{ s.customerMobile }}</div> }
          @if (s.customerGstin) { <div>GSTIN: {{ s.customerGstin }}</div> }
        </div>
        <div class="invoice__number">
          <div><span class="label">No.</span> <strong>{{ s.invoiceNumber }}</strong></div>
          <div><span class="label">Date:</span> {{ s.invoiceDate | date: 'dd-MM-yyyy' }}</div>
        </div>
      </section>

      <table class="invoice__lines">
        <thead>
          <tr>
            <th>S.No</th>
            <th class="left">Particulars</th>
            <th>HSN</th>
            <th>Net Wt</th>
            <th>Wastage</th>
            <th>Gross Wt</th>
            <th>Rate</th>
            <th>Making</th>
            <th>Amount</th>
          </tr>
        </thead>
        <tbody>
          @for (line of s.items; track line.lineNumber) {
            <tr>
              <td class="center">{{ line.lineNumber }}</td>
              <td class="left">
                {{ line.particulars }}
                <!-- Purity only: the serial number is the shop's own stock
                     reference and has no place on the customer's bill. -->
                <div class="sub">{{ line.purityName }}</div>
              </td>
              <td class="center">{{ line.hsnCode }}</td>
              <td>{{ line.netWeightGrams.toFixed(3) }}</td>
              <td>{{ line.wastagePercentage }}%<div class="sub">{{ line.wastageWeightGrams.toFixed(3) }}</div></td>
              <td>{{ line.grossWeightGrams.toFixed(3) }}</td>
              <td>{{ line.ratePerGram | inr: 'whole' }}</td>
              <td>{{ line.makingCharge | inr: 'whole' }}</td>
              <td class="strong">{{ line.amount | inr: 'whole' }}</td>
            </tr>
          }
        </tbody>
        <tfoot>
          <tr><td colspan="8" class="right">TOTAL</td><td class="strong">{{ s.totals.subtotal | inr: 'whole' }}</td></tr>
          <tr><td colspan="8" class="right">Add: CGST {{ halfRate() }}</td><td>{{ s.totals.cgstAmount | inr }}</td></tr>
          <tr><td colspan="8" class="right">Add: SGST {{ halfRate() }}</td><td>{{ s.totals.sgstAmount | inr }}</td></tr>
          @if (s.totals.discountAmount > 0) {
            <tr><td colspan="8" class="right">Discount</td><td>- {{ s.totals.discountAmount | inr }}</td></tr>
          }
          <tr class="grand"><td colspan="8" class="right">GRAND TOTAL</td><td>{{ s.totals.grandTotal | inr }}</td></tr>
          @for (adjustment of activeAdjustments(); track adjustment.id) {
            <tr><td colspan="8" class="right">Less: Old gold / silver ({{ adjustment.transactionNumber }})</td><td>- {{ adjustment.adjustmentAmount | inr }}</td></tr>
          }
          @if (s.totals.roundOffAmount !== 0) {
            <tr><td colspan="8" class="right">Round off</td><td>{{ s.totals.roundOffAmount | inr }}</td></tr>
          }
          @if (s.totals.oldMetalAdjustmentAmount > 0 || s.totals.roundOffAmount !== 0) {
            <tr class="grand"><td colspan="8" class="right">NET PAYABLE</td><td>{{ s.totals.netPayable | inr }}</td></tr>
          }
        </tfoot>
      </table>

      <p class="invoice__words"><span class="label">Rupees:</span> {{ s.totals.grandTotalInWords }}</p>
      @if (s.totals.netPayable !== s.totals.grandTotal) {
        <p class="invoice__words"><span class="label">Net payable:</span> {{ s.totals.netPayableInWords }}</p>
      }
      @if (s.totals.balanceAmount > 0 && s.status !== 'CANCELLED') {
        <p class="invoice__words">Paid {{ s.totals.amountPaid | inr }} &middot; <strong>Balance {{ s.totals.balanceAmount | inr }}</strong></p>
      }

      <!-- Takes up whatever is left of the sheet, so the shop's signature line
           lands at the bottom of the page rather than under the last figure. -->
      <div class="invoice__fill"></div>

      <footer class="invoice__sign">
        <div>For {{ s.sellerName }}</div>
      </footer>
    </article>
  `,
  styles: [
    `
      :host { display: block; overflow-x: auto; }
      /*
       * One A4 sheet, on screen as well as on paper.
       *
       * The whole page, 210 x 297 mm, with the same 10 mm margin _print.scss
       * sets in @page as padding. So the content box is 190 mm wide on screen
       * and 190 mm wide on paper, and the preview is the sheet - rather than
       * being a little narrower and quietly lying about where the columns fall.
       * border-box, or the padding would be added to the 210 and overflow.
       *
       * The column layout is what lets .invoice__fill push the signature to the
       * foot of the page: without it a one-line bill ended halfway up and left
       * the rest of the sheet blank.
       */
      .invoice {
        position: relative;
        width: 210mm; min-height: 297mm; margin: 0 auto; padding: 10mm;
        box-sizing: border-box;
        display: flex; flex-direction: column;
        background: #fff; color: #1a1a1a; border: 1px solid #d8cfc0;
        font-size: 12.5px; line-height: 1.45;
      }
      .invoice__void {
        position: absolute; top: 40%; left: 50%; transform: translate(-50%, -50%) rotate(-20deg);
        font-size: 64px; font-weight: 800; color: rgba(180, 0, 0, 0.18); letter-spacing: 0.1em; pointer-events: none;
      }
      .invoice__seller { display: flex; gap: 14px; align-items: center; justify-content: center; text-align: center; border-bottom: 2px solid #7a1f2b; padding-bottom: 8px; }
      .invoice__seller p { margin: 0; }
      .invoice__logo { width: 72px; height: 72px; border-radius: 50%; flex-shrink: 0; }
      .invoice__shop { margin: 0; font-size: 25px; color: #7a1f2b; letter-spacing: 0.04em; text-transform: uppercase; }
      /* On its own line: a GSTIN is 15 characters and crowds the phone number
         when the two share one. */
      .gstin { display: block; margin-top: 2px; font-weight: 600; letter-spacing: 0.03em; }
      .invoice__title { text-align: center; margin: 8px auto; font-weight: 700; letter-spacing: 0.2em; background: #7a1f2b; color: #fff; width: fit-content; padding: 3px 18px; border-radius: 3px; }
      .invoice__meta { display: flex; justify-content: space-between; gap: 16px; margin: 10px 0; }
      .invoice__number { text-align: right; white-space: nowrap; }
      .label { color: #6b5f50; }
      .invoice__lines { width: 100%; border-collapse: collapse; }
      .invoice__lines th, .invoice__lines td { border: 1px solid #bfb3a0; padding: 5px 6px; text-align: right; font-variant-numeric: tabular-nums; vertical-align: top; }
      .invoice__lines th { background: #f3ece0; text-align: center; font-weight: 600; }
      .invoice__lines .left { text-align: left; }
      .invoice__lines .center { text-align: center; }
      .invoice__lines .right { text-align: right; }
      .invoice__lines .strong { font-weight: 600; }
      .invoice__lines .sub { font-size: 10px; color: #6b5f50; }
      .invoice__lines tfoot td { border-top: 0; }
      .invoice__lines tr.grand td { font-weight: 800; font-size: 13px; background: #f3ece0; }
      .invoice__words { margin: 8px 0 0; }
      /* Never shrinks below nothing, so a long invoice still pages properly. */
      .invoice__fill { flex: 1 1 auto; min-height: 16mm; }
      .invoice__sign { display: flex; justify-content: flex-end; font-weight: 600; }
      /* Room to actually sign above the line. */
      .invoice__sign > div { border-top: 1px solid #bfb3a0; padding-top: 4px; min-width: 55mm; text-align: center; }
      @media (max-width: 900px) {
        /* A phone cannot show 210 mm, so the sheet stops pretending to be one. */
        .invoice { width: auto; min-height: 0; padding: 12px; }
        .invoice__lines { display: block; overflow-x: auto; }
        .invoice__meta { flex-direction: column; }
        .invoice__number { text-align: left; }
        .invoice__fill { display: none; }
      }

      @media print {
        /* @page already supplies the 10 mm margin, so the sheet adds none of
           its own and simply fills what is left. */
        .invoice { border: 0; width: auto; min-height: 100%; padding: 0; }
        /* A bill long enough to run over carries its column headings on to the
           next sheet, and no row is split down the middle. */
        .invoice__lines thead { display: table-header-group; }
        .invoice__lines tr { break-inside: avoid; page-break-inside: avoid; }
        .invoice__lines tfoot { break-inside: avoid; page-break-inside: avoid; }
      }
    `,
  ],
})
export class InvoicePrintComponent {
  readonly sale = input.required<Sale>();


  protected readonly activeAdjustments = computed(() =>
    this.sale().oldMetalAdjustments.filter((adjustment) => adjustment.status === 'ACTIVE' || this.sale().status === 'CANCELLED'),
  );

  /** "1.5%" when every line shares one GST rate - the usual case - otherwise blank. */
  protected readonly halfRate = computed(() => {
    const rates = new Set(this.sale().items.map((line) => line.gstPercentage));
    return rates.size === 1 ? `${[...rates][0] / 2}%` : '';
  });
}
