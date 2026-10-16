-- ===========================================================================
-- V29: SALES_DELETE now covers erasing an invoice, not only cancelling one.
--
-- Cancelling keeps the invoice and its number in the books and marks it
-- CANCELLED, which is what the GST record wants. The shop also needs to erase
-- one outright - a bill raised in error, before it ever reached a customer -
-- and to have its number come back so the next sale is not pushed along by a
-- mistake.
--
-- Both are the same authority over the same document, and the permission
-- screen offers one DELETE column per module, so they share SALES_DELETE
-- rather than inventing an action the screen cannot grant. The practical
-- effect is the shop's: the administrator holds it, and a counter user is
-- given it only if the shop wants counter staff erasing bills.
-- ===========================================================================

UPDATE permissions
   SET description = 'Cancel or delete a sales invoice'
 WHERE code = 'SALES_DELETE';
