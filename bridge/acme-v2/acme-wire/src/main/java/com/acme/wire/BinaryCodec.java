package com.acme.wire;

import com.acme.model.Customer;
import com.acme.model.LineItem;
import com.acme.model.Order;
import com.acme.model.Status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Protocol 2.0 encoding: compact binary.
 * <pre>
 * magic 0xAC02 | id | amount (unscaled long + scale byte) | status byte
 * | name | email | line count | (sku | quantity)* | channel
 * </pre>
 * Strings are Java "modified UTF-8" with a 2-byte length (DataOutput.writeUTF).
 */
public class BinaryCodec implements OrderCodec {

    private static final short MAGIC = (short) 0xAC02;

    @Override
    public String name() {
        return "binary/v2";
    }

    @Override
    public byte[] encode(Order order) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeShort(MAGIC);
            out.writeUTF(order.getId());
            out.writeLong(order.getAmount().unscaledValue().longValueExact());
            out.writeByte(order.getAmount().scale());
            out.writeByte(order.getStatus().ordinal());
            out.writeUTF(order.getCustomer().name());
            out.writeUTF(order.getCustomer().email());
            out.writeShort(order.getLines().size());
            for (LineItem line : order.getLines()) {
                out.writeUTF(line.getSku());
                out.writeInt(line.getQuantity());
            }
            out.writeUTF(order.getChannel() == null ? "" : order.getChannel());
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Order decode(byte[] payload) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            if (in.readShort() != MAGIC) {
                throw new IllegalArgumentException("Not a protocol 2.0 payload");
            }
            Order order = new Order();
            order.setId(in.readUTF());
            long unscaled = in.readLong();
            order.setAmount(BigDecimal.valueOf(unscaled, in.readByte()));
            order.setStatus(Status.values()[in.readByte()]);
            order.setCustomer(new Customer(in.readUTF(), in.readUTF()));
            int count = in.readShort();
            List<LineItem> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(new LineItem(in.readUTF(), in.readInt()));
            }
            order.setLines(lines);
            String channel = in.readUTF();
            order.setChannel(channel.isEmpty() ? null : channel);
            return order;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
