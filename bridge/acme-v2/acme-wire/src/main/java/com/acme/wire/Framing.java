package com.acme.wire;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Protocol 2.0 framing: a 4-byte big-endian length, then the payload. */
final class Framing {
    private Framing() {
    }

    static void write(OutputStream out, byte[] payload) throws IOException {
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(payload.length);
        data.write(payload);
    }

    static byte[] read(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        int length = data.readInt();   // throws EOFException when the peer disconnects
        byte[] payload = new byte[length];
        data.readFully(payload);
        return payload;
    }
}
