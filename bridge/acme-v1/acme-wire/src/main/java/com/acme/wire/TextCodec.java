package com.acme.wire;

import com.acme.model.Customer;
import com.acme.model.Item;
import com.acme.model.Order;
import com.acme.model.Status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Protocol 1.0 encoding: one pipe-delimited line of UTF-8 text.
 * <pre>id|amountCents|status|customerName|customerEmail|sku:qty,sku:qty</pre>
 */
public class TextCodec implements OrderCodec {

    @Override
    public String name() {
        return "text/pipe-delimited";
    }

    @Override
    public byte[] encode(Order order) {
        StringBuilder items = new StringBuilder();
        for (Item item : order.getItems()) {
            if (items.length() > 0) {
                items.append(',');
            }
            items.append(item.getSku()).append(':').append(item.getQty());
        }
        String line = String.join("|",
                order.getId(),
                Long.toString(order.getAmountCents()),
                order.getStatus().name(),
                order.getCustomer().getName(),
                order.getCustomer().getEmail(),
                items);
        return line.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Order decode(byte[] payload) {
        String[] f = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
        if (f.length != 6) {
            throw new IllegalArgumentException("Expected 6 fields, got " + f.length);
        }
        Customer customer = new Customer();
        customer.setName(f[3]);
        customer.setEmail(f[4]);

        List<Item> items = new ArrayList<>();
        if (!f[5].isEmpty()) {
            for (String entry : f[5].split(",")) {
                String[] skuQty = entry.split(":");
                items.add(new Item(skuQty[0], Integer.parseInt(skuQty[1])));
            }
        }

        Order order = new Order();
        order.setId(f[0]);
        order.setAmountCents(Long.parseLong(f[1]));
        order.setStatus(Status.valueOf(f[2]));
        order.setCustomer(customer);
        order.setItems(items);
        return order;
    }
}
