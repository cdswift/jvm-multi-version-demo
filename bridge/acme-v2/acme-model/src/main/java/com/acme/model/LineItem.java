package com.acme.model;

/** Replaces 1.0's Item; "qty" was renamed "quantity". */
public class LineItem {
    private String sku;
    private int quantity;

    public LineItem() {
    }

    public LineItem(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    @Override
    public String toString() {
        return sku + " x" + quantity;
    }
}
