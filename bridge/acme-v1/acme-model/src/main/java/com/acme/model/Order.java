package com.acme.model;

import java.util.ArrayList;
import java.util.List;

/** An order in protocol 1.0. Money is a whole number of cents. */
public class Order {
    private String id;
    private long amountCents;
    private Status status;
    private Customer customer;
    private List<Item> items = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    @Override
    public String toString() {
        return "Order{id=" + id + ", amountCents=" + amountCents + ", status=" + status
                + ", customer=" + customer + ", items=" + items + "}";
    }
}
