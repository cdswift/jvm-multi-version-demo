package com.acme.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * An order in protocol 2.0. Compared with 1.0:
 * <ul>
 *   <li>{@code amountCents} (long) became {@code amount} (BigDecimal, in dollars)</li>
 *   <li>{@code items} (List&lt;Item&gt;) became {@code lines} (List&lt;LineItem&gt;)</li>
 *   <li>{@code channel} is new</li>
 * </ul>
 */
public class Order {
    private String id;
    private BigDecimal amount;
    private Status status;
    private Customer customer;
    private List<LineItem> lines = new ArrayList<>();
    private String channel;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public List<LineItem> getLines() { return lines; }
    public void setLines(List<LineItem> lines) { this.lines = lines; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    @Override
    public String toString() {
        return "Order{id=" + id + ", amount=" + amount + ", status=" + status
                + ", customer=" + customer + ", lines=" + lines + ", channel=" + channel + "}";
    }
}
