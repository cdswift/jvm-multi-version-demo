package com.acme.model;

public class Item {
    private String sku;
    private int qty;

    public Item() {
    }

    public Item(String sku, int qty) {
        this.sku = sku;
        this.qty = qty;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    @Override
    public String toString() {
        return sku + " x" + qty;
    }
}
